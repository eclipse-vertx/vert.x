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
import java.util.function.BooleanSupplier;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public abstract class Http2ConnectionManager extends ConnectionManager {

  private final HttpClientImpl client;

  public Http2ConnectionManager(HttpClientImpl client) {
    this.client = client;
  }

  @Override
  public void getConnection(int port, String host, HttpClientRequestImpl req, Handler<HttpClientStream> handler, Handler<Throwable> connectionExceptionHandler, ContextImpl clientContext, BooleanSupplier canceled) {

    ContextInternal context;
    if (clientContext == null) {
      // Embedded
      context = client.getVertx().getOrCreateContext();
    } else {
      context = clientContext;
    }

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
              VertxClientHandlerBuilder clientHandlerBuilder = new VertxClientHandlerBuilder(ctx);
              VertxClientHandler clientHandler = clientHandlerBuilder.build(connection);
              p.addLast(clientHandler);
              Http2ClientStream stream = clientHandler.createStream(req);
              handler.handle(stream);
              return;
            }
            ctx.close();
            connectionExceptionHandler.handle(new IllegalStateException("unknown protocol: " + protocol));
          }
        });
      }
    });
    applyConnectionOptions(client.getOptions(), bootstrap);
    bootstrap.connect(new InetSocketAddress(host, port));
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
    private final IntObjectMap<Http2ClientStream> streams = new IntObjectHashMap<>();

    public VertxClientHandler(
        ChannelHandlerContext context,
        Http2ConnectionDecoder decoder,
        Http2ConnectionEncoder encoder,
        Http2Settings initialSettings) {
      super(decoder, encoder, initialSettings);

      decoder.frameListener(this);

      this.context = context;
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

    public VertxClientHandlerBuilder(ChannelHandlerContext context) {
      this.context = context;
    }

    @Override
    protected VertxClientHandler build(Http2ConnectionDecoder decoder, Http2ConnectionEncoder encoder, Http2Settings initialSettings) throws Exception {
      return new VertxClientHandler(context, decoder, encoder, initialSettings);
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
