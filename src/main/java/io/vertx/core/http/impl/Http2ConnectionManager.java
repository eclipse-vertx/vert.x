/*
 * Copyright (c) 2011-2013 The original author or authors
 *  ------------------------------------------------------
 *  All rights reserved. This program and the accompanying materials
 *  are made available under the terms of the Eclipse Public License v1.0
 *  and Apache License v2.0 which accompanies this distribution.
 *
 *      The Eclipse Public License is available at
 *      http://www.eclipse.org/legal/epl-v10.html
 *
 *      The Apache License v2.0 is available at
 *      http://www.opensource.org/licenses/apache2.0.php
 *
 *  You may elect to redistribute this code under either of these licenses.
 */

package io.vertx.core.http.impl;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.handler.codec.http2.AbstractHttp2ConnectionHandlerBuilder;
import io.netty.handler.codec.http2.DefaultHttp2Connection;
import io.netty.handler.codec.http2.DefaultHttp2Headers;
import io.netty.handler.codec.http2.Http2Connection;
import io.netty.handler.codec.http2.Http2ConnectionDecoder;
import io.netty.handler.codec.http2.Http2ConnectionEncoder;
import io.netty.handler.codec.http2.Http2ConnectionHandler;
import io.netty.handler.codec.http2.Http2EventAdapter;
import io.netty.handler.codec.http2.Http2Exception;
import io.netty.handler.codec.http2.Http2Flags;
import io.netty.handler.codec.http2.Http2FrameListener;
import io.netty.handler.codec.http2.Http2Headers;
import io.netty.handler.codec.http2.Http2Settings;
import io.netty.handler.ssl.ApplicationProtocolNames;
import io.netty.handler.ssl.ApplicationProtocolNegotiationHandler;
import io.netty.handler.ssl.SslHandler;
import io.netty.util.collection.IntObjectHashMap;
import io.netty.util.collection.IntObjectMap;
import io.vertx.core.Context;
import io.vertx.core.Handler;
import io.vertx.core.MultiMap;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.CaseInsensitiveHeaders;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpVersion;
import io.vertx.core.impl.ContextImpl;
import io.vertx.core.impl.ContextInternal;
import io.vertx.core.net.NetSocket;
import io.vertx.core.net.impl.SSLHelper;

import java.net.InetSocketAddress;
import java.util.ArrayDeque;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.BooleanSupplier;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public abstract class Http2ConnectionManager extends ConnectionManager {

  private final HttpClientImpl client;
  private final ConcurrentMap<TargetAddress, ClientConnection> connectionMap = new ConcurrentHashMap<>();

  public Http2ConnectionManager(HttpClientImpl client) {
    this.client = client;
  }

  @Override
  public void getConnection(int port, String host, HttpClientRequestImpl req, Handler<HttpClientStream> handler, Handler<Throwable> connectionExceptionHandler, ContextImpl clientContext, BooleanSupplier canceled) {
    TargetAddress key = new TargetAddress(host, port);
    ClientConnection conn = connectionMap.get(key);
    if (conn == null) {
      ClientConnection prev = connectionMap.putIfAbsent(key, conn = new ClientConnection(key, clientContext, host, port));
      if (prev == null) {
        conn.connect();
      }
    }
    conn.handle(req, handler, connectionExceptionHandler, canceled);
  }

  private class ClientConnection {

    private final TargetAddress key;
    private final ContextInternal context;
    private final String host;
    private final int port;
    private final ArrayDeque<Waiter> waiters = new ArrayDeque<>();
    private VertxClientHandler clientHandler;

    ClientConnection(TargetAddress key, ContextInternal clientContext, String host, int port) {

      if (clientContext == null) {
        // Embedded
        context = client.getVertx().getOrCreateContext();
      } else {
        context = clientContext;
      }

      this.key = key;
      this.host = host;
      this.port = port;
    }

    private class Waiter {
      final HttpClientRequestImpl req;
      final Handler<HttpClientStream> handler;
      final Handler<Throwable> connectionExceptionHandler;
      final BooleanSupplier canceled;
      public Waiter(HttpClientRequestImpl req, Handler<HttpClientStream> handler, Handler<Throwable> connectionExceptionHandler, BooleanSupplier canceled) {
        this.req = req;
        this.handler = handler;
        this.connectionExceptionHandler = connectionExceptionHandler;
        this.canceled = canceled;
      }
    }

    synchronized void handle(HttpClientRequestImpl req, Handler<HttpClientStream> handler, Handler<Throwable> connectionExceptionHandler, BooleanSupplier canceled) {
      if (clientHandler == null) {
        waiters.add(new Waiter(req, handler, connectionExceptionHandler, canceled));
      } else {
        context.runOnContext(v -> {
          // Handle case of non creation like max concurrency reaced
          clientHandler.handle(handler, req);
        });
      }
    }

    void connect() {
      Bootstrap bootstrap = new Bootstrap();
      bootstrap.group(context.nettyEventLoop());
      bootstrap.channelFactory(new VertxNioSocketChannelFactory());
      SSLHelper sslHelper = client.getSslHelper();
      sslHelper.validate(client.getVertx());
      bootstrap.handler(new ChannelInitializer<Channel>() {
        @Override
        protected void initChannel(Channel ch) throws Exception {
          SslHandler sslHandler = sslHelper.createSslHandler(client.getVertx(), true, host, port);
          ch.pipeline().addLast(sslHandler);
          ch.pipeline().addLast(new ApplicationProtocolNegotiationHandler("alpn") {
            @Override
            protected void configurePipeline(ChannelHandlerContext ctx, String protocol) {
              if (ApplicationProtocolNames.HTTP_2.equals(protocol)) {
                ChannelPipeline p = ctx.pipeline();
                Http2Connection connection = new DefaultHttp2Connection(false);
                VertxClientHandlerBuilder clientHandlerBuilder = new VertxClientHandlerBuilder(ClientConnection.this, ctx);
                synchronized (ClientConnection.this) {
                  VertxClientHandler handler = clientHandlerBuilder.build(connection);
                  handler.decoder().frameListener(handler);
                  clientHandler = handler;
                  p.addLast(handler);
                  Waiter waiter;
                  while ((waiter = waiters.poll()) != null) {
                    Handler<HttpClientStream> reqHandler = waiter.handler;
                    HttpClientRequestImpl req = waiter.req;
                    context.executeFromIO(() -> {
                      handler.handle(reqHandler, req);
                    });
                  }
                }
              } else {
                ctx.close();
                connectionFailed(new Exception());
              }
            }
          });
        }
      });
      applyConnectionOptions(client.getOptions(), bootstrap);
      ChannelFuture fut = bootstrap.connect(new InetSocketAddress(host, port));
      fut.addListener(v -> {
        if (!v.isSuccess()) {
          connectionFailed(v.cause());
        }
      });
    }

    void connectionFailed(Throwable err) {
      synchronized (this) {
        if (waiters.size() > 0) {
          // It failed for the first connection
          Waiter waiter = waiters.pop();
          Handler<Throwable> errHandler = waiter.connectionExceptionHandler;
          if (errHandler != null) {
            context.executeFromIO(() -> {
              errHandler.handle(err);
            });
          }
        }
        if (waiters.size() > 0) {
          // We retry for remaining connections
          // this is the current behavior of HTTP/1.1 pool, need to see if we change it with a retry count
          connect();
        } else {
          connectionMap.remove(key);
        }
      }
    }

    void close() {
      // Handler waiters here with
      // connectionExceptionHandler.handle(new IllegalStateException("unknown protocol: " + protocol));
      // remove from map
//      connectionMap.remove(key);
    }
  }

  class Http2ClientStream implements HttpClientStream {

    private final HttpClientRequestImpl req;
    private final ChannelHandlerContext context;
    private final Http2Connection conn;
    private final int id;
    private final Http2ConnectionEncoder encoder;
    private HttpClientResponseImpl resp;

    public Http2ClientStream(HttpClientRequestImpl req,
                             ChannelHandlerContext context,
                             Http2Connection conn,
                             Http2ConnectionEncoder encoder) {
      this.req = req;
      this.context = context;
      this.conn = conn;
      this.id = conn.local().incrementAndGetNextStreamId();
      this.encoder = encoder;
    }

    void handleHeaders(Http2Headers headers, boolean end) {
      resp = new HttpClientResponseImpl(
          req,
          this,
          Integer.parseInt(headers.status().toString()),
          "todo",
          new Http2HeadersAdaptor(headers)
      );
      req.handleResponse(resp);
      if (end) {
        handleEnd();
      }
    }

    void handleData(ByteBuf chunk, boolean end) {
      if (chunk.isReadable()) {
        Buffer buff = Buffer.buffer(chunk.slice());
        resp.handleChunk(buff);
      }
      if (end) {
        handleEnd();
      }
    }

    private void handleEnd() {
      // Should use an shared immutable object ?
      resp.handleEnd(new CaseInsensitiveHeaders());
    }

    @Override
    public void writeHead(HttpVersion version, HttpMethod method, String uri, MultiMap headers, boolean chunked) {
      throw new UnsupportedOperationException();
    }

    @Override
    public void writeHeadWithContent(HttpVersion version, HttpMethod method, String uri, MultiMap headers, boolean chunked, ByteBuf buf, boolean end) {
      Http2Headers h = new DefaultHttp2Headers();
      h.method(method.name());
      h.path(uri);
      h.scheme("https");
      encoder.writeHeaders(context, id, h, 0, end, context.newPromise());
      context.flush();
    }
    @Override
    public void writeBuffer(ByteBuf buf, boolean end) {
      throw new UnsupportedOperationException();
    }
    @Override
    public String hostHeader() {
      throw new UnsupportedOperationException();
    }
    @Override
    public Context getContext() {
      throw new UnsupportedOperationException();
    }
    @Override
    public void doSetWriteQueueMaxSize(int size) {
      throw new UnsupportedOperationException();
    }
    @Override
    public boolean isNotWritable() {
      throw new UnsupportedOperationException();
    }
    @Override
    public void handleInterestedOpsChanged() {
      throw new UnsupportedOperationException();
    }
    @Override
    public void endRequest() {
    }
    @Override
    public void doPause() {
      throw new UnsupportedOperationException();
    }
    @Override
    public void doResume() {
      throw new UnsupportedOperationException();
    }
    @Override
    public void reportBytesWritten(long numberOfBytes) {
    }
    @Override
    public void reportBytesRead(long s) {
    }
    @Override
    public NetSocket createNetSocket() {
      throw new UnsupportedOperationException();
    }
  }

  class VertxClientHandler extends Http2ConnectionHandler implements Http2FrameListener {

    private final ChannelHandlerContext context;
    private final ClientConnection conn;
    private final IntObjectMap<Http2ClientStream> streams = new IntObjectHashMap<>();

    public VertxClientHandler(
        ClientConnection conn,
        ChannelHandlerContext context,
        Http2ConnectionDecoder decoder,
        Http2ConnectionEncoder encoder,
        Http2Settings initialSettings) {
      super(decoder, encoder, initialSettings);
      this.conn = conn;
      this.context = context;
    }

    void handle(Handler<HttpClientStream> handler, HttpClientRequestImpl req) {
      Http2ClientStream stream = createStream(req);
      handler.handle(stream);
    }

    Http2ClientStream createStream(HttpClientRequestImpl req) {
      Http2ClientStream stream = new Http2ClientStream(req, context, connection(), encoder());
      streams.put(stream.id, stream);
      return stream;
    }

    @Override
    public int onDataRead(ChannelHandlerContext ctx, int streamId, ByteBuf data, int padding, boolean endOfStream) throws Http2Exception {
      Http2ClientStream stream = streams.get(streamId);
      stream.handleData(data, endOfStream);
      return data.readableBytes() + padding;
    }

    @Override
    public void onHeadersRead(ChannelHandlerContext ctx, int streamId, Http2Headers headers, int padding, boolean endOfStream) throws Http2Exception {
    }

    @Override
    public void onHeadersRead(ChannelHandlerContext ctx, int streamId, Http2Headers headers, int streamDependency, short weight, boolean exclusive, int padding, boolean endOfStream) throws Http2Exception {
      Http2ClientStream stream = streams.get(streamId);
      stream.handleHeaders(headers, endOfStream);
    }

    @Override
    public void onPriorityRead(ChannelHandlerContext ctx, int streamId, int streamDependency, short weight, boolean exclusive) throws Http2Exception {
    }

    @Override
    public void onRstStreamRead(ChannelHandlerContext ctx, int streamId, long errorCode) throws Http2Exception {
    }

    @Override
    public void onSettingsAckRead(ChannelHandlerContext ctx) throws Http2Exception {
    }

    @Override
    public void onSettingsRead(ChannelHandlerContext ctx, Http2Settings settings) throws Http2Exception {
    }

    @Override
    public void onPingRead(ChannelHandlerContext ctx, ByteBuf data) throws Http2Exception {
    }

    @Override
    public void onPingAckRead(ChannelHandlerContext ctx, ByteBuf data) throws Http2Exception {
    }

    @Override
    public void onPushPromiseRead(ChannelHandlerContext ctx, int streamId, int promisedStreamId, Http2Headers headers, int padding) throws Http2Exception {
    }

    @Override
    public void onGoAwayRead(ChannelHandlerContext ctx, int lastStreamId, long errorCode, ByteBuf debugData) throws Http2Exception {
    }

    @Override
    public void onWindowUpdateRead(ChannelHandlerContext ctx, int streamId, int windowSizeIncrement) throws Http2Exception {
    }

    @Override
    public void onUnknownFrame(ChannelHandlerContext ctx, byte frameType, int streamId, Http2Flags flags, ByteBuf payload) throws Http2Exception {
    }
  }

  class VertxClientHandlerBuilder extends AbstractHttp2ConnectionHandlerBuilder<VertxClientHandler, VertxClientHandlerBuilder> {

    private final ChannelHandlerContext context;
    private final ClientConnection conn;

    public VertxClientHandlerBuilder(ClientConnection conn, ChannelHandlerContext context) {
      this.context = context;
      this.conn = conn;
    }

    @Override
    protected VertxClientHandler build(Http2ConnectionDecoder decoder, Http2ConnectionEncoder encoder, Http2Settings initialSettings) throws Exception {
      return new VertxClientHandler(conn, context, decoder, encoder, initialSettings);
    }

    public VertxClientHandler build(Http2Connection conn) {
      connection(conn);
      initialSettings(new Http2Settings());
      frameListener(new Http2EventAdapter() {
        @Override
        public int onDataRead(ChannelHandlerContext ctx, int streamId, ByteBuf data, int padding, boolean endOfStream) throws Http2Exception {
          return super.onDataRead(ctx, streamId, data, padding, endOfStream);
        }
      });
      return super.build();
    }
  }
}
