/*
 * Copyright (c) 2011-2019 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package io.vertx.core.http.impl;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufHolder;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelPipeline;
import io.netty.handler.codec.TooLongFrameException;
import io.netty.handler.codec.http.*;
import io.netty.util.concurrent.EventExecutor;
import io.vertx.codegen.annotations.Nullable;
import io.vertx.core.*;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.*;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpVersion;
import io.vertx.core.http.impl.pool.ConnectionListener;
import io.vertx.core.impl.ContextInternal;
import io.vertx.core.impl.logging.Logger;
import io.vertx.core.impl.logging.LoggerFactory;
import io.vertx.core.net.NetSocket;
import io.vertx.core.net.SocketAddress;

import javax.net.ssl.SSLPeerUnverifiedException;
import javax.net.ssl.SSLSession;
import javax.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;

/**
 * An HTTP/2 connection in clear text that upgraded from an HTTP/1 upgrade.
 */
public class Http2UpgradedClientConnection implements HttpClientConnection {

  private static final Logger log = LoggerFactory.getLogger(Http2UpgradedClientConnection.class);

  private HttpClientImpl client;
  private HttpClientConnection current;

  private Handler<Void> closeHandler;
  private Handler<Void> shutdownHandler;
  private Handler<GoAway> goAwayHandler;
  private Handler<Throwable> exceptionHandler;
  private Handler<Buffer> pingHandler;
  private Handler<Http2Settings> remoteSettingsHandler;

  Http2UpgradedClientConnection(HttpClientImpl client, Http1xClientConnection connection) {
    this.client = client;
    this.current = connection;
  }

  @Override
  public ChannelHandlerContext channelHandlerContext() {
    return current.channelHandlerContext();
  }

  @Override
  public Channel channel() {
    return current.channel();
  }

  @Override
  public Future<Void> close() {
    return current.close();
  }

  @Override
  public Object metric() {
    return current.metric();
  }

  /**
   * The first stream that will send the request using HTTP/1, upgrades the connection when the protocol
   * switches and receives the response with HTTP/2 frames.
   */
  private class UpgradingStream implements HttpClientStream {

    private HttpClientRequestImpl request;
    private Promise<NetSocket> netSocketPromise;
    private Http1xClientConnection conn;
    private HttpClientStream stream;
    private long pendingSize = 0;
    private List<Object> pending = new ArrayList<>();

    UpgradingStream(HttpClientStream stream, HttpClientRequestImpl request, Promise<NetSocket> netSocketPromise, Http1xClientConnection conn) {
      this.conn = conn;
      this.request = request;
      this.netSocketPromise = netSocketPromise;
      this.stream = stream;
    }

    @Override
    public HttpClientConnection connection() {
      return Http2UpgradedClientConnection.this;
    }

    /**
     * HTTP/2 clear text upgrade here.
     */
    @Override
    public void writeHead(HttpMethod method,
                          String uri,
                          MultiMap headers,
                          String authority,
                          boolean chunked,
                          ByteBuf buf,
                          boolean end,
                          StreamPriority priority,
                          Handler<AsyncResult<Void>> listener) {
      ChannelPipeline pipeline = conn.channel().pipeline();
      HttpClientCodec httpCodec = pipeline.get(HttpClientCodec.class);

      class UpgradeRequestHandler extends ChannelInboundHandlerAdapter {
        @Override
        public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
          super.userEventTriggered(ctx, evt);
          ChannelPipeline pipeline = ctx.pipeline();
          if (evt == HttpClientUpgradeHandler.UpgradeEvent.UPGRADE_SUCCESSFUL) {
            // Upgrade handler will remove itself and remove the HttpClientCodec
            pipeline.remove(conn.handler());
          }
        }

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
          if (msg instanceof HttpResponse) {
            pipeline.remove(this);
            HttpResponse resp = (HttpResponse) msg;
            if (resp.status() != HttpResponseStatus.SWITCHING_PROTOCOLS) {
              // Insert the close headers to let the HTTP/1 stream close the connection
              resp.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.CLOSE);
            }
          }
          super.channelRead(ctx, msg);
        }
      }

      VertxHttp2ClientUpgradeCodec upgradeCodec = new VertxHttp2ClientUpgradeCodec(client.getOptions().getInitialSettings()) {
        @Override
        public void upgradeTo(ChannelHandlerContext ctx, FullHttpResponse upgradeResponse) throws Exception {

          // Now we need to upgrade this to an HTTP2
          ConnectionListener<HttpClientConnection> listener = conn.listener();
          VertxHttp2ConnectionHandler<Http2ClientConnection> handler = Http2ClientConnection.createHttp2ConnectionHandler(client, conn.endpointMetric(), listener, conn.getContext(), current.metric(), (conn, concurrency) -> {
            conn.upgradeStream(stream.metric(), request, netSocketPromise, stream.getContext(), ar -> {
              UpgradingStream.this.conn.closeHandler(null);
              UpgradingStream.this.conn.exceptionHandler(null);
              if (ar.succeeded()) {
                HttpClientStream upgradedStream = ar.result();
                current = conn;
                conn.closeHandler(closeHandler);
                conn.exceptionHandler(exceptionHandler);
                conn.pingHandler(pingHandler);
                conn.goAwayHandler(goAwayHandler);
                conn.shutdownHandler(shutdownHandler);
                conn.remoteSettingsHandler(remoteSettingsHandler);
                listener.onConcurrencyChange(concurrency);
              } else {
                // Handle me
                log.error(ar.cause().getMessage(), ar.cause());
              }
            });
          });
          conn.channel().pipeline().addLast(handler);
          handler.clientUpgrade(ctx);
        }
      };
      HttpClientUpgradeHandler upgradeHandler = new HttpClientUpgradeHandler(httpCodec, upgradeCodec, 65536) {
        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
          if (pending != null) {
            // Buffer all messages received from the server until the HTTP request is fully sent.
            //
            // Explanation:
            //
            // It is necessary that the client only starts to process the response when the request
            // has been fully sent because the current HTTP2 implementation will not be able to process
            // the server preface until the client preface has been sent.
            //
            // Adding the VertxHttp2ConnectionHandler to the pipeline has two effects:
            // - it is required to process the server preface
            // - it will send the request preface to the server
            //
            // As we are adding this handler to the pipeline when we receive the 101 response from the server
            // this might send the client preface before the initial HTTP request (doing the upgrade) is fully sent
            // resulting in corrupting the protocol (the server might interpret it as an corrupted connection preface).
            //
            // Therefore we must buffer all pending messages until the request is fully sent.

            int maxContent = maxContentLength();
            boolean lower = pendingSize < maxContent;
            if (msg instanceof ByteBufHolder) {
              pendingSize += ((ByteBufHolder)msg).content().readableBytes();
            } else if (msg instanceof ByteBuf) {
              pendingSize += ((ByteBuf)msg).readableBytes();
            }

            if (pendingSize >= maxContent) {
              if (lower) {
                pending.clear();
                ctx.fireExceptionCaught(new TooLongFrameException("Max content exceeded " + maxContentLength() + " bytes."));
              }
              return;
            }
            pending.add(msg);
          } else {
            super.channelRead(ctx, msg);
          }
        }
      };
      pipeline.addAfter("codec", null, new UpgradeRequestHandler());
      pipeline.addAfter("codec", null, upgradeHandler);
      doWriteHead(method, uri, headers, authority, chunked, buf, end, priority, listener);
    }

    private void doWriteHead(HttpMethod method,
                             String uri,
                             MultiMap headers,
                             String authority,
                             boolean chunked,
                             ByteBuf buf,
                             boolean end,
                             StreamPriority priority,
                             Handler<AsyncResult<Void>> listener) {
      EventExecutor exec = conn.channelHandlerContext().executor();
      if (exec.inEventLoop()) {
        stream.writeHead(method, uri, headers, authority, chunked, buf, end, priority, listener);
        if (end) {
          end();
        }
      } else {
        exec.execute(() -> doWriteHead(method, uri, headers, authority, chunked, buf, end, priority, listener));
      }
    }

    private void end() {
      // Deliver pending messages to the handler
      List<Object> messages = pending;
      pending = null;
      ChannelHandlerContext context = conn.channelHandlerContext().pipeline().context("codec");
      for (Object msg : messages) {
        context.fireChannelRead(msg);
      }
    }

    @Override
    public int id() {
      return 1;
    }

    @Override
    public Object metric() {
      return stream.metric();
    }

    @Override
    public HttpVersion version() {
      return HttpVersion.HTTP_2;
    }

    @Override
    public ContextInternal getContext() {
      return stream.getContext();
    }

    @Override
    public void writeBuffer(ByteBuf buf, boolean end, Handler<AsyncResult<Void>> handler) {
      EventExecutor exec = conn.channelHandlerContext().executor();
      if (exec.inEventLoop()) {
        stream.writeBuffer(buf, end, handler);
        if (end) {
          end();
        }
      } else {
        exec.execute(() -> writeBuffer(buf, end, handler));
      }
    }

    @Override
    public void writeFrame(int type, int flags, ByteBuf payload) {
      stream.writeFrame(type, flags, payload);
    }

    @Override
    public void doSetWriteQueueMaxSize(int size) {
      stream.doSetWriteQueueMaxSize(size);
    }

    @Override
    public boolean isNotWritable() {
      return stream.isNotWritable();
    }

    @Override
    public void doPause() {
      stream.doPause();
    }

    @Override
    public void doFetch(long amount) {
      stream.doFetch(amount);
    }

    @Override
    public void reset(Throwable cause) {
      stream.reset(cause);
    }

    @Override
    public StreamPriority priority() {
      return stream.priority();
    }

    @Override
    public void updatePriority(StreamPriority streamPriority) {
      stream.updatePriority(streamPriority);
    }
  }

  @Override
  public void createStream(ContextInternal context, HttpClientRequestImpl req, Promise<NetSocket> netSocketPromise, Handler<AsyncResult<HttpClientStream>> handler) {
    if (current instanceof Http1xClientConnection) {
      current.createStream(context, req, netSocketPromise, ar -> {
        if (ar.succeeded()) {
          HttpClientStream stream = ar.result();
          UpgradingStream upgradingStream = new UpgradingStream(stream, req, netSocketPromise, (Http1xClientConnection) current);
          handler.handle(Future.succeededFuture(upgradingStream));
        } else {
          handler.handle(ar);
        }
      });
    } else {
      current.createStream(context, req, netSocketPromise, handler);
    }
  }

  @Override
  public ContextInternal getContext() {
    return current.getContext();
  }

  @Override
  public HttpConnection closeHandler(Handler<Void> handler) {
    closeHandler = handler;
    current.closeHandler(handler);
    return this;
  }

  @Override
  public HttpConnection exceptionHandler(Handler<Throwable> handler) {
    exceptionHandler = handler;
    current.exceptionHandler(handler);
    return this;
  }

  @Override
  public HttpConnection remoteSettingsHandler(Handler<Http2Settings> handler) {
    if (current instanceof Http1xClientConnection) {
      remoteSettingsHandler = handler;
    } else {
      current.remoteSettingsHandler(handler);
    }
    return this;
  }

  @Override
  public HttpConnection pingHandler(@Nullable Handler<Buffer> handler) {
    if (current instanceof Http1xClientConnection) {
      pingHandler = handler;
    } else {
      current.pingHandler(handler);
    }
    return this;
  }

  @Override
  public HttpConnection goAwayHandler(@Nullable Handler<GoAway> handler) {
    if (current instanceof Http1xClientConnection) {
      goAwayHandler = handler;
    } else {
      current.goAwayHandler(handler);
    }
    return this;
  }

  @Override
  public HttpConnection shutdownHandler(@Nullable Handler<Void> handler) {
    if (current instanceof Http1xClientConnection) {
      shutdownHandler = handler;
    } else {
      current.shutdownHandler(handler);
    }
    return this;
  }

  @Override
  public HttpConnection goAway(long errorCode, int lastStreamId, Buffer debugData) {
    return current.goAway(errorCode, lastStreamId, debugData);
  }

  @Override
  public HttpConnection shutdown() {
    return current.shutdown();
  }

  @Override
  public HttpConnection shutdown(long timeoutMs) {
    return current.shutdown(timeoutMs);
  }

  @Override
  public Future<Void> updateSettings(Http2Settings settings) {
    return current.updateSettings(settings);
  }

  @Override
  public HttpConnection updateSettings(Http2Settings settings, Handler<AsyncResult<Void>> completionHandler) {
    return current.updateSettings(settings, completionHandler);
  }

  @Override
  public Http2Settings settings() {
    return current.settings();
  }

  @Override
  public Http2Settings remoteSettings() {
    return current.remoteSettings();
  }

  @Override
  public HttpConnection ping(Buffer data, Handler<AsyncResult<Buffer>> pongHandler) {
    return current.ping(data, pongHandler);
  }

  @Override
  public Future<Buffer> ping(Buffer data) {
    return current.ping(data);
  }

  @Override
  public SocketAddress remoteAddress() {
    return current.remoteAddress();
  }

  @Override
  public SocketAddress localAddress() {
    return current.localAddress();
  }

  @Override
  public boolean isSsl() {
    return current.isSsl();
  }

  @Override
  public SSLSession sslSession() {
    return current.sslSession();
  }

  @Override
  public X509Certificate[] peerCertificateChain() throws SSLPeerUnverifiedException {
    return current.peerCertificateChain();
  }

  @Override
  public String indicatedServerName() {
    return current.indicatedServerName();
  }
}
