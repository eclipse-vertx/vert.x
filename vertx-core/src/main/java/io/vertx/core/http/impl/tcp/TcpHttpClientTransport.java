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

package io.vertx.core.http.impl.tcp;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelPipeline;
import io.netty.handler.codec.http.HttpClientCodec;
import io.netty.handler.codec.http.HttpContentDecompressor;
import io.netty.handler.logging.ByteBufFormat;
import io.netty.handler.logging.LoggingHandler;
import io.netty.handler.ssl.SslHandler;
import io.netty.handler.timeout.IdleStateHandler;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpVersion;
import io.vertx.core.http.Http1ClientConfig;
import io.vertx.core.http.Http2ClientConfig;
import io.vertx.core.http.HttpClientConfig;
import io.vertx.core.http.impl.*;
import io.vertx.core.http.impl.http1.Http1ClientConnection;
import io.vertx.core.http.impl.http2.Http2ClientChannelInitializer;
import io.vertx.core.http.impl.http2.codec.Http2CodecClientChannelInitializer;
import io.vertx.core.http.impl.http2.multiplex.Http2MultiplexClientChannelInitializer;
import io.vertx.core.internal.ContextInternal;
import io.vertx.core.internal.PromiseInternal;
import io.vertx.core.internal.http.HttpClientTransport;
import io.vertx.core.internal.http.HttpHeadersInternal;
import io.vertx.core.internal.net.NetClientInternal;
import io.vertx.core.net.*;
import io.vertx.core.net.impl.VertxHandler;
import io.vertx.core.net.impl.tcp.NetSocketImpl;
import io.vertx.core.spi.metrics.ClientMetrics;
import io.vertx.core.spi.metrics.HttpClientMetrics;
import io.vertx.core.spi.metrics.TransportMetrics;
import io.vertx.core.spi.metrics.WebSocketMetrics;
import io.vertx.core.tracing.TracingPolicy;

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
public class TcpHttpClientTransport implements HttpClientTransport {

  public static TcpHttpClientTransport create(NetClientInternal netClient,
                                              HttpClientConfig config,
                                              HttpClientMetrics httpMetrics) {
    return new TcpHttpClientTransport(netClient,
      config.getTracingPolicy(),
      config.isDecompressionEnabled(),
      config.getTcpConfig().getNetworkLogging() != null && config.getTcpConfig().getNetworkLogging().isEnabled(),
      config.getTcpConfig().getNetworkLogging() != null ? config.getTcpConfig().getNetworkLogging().getDataFormat() : null,
      config.isForceSni(),
      config.getHttp1Config(),
      config.getHttp2Config(),
      config.getTcpConfig().getIdleTimeout(),
      config.getTcpConfig().getReadIdleTimeout(),
      config.getTcpConfig().getWriteIdleTimeout(),
      httpMetrics);
  }

  private final TracingPolicy tracingPolicy;
  private final boolean useDecompression;
  private final boolean logActivity;
  private final ByteBufFormat logFormat;
  private final boolean forceSni;
  private final Http1ClientConfig http1Config;
  private final Http2ClientConfig http2Config;
  private final Duration idleTimeout;
  private final Duration readIdleTimeout;
  private final Duration writeIdleTimeout;
  private final WebSocketMetrics<?> webSocketMetrics;
  private final NetClientInternal client;

  public TcpHttpClientTransport(NetClientInternal netClient,
                                TracingPolicy tracingPolicy,
                                boolean useDecompression,
                                boolean logActivity,
                                ByteBufFormat logFormat,
                                boolean forceSni,
                                Http1ClientConfig http1Config,
                                Http2ClientConfig http2Config,
                                Duration idleTimeout,
                                Duration readIdleTimeout,
                                Duration writeIdleTimeout,
                                HttpClientMetrics<?, ?> webSocketMetrics) {

    if (http1Config != null && !http1Config.isKeepAlive() && http1Config.isPipelining()) {
      throw new IllegalStateException("Cannot have pipelining with no keep alive");
    }
    this.webSocketMetrics = webSocketMetrics;
    this.tracingPolicy = tracingPolicy;
    this.useDecompression = useDecompression;
    this.logActivity = logActivity;
    this.logFormat = logFormat;
    this.forceSni = forceSni;
    this.http1Config = http1Config;
    this.http2Config = http2Config;
    this.idleTimeout = idleTimeout != null ? idleTimeout : Duration.ofMillis(0);
    this.readIdleTimeout = readIdleTimeout != null ? readIdleTimeout : Duration.ofMillis(0);
    this.writeIdleTimeout = writeIdleTimeout != null ? writeIdleTimeout : Duration.ofMillis(0);
    this.client = netClient;
  }

  public NetClientInternal client() {
    return client;
  }

  private Http2ClientChannelInitializer http2Initializer() {
    if (http2Config.getMultiplexImplementation()) {
      return new Http2MultiplexClientChannelInitializer(
        HttpUtils.fromVertxSettings(http2Config.getInitialSettings()),
        http2Config.getKeepAliveTimeout() == null ? 0 : http2Config.getKeepAliveTimeout().toMillis(),
        http2Config.getMultiplexingLimit(),
        useDecompression,
        logActivity);
    } else {
      return new Http2CodecClientChannelInitializer(http2Config, tracingPolicy, useDecompression, logActivity);
    }
  }

  private void connect(ContextInternal context, HttpConnectParams params, HostAndPort authority, SocketAddress server, Promise<NetSocket> promise) {
    ConnectOptions connectOptions = new ConnectOptions();
    connectOptions.setRemoteAddress(server);
    if (authority != null) {
      connectOptions.setHost(authority.host());
      connectOptions.setPort(authority.port());
      if (params.ssl && forceSni) {
        connectOptions.setSniServerName(authority.host());
      }
    }
    connectOptions.setSsl(params.ssl);
    if (params.ssl) {
      ClientSSLOptions copy;
      if (params.sslOptions != null) {
        copy = params.sslOptions.copy();
      } else {
        // We might end up using javax.net.ssl.trustStore
        copy = new ClientSSLOptions().setHostnameVerificationAlgorithm("HTTPS");
      }
      if (params.protocol == HttpVersion.HTTP_2) {
        copy
          .setUseAlpn(true)
          .setApplicationLayerProtocols(List.of(HttpVersion.HTTP_2.alpnName(), HttpVersion.HTTP_1_1.alpnName()));
      } else {
        copy.setApplicationLayerProtocols(List.of(HttpVersion.HTTP_1_1.alpnName()));
      }
      connectOptions.setSslOptions(copy);
    }
    connectOptions.setProxyOptions(params.proxyOptions);
    client.connectInternal(connectOptions, promise, context);
  }

  public Future<HttpClientConnection> wrap(ContextInternal context, HttpConnectParams params, HostAndPort authority, ClientMetrics<?, ?, ?> clientMetrics, SocketAddress server, NetSocket so_) {
    NetSocketImpl so = (NetSocketImpl) so_;
    Object metric = so.metric();
    TransportMetrics<?> transportMetrics = so.metrics();
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
      if ("h2".equals(protocol)) {
        applyHttp2ConnectionOptions(ch.pipeline());
        http2Connected(context, authority, transportMetrics, metric, ch, clientMetrics, promise);
      } else {
        applyHttp1xConnectionOptions(ch.pipeline());
        HttpVersion fallbackProtocol = "http/1.0".equals(protocol) ? HttpVersion.HTTP_1_0 : HttpVersion.HTTP_1_1;
        http1xConnected(fallbackProtocol, server, authority, true, context, transportMetrics, metric, ch, clientMetrics, promise);
      }
    } else {
      if (params.protocol == HttpVersion.HTTP_2) {
        if (http2Config.isClearTextUpgrade()) {
          applyHttp1xConnectionOptions(pipeline);
          http1xConnected(params.protocol, server, authority, false, context, transportMetrics, metric, ch, clientMetrics, promise);
        } else {
          applyHttp2ConnectionOptions(pipeline);
          http2Connected(context, authority, transportMetrics, metric, ch, clientMetrics, promise);
        }
      } else {
        applyHttp1xConnectionOptions(pipeline);
        http1xConnected(params.protocol, server, authority, false, context, transportMetrics, metric, ch, clientMetrics, promise);
      }
    }
    return promise.future();
  }

  private void http2Connected(ContextInternal context, HostAndPort authority, TransportMetrics<?> transportMetrics, Object metric, Channel ch, ClientMetrics<?, ?, ?> clientMetrics, PromiseInternal<HttpClientConnection> promise) {
    Http2ClientChannelInitializer http2ChannelInitializer = http2Initializer();
    http2ChannelInitializer.http2Connected(context, authority, transportMetrics, metric, ch, clientMetrics, promise);
    if (clientMetrics != null) {
      clientMetrics.connected();
    }
  }

  public Future<HttpClientConnection> connect(ContextInternal context, SocketAddress server, HostAndPort authority, HttpConnectParams params, ClientMetrics<?, ?, ?> clientMetrics) {

    if (params.sslOptions != null && !params.sslOptions.isUseAlpn() && params.ssl && params.protocol == HttpVersion.HTTP_2) {
      return context.failedFuture("Must enable ALPN when using H2");
    }

    if (!params.ssl && params.protocol == HttpVersion.HTTP_2) {
      if (http2Config.isClearTextUpgrade() && http1Config == null) {
        return context.failedFuture("Must enable HTTP/1.1 when using H2C with upgrade");
      }
    }

    Promise<NetSocket> promise = context.promise();
    Future<NetSocket> future = promise.future();
    // We perform the compose operation before calling connect to be sure that the composition happens
    // before the promise is completed by the connect operation
    Future<HttpClientConnection> ret = future.compose(so -> wrap(context, params, authority, clientMetrics, server, so));
    connect(context, params, authority, server, promise);
    return ret;
  }

  private void applyHttp2ConnectionOptions(ChannelPipeline pipeline) {
    long idleTimeout = this.idleTimeout.toMillis();
    long readIdleTimeout = this.readIdleTimeout.toMillis();
    long writeIdleTimeout = this.writeIdleTimeout.toMillis();
    if (idleTimeout > 0 || readIdleTimeout > 0 || writeIdleTimeout > 0) {
      pipeline.addLast("idle", new IdleStateHandler(readIdleTimeout, writeIdleTimeout, idleTimeout, TimeUnit.MILLISECONDS));
    }
  }

  private void applyHttp1xConnectionOptions(ChannelPipeline pipeline) {
    long idleTimeout = this.idleTimeout.toMillis();
    long readIdleTimeout = this.readIdleTimeout.toMillis();
    long writeIdleTimeout = this.writeIdleTimeout.toMillis();
    if (idleTimeout > 0 || readIdleTimeout > 0 || writeIdleTimeout > 0) {
      pipeline.addLast("idle", new IdleStateHandler(readIdleTimeout, writeIdleTimeout, idleTimeout, TimeUnit.MILLISECONDS));
    }
    if (logActivity) {
      pipeline.addLast("logging", new LoggingHandler(logFormat));
    }
    pipeline.addLast("codec", new HttpClientCodec(
      http1Config.getMaxInitialLineLength(),
      http1Config.getMaxHeaderSize(),
      http1Config.getMaxChunkSize(),
      false,
      !HttpHeadersInternal.DISABLE_HTTP_HEADERS_VALIDATION,
      http1Config.getDecoderInitialBufferSize()));
    if (useDecompression) {
      pipeline.addLast("inflater", new HttpContentDecompressor(false));
    }
  }

  private void http1xConnected(HttpVersion version,
                               SocketAddress server,
                               HostAndPort authority,
                               boolean ssl,
                               ContextInternal context,
                               TransportMetrics<?> transportMetrics,
                               Object socketMetric,
                               Channel ch,
                               ClientMetrics clientMetrics,
                               Promise<HttpClientConnection> future) {
    boolean upgrade = version == HttpVersion.HTTP_2 && http2Config.isClearTextUpgrade();
    VertxHandler<Http1ClientConnection> clientHandler = VertxHandler.create(chctx -> {
      Http1ClientConnection conn = new Http1ClientConnection(upgrade ? HttpVersion.HTTP_1_1 : version, webSocketMetrics, transportMetrics,
        http1Config, tracingPolicy, useDecompression, chctx, ssl, server, authority, context, clientMetrics);
      if (clientMetrics != null) {
        conn.metric(socketMetric);
        clientMetrics.connected();
      }
      return conn;
    });
    clientHandler.addHandler(conn -> {
      if (upgrade) {
        Http2ClientChannelInitializer http2ChannelInitializer = http2Initializer();
        Http2UpgradeClientConnection.Http2ChannelUpgrade channelUpgrade= http2ChannelInitializer.channelUpgrade(conn, clientMetrics);
        boolean preflightRequest = http2Config.isClearTextUpgradeWithPreflightRequest();
        if (preflightRequest) {
          Http2UpgradeClientConnection conn2 = new Http2UpgradeClientConnection(conn, clientMetrics, channelUpgrade);
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
          future.complete(new Http2UpgradeClientConnection(conn, clientMetrics, channelUpgrade));
        }
      } else {
        future.complete(conn);
      }
    });
    ch.pipeline().addLast("handler", clientHandler);
  }

  @Override
  public Future<Void> shutdown(Duration timeout) {
    return client.shutdown(timeout.toMillis(), TimeUnit.MILLISECONDS);
  }

  @Override
  public Future<Void> close() {
    return client.close();
  }
}
