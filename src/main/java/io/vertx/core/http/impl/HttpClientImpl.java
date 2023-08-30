/*
 * Copyright (c) 2011-2022 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */

package io.vertx.core.http.impl;

import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.MultiMap;
import io.vertx.core.Promise;
import io.vertx.core.http.*;
import io.vertx.core.impl.*;
import io.vertx.core.impl.future.PromiseInternal;
import io.vertx.core.net.*;
import io.vertx.core.net.impl.NetClientBuilder;
import io.vertx.core.net.impl.NetClientInternal;
import io.vertx.core.net.impl.ProxyFilter;
import io.vertx.core.net.impl.pool.*;
import io.vertx.core.spi.metrics.ClientMetrics;
import io.vertx.core.spi.metrics.HttpClientMetrics;
import io.vertx.core.spi.metrics.Metrics;
import io.vertx.core.spi.metrics.MetricsProvider;
import io.vertx.core.spi.resolver.AddressResolver;

import java.lang.ref.WeakReference;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static io.vertx.core.http.HttpHeaders.*;

/**
 * This class is thread-safe.
 *
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class HttpClientImpl implements HttpClientInternal, MetricsProvider {

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

  private final VertxInternal vertx;
  final HttpClientOptions options;
  private final EndpointProvider<EndpointKey, Lease<HttpClientConnection>> httpEndpointProvider;
  private final ConnectionManager<EndpointKey, HttpClientConnection> webSocketCM;
  private final ConnectionManager<EndpointKey, Lease<HttpClientConnection>> httpCM;
  private final NetClientInternal netClient;
  private final HttpClientMetrics metrics;
  private final boolean keepAlive;
  private final boolean pipelining;
  private final CloseSequence closeSequence;
  private EndpointResolver<?, EndpointKey, Lease<HttpClientConnection>, ?> endpointResolver;
  private long closeTimeout = 0L;
  private TimeUnit closeTimeoutUnit = TimeUnit.SECONDS;
  private long timerID;
  private Predicate<SocketAddress> proxyFilter;
  private volatile Handler<HttpConnection> connectionHandler;
  private volatile Function<HttpClientResponse, Future<RequestOptions>> redirectHandler = DEFAULT_HANDLER;
  private final Function<ContextInternal, EventLoopContext> contextProvider;

  public HttpClientImpl(VertxInternal vertx, HttpClientOptions options) {
    this.vertx = vertx;
    this.metrics = vertx.metricsSPI() != null ? vertx.metricsSPI().createHttpClientMetrics(options) : null;
    this.options = new HttpClientOptions(options);
    this.closeSequence = new CloseSequence(this::doClose, this::doShutdown);
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
    this.netClient = new NetClientBuilder(vertx, new NetClientOptions(options)
      .setHostnameVerificationAlgorithm(options.isVerifyHost() ? "HTTPS": "")
      .setProxyOptions(null)
      .setApplicationLayerProtocols(alpnVersions
        .stream()
        .map(HttpVersion::alpnName)
        .collect(Collectors.toList())))
      .metrics(metrics)
      .build();
    httpEndpointProvider = httpEndpointProvider();
    webSocketCM = webSocketConnectionManager();
    httpCM = new ConnectionManager<>(httpEndpointProvider);
    endpointResolver = null;
    if (options.getPoolCleanerPeriod() > 0 && (options.getKeepAliveTimeout() > 0L || options.getHttp2KeepAliveTimeout() > 0L)) {
      PoolChecker checker = new PoolChecker(this);
      ContextInternal timerContext = vertx.createEventLoopContext();
      timerID = timerContext.setTimer(options.getPoolCleanerPeriod(), checker);
    }
    int eventLoopSize = options.getPoolEventLoopSize();
    if (eventLoopSize > 0) {
      EventLoopContext[] eventLoops = new EventLoopContext[eventLoopSize];
      for (int i = 0;i < eventLoopSize;i++) {
        eventLoops[i] = vertx.createEventLoopContext();
      }
      AtomicInteger idx = new AtomicInteger();
      contextProvider = ctx -> {
        int i = idx.getAndIncrement();
        return eventLoops[i % eventLoopSize];
      };
    } else {
      contextProvider = ConnectionPool.EVENT_LOOP_CONTEXT_PROVIDER;
    }
  }

  public NetClientInternal netClient() {
    return netClient;
  }

  @Override
  public Future<Void> closeFuture() {
    return closeSequence.future();
  }

  @Override
  public void close(Promise<Void> completion) {
    closeSequence.close(completion);
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
    httpCM.checkExpired();
    if (endpointResolver != null) {
      endpointResolver.checkExpired();
    }
    synchronized (this) {
      if (!closeSequence.started()) {
        timerID = vertx.setTimer(options.getPoolCleanerPeriod(), checker);
      }
    }
  }

  private EndpointProvider<EndpointKey, Lease<HttpClientConnection>> httpEndpointProvider() {
    return (key, dispose) -> {
      int maxPoolSize = Math.max(options.getMaxPoolSize(), options.getHttp2MaxPoolSize());
      ClientMetrics metrics = HttpClientImpl.this.metrics != null ? HttpClientImpl.this.metrics.createEndpointMetrics(key.serverAddr, maxPoolSize) : null;
      ProxyOptions proxyOptions = key.proxyOptions;
      if (proxyOptions != null && !key.ssl && proxyOptions.getType() == ProxyType.HTTP) {
        SocketAddress server = SocketAddress.inetSocketAddress(proxyOptions.getPort(), proxyOptions.getHost());
        key = new EndpointKey(key.ssl, proxyOptions, server, key.peerAddr);
        proxyOptions = null;
      }
      HttpChannelConnector connector = new HttpChannelConnector(HttpClientImpl.this, netClient, proxyOptions, metrics, options.getProtocolVersion(), key.ssl, options.isUseAlpn(), key.peerAddr, key.serverAddr);
      return new SharedClientHttpStreamEndpoint(
        HttpClientImpl.this,
        metrics,
        options.getMaxWaitQueueSize(),
        options.getMaxPoolSize(),
        options.getHttp2MaxPoolSize(),
        connector,
        dispose);
    };
  }

  private ConnectionManager<EndpointKey, HttpClientConnection> webSocketConnectionManager() {
    EndpointProvider<EndpointKey, HttpClientConnection> provider = (key, dispose) -> {
      int maxPoolSize = options.getMaxWebSockets();
      ClientMetrics metrics = HttpClientImpl.this.metrics != null ? HttpClientImpl.this.metrics.createEndpointMetrics(key.serverAddr, maxPoolSize) : null;
      HttpChannelConnector connector = new HttpChannelConnector(HttpClientImpl.this, netClient, key.proxyOptions, metrics, HttpVersion.HTTP_1_1, key.ssl, false, key.peerAddr, key.serverAddr);
      return new WebSocketEndpoint(null, maxPoolSize, connector, dispose);
    };
    return new ConnectionManager<>(provider);
  }

  Function<ContextInternal, EventLoopContext> contextProvider() {
    return contextProvider;
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

  private ProxyOptions computeProxyOptions(ProxyOptions proxyOptions, SocketAddress addr) {
    proxyOptions = getProxyOptions(proxyOptions);
    if (proxyFilter != null) {
      if (!proxyFilter.test(addr)) {
        proxyOptions = null;
      }
    }
    return proxyOptions;
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
  public ClientWebSocket webSocket() {
    return new ClientWebSocketImpl(this);
  }

  Future<WebSocket> webSocket(ContextInternal ctx, WebSocketConnectOptions connectOptions) {
    PromiseInternal<WebSocket> promise = ctx.promise();
    webSocket(ctx, connectOptions, promise);
    return promise.andThen(ar -> {
      if (ar.succeeded()) {
        ar.result().resume();
      }
    });
  }

  void webSocket(ContextInternal ctx, WebSocketConnectOptions connectOptions, Promise<WebSocket> promise) {
    int port = getPort(connectOptions);
    String host = getHost(connectOptions);
    SocketAddress addr = SocketAddress.inetSocketAddress(port, host);
    ProxyOptions proxyOptions = computeProxyOptions(connectOptions.getProxyOptions(), addr);
    EndpointKey key = new EndpointKey(connectOptions.isSsl() != null ? connectOptions.isSsl() : options.isSsl(), proxyOptions, addr, addr);
    EventLoopContext eventLoopContext;
    if (ctx instanceof EventLoopContext) {
      eventLoopContext = (EventLoopContext) ctx;
    } else {
      eventLoopContext = vertx.createEventLoopContext(ctx.nettyEventLoop(), ctx.workerPool(), ctx.classLoader());
    }
    webSocketCM
      .getConnection(eventLoopContext, key)
      .onComplete(c -> {
        if (c.succeeded()) {
          Http1xClientConnection conn = (Http1xClientConnection) c.result();
          conn.toWebSocket(
            ctx,
            connectOptions.getURI(),
            connectOptions.getHeaders(),
            connectOptions.getAllowOriginHeader(),
            connectOptions.getVersion(),
            connectOptions.getSubProtocols(),
            connectOptions.getTimeout(),
            connectOptions.isRegisterWriteHandlers(),
            HttpClientImpl.this.options.getMaxWebSocketFrameSize(),
            promise);
        } else {
          promise.fail(c.cause());
        }
    });
  }

  @Override
  public Future<WebSocket> webSocket(String host, String requestURI) {
    return webSocket(options.getDefaultPort(), host, requestURI);
  }

  @Override
  public Future<WebSocket> webSocket(String requestURI) {
    return webSocket(options.getDefaultPort(), options.getDefaultHost(), requestURI);
  }

  @Override
  public Future<WebSocket> webSocket(WebSocketConnectOptions options) {
    return webSocket(vertx.getOrCreateContext(), options);
  }

  static WebSocketConnectOptions webSocketConnectOptionsAbs(String url, MultiMap headers, WebsocketVersion version, List<String> subProtocols) {
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
    return new WebSocketConnectOptions()
      .setHost(uri.getHost())
      .setPort(port).setSsl(ssl)
      .setURI(relativeUri.toString())
      .setHeaders(headers)
      .setVersion(version)
      .setSubProtocols(subProtocols);
  }

  @Override
  public Future<WebSocket> webSocketAbs(String url, MultiMap headers, WebsocketVersion version, List<String> subProtocols) {
    return webSocket(webSocketConnectOptionsAbs(url, headers, version, subProtocols));
  }

  @Override
  public Future<HttpClientRequest> request(RequestOptions request) {
    SocketAddress server = request.getServer();
    Integer port = request.getPort();
    String host = request.getHost();
    if (server == null) {
      if (port == null) {
        port = options.getDefaultPort();
      }
      if (host == null) {
        host = options.getDefaultHost();
      }
      server = SocketAddress.inetSocketAddress(port, host);
    } else {
      if (port == null) {
        port = server.port();
      }
      if (host == null) {
        host = server.host();
      }
    }

    return doRequest(server, port, host, request);
  }

  @Override
  public Future<HttpClientRequest> request(HttpMethod method, int port, String host, String requestURI) {
    return request(new RequestOptions().setMethod(method).setPort(port).setHost(host).setURI(requestURI));
  }

  @Override
  public Future<HttpClientRequest> request(Address address, HttpMethod method, int port, String host, String requestURI) {
    return doRequest(address, port, host, new RequestOptions().setMethod(method).setPort(port).setHost(host).setURI(requestURI));
  }

  @Override
  public Future<HttpClientRequest> request(HttpMethod method, String host, String requestURI) {
    return request(method, options.getDefaultPort(), host, requestURI);
  }

  @Override
  public Future<HttpClientRequest> request(HttpMethod method, String requestURI) {
    return request(method, options.getDefaultPort(), options.getDefaultHost(), requestURI);
  }

  private void doShutdown(Promise<Void> p) {
    synchronized (this) {
      if (timerID >= 0) {
        vertx.cancelTimer(timerID);
        timerID = -1;
      }
    }
    httpCM.shutdown();
    webSocketCM.shutdown();
    netClient.shutdown(closeTimeout, closeTimeoutUnit).onComplete(p);
  }

  private void doClose(Promise<Void> p) {
    httpCM.close();
    webSocketCM.close();
    netClient.close().onComplete(p);
  }

  @Override
  public Future<Void> close(long timeout, TimeUnit timeUnit) {
    this.closeTimeout = timeout;
    this.closeTimeoutUnit = timeUnit;
    return closeSequence.close();
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
  public Future<Void> updateSSLOptions(SSLOptions options) {
    return netClient.updateSSLOptions(options);
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

  @Override
  public HttpClientOptions options() {
    return options;
  }

  @Override
  public VertxInternal vertx() {
    return vertx;
  }

  public void addressResolver(AddressResolver<?, ?, ?> addressResolver) {
    if (addressResolver != null) {
      this.endpointResolver = new EndpointResolver<>(httpEndpointProvider, addressResolver,
        (key, addr) -> new EndpointKey(key.ssl, key.proxyOptions, addr, addr));
    } else {
      this.endpointResolver = null;
    }
  }

  private Future<HttpClientRequest> doRequest(Address server, int port, String host, RequestOptions request) {
    if (server == null) {
      throw new NullPointerException();
    }
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
      return vertx.getOrCreateContext().failedFuture("Must enable ALPN when using H2");
    }
    checkClosed();
    String peerHost = host;
    if (peerHost.endsWith(".")) {
      peerHost = peerHost.substring(0, peerHost.length() -  1);
    }
    SocketAddress peerAddress = SocketAddress.inetSocketAddress(port, peerHost);
    return doRequest(method, peerAddress, server, host, port, useSSL, requestURI, headers, request.getTraceOperation(), timeout, followRedirects, request.getProxyOptions());
  }

  private Future<HttpClientRequest> doRequest(
    HttpMethod method,
    SocketAddress peerAddress,
    Address server,
    String host,
    int port,
    Boolean useSSL,
    String requestURI,
    MultiMap headers,
    String traceOperation,
    long timeout,
    Boolean followRedirects,
    ProxyOptions proxyConfig) {
    ContextInternal ctx = vertx.getOrCreateContext();
    ContextInternal connCtx = ctx.isEventLoopContext() ? ctx : vertx.createEventLoopContext(ctx.nettyEventLoop(), ctx.workerPool(), ctx.classLoader());
    Promise<HttpClientRequest> promise = ctx.promise();
    Future<HttpClientStream> future;
    ProxyOptions proxyOptions;
    if (server instanceof SocketAddress) {
      proxyOptions = computeProxyOptions(proxyConfig, (SocketAddress) server);
      EndpointKey key = new EndpointKey(useSSL, proxyOptions, (SocketAddress) server, peerAddress);
      future = httpCM.withEndpoint(key, endpoint -> {
        Future<Lease<HttpClientConnection>> fut = endpoint.getConnection(connCtx, timeout);
        if (fut == null) {
          return Optional.empty();
        } else {
          return Optional.of(fut.compose(lease -> {
            HttpClientConnection conn = lease.get();
            return conn.createStream(ctx).andThen(ar -> {
              if (ar.succeeded()) {
                HttpClientStream stream = ar.result();
                stream.closeHandler(v -> {
                  lease.recycle();
                });
              }
            });
          }));
        }
      });
    } else {
      EndpointKey key = new EndpointKey(useSSL, proxyConfig, SocketAddress.inetSocketAddress(port, host), peerAddress);
      future = endpointResolver.withEndpoint(server, key, endpoint -> createDecoratedHttpClientStream(
        timeout,
        ctx,
        connCtx,
        (EndpointResolver.ResolvedEndpoint<?, ?, ?, Lease<HttpClientConnection>>) endpoint));
      if (future != null) {
        proxyOptions = proxyConfig;
      } else {
        proxyOptions = null;
      }
    }
    if (future == null) {
      return connCtx.failedFuture("Cannot resolve address " + server);
    } else {
      future.map(stream -> {
        String u = requestURI;
        if (proxyOptions != null && !useSSL && proxyOptions.getType() == ProxyType.HTTP) {
          if (!ABS_URI_START_PATTERN.matcher(u).find()) {
            int defaultPort = 80;
            String addPort = (port != -1 && port != defaultPort) ? (":" + port) : "";
            u = (useSSL == Boolean.TRUE ? "https://" : "http://") + host + addPort + requestURI;
          }
        }
        HttpClientRequest req = new HttpClientRequestImpl(this, stream, ctx.promise(), useSSL, method, peerAddress, host, port, u, traceOperation);
        if (headers != null) {
          req.headers().setAll(headers);
        }
        if (proxyOptions != null && !useSSL && proxyOptions.getType() == ProxyType.HTTP) {
          if (proxyOptions.getUsername() != null && proxyOptions.getPassword() != null) {
            req.headers().add("Proxy-Authorization", "Basic " + Base64.getEncoder()
              .encodeToString((proxyOptions.getUsername() + ":" + proxyOptions.getPassword()).getBytes()));
          }
        }
        if (followRedirects != null) {
          req.setFollowRedirects(followRedirects);
        }
        if (timeout > 0L) {
          // Maybe later ?
          req.setTimeout(timeout);
        }
        return req;
      }).onComplete(promise);
      return promise.future();
    }
  }

  /**
   * Create a decorated {@link HttpClientStream} that will gather stream statistics reported to the {@link AddressResolver}
   */
  private static <S, A extends Address, K> Optional<Future<HttpClientStream>> createDecoratedHttpClientStream(
    long timeout,
    ContextInternal ctx,
    ContextInternal connCtx,
    EndpointResolver.ResolvedEndpoint<S, A, K, Lease<HttpClientConnection>> resolvedEndpoint) {
    Future<Lease<HttpClientConnection>> f = resolvedEndpoint.getConnection(connCtx, timeout);
    if (f == null) {
      return Optional.empty();
    } else {
      return Optional.of(f.compose(lease -> {
        HttpClientConnection conn = lease.get();
        return conn
          .createStream(ctx)
          .map(stream -> {
            AddressResolver<S, A, ?> resolver = resolvedEndpoint.resolver();
            HttpClientStream wrapped = new StatisticsGatheringHttpClientStream<>(stream, resolver, resolvedEndpoint.state(), conn.remoteAddress());
            wrapped.closeHandler(v -> lease.recycle());
            return wrapped;
          });
      }));
    }
  }

  private void checkClosed() {
    if (closeSequence.started()) {
      throw new IllegalStateException("Client is closed");
    }
  }
}
