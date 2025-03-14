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
import io.vertx.core.internal.ContextInternal;
import io.vertx.core.internal.PromiseInternal;
import io.vertx.core.internal.http.HttpHeadersInternal;
import io.vertx.core.net.*;
import io.vertx.core.internal.net.NetClientInternal;
import io.vertx.core.net.*;
import io.vertx.core.net.impl.NetSocketImpl;
import io.vertx.core.net.impl.VertxHandler;
import io.vertx.core.spi.metrics.ClientMetrics;
import io.vertx.core.spi.metrics.HttpClientMetrics;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static io.vertx.core.http.HttpMethod.*;
import static io.vertx.core.net.impl.ChannelProvider.*;

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
                              boolean pooled) {
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

  public Future<HttpClientConnectionInternal> wrap(ContextInternal context, NetSocket so_) {
    NetSocketImpl so = (NetSocketImpl) so_;
    Object metric = so.metric();
    PromiseInternal<HttpClientConnectionInternal> promise = context.promise();

    // Remove all un-necessary handlers
    ChannelPipeline pipeline = so.channelHandlerContext().pipeline();
    List<ChannelHandler> removedHandlers = new ArrayList<>();
    for (Map.Entry<String, ChannelHandler> entry : pipeline) {
      ChannelHandler handler = entry.getValue();
      if (!(handler instanceof SslHandler) && !(CLIENT_SSL_HANDLER_NAME.equals(entry.getKey()))) {
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
          http3Connected(context, metric, ch, promise);
        } else if ("h2".equals(protocol)) {
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
      if (version == HttpVersion.HTTP_3) {
        applyHttp3ConnectionOptions(pipeline);
        http3Connected(context, metric, ch, promise);
      } else if (version == HttpVersion.HTTP_2) {
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

  public Future<HttpClientConnectionInternal> httpConnect(ContextInternal context) {
    Promise<NetSocket> promise = context.promise();
    Future<NetSocket> future = promise.future();
    // We perform the compose operation before calling connect to be sure that the composition happens
    // before the promise is completed by the connect operation
    Future<HttpClientConnectionInternal> ret = future.compose(so -> wrap(context, so));
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
                               Promise<HttpClientConnectionInternal> future) {
    boolean upgrade = version == HttpVersion.HTTP_2 && options.isHttp2ClearTextUpgrade();
    VertxHandler<Http1xClientConnection> clientHandler = VertxHandler.create(chctx -> {
      HttpClientMetrics met = client.metrics();
      Http1xClientConnection conn = new Http1xClientConnection(upgrade ? HttpVersion.HTTP_1_1 : version, client, chctx, ssl, server, authority, context, metrics, pooled);
      if (met != null) {
        conn.metric(socketMetric);
        met.endpointConnected(metrics);
      }
      return conn;
    });
    clientHandler.addHandler(conn -> {
      if (upgrade) {
        boolean preflightRequest = options.isHttp2ClearTextUpgradeWithPreflightRequest();
        if (preflightRequest) {
          Http2UpgradeClientConnection conn2 = new Http2UpgradeClientConnection(client, conn);
          conn2.concurrencyChangeHandler(concurrency -> {
            // Ignore
          });
          conn2.createStream(conn.context()).onComplete(ar -> {
            if (ar.succeeded()) {
              HttpClientStream stream = ar.result();
              stream.headHandler(resp -> {
                Http2UpgradeClientConnection connection = (Http2UpgradeClientConnection) stream.connection();
                HttpClientConnectionInternal unwrap = connection.unwrap();
                future.tryComplete(unwrap);
              });
              stream.exceptionHandler(future::tryFail);
              HttpRequestHead request = new HttpRequestHead(OPTIONS, "/", HttpHeaders.headers(), server.toString(),
                "http://" + server + "/", null);
              stream.writeHead(request, false, null, true, null, false);
            } else {
              future.fail(ar.cause());
            }
          });
        } else {
          future.complete(new Http2UpgradeClientConnection(client, conn));
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
                              PromiseInternal<HttpClientConnectionInternal> promise) {
    VertxHttp2ConnectionHandler<Http2ClientConnection> clientHandler;
    try {
      clientHandler = Http2ClientConnection.createHttp2ConnectionHandler(client, metrics, context, false, metric, authority, pooled);
      ch.pipeline().addLast("handler", clientHandler);
      ch.flush();
    } catch (Exception e) {
      connectFailed(ch, e, promise);
      return;
    }
    clientHandler.connectFuture().addListener(promise);
  }

  private void http3Connected(ContextInternal context,
                              Object metric,
                              Channel ch,
                              PromiseInternal<HttpClientConnectionInternal> promise) {
    VertxHttp3ConnectionHandler<Http3ClientConnection> clientHandler;
    try {
      clientHandler = Http3ClientConnection.createVertxHttp3ConnectionHandler(client, metrics, context, false, metric
        , authority, pooled);
      ch.pipeline().addLast("handler", clientHandler.getHttp3ConnectionHandler());
//      ch.pipeline().addLast(clientHandler.getUserEventHandler());
      ch.pipeline().addLast(clientHandler);
      ch.flush();
    } catch (Exception e) {
      connectFailed(ch, e, promise);
      return;
    }
    clientHandler.connectFuture().addListener(promise);
  }

  private void connectFailed(Channel ch, Throwable t, Promise<HttpClientConnectionInternal> future) {
    if (ch != null) {
      try {
        ch.close();
      } catch (Exception ignore) {
      }
    }
    future.tryFail(t);
  }
}
