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

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelPipeline;
import io.netty.handler.codec.http.HttpClientCodec;
import io.netty.handler.codec.http.HttpContentDecompressor;
import io.netty.handler.logging.LoggingHandler;
import io.netty.handler.ssl.SslHandler;
import io.netty.handler.timeout.IdleStateHandler;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpVersion;
import io.vertx.core.http.impl.http1x.Http1xClientConnection;
import io.vertx.core.http.impl.http1x.Http2UpgradeClientConnection;
import io.vertx.core.http.impl.http2.Http2ClientChannelInitializer;
import io.vertx.core.http.impl.http2.codec.Http2CodecClientChannelInitializer;
import io.vertx.core.http.impl.http2.multiplex.Http2MultiplexClientChannelInitializer;
import io.vertx.core.internal.ContextInternal;
import io.vertx.core.internal.PromiseInternal;
import io.vertx.core.internal.http.HttpChannelConnector;
import io.vertx.core.internal.http.HttpHeadersInternal;
import io.vertx.core.internal.net.NetClientInternal;
import io.vertx.core.net.*;
import io.vertx.core.net.impl.VertxHandler;
import io.vertx.core.net.impl.tcp.NetSocketImpl;
import io.vertx.core.spi.metrics.ClientMetrics;
import io.vertx.core.spi.metrics.HttpClientMetrics;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static io.vertx.core.http.HttpMethod.OPTIONS;

/**
 * Performs the channel configuration and connection according to the client options and the protocol version.
 *
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class Http1xOrH2ChannelConnector implements HttpChannelConnector {

  private final HttpClientOptions options;
  private final HttpClientMetrics clientMetrics;
  private final NetClientInternal netClient;

  public Http1xOrH2ChannelConnector(NetClientInternal netClient,
                                    HttpClientOptions options,
                                    HttpClientMetrics clientMetrics) {

    if (!options.isKeepAlive() && options.isPipelining()) {
      throw new IllegalStateException("Cannot have pipelining with no keep alive");
    }
    this.clientMetrics = clientMetrics;
    this.options = options;
    this.netClient = netClient;
  }

  public NetClientInternal netClient() {
    return netClient;
  }

  public HttpClientOptions options() {
    return options;
  }

  private Http2ClientChannelInitializer http2Initializer() {
    if (options.getHttp2MultiplexImplementation()) {
      return new Http2MultiplexClientChannelInitializer(
        HttpUtils.fromVertxSettings(options.getInitialSettings()),
        clientMetrics,
        TimeUnit.SECONDS.toMillis(options.getHttp2KeepAliveTimeout()),
        options.getHttp2MultiplexingLimit(),
        options.isDecompressionSupported(),
        options.getLogActivity());
    } else {
      return new Http2CodecClientChannelInitializer(options, clientMetrics);
    }
  }

  private void connect(ContextInternal context, HttpConnectParams params, HostAndPort authority, SocketAddress server, Promise<NetSocket> promise) {
    ConnectOptions connectOptions = new ConnectOptions();
    connectOptions.setRemoteAddress(server);
    if (authority != null) {
      connectOptions.setHost(authority.host());
      connectOptions.setPort(authority.port());
      if (params.ssl && options.isForceSni()) {
        connectOptions.setSniServerName(authority.host());
      }
    }
    connectOptions.setSsl(params.ssl);
    if (params.ssl) {
      if (params.sslOptions != null) {
        ClientSSLOptions copy = params.sslOptions.copy();
        if (options.isUseAlpn()) {
          copy.setUseAlpn(true);
          if (params.protocol == HttpVersion.HTTP_2) {
            copy.setApplicationLayerProtocols(List.of(HttpVersion.HTTP_2.alpnName(), HttpVersion.HTTP_1_1.alpnName()));
          } else {
            copy.setApplicationLayerProtocols(List.of(options.getProtocolVersion().alpnName()));
          }
        }
        connectOptions.setSslOptions(copy);
      } else {
        connectOptions.setSslOptions(new ClientSSLOptions().setHostnameVerificationAlgorithm("HTTPS"));
      }
    }
    connectOptions.setProxyOptions(params.proxyOptions);
    netClient.connectInternal(connectOptions, promise, context);
  }

  public Future<HttpClientConnection> wrap(ContextInternal context, HttpConnectParams params, HostAndPort authority, long maxLifetimeMillis, ClientMetrics<?, ?, ?> metrics, SocketAddress server, NetSocket so_) {
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
    if (params.ssl) {
      String protocol = so.applicationLayerProtocol();
      if (options.isUseAlpn()) {
        if ("h2".equals(protocol)) {
          applyHttp2ConnectionOptions(ch.pipeline());
          Http2ClientChannelInitializer http2ChannelInitializer = http2Initializer();
          http2ChannelInitializer.http2Connected(context, authority, metric, maxLifetimeMillis, ch, metrics, promise);
        } else {
          applyHttp1xConnectionOptions(ch.pipeline());
          HttpVersion fallbackProtocol = "http/1.0".equals(protocol) ?
            HttpVersion.HTTP_1_0 : HttpVersion.HTTP_1_1;
          http1xConnected(fallbackProtocol, server, authority, true, context, metric, maxLifetimeMillis, ch, metrics, promise);
        }
      } else {
        applyHttp1xConnectionOptions(ch.pipeline());
        http1xConnected(params.protocol, server, authority, true, context, metric, maxLifetimeMillis, ch, metrics, promise);
      }
    } else {
      if (params.protocol == HttpVersion.HTTP_2) {
        if (options.isHttp2ClearTextUpgrade()) {
          applyHttp1xConnectionOptions(pipeline);
          http1xConnected(params.protocol, server, authority, false, context, metric, maxLifetimeMillis, ch, metrics, promise);
        } else {
          applyHttp2ConnectionOptions(pipeline);
          Http2ClientChannelInitializer http2ChannelInitializer = http2Initializer();
          http2ChannelInitializer.http2Connected(context, authority, metric, maxLifetimeMillis, ch, metrics, promise);
        }
      } else {
        applyHttp1xConnectionOptions(pipeline);
        http1xConnected(params.protocol, server, authority, false, context, metric, maxLifetimeMillis, ch, metrics, promise);
      }
    }
    return promise.future();
  }

  public Future<HttpClientConnection> httpConnect(ContextInternal context, SocketAddress server, HostAndPort authority, HttpConnectParams params, long maxLifetimeMillis, ClientMetrics<?, ?, ?> metrics) {

    if (!options.isUseAlpn() && params.ssl && params.protocol == HttpVersion.HTTP_2) {
      return context.failedFuture("Must enable ALPN when using H2");
    }

    Promise<NetSocket> promise = context.promise();
    Future<NetSocket> future = promise.future();
    // We perform the compose operation before calling connect to be sure that the composition happens
    // before the promise is completed by the connect operation
    Future<HttpClientConnection> ret = future.compose(so -> wrap(context, params, authority, maxLifetimeMillis, metrics, server, so));
    connect(context, params, authority, server, promise);
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
                               HostAndPort authority,
                               boolean ssl,
                               ContextInternal context,
                               Object socketMetric,
                               long maxLifetimeMillis, Channel ch,
                               ClientMetrics<?, ?, ?> metrics,
                               Promise<HttpClientConnection> future) {
    boolean upgrade = version == HttpVersion.HTTP_2 && options.isHttp2ClearTextUpgrade();
    VertxHandler<Http1xClientConnection> clientHandler = VertxHandler.create(chctx -> {
      Http1xClientConnection conn = new Http1xClientConnection(upgrade ? HttpVersion.HTTP_1_1 : version, clientMetrics, options, chctx, ssl, server, authority, context, metrics, maxLifetimeMillis);
      if (clientMetrics != null) {
        conn.metric(socketMetric);
        clientMetrics.endpointConnected(metrics);
      }
      return conn;
    });
    clientHandler.addHandler(conn -> {
      if (upgrade) {
        Http2ClientChannelInitializer http2ChannelInitializer = http2Initializer();
        Http2UpgradeClientConnection.Http2ChannelUpgrade channelUpgrade= http2ChannelInitializer.channelUpgrade(conn, maxLifetimeMillis, metrics);
        boolean preflightRequest = options.isHttp2ClearTextUpgradeWithPreflightRequest();
        if (preflightRequest) {
          Http2UpgradeClientConnection conn2 = new Http2UpgradeClientConnection(conn, maxLifetimeMillis, metrics, channelUpgrade);
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
              HttpRequestHead request = new HttpRequestHead("http", OPTIONS, "/", HttpHeaders.headers(), HostAndPort.authority(server.host(), server.port()),
                "http://" + server + "/", null);
              stream.writeHead(request, false, null, true, null, false);
            } else {
              future.fail(ar.cause());
            }
          });
        } else {
          future.complete(new Http2UpgradeClientConnection(conn, maxLifetimeMillis, metrics, channelUpgrade));
        }
      } else {
        future.complete(conn);
      }
    });
    ch.pipeline().addLast("handler", clientHandler);
  }

  @Override
  public Future<Void> shutdown(Duration timeout) {
    return netClient.shutdown(timeout.toMillis(), TimeUnit.MILLISECONDS);
  }

  @Override
  public Future<Void> close() {
    return netClient.close();
  }
}
