/*
 * Copyright (c) 2011-2018 Contributors to the Eclipse Foundation
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
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelPipeline;
import io.netty.handler.codec.http.*;
import io.vertx.codegen.annotations.Nullable;
import io.vertx.core.*;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.*;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpVersion;
import io.vertx.core.http.impl.pool.ConnectionListener;
import io.vertx.core.impl.ContextInternal;
import io.vertx.core.net.NetSocket;
import io.vertx.core.net.SocketAddress;

import javax.net.ssl.SSLPeerUnverifiedException;
import javax.net.ssl.SSLSession;
import javax.security.cert.X509Certificate;

/**
 * An HTTP/2 connection in clear text that upgraded from an HTTP/1 upgrade.
 */
public class Http2UpgradedClientConnection implements HttpClientConnection {

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
  public void close() {
    current.close();
  }

  /**
   * The first stream that will send the request using HTTP/1, upgrades the connection when the protocol
   * switches and receives the response with HTTP/2 frames.
   */
  private class UpgradingStream implements HttpClientStream {

    private HttpClientRequestImpl request;
    private Http1xClientConnection conn;
    private HttpClientStream stream;

    UpgradingStream(HttpClientStream stream, Http1xClientConnection conn) {
      this.conn = conn;
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
                          String rawMethod,
                          String uri,
                          MultiMap headers,
                          String hostHeader,
                          boolean chunked,
                          ByteBuf buf,
                          boolean end) {
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
              // Insert the cloe headers to let the HTTP/1 stream close the connection
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
          VertxHttp2ConnectionHandler<Http2ClientConnection> handler = Http2ClientConnection.createHttp2ConnectionHandler(client, conn.endpointMetric(), listener, conn.getContext(), (conn, concurrency) -> {
            conn.upgradeStream(request, ar -> {
              UpgradingStream.this.conn.closeHandler(null);
              UpgradingStream.this.conn.exceptionHandler(null);
              if (ar.succeeded()) {
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
                ar.cause().printStackTrace();
              }
            });
          });
          conn.channel().pipeline().addLast(handler);
          handler.clientUpgrade(ctx);
        }
      };
      HttpClientUpgradeHandler upgradeHandler = new HttpClientUpgradeHandler(httpCodec, upgradeCodec, 65536);
      pipeline.addAfter("codec", null, new UpgradeRequestHandler());
      pipeline.addAfter("codec", null, upgradeHandler);
      stream.writeHead(method, rawMethod, uri, headers, hostHeader, chunked, buf, end);
    }

    @Override
    public int id() {
      return 1;
    }

    @Override
    public HttpVersion version() {
      return HttpVersion.HTTP_2;
    }

    @Override
    public Context getContext() {
      return stream.getContext();
    }

    @Override
    public void writeBuffer(ByteBuf buf, boolean end) {
      stream.writeBuffer(buf, end);
    }

    @Override
    public void writeFrame(int type, int flags, ByteBuf payload) {
      stream.writeFrame(type, flags, payload);
    }

    @Override
    public void reportBytesWritten(long numberOfBytes) {
      stream.reportBytesWritten(numberOfBytes);
    }

    @Override
    public void reportBytesRead(long numberOfBytes) {
      stream.reportBytesRead(numberOfBytes);
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
    public void doResume() {
      stream.doResume();
    }

    @Override
    public void doFetch(long amount) {
      stream.doFetch(amount);
    }

    @Override
    public void reset(long code) {
      stream.reset(code);
    }

    @Override
    public void beginRequest(HttpClientRequestImpl req) {
      request = req;
      stream.beginRequest(req);
    }

    @Override
    public void endRequest() {
      stream.endRequest();
    }

    @Override
    public NetSocket createNetSocket() {
      return stream.createNetSocket();
    }
  }

  @Override
  public void createStream(Handler<AsyncResult<HttpClientStream>> handler) {
    if (current instanceof Http1xClientConnection) {
      current.createStream(ar -> {
        if (ar.succeeded()) {
          HttpClientStream stream = ar.result();
          UpgradingStream upgradingStream = new UpgradingStream(stream, (Http1xClientConnection) current);
          handler.handle(Future.succeededFuture(upgradingStream));
        } else {
          handler.handle(ar);
        }
      });
    } else {
      current.createStream(handler);
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
  public HttpConnection updateSettings(Http2Settings settings) {
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
