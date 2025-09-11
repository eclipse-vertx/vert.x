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
import io.vertx.core.http.impl.http2.Http2ClientChannelInitializer;
import io.vertx.core.http.impl.http2.codec.Http2CodecClientChannelInitializer;
import io.vertx.core.http.impl.http2.multiplex.Http2MultiplexClientChannelInitializer;
import io.vertx.core.http.impl.http2.h3.Http3CodecClientChannelInitializer;
import io.vertx.core.internal.ContextInternal;
import io.vertx.core.internal.PromiseInternal;
import io.vertx.core.internal.http.HttpHeadersInternal;
import io.vertx.core.internal.net.NetClientInternal;
import io.vertx.core.net.*;
import io.vertx.core.net.impl.NetSocketImpl;
import io.vertx.core.net.impl.VertxHandler;
import io.vertx.core.spi.metrics.ClientMetrics;
import io.vertx.core.spi.metrics.HttpClientMetrics;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static io.vertx.core.http.HttpMethod.OPTIONS;
import static io.vertx.core.net.impl.ChannelProvider.CLIENT_SSL_HANDLER_NAME;

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
  private final Http2ClientChannelInitializer http2ChannelInitializer;
  private final Http2ClientChannelInitializer http3ChannelInitializer;

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
                              long maxLifetimeMillis) {

    Http2ClientChannelInitializer http2ChannelInitializer = null;
    Http2ClientChannelInitializer http3ChannelInitializer = null;
    if (client.options.getHttp2MultiplexImplementation()) {
      http2ChannelInitializer = new Http2MultiplexClientChannelInitializer(
        HttpUtils.fromVertxSettings(client.options.getInitialSettings()),
        client.metrics,
        metrics,
        TimeUnit.SECONDS.toMillis(client.options.getHttp2KeepAliveTimeout()),
        maxLifetimeMillis,
        authority,
        client.options.getHttp2MultiplexingLimit(),
        client.options.isDecompressionSupported(),
        client.options.getLogActivity());
    } else if (client.options.getProtocolVersion() == HttpVersion.HTTP_2) {
      http2ChannelInitializer = new Http2CodecClientChannelInitializer(client, metrics, pooled, maxLifetimeMillis, authority);
    } else {
      http3ChannelInitializer = new Http3CodecClientChannelInitializer(client, metrics, pooled, maxLifetimeMillis, authority);
    }

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
    this.maxLifetime = maxLifetimeMillis;
    this.http2ChannelInitializer = http2ChannelInitializer;
    this.http3ChannelInitializer = http3ChannelInitializer;
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
      if (!(handler instanceof SslHandler) && !(CLIENT_SSL_HANDLER_NAME.equals(stringChannelHandlerEntry.getKey()))) {
        removedHandlers.add(handler);
      }
    }
    removedHandlers.forEach(pipeline::remove);

    //
    Channel ch = so.channelHandlerContext().channel();
    if (ssl) {
      String protocol = so.applicationLayerProtocol();
      if (useAlpn) {
        if (protocol != null && protocol.startsWith("h3")) {
          applyHttp3ConnectionOptions(ch.pipeline());
          http3ChannelInitializer.http2Connected(context, metric, ch ,promise);
        } else if ("h2".equals(protocol)) {
          applyHttp2ConnectionOptions(ch.pipeline());
          http2ChannelInitializer.http2Connected(context, metric, ch ,promise);
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
      if (version == HttpVersion.HTTP_3) {
        applyHttp3ConnectionOptions(pipeline);
        http3ChannelInitializer.http2Connected(context, metric, ch, promise);
      } else if (version == HttpVersion.HTTP_2) {
        if (this.options.isHttp2ClearTextUpgrade()) {
          applyHttp1xConnectionOptions(pipeline);
          http1xConnected(version, server, false, context, metric, ch, promise);
        } else {
          applyHttp2ConnectionOptions(pipeline);
          http2ChannelInitializer.http2Connected(context, metric, ch, promise);
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

  private void applyHttp3ConnectionOptions(ChannelPipeline pipeline) {
    int idleTimeout = options.getIdleTimeout();
    int readIdleTimeout = options.getReadIdleTimeout();
    int writeIdleTimeout = options.getWriteIdleTimeout();
    if (idleTimeout > 0 || readIdleTimeout > 0 || writeIdleTimeout > 0) {
      pipeline.addLast("idle", new IdleStateHandler(readIdleTimeout, writeIdleTimeout, idleTimeout,
        options.getIdleTimeoutUnit()));
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
        channelUpgrade = http2ChannelInitializer.channelUpgrade(conn);
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
}
