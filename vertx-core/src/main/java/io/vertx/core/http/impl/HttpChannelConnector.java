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
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelPipeline;
import io.netty.handler.codec.TooLongFrameException;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpClientCodec;
import io.netty.handler.codec.http.HttpClientUpgradeHandler;
import io.netty.handler.codec.http.HttpContentDecompressor;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http2.Http2ClientUpgradeCodec;
import io.netty.handler.codec.http2.Http2ConnectionHandler;
import io.netty.handler.codec.http2.Http2FrameCodec;
import io.netty.handler.codec.http2.Http2FrameCodecBuilder;
import io.netty.handler.codec.http2.Http2MultiplexHandler;
import io.netty.handler.codec.http2.Http2Settings;
import io.netty.handler.codec.http2.Http2StreamChannel;
import io.netty.handler.logging.LoggingHandler;
import io.netty.handler.ssl.SslHandler;
import io.netty.handler.timeout.IdleStateHandler;
import io.netty.util.ReferenceCountUtil;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpVersion;
import io.vertx.core.http.impl.http2.codec.Http2ClientConnectionImpl;
import io.vertx.core.http.impl.http2.codec.VertxHttp2ConnectionHandler;
import io.vertx.core.internal.ContextInternal;
import io.vertx.core.internal.PromiseInternal;
import io.vertx.core.internal.http.HttpHeadersInternal;
import io.vertx.core.internal.net.NetClientInternal;
import io.vertx.core.net.*;
import io.vertx.core.net.impl.NetSocketImpl;
import io.vertx.core.net.impl.VertxHandler;
import io.vertx.core.spi.metrics.ClientMetrics;
import io.vertx.core.spi.metrics.HttpClientMetrics;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static io.vertx.core.http.HttpMethod.OPTIONS;
import static io.vertx.core.http.impl.Http2UpgradeClientConnection.SEND_BUFFERED_MESSAGES_EVENT;

/**
 * Performs the channel configuration and connection according to the client options and the protocol version.
 *
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class HttpChannelConnector {

  private final HttpClientBase client;
  private final NetClientInternal netClient;
  private final HttpClientOptions options;
  private final ClientSSLOptions sslOptions;
  private final ProxyOptions proxyOptions;
  private final ClientMetrics metrics;
  private final boolean ssl;
  private final boolean useAlpn;
  private final HttpVersion version;
  private final HostAndPort authority;
  private final SocketAddress server;
  private final boolean pooled;
  private final long maxLifetime;

  public HttpChannelConnector(HttpClientBase client,
                              NetClientInternal netClient,
                              ClientSSLOptions sslOptions,
                              ProxyOptions proxyOptions,
                              ClientMetrics metrics,
                              HttpVersion version,
                              boolean ssl,
                              boolean useAlpn,
                              HostAndPort authority,
                              SocketAddress server,
                              boolean pooled,
                              long maxLifetime) {
    this.client = client;
    this.netClient = netClient;
    this.metrics = metrics;
    this.options = client.options();
    this.proxyOptions = proxyOptions;
    this.sslOptions = sslOptions;
    this.ssl = ssl;
    this.useAlpn = useAlpn;
    this.version = version;
    this.authority = authority;
    this.server = server;
    this.pooled = pooled;
    this.maxLifetime = maxLifetime;
  }

  public SocketAddress server() {
    return server;
  }

  private void connect(ContextInternal context, Promise<NetSocket> promise) {
    ConnectOptions connectOptions = new ConnectOptions();
    connectOptions.setRemoteAddress(server);
    if (authority != null) {
      connectOptions.setHost(authority.host());
      connectOptions.setPort(authority.port());
      if (ssl && options.isForceSni()) {
        connectOptions.setSniServerName(authority.host());
      }
    }
    connectOptions.setSsl(ssl);
    if (ssl) {
      if (sslOptions != null) {
        connectOptions.setSslOptions(sslOptions.copy().setUseAlpn(useAlpn));
      } else {
        connectOptions.setSslOptions(new ClientSSLOptions().setHostnameVerificationAlgorithm("HTTPS"));
      }
    }
    connectOptions.setProxyOptions(proxyOptions);
    netClient.connectInternal(connectOptions, promise, context);
  }

  public Future<HttpClientConnection> wrap(ContextInternal context, NetSocket so_) {
    NetSocketImpl so = (NetSocketImpl) so_;
    Object metric = so.metric();
    PromiseInternal<HttpClientConnection> promise = context.promise();

    // Remove all un-necessary handlers
    ChannelPipeline pipeline = so.channelHandlerContext().pipeline();
    List<ChannelHandler> removedHandlers = new ArrayList<>();
    for (Map.Entry<String, ChannelHandler> stringChannelHandlerEntry : pipeline) {
      ChannelHandler handler = stringChannelHandlerEntry.getValue();
      if (!(handler instanceof SslHandler)) {
        removedHandlers.add(handler);
      }
    }
    removedHandlers.forEach(pipeline::remove);

    //
    Channel ch = so.channelHandlerContext().channel();
    if (ssl) {
      String protocol = so.applicationLayerProtocol();
      if (useAlpn) {
        if ("h2".equals(protocol)) {
          applyHttp2ConnectionOptions(ch.pipeline());
          http2Connected(context, metric, ch, promise);
        } else {
          applyHttp1xConnectionOptions(ch.pipeline());
          HttpVersion fallbackProtocol = "http/1.0".equals(protocol) ?
            HttpVersion.HTTP_1_0 : HttpVersion.HTTP_1_1;
          http1xConnected(fallbackProtocol, server, true, context, metric, ch, promise);
        }
      } else {
        applyHttp1xConnectionOptions(ch.pipeline());
        http1xConnected(version, server, true, context, metric, ch, promise);
      }
    } else {
      if (version == HttpVersion.HTTP_2) {
        if (this.options.isHttp2ClearTextUpgrade()) {
          applyHttp1xConnectionOptions(pipeline);
          http1xConnected(version, server, false, context, metric, ch, promise);
        } else {
          applyHttp2ConnectionOptions(pipeline);
          http2Connected(context, metric, ch, promise);
        }
      } else {
        applyHttp1xConnectionOptions(pipeline);
        http1xConnected(version, server, false, context, metric, ch, promise);
      }
    }
    return promise.future();
  }

  public Future<HttpClientConnection> httpConnect(ContextInternal context) {
    Promise<NetSocket> promise = context.promise();
    Future<NetSocket> future = promise.future();
    // We perform the compose operation before calling connect to be sure that the composition happens
    // before the promise is completed by the connect operation
    Future<HttpClientConnection> ret = future.compose(so -> wrap(context, so));
    connect(context, promise);
    return ret;
  }

  private void applyHttp2ConnectionOptions(ChannelPipeline pipeline) {
    int idleTimeout = options.getIdleTimeout();
    int readIdleTimeout = options.getReadIdleTimeout();
    int writeIdleTimeout = options.getWriteIdleTimeout();
    if (idleTimeout > 0 || readIdleTimeout > 0 || writeIdleTimeout > 0) {
      pipeline.addLast("idle", new IdleStateHandler(readIdleTimeout, writeIdleTimeout, idleTimeout, options.getIdleTimeoutUnit()));
    }
  }

  private void applyHttp1xConnectionOptions(ChannelPipeline pipeline) {
    int idleTimeout = options.getIdleTimeout();
    int readIdleTimeout = options.getReadIdleTimeout();
    int writeIdleTimeout = options.getWriteIdleTimeout();
    if (idleTimeout > 0 || readIdleTimeout > 0 || writeIdleTimeout > 0) {
      pipeline.addLast("idle", new IdleStateHandler(readIdleTimeout, writeIdleTimeout, idleTimeout, options.getIdleTimeoutUnit()));
    }
    if (options.getLogActivity()) {
      pipeline.addLast("logging", new LoggingHandler(options.getActivityLogDataFormat()));
    }
    pipeline.addLast("codec", new HttpClientCodec(
      options.getMaxInitialLineLength(),
      options.getMaxHeaderSize(),
      options.getMaxChunkSize(),
      false,
      !HttpHeadersInternal.DISABLE_HTTP_HEADERS_VALIDATION,
      options.getDecoderInitialBufferSize()));
    if (options.isDecompressionSupported()) {
      pipeline.addLast("inflater", new HttpContentDecompressor(false));
    }
  }

  private void http1xConnected(HttpVersion version,
                               SocketAddress server,
                               boolean ssl,
                               ContextInternal context,
                               Object socketMetric,
                               Channel ch,
                               Promise<HttpClientConnection> future) {
    boolean upgrade = version == HttpVersion.HTTP_2 && options.isHttp2ClearTextUpgrade();
    VertxHandler<Http1xClientConnection> clientHandler = VertxHandler.create(chctx -> {
      HttpClientMetrics met = client.metrics();
      Http1xClientConnection conn = new Http1xClientConnection(upgrade ? HttpVersion.HTTP_1_1 : version, client, chctx, ssl, server, authority, context, metrics, pooled, maxLifetime);
      if (met != null) {
        conn.metric(socketMetric);
        met.endpointConnected(metrics);
      }
      return conn;
    });
    clientHandler.addHandler(conn -> {
      if (upgrade) {
        Http2UpgradeClientConnection.Http2ChannelUpgrade channelUpgrade;
        channelUpgrade = new CodecChannelUpgrade(client, metrics,
          conn.metric(),
          client.options.getInitialSettings(),
          client.options().getHttp2UpgradeMaxContentLength(), maxLifetime);
        boolean preflightRequest = options.isHttp2ClearTextUpgradeWithPreflightRequest();
        if (preflightRequest) {
          Http2UpgradeClientConnection conn2 = new Http2UpgradeClientConnection(conn, channelUpgrade);
          conn2.concurrencyChangeHandler(concurrency -> {
            // Ignore
          });
          conn2.createStream(conn.context()).onComplete(ar -> {
            if (ar.succeeded()) {
              HttpClientStream stream = ar.result();
              stream.headHandler(resp -> {
                Http2UpgradeClientConnection connection = (Http2UpgradeClientConnection) stream.connection();
                HttpClientConnection unwrap = connection.unwrap();
                future.tryComplete(unwrap);
              });
              stream.exceptionHandler(future::tryFail);
              HttpRequestHead request = new HttpRequestHead(OPTIONS, "/", HttpHeaders.headers(), HostAndPort.authority(server.host(), server.port()),
                "http://" + server + "/", null);
              stream.writeHead(request, false, null, true, null, false);
            } else {
              future.fail(ar.cause());
            }
          });
        } else {
          future.complete(new Http2UpgradeClientConnection(conn, channelUpgrade));
        }
      } else {
        future.complete(conn);
      }
    });
    ch.pipeline().addLast("handler", clientHandler);
  }

  private void http2Connected(ContextInternal context,
                              Object metric,
                              Channel ch,
                              PromiseInternal<HttpClientConnection> promise) {
    VertxHttp2ConnectionHandler<Http2ClientConnectionImpl> clientHandler;
    try {
      clientHandler = Http2ClientConnectionImpl.createHttp2ConnectionHandler(client, metrics, context, false, metric, authority, pooled, maxLifetime);
      ch.pipeline().addLast("handler", clientHandler);
      ch.flush();
    } catch (Exception e) {
      connectFailed(ch, e, promise);
      return;
    }
    clientHandler.connectFuture().addListener(promise);
  }

  private void connectFailed(Channel ch, Throwable t, Promise<HttpClientConnection> future) {
    if (ch != null) {
      try {
        ch.close();
      } catch (Exception ignore) {
      }
    }
    future.tryFail(t);
  }

  public static class CodecChannelUpgrade implements Http2UpgradeClientConnection.Http2ChannelUpgrade {

    private final HttpClientBase client;
    private final ClientMetrics clientMetrics;
    private final Object connectionMetric;
    private final int maxContentLength;
    private final io.vertx.core.http.Http2Settings initialSettings;
    private final long maxLifetime;

    public CodecChannelUpgrade(HttpClientBase client,
                               ClientMetrics clientMetrics,
                               Object connectionMetric,
                               io.vertx.core.http.Http2Settings initialSettings,
                               int maxContentLength,
                               long maxLifetime) {
      this.initialSettings = initialSettings;
      this.maxContentLength = maxContentLength;
      this.client = client;
      this.maxLifetime = maxLifetime;
      this.clientMetrics = clientMetrics;
      this.connectionMetric = connectionMetric;
    }

    public void upgrade(HttpClientStream upgradingStream, HttpRequestHead request,
                        ByteBuf content,
                        boolean end,
                        Channel channel,
                        boolean pooled,
                        Http2UpgradeClientConnection.UpgradeResult result) {
      ChannelPipeline pipeline = channel.pipeline();
      HttpClientCodec httpCodec = pipeline.get(HttpClientCodec.class);

      class UpgradeRequestHandler extends ChannelInboundHandlerAdapter {
        @Override
        public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
          super.userEventTriggered(ctx, evt);
          ChannelPipeline pipeline = ctx.pipeline();
          if (evt instanceof HttpClientUpgradeHandler.UpgradeEvent) {
            switch ((HttpClientUpgradeHandler.UpgradeEvent)evt) {
              case UPGRADE_SUCCESSFUL:
                // Remove Http1xClientConnection handler
                pipeline.remove("handler");
                // Go through
              case UPGRADE_REJECTED:
                // Remove this handler
                pipeline.remove(this);
                // Upgrade handler will remove itself and remove the HttpClientCodec
                result.upgradeRejected();
                break;
            }
          }
        }

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
          if (msg instanceof HttpResponseHead) {
            pipeline.remove(this);
            HttpResponseHead resp = (HttpResponseHead) msg;
            if (resp.statusCode != HttpResponseStatus.SWITCHING_PROTOCOLS.code()) {
              // Insert the close headers to let the HTTP/1 stream close the connection
              resp.headers.set(HttpHeaderNames.CONNECTION, HttpHeaderValues.CLOSE);
            }
          }
          super.channelRead(ctx, msg);
        }
      }

      VertxHttp2ClientUpgradeCodec upgradeCodec = new VertxHttp2ClientUpgradeCodec(initialSettings) {
        @Override
        public void upgradeTo(ChannelHandlerContext ctx, FullHttpResponse upgradeResponse) throws Exception {

          // Now we need to upgrade this to an HTTP2
          VertxHttp2ConnectionHandler<Http2ClientConnectionImpl> handler = Http2ClientConnectionImpl.createHttp2ConnectionHandler(
            client,
            clientMetrics,
            upgradingStream.context(),
            true,
            connectionMetric,
            request.authority,
            pooled,
            maxLifetime);
          channel.pipeline().addLast(handler);
          handler.connectFuture().addListener(future -> {
            if (!future.isSuccess()) {
              // Handle me
              // log.error(future.cause().getMessage(), future.cause());
            } else {
              Http2ClientConnectionImpl connection = (Http2ClientConnectionImpl) future.getNow();
              HttpClientStream upgradedStream;
              try {
                upgradedStream = connection.upgradeStream(upgradingStream.metric(), upgradingStream.trace(), upgradingStream.context());
                result.upgradeAccepted(connection, upgradedStream);
              } catch (Exception e) {
                result.upgradeFailure(e);
              }
            }
          });
          handler.clientUpgrade(ctx);
        }
      };
      HttpClientUpgradeHandler upgradeHandler = new HttpClientUpgradeHandler(httpCodec, upgradeCodec, maxContentLength) {

        private long bufferedSize = 0;
        private Deque<Object> buffered = new ArrayDeque<>();

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
          if (buffered != null) {
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
            boolean lower = bufferedSize < maxContent;
            if (msg instanceof ByteBufHolder) {
              bufferedSize += ((ByteBufHolder)msg).content().readableBytes();
            } else if (msg instanceof ByteBuf) {
              bufferedSize += ((ByteBuf)msg).readableBytes();
            }
            buffered.add(msg);

            if (bufferedSize >= maxContent && lower) {
              ctx.fireExceptionCaught(new TooLongFrameException("Max content exceeded " + maxContentLength() + " bytes."));
            }
          } else {
            super.channelRead(ctx, msg);
          }
        }

        @Override
        public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
          if (SEND_BUFFERED_MESSAGES_EVENT == evt) {
            Deque<Object> messages = buffered;
            buffered = null;
            Object msg;
            while ((msg = messages.poll()) != null) {
              super.channelRead(ctx, msg);
            }
          } else {
            super.userEventTriggered(ctx, evt);
          }
        }

        @Override
        public void handlerRemoved(ChannelHandlerContext ctx) throws Exception {
          if (buffered != null) {
            Deque<Object> messages = buffered;
            buffered = null;
            Object msg;
            while ((msg = messages.poll()) != null) {
              ReferenceCountUtil.release(msg);
            }
          }
          super.handlerRemoved(ctx);
        }

      };
      pipeline.addAfter("codec", null, new UpgradeRequestHandler());
      pipeline.addAfter("codec", null, upgradeHandler);
    }
  }
}
