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

import io.netty.channel.group.ChannelGroup;
import io.netty.channel.group.DefaultChannelGroup;
import io.vertx.core.Closeable;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.MultiMap;
import io.vertx.core.*;
import io.vertx.core.http.*;
import io.vertx.core.impl.CloseFuture;
import io.vertx.core.impl.EventLoopContext;
import io.vertx.core.net.NetClientOptions;
import io.vertx.core.net.impl.NetClientImpl;
import io.vertx.core.net.impl.ProxyFilter;
import io.vertx.core.net.impl.pool.ConnectionManager;
import io.vertx.core.impl.ContextInternal;
import io.vertx.core.impl.future.PromiseInternal;
import io.vertx.core.impl.VertxInternal;
import io.vertx.core.impl.logging.Logger;
import io.vertx.core.impl.logging.LoggerFactory;
import io.vertx.core.net.ProxyOptions;
import io.vertx.core.net.ProxyType;
import io.vertx.core.net.SocketAddress;
import io.vertx.core.net.impl.pool.Endpoint;
import io.vertx.core.net.impl.pool.Lease;
import io.vertx.core.spi.metrics.ClientMetrics;
import io.vertx.core.spi.metrics.HttpClientMetrics;
import io.vertx.core.spi.metrics.Metrics;
import io.vertx.core.spi.metrics.MetricsProvider;

import java.lang.ref.WeakReference;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static io.vertx.core.http.HttpHeaders.CONTENT_LENGTH;

/**
 *
 * This class is thread-safe.
 *
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class HttpClientImpl implements HttpClient, MetricsProvider, Closeable {

  // Pattern to check we are not dealing with an absoluate URI
  private static final Pattern ABS_URI_START_PATTERN = Pattern.compile("^\\p{Alpha}[\\p{Alpha}\\p{Digit}+.\\-]*:");

  private static final Function<HttpClientResponse, Future<RequestOptions>> DEFAULT_HANDLER = resp -> {
    try {
      int statusCode = resp.statusCode();
      String location = resp.getHeader(HttpHeaders.LOCATION);
      if (location != null && (statusCode == 301 || statusCode == 302 || statusCode == 303 || statusCode == 307 || statusCode == 308)) {
        HttpMethod m = resp.request().getMethod();
        if (statusCode == 303) {
          m = HttpMethod.GET;
        } else if (m != HttpMethod.GET && m != HttpMethod.HEAD) {
          return null;
        }
        URI uri = HttpUtils.resolveURIReference(resp.request().absoluteURI(), location);
        boolean ssl;
        int port = uri.getPort();
        String protocol = uri.getScheme();
        char chend = protocol.charAt(protocol.length() - 1);
        if (chend == 'p') {
          ssl = false;
          if (port == -1) {
            port = 80;
          }
        } else if (chend == 's') {
          ssl = true;
          if (port == -1) {
            port = 443;
          }
        } else {
          return null;
        }
        String requestURI = uri.getPath();
        if (requestURI == null || requestURI.isEmpty()) {
          requestURI = "/";
        }
        String query = uri.getQuery();
        if (query != null) {
          requestURI += "?" + query;
        }
        RequestOptions options = new RequestOptions();
        options.setMethod(m);
        options.setHost(uri.getHost());
        options.setPort(port);
        options.setSsl(ssl);
        options.setURI(requestURI);
        options.setHeaders(resp.request().headers());
        options.removeHeader(CONTENT_LENGTH);
        return Future.succeededFuture(options);
      }
      return null;
    } catch (Exception e) {
      return Future.failedFuture(e);
    }
  };

  private static final Logger log = LoggerFactory.getLogger(HttpClientImpl.class);
  private static final Consumer<Endpoint<Lease<HttpClientConnection>>> EXPIRED_CHECKER = endpoint -> ((ClientHttpEndpointBase)endpoint).checkExpired();


  private final VertxInternal vertx;
  private final ChannelGroup channelGroup;
  private final HttpClientOptions options;
  private final ConnectionManager<EndpointKey, HttpClientConnection> webSocketCM;
  private final ConnectionManager<EndpointKey, Lease<HttpClientConnection>> httpCM;
  private final NetClientImpl netClient;
  private final HttpClientMetrics metrics;
  private final boolean keepAlive;
  private final boolean pipelining;
  private final CloseFuture closeFuture;
  private long timerID;
  private Predicate<SocketAddress> proxyFilter;
  private volatile Handler<HttpConnection> connectionHandler;
  private volatile Function<HttpClientResponse, Future<RequestOptions>> redirectHandler = DEFAULT_HANDLER;

  public HttpClientImpl(VertxInternal vertx, HttpClientOptions options, CloseFuture closeFuture) {
    this.vertx = vertx;
    this.metrics = vertx.metricsSPI() != null ? vertx.metricsSPI().createHttpClientMetrics(options) : null;
    this.options = new HttpClientOptions(options);
    this.channelGroup = new DefaultChannelGroup(vertx.getAcceptorEventLoopGroup().next());
    this.closeFuture = closeFuture;
    List<HttpVersion> alpnVersions = options.getAlpnVersions();
    if (alpnVersions == null || alpnVersions.isEmpty()) {
      switch (options.getProtocolVersion()) {
        case HTTP_2:
          alpnVersions = Arrays.asList(HttpVersion.HTTP_2, HttpVersion.HTTP_1_1);
          break;
        default:
          alpnVersions = Collections.singletonList(options.getProtocolVersion());
          break;
      }
    }
    this.keepAlive = options.isKeepAlive();
    this.pipelining = options.isPipelining();
    if (!keepAlive && pipelining) {
      throw new IllegalStateException("Cannot have pipelining with no keep alive");
    }
    this.proxyFilter = options.getNonProxyHosts() != null ? ProxyFilter.nonProxyHosts(options.getNonProxyHosts()) : ProxyFilter.DEFAULT_PROXY_FILTER;
    this.netClient = new NetClientImpl(
      vertx,
      new NetClientOptions(options)
        .setHostnameVerificationAlgorithm(options.isVerifyHost() ? "HTTPS": "")
        .setProxyOptions(null)
        .setApplicationLayerProtocols(alpnVersions
          .stream()
          .map(HttpVersion::alpnName)
          .collect(Collectors.toList())),
      closeFuture);
    webSocketCM = webSocketConnectionManager();
    httpCM = httpConnectionManager();
    if (options.getPoolCleanerPeriod() > 0 && (options.getKeepAliveTimeout() > 0L || options.getHttp2KeepAliveTimeout() > 0L)) {
      PoolChecker checker = new PoolChecker(this);
      timerID = vertx.setTimer(options.getPoolCleanerPeriod(), checker);
    }

    closeFuture.add(netClient);
    closeFuture.add(this);
  }

  /**
   * A weak ref to the client so it can be finalized.
   */
  private static class PoolChecker implements Handler<Long> {

    final WeakReference<HttpClientImpl> ref;

    private PoolChecker(HttpClientImpl client) {
      ref = new WeakReference<>(client);
    }

    @Override
    public void handle(Long event) {
      HttpClientImpl client = ref.get();
      if (client != null) {
        client.checkExpired(this);
      }
    }
  }

  private void checkExpired(Handler<Long> checker) {
    httpCM.forEach(EXPIRED_CHECKER);
    synchronized (this) {
      if (!closeFuture.isClosed()) {
        timerID = vertx.setTimer(options.getPoolCleanerPeriod(), checker);
      }
    }
  }

  private ConnectionManager<EndpointKey, Lease<HttpClientConnection>> httpConnectionManager() {
    int maxPoolSize = Math.max(options.getMaxPoolSize(), options.getHttp2MaxPoolSize());
    return new ConnectionManager<>((key, ctx, dispose) -> {
      ClientMetrics metrics = this.metrics != null ? this.metrics.createEndpointMetrics(key.serverAddr, maxPoolSize) : null;
      HttpChannelConnector connector = new HttpChannelConnector(this, netClient, key.proxyOptions, metrics, options.getProtocolVersion(), key.ssl, options.isUseAlpn(), key.peerAddr, key.serverAddr);
      return new SharedClientHttpStreamEndpoint(
        this,
        metrics,
        options.getMaxWaitQueueSize(),
        options.getMaxPoolSize(),
        options.getHttp2MaxPoolSize(),
        connector,
        dispose);
    });
  }

  private ConnectionManager<EndpointKey, HttpClientConnection> webSocketConnectionManager() {
    int maxPoolSize = options.getMaxWebSockets();
    return new ConnectionManager<>((key, ctx, dispose) -> {
      ClientMetrics metrics = this.metrics != null ? this.metrics.createEndpointMetrics(key.serverAddr, maxPoolSize) : null;
      HttpChannelConnector connector = new HttpChannelConnector(this, netClient, key.proxyOptions, metrics, HttpVersion.HTTP_1_1, key.ssl, false, key.peerAddr, key.serverAddr);
      return new WebSocketEndpoint(null, maxPoolSize, connector, dispose);
    });
  }

  private int getPort(RequestOptions request) {
    Integer port = request.getPort();
    if (port != null) {
      return port;
    }
    SocketAddress server = request.getServer();
    if (server != null && server.isInetSocket()) {
      return server.port();
    }
    return options.getDefaultPort();
  }

  private ProxyOptions getProxyOptions(ProxyOptions proxyOptions) {
    if (proxyOptions == null) {
      proxyOptions = options.getProxyOptions();
    }
    return proxyOptions;
  }

  private String getHost(RequestOptions request) {
    String host = request.getHost();
    if (host != null) {
      return host;
    }
    SocketAddress server = request.getServer();
    if (server != null && server.isInetSocket()) {
      return server.host();
    }
    return options.getDefaultHost();
  }

  HttpClientMetrics metrics() {
    return metrics;
  }

  /**
   * Connect to a server.
   */
  public Future<HttpClientConnection> connect(SocketAddress server) {
    return connect(server, null);
  }

  /**
   * Connect to a server.
   */
  public Future<HttpClientConnection> connect(SocketAddress server, SocketAddress peer) {
    EventLoopContext context = (EventLoopContext) vertx.getOrCreateContext();
    HttpChannelConnector connector = new HttpChannelConnector(this, netClient, null, null, options.getProtocolVersion(), options.isSsl(), options.isUseAlpn(), peer, server);
    return connector.httpConnect(context);
  }

  @Override
  public void webSocket(WebSocketConnectOptions connectOptions, Handler<AsyncResult<WebSocket>> handler) {
    webSocket(connectOptions, vertx.promise(handler));
  }

  private void webSocket(WebSocketConnectOptions connectOptions, PromiseInternal<WebSocket> promise) {
    ProxyOptions proxyOptions = getProxyOptions(connectOptions.getProxyOptions());
    int port = getPort(connectOptions);
    String host = getHost(connectOptions);
    SocketAddress addr = SocketAddress.inetSocketAddress(port, host);
    if (proxyFilter != null) {
      if (!proxyFilter.test(addr)) {
        proxyOptions = null;
      }
    }
    EndpointKey key = new EndpointKey(connectOptions.isSsl() != null ? connectOptions.isSsl() : options.isSsl(), proxyOptions, addr, addr);
    ContextInternal ctx = promise.context();
    EventLoopContext eventLoopContext;
    if (ctx instanceof EventLoopContext) {
      eventLoopContext = (EventLoopContext) ctx;
    } else {
      eventLoopContext = vertx.createEventLoopContext(ctx.nettyEventLoop(), ctx.workerPool(), ctx.classLoader());
    }
    webSocketCM.getConnection(
      eventLoopContext,
      key,
      ar -> {
        if (ar.succeeded()) {
          Http1xClientConnection conn = (Http1xClientConnection) ar.result();
          conn.toWebSocket(ctx, connectOptions.getURI(), connectOptions.getHeaders(), connectOptions.getVersion(), connectOptions.getSubProtocols(), HttpClientImpl.this.options.getMaxWebSocketFrameSize(), promise);
        } else {
          promise.fail(ar.cause());
        }
      });
  }

  @Override
  public Future<WebSocket> webSocket(int port, String host, String requestURI) {
    Promise<WebSocket> promise = vertx.promise();
    webSocket(port, host, requestURI, promise);
    return promise.future();
  }

  @Override
  public Future<WebSocket> webSocket(String host, String requestURI) {
    Promise<WebSocket> promise = vertx.promise();
    webSocket(host, requestURI, promise);
    return promise.future();
  }

  @Override
  public Future<WebSocket> webSocket(String requestURI) {
    Promise<WebSocket> promise = vertx.promise();
    webSocket(requestURI, promise);
    return promise.future();
  }

  @Override
  public Future<WebSocket> webSocket(WebSocketConnectOptions options) {
    Promise<WebSocket> promise = vertx.promise();
    webSocket(options, promise);
    return promise.future();
  }

  @Override
  public Future<WebSocket> webSocketAbs(String url, MultiMap headers, WebsocketVersion version, List<String> subProtocols) {
    Promise<WebSocket> promise = vertx.promise();
    webSocketAbs(url, headers, version, subProtocols, promise);
    return promise.future();
  }

  @Override
  public void webSocket(int port, String host, String requestURI, Handler<AsyncResult<WebSocket>> handler) {
    webSocket(new WebSocketConnectOptions().setURI(requestURI).setHost(host).setPort(port), handler);
  }

  @Override
  public void webSocket(String host, String requestURI, Handler<AsyncResult<WebSocket>> handler) {
    webSocket(options.getDefaultPort(), host, requestURI, handler);
  }

  @Override
  public void webSocket(String requestURI, Handler<AsyncResult<WebSocket>> handler) {
    webSocket(options.getDefaultPort(), options.getDefaultHost(), requestURI, handler);
  }

  @Override
  public void webSocketAbs(String url, MultiMap headers, WebsocketVersion version, List<String> subProtocols, Handler<AsyncResult<WebSocket>> handler) {
    URI uri;
    try {
      uri = new URI(url);
    } catch (URISyntaxException e) {
      throw new IllegalArgumentException(e);
    }
    String scheme = uri.getScheme();
    if (!"ws".equals(scheme) && !"wss".equals(scheme)) {
      throw new IllegalArgumentException("Scheme: " + scheme);
    }
    boolean ssl = scheme.length() == 3;
    int port = uri.getPort();
    if (port == -1) port = ssl ? 443 : 80;
    StringBuilder relativeUri = new StringBuilder();
    if (uri.getRawPath() != null) {
      relativeUri.append(uri.getRawPath());
    }
    if (uri.getRawQuery() != null) {
      relativeUri.append('?').append(uri.getRawQuery());
    }
    if (uri.getRawFragment() != null) {
      relativeUri.append('#').append(uri.getRawFragment());
    }
    WebSocketConnectOptions options = new WebSocketConnectOptions()
      .setHost(uri.getHost())
      .setPort(port).setSsl(ssl)
      .setURI(relativeUri.toString())
      .setHeaders(headers)
      .setVersion(version)
      .setSubProtocols(subProtocols);
    webSocket(options, handler);
  }

  @Override
  public void request(RequestOptions options, Handler<AsyncResult<HttpClientRequest>> handler) {
    ContextInternal ctx = vertx.getOrCreateContext();
    PromiseInternal<HttpClientRequest> promise = ctx.promise(handler);
    doRequest(options, promise);
  }

  @Override
  public Future<HttpClientRequest> request(RequestOptions options) {
    ContextInternal ctx = vertx.getOrCreateContext();
    PromiseInternal<HttpClientRequest> promise = ctx.promise();
    doRequest(options, promise);
    return promise.future();
  }

  @Override
  public void request(HttpMethod method, int port, String host, String requestURI, Handler<AsyncResult<HttpClientRequest>> handler) {
    request(new RequestOptions().setMethod(method).setPort(port).setHost(host).setURI(requestURI), handler);
  }

  @Override
  public Future<HttpClientRequest> request(HttpMethod method, int port, String host, String requestURI) {
    return request(new RequestOptions().setMethod(method).setPort(port).setHost(host).setURI(requestURI));
  }

  @Override
  public void request(HttpMethod method, String host, String requestURI, Handler<AsyncResult<HttpClientRequest>> handler) {
    request(method, options.getDefaultPort(), host, requestURI, handler);
  }

  @Override
  public Future<HttpClientRequest> request(HttpMethod method, String host, String requestURI) {
    return request(method, options.getDefaultPort(), host, requestURI);
  }

  @Override
  public void request(HttpMethod method, String requestURI, Handler<AsyncResult<HttpClientRequest>> handler) {
    request(method, options.getDefaultPort(), options.getDefaultHost(), requestURI, handler);
  }

  @Override
  public Future<HttpClientRequest> request(HttpMethod method, String requestURI) {
    return request(method, options.getDefaultPort(), options.getDefaultHost(), requestURI);
  }

  @Override
  public void close(Promise<Void> completion) {
    synchronized (this) {
      if (timerID >= 0) {
        vertx.cancelTimer(timerID);
        timerID = -1;
      }
    }
    webSocketCM.close();
    httpCM.close();
    if (metrics != null) {
      metrics.close();
    }
    completion.complete();
  }

  @Override
  public void close(Handler<AsyncResult<Void>> handler) {
    netClient.close(handler);
  }

  @Override
  public Future<Void> close() {
    return netClient.close();
  }

  @Override
  public boolean isMetricsEnabled() {
    return getMetrics() != null;
  }

  @Override
  public Metrics getMetrics() {
    return metrics;
  }

  @Override
  public HttpClient connectionHandler(Handler<HttpConnection> handler) {
    connectionHandler = handler;
    return this;
  }

  Handler<HttpConnection> connectionHandler() {
    return connectionHandler;
  }

  @Override
  public HttpClient redirectHandler(Function<HttpClientResponse, Future<RequestOptions>> handler) {
    if (handler == null) {
      handler = DEFAULT_HANDLER;
    }
    redirectHandler = handler;
    return this;
  }

  @Override
  public Function<HttpClientResponse, Future<RequestOptions>> redirectHandler() {
    return redirectHandler;
  }

  public HttpClient proxyFilter(Predicate<SocketAddress> filter) {
    proxyFilter = filter;
    return this;
  }

  public HttpClientOptions getOptions() {
    return options;
  }

  /**
   * @return the vertx, for use in package related classes only.
   */
  public VertxInternal getVertx() {
    return vertx;
  }

  private void doRequest(RequestOptions request, PromiseInternal<HttpClientRequest> promise) {
    String host = getHost(request);
    int port = getPort(request);
    SocketAddress server = request.getServer();
    if (server == null) {
      server = SocketAddress.inetSocketAddress(port, host);
    }
    ProxyOptions proxyOptions = getProxyOptions(request.getProxyOptions());
    HttpMethod method = request.getMethod();
    String requestURI = request.getURI();
    Boolean ssl = request.isSsl();
    MultiMap headers = request.getHeaders();
    long timeout = request.getTimeout();
    Boolean followRedirects = request.getFollowRedirects();
    Objects.requireNonNull(method, "no null method accepted");
    Objects.requireNonNull(host, "no null host accepted");
    Objects.requireNonNull(requestURI, "no null requestURI accepted");
    boolean useAlpn = this.options.isUseAlpn();
    boolean useSSL = ssl != null ? ssl : this.options.isSsl();
    if (!useAlpn && useSSL && this.options.getProtocolVersion() == HttpVersion.HTTP_2) {
      throw new IllegalArgumentException("Must enable ALPN when using H2");
    }
    checkClosed();
    if (proxyFilter != null) {
      if (!proxyFilter.test(server)) {
        proxyOptions = null;
      }
    }
    if (proxyOptions != null) {
      if (!useSSL && proxyOptions.getType() == ProxyType.HTTP) {
        // If the requestURI is as not absolute URI then we do not recompute one for the proxy
        if (!ABS_URI_START_PATTERN.matcher(requestURI).find()) {
          int defaultPort = 80;
          String addPort = (port != -1 && port != defaultPort) ? (":" + port) : "";
          requestURI = (ssl == Boolean.TRUE ? "https://" : "http://") + host + addPort + requestURI;
        }
        if (proxyOptions.getUsername() != null && proxyOptions.getPassword() != null) {
          if (headers == null) {
            headers = HttpHeaders.headers();
          }
          headers.add("Proxy-Authorization", "Basic " + Base64.getEncoder()
            .encodeToString((proxyOptions.getUsername() + ":" + proxyOptions.getPassword()).getBytes()));
        }
        server = SocketAddress.inetSocketAddress(proxyOptions.getPort(), proxyOptions.getHost());
        proxyOptions = null;
      }
    }

    String peerHost = host;
    if (peerHost.endsWith(".")) {
      peerHost = peerHost.substring(0, peerHost.length() -  1);
    }
    SocketAddress peerAddress = SocketAddress.inetSocketAddress(port, peerHost);
    doRequest(method, peerAddress, server, host, port, useSSL, requestURI, headers, timeout, followRedirects, proxyOptions, promise);
  }

  private void doRequest(
    HttpMethod method,
    SocketAddress peerAddress,
    SocketAddress server,
    String host,
    int port,
    Boolean useSSL,
    String requestURI,
    MultiMap headers,
    long timeout,
    Boolean followRedirects,
    ProxyOptions proxyOptions,
    PromiseInternal<HttpClientRequest> requestPromise) {
    ContextInternal ctx = requestPromise.context();
    EndpointKey key = new EndpointKey(useSSL, proxyOptions, server, peerAddress);
    EventLoopContext eventLoopContext;
    if (ctx instanceof EventLoopContext) {
      eventLoopContext = (EventLoopContext) ctx;
    } else {
      eventLoopContext = vertx.createEventLoopContext(ctx.nettyEventLoop(), ctx.workerPool(), ctx.classLoader());
    }
    httpCM.getConnection(eventLoopContext, key, timeout, ar1 -> {
      if (ar1.succeeded()) {
        Lease<HttpClientConnection> lease = ar1.result();
        HttpClientConnection conn = lease.get();
        conn.createStream(ctx, ar2 -> {
          if (ar2.succeeded()) {
            HttpClientStream stream = ar2.result();
            stream.closeHandler(v -> {
              lease.recycle();
            });
            HttpClientRequestImpl req = new HttpClientRequestImpl(this, stream, ctx.promise(), useSSL, method, server, host, port, requestURI);
            if (headers != null) {
              req.headers().setAll(headers);
            }
            if (followRedirects != null) {
              req.setFollowRedirects(followRedirects);
            }
            if (timeout > 0L) {
              // Maybe later ?
              req.setTimeout(timeout);
            }
            requestPromise.tryComplete(req);
          } else {
            requestPromise.tryFail(ar2.cause());
          }
        });
      } else {
        requestPromise.tryFail(ar1.cause());
      }
    });
  }

  private void checkClosed() {
    if (closeFuture.isClosed()) {
      throw new IllegalStateException("Client is closed");
    }
  }

  @Override
  protected void finalize() throws Throwable {
    // Make sure this gets cleaned up if there are no more references to it
    // so as not to leave connections and resources dangling until the system is shutdown
    // which could make the JVM run out of file handles.
    close((Handler<AsyncResult<Void>>) Promise.<Void>promise());
    super.finalize();
  }
}
