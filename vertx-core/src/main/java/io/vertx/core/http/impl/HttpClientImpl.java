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

import io.vertx.core.*;
import io.vertx.core.http.*;
import io.vertx.core.http.impl.tcp.TcpHttpClientTransport;
import io.vertx.core.internal.CloseableResource;
import io.vertx.core.internal.ContextInternal;
import io.vertx.core.internal.PromiseInternal;
import io.vertx.core.internal.VertxInternal;
import io.vertx.core.internal.http.HttpClientTransport;
import io.vertx.core.internal.http.HttpClientInternal;
import io.vertx.core.internal.net.endpoint.EndpointResolverInternal;
import io.vertx.core.internal.pool.ConnectionPool;
import io.vertx.core.internal.pool.Lease;
import io.vertx.core.internal.resource.ResourceManager;
import io.vertx.core.net.*;
import io.vertx.core.net.endpoint.*;
import io.vertx.core.net.endpoint.impl.EndpointResolverImpl;
import io.vertx.core.spi.metrics.ClientMetrics;
import io.vertx.core.spi.metrics.HttpClientMetrics;
import io.vertx.core.spi.metrics.MetricsProvider;
import io.vertx.core.spi.metrics.PoolMetrics;

import java.lang.ref.WeakReference;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.regex.Pattern;

import static java.util.concurrent.TimeUnit.MILLISECONDS;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class HttpClientImpl extends HttpClientBase implements HttpClientInternal, MetricsProvider, CloseableResource<HttpClientInternal> {

  // Pattern to check we are not dealing with an absoluate URI
  static final Pattern ABS_URI_START_PATTERN = Pattern.compile("^\\p{Alpha}[\\p{Alpha}\\p{Digit}+.\\-]*:");

  private final PoolOptions poolOptions;
  private final ResourceManager<EndpointKey, SharedHttpClientConnectionGroup> resourceManager;
  private final Function<HttpClientResponse, Future<RequestOptions>> redirectHandler;
  private long timerID;
  private final Function<ContextInternal, ContextInternal> contextProvider;
  private final long maxLifetime;
  private final HttpClientTransport tcpTransport;
  private final HttpClientTransport quicTransport;
  private final HttpClientTransport defaultTransport;
  private final EndpointResolverInternal resolver;
  private final OriginResolver<Object> originEndpoints;
  private final EndpointResolverInternal originResolver;
  private final boolean followAlternativeServices;
  private final boolean verifyHost;
  private final boolean validateUri;
  private final boolean defaultSsl;
  private final String defaultHost;
  private final int defaultPort;
  private final int maxRedirects;
  private final int maxRedirectBufferedSize;
  private final List<HttpVersion> versions;
  private final Handler<HttpConnection> connectHandler;
  private volatile Handler<Throwable> exceptionHandler;
  private volatile ClientSSLOptions sslOptions;

  HttpClientImpl(VertxInternal vertx,
                 EndpointResolver resolver,
                 Function<HttpClientResponse, Future<RequestOptions>> redirectHandler,
                 HttpClientMetrics<?, ?> httpMetrics,
                 PoolOptions poolOptions,
                 ProxyOptions defaultProxyOptions,
                 List<String> nonProxyHosts,
                 LoadBalancer loadBalancer,
                 boolean followAlternativeServices,
                 Duration resolverKeepAliveTimeout,
                 Duration resolverMaxKeepAlive,
                 boolean verifyHost,
                 boolean validateUri,
                 boolean defaultSsl,
                 String defaultHost,
                 int defaultPort,
                 int maxRedirects,
                 int maxRedirectBufferedSize,
                 List<HttpVersion> versions,
                 ClientSSLOptions sslOptions,
                 Handler<HttpConnection> connectHandler,
                 HttpClientTransport tcpTransport,
                 HttpClientTransport quicTransport) {
    super(vertx, httpMetrics, defaultProxyOptions, nonProxyHosts);

    if (sslOptions != null) {
      configureSSLOptions(verifyHost, sslOptions);
    }

    boolean resolveAll = loadBalancer != null;

    this.tcpTransport = tcpTransport;
    this.quicTransport = quicTransport;
    this.defaultTransport = tcpTransport != null ? tcpTransport : quicTransport;
    this.originEndpoints = new OriginResolver<>(vertx, resolveAll, this);
    this.resolver = (EndpointResolverInternal) resolver;
    this.originResolver = new EndpointResolverImpl<>(vertx, originEndpoints, resolveAll ? loadBalancer : LoadBalancer.FIRST, resolverKeepAliveTimeout, resolverMaxKeepAlive);
    this.poolOptions = poolOptions;
    this.resourceManager = new ResourceManager<>();
    this.maxLifetime = MILLISECONDS.convert(poolOptions.getMaxLifetime(), poolOptions.getMaxLifetimeUnit());
    this.redirectHandler = Objects.requireNonNull(redirectHandler);
    this.followAlternativeServices = followAlternativeServices;
    this.verifyHost = verifyHost;
    this.validateUri = validateUri;
    this.defaultSsl = defaultSsl;
    this.defaultHost = defaultHost;
    this.defaultPort = defaultPort;
    this.maxRedirects = maxRedirects;
    this.maxRedirectBufferedSize = maxRedirectBufferedSize;
    this.versions = versions;
    this.sslOptions = sslOptions;
    this.connectHandler = connectHandler;
    int eventLoopSize = poolOptions.getEventLoopSize();
    if (eventLoopSize > 0) {
      ContextInternal[] eventLoops = new ContextInternal[eventLoopSize];
      for (int i = 0;i < eventLoopSize;i++) {
        eventLoops[i] = vertx.createEventLoopContext();
      }
      AtomicInteger idx = new AtomicInteger();
      this.contextProvider = ctx -> {
        int i = idx.getAndIncrement();
        return eventLoops[i % eventLoopSize];
      };
    } else {
      this.contextProvider = ConnectionPool.EVENT_LOOP_CONTEXT_PROVIDER;
    }

    // Init time
    if (poolOptions.getCleanerPeriod() > 0) {
      PoolChecker checker = new PoolChecker(this);
      ContextInternal timerContext = vertx.createEventLoopContext();
      timerID = timerContext.setTimer(poolOptions.getCleanerPeriod(), checker);
    }
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

  public EndpointResolverInternal originResolver() {
    return originResolver;
  }

  public EndpointResolverInternal resolver() {
    return resolver;
  }
;
  protected void checkExpired(Handler<Long> checker) {
    synchronized (this) {
      if (!closeSequence.started()) {
        timerID = vertx.setTimer(poolOptions.getCleanerPeriod(), checker);
      }
    }
    originResolver.checkExpired();
    resourceManager.checkExpired();
    if (resolver != null) {
      resolver.checkExpired();
    }
  }

  private Function<EndpointKey, SharedHttpClientConnectionGroup> httpEndpointProvider(boolean resolveOrigin, SocketAddress server, ProxyOptions _proxyOptions, HttpClientTransport transport) {
    return (key) -> {
      int maxPoolSize = Math.max(poolOptions.getHttp1MaxSize(), poolOptions.getHttp2MaxSize());
      ClientMetrics clientMetrics = HttpClientImpl.this.httpMetrics != null ? HttpClientImpl.this.httpMetrics.createEndpointMetrics(server, maxPoolSize) : null;
      PoolMetrics poolMetrics = HttpClientImpl.this.httpMetrics != null ? vertx.metrics().createPoolMetrics("http", key.authority.toString(), maxPoolSize) : null;
      ProxyOptions proxyOptions = _proxyOptions;
      ClientSSLOptions sslOptions = key.sslOptions;
      SocketAddress connect;
      boolean forwardProxy;
      if (proxyOptions != null && !key.ssl && (proxyOptions.getType() == ProxyType.HTTP || proxyOptions.getType() == ProxyType.HTTPS)) {
        connect = SocketAddress.inetSocketAddress(proxyOptions.getPort(), proxyOptions.getHost());
        key = new EndpointKey(key.ssl, key.protocol, sslOptions, proxyOptions, key.authority);
        // Forward mode: the single socket connects to the proxy as the server while the logical
        // origin stays plain HTTP (key.ssl == false).
        forwardProxy = true;
      } else {
        forwardProxy = false;
        connect = server;
      }
      HttpVersion protocol = key.protocol;
      List<HttpVersion> protocols;
      if (protocol == null) {
        protocols = versions;
      } else {
        protocols = List.of(protocol);
      }
      HttpConnectParams params = new HttpConnectParams(protocols, sslOptions, proxyOptions, key.ssl, forwardProxy);
      Function<SharedHttpClientConnectionGroup, SharedHttpClientConnectionGroup.Pool> p = group -> {
        int queueMaxSize = poolOptions.getMaxWaitQueueSize();
        if (transport instanceof TcpHttpClientTransport) {
          int http1MaxSize = poolOptions.getHttp1MaxSize();
          int http2MaxSize = poolOptions.getHttp2MaxSize();
          int initialPoolKind;
          if (protocol == null) {
            initialPoolKind = versions.contains(HttpVersion.HTTP_2) ? 1 : 0;
          } else {
            initialPoolKind = protocol == HttpVersion.HTTP_1_1 || protocol == HttpVersion.HTTP_1_0 ? 0 : 1;
          }
          return new SharedHttpClientConnectionGroup.Pool(group, transport, queueMaxSize, http1MaxSize, http2MaxSize, maxLifetime, initialPoolKind, params, contextProvider);
        } else {
          int http3MaxSize = poolOptions.getHttp3MaxSize();
          return new SharedHttpClientConnectionGroup.Pool(group, transport, queueMaxSize, http3MaxSize, maxLifetime, params, contextProvider);
        }
      };
      return new SharedHttpClientConnectionGroup(
        clientMetrics,
        httpMetrics,
        connection -> {
          if (connectHandler != null) {
            connectHandler.handle(connection);
          }
          if (resolveOrigin) {
            ((HttpClientConnection)connection).alternativeServicesHandler(evt -> {
              AltSvc altSvc = evt.altSvc;
              if (altSvc instanceof AltSvc.Clear) {
                originEndpoints.clearAlternatives(evt.origin);
              } else if (altSvc instanceof AltSvc.ListOfValue) {
                originEndpoints.updateAlternatives(sslOptions, evt.origin, (AltSvc.ListOfValue)altSvc);
              }
            });
          }
        },
        exceptionHandler,
        p,
        poolMetrics,
        key.authority,
        connect);
    };
  }

  @Override
  public HttpClientInternal get() {
    return this;
  }

  @Override
  public HttpClientTransport tcpTransport() {
    return tcpTransport;
  }

  @Override
  public HttpClientTransport quicTransport() {
    return quicTransport;
  }

  @Override
  public HttpClientInternal exceptionHandler(Handler<Throwable> handler) {
    this.exceptionHandler = handler;
    return this;
  }

  @Override
  public Future<Void> shutdown(long timeout, TimeUnit unit) {
    return HttpClientInternal.super.shutdown(timeout, unit);
  }

  @Override
  public Future<Void> close() {
    return super.close();
  }

  @Override
  public Future<Void> shutdown() {
    return super.shutdown();
  }

  protected void setDefaultSslOptions(ClientSSLOptions options) {
    configureSSLOptions(verifyHost, options);
    this.sslOptions = options;
  }

  @Override
  protected void doShutdown(Duration timeout, Completable<Void> p) {
    synchronized (this) {
      if (timerID >= 0) {
        vertx.cancelTimer(timerID);
        timerID = -1;
      }
    }
    resourceManager.shutdown();
    List<Future<Void>> list = new ArrayList<>();
    if (tcpTransport != null) {
      list.add(tcpTransport.shutdown(timeout));
    }
    if (quicTransport != null) {
      list.add(quicTransport.shutdown(timeout));
    }
    Future.join(list).<Void>mapEmpty().onComplete(p);
  }

  @Override
  protected void doClose(Completable<Void> p) {
    resourceManager.close();
    List<Future<Void>> list = new ArrayList<>();
    if (tcpTransport != null) {
      list.add(tcpTransport.close());
    }
    if (quicTransport != null) {
      list.add(quicTransport.close());
    }
    Future<?> root = Future.join(list);
    if (httpMetrics != null) {
      root = root.andThen(ar -> {
        httpMetrics.close();
      });
    }
    root.<Void>mapEmpty().onComplete(p);
  }

  public Function<HttpClientResponse, Future<RequestOptions>> redirectHandler() {
    return redirectHandler;
  }

  @Override
  public Future<io.vertx.core.http.HttpClientConnection> connect(HttpConnectOptions connect) {
    Address addr = connect.getServer();
    Integer port = connect.getPort();
    String host = connect.getHost();
    SocketAddress server;
    if (addr == null) {
      server = SocketAddress.inetSocketAddress(port != null ? port : defaultPort, host != null ? host : defaultHost);
    } else if (addr instanceof SocketAddress) {
      server = (SocketAddress) addr;
    } else {
      throw new IllegalArgumentException("Only socket address are currently supported");
    }
    if (port == null) {
      port = server.port();
    }
    if (host == null) {
      host = server.host();
    }
    HttpVersion protocol = connect.getProtocolVersion();
    List<HttpVersion> protocols;
    if (protocol == null) {
      protocols = versions;
    } else {
      protocols = List.of(protocol);
    }
    HostAndPort authority = HostAndPort.create(host, port);
    ClientSSLOptions sslOptions = sslOptions(verifyHost, connect, this.sslOptions);
    ProxyOptions proxyOptions = computeProxyOptions(connect.getProxyOptions(), server);
    ClientMetrics clientMetrics = httpMetrics != null ? httpMetrics.createEndpointMetrics(server, 1) : null;
    Boolean ssl = connect.isSsl();
    boolean useSSL = ssl != null ? ssl : defaultSsl;
    checkClosed();
    HttpConnectParams params = new HttpConnectParams(protocols, sslOptions, proxyOptions, useSSL);
    return defaultTransport.connect(vertx.getOrCreateContext(), server, authority, params, clientMetrics)
      .map(conn -> new UnpooledHttpClientConnection(conn, validateUri).init());
  }

  private RequestOptions prepare(RequestOptions request) {

    request = new RequestOptions(request);

    HttpMethod method = request.getMethod();
    String requestURI = request.getURI();
    Objects.requireNonNull(method, "no null method accepted");
    Objects.requireNonNull(requestURI, "no null requestURI accepted");

    long connectTimeout = 0L;
    long idleTimeout = 0L;
    if (request.getTimeout() >= 0L) {
      connectTimeout = request.getTimeout();
      idleTimeout = request.getTimeout();
    }
    if (request.getConnectTimeout() >= 0L) {
      connectTimeout = request.getConnectTimeout();
    }
    if (request.getIdleTimeout() >= 0L) {
      idleTimeout = request.getIdleTimeout();
    }
    request.setConnectTimeout(connectTimeout);
    request.setIdleTimeout(idleTimeout);

    HttpVersion protocolVersion = request.getProtocolVersion();
    if (protocolVersion == null && versions.size() == 1) {
      protocolVersion = versions.get(0);
    }
    request.setProtocolVersion(protocolVersion);

    return request;
  }

  @Override
  public Future<HttpClientRequest> request(RequestOptions request) {
    if (validateUri && request.getURI() != null) {
      try {
        HttpUtils.validateRequestURI(request.getURI());
      } catch (IllegalArgumentException e) {
        return vertx.getOrCreateContext().failedFuture(e);
      }
    }
    request = prepare(request);
    Future<ConnectionObtainedResult> fut = doRequest(request);
    return toRequest(request, fut);
  }

  public Future<HttpClientStream> openStream(RequestOptions request) {
    request = prepare(request);
    Future<ConnectionObtainedResult> fut = doRequest(request);
    return toStream(fut);
  }

  private HttpClientTransport transport(HttpVersion version) {
    return version == HttpVersion.HTTP_3 ? quicTransport : tcpTransport;
  }

  private Future<ConnectionObtainedResult> doRequest(RequestOptions request) {
    HttpVersion version = request.getProtocolVersion();
    if (version != null && !versions.contains(version)) {
      return vertx.failedFuture("Requested HTTP version " + version + " unsupported by this client " + versions);
    }
    if ((version != null ? transport(version) : defaultTransport) == null) {
      return vertx.getOrCreateContext().failedFuture("Handle me");
    }
    Address server = request.getServer();
    Integer port = request.getPort();
    String host = request.getHost();
    if (server == null) {
      server = SocketAddress.inetSocketAddress(port != null ? port : defaultPort, host != null ? host : defaultHost);
    }
    if (server instanceof SocketAddress) {
      SocketAddress socketAddr = (SocketAddress) server;
      if (port == null) {
        port = socketAddr.port();
      }
      if (host == null) {
        host = socketAddr.host();
      }
    }
    checkClosed();
    HostAndPort authority;
    if (host != null && port != null) {
      if (host.endsWith(".")) {
        host = host.substring(0, host.length() - 1);
      }
      authority = HostAndPort.create(host, port);
    } else {
      authority = null;
    }
    ClientSSLOptions sslOptions = sslOptions(verifyHost, request, this.sslOptions);
    Boolean ssl = request.isSsl();
    boolean useSSL = ssl != null ? ssl : defaultSsl;
    ContextInternal streamCtx = vertx.getOrCreateContext();
    long connectTimeout = request.getConnectTimeout();
    if (server instanceof SocketAddress) {
      SocketAddress serverSocketAddress = (SocketAddress) server;
      ProxyOptions proxyOptions = computeProxyOptions(request.getProxyOptions(), serverSocketAddress);
      if (proxyOptions != null || serverSocketAddress.isDomainSocket()) {
        return fromPool(false, useSSL, version, sslOptions, serverSocketAddress, authority, proxyOptions,
          group -> acquireStream(group, streamCtx, connectTimeout, null, authority, null, proxyOptions));
      }
    }
    boolean secure = useSSL || version == HttpVersion.HTTP_3;
    HttpProtocol protocol = protocolOf(version, secure);
    EndpointResolverInternal endpointResolver;
    Address address;
    if (server instanceof SocketAddress && (resolver == null || !resolver.resolves(server))) {
      SocketAddress serverSocketAddress = (SocketAddress) server;
      endpointResolver = originResolver;
      address = new Origin(secure ? "https" : "http", serverSocketAddress.host(), serverSocketAddress.port());
    } else {
      endpointResolver = resolver;
      address = server;
    }
    String routingKey = request.getRoutingKey();
    PromiseInternal<Endpoint> promise = vertx.promise();
    endpointResolver.lookupEndpoint(address, promise);
    return promise.future().compose(endpoint -> {
      ServerSelection selection = selectServer(endpoint, address, protocol, routingKey);
      SocketAddress serverAddress = selection.server.address();
      return fromPool(
        followAlternativeServices && secure && selection.alternative == null,
        secure,
        selection.protocol != null ? selection.protocol.version() : null,
        sslOptions,
        serverAddress,
        authority != null ? authority : HostAndPort.create(serverAddress.host(), serverAddress.port()),
        null,
        group -> acquireStream(group, streamCtx, connectTimeout, selection.server, authority, selection.alternative, null));
    });
  }

  private static HttpProtocol protocolOf(HttpVersion version, boolean ssl) {
    if (version == null) {
      return null;
    } else if (version == HttpVersion.HTTP_1_0) {
      return HttpProtocol.HTTP_1_0;
    } else if (version == HttpVersion.HTTP_1_1) {
      return HttpProtocol.HTTP_1_1;
    } else if (version == HttpVersion.HTTP_2) {
      return ssl ? HttpProtocol.H2 : HttpProtocol.H2C;
    } else if (version == HttpVersion.HTTP_3) {
      return HttpProtocol.H3;
    } else {
      throw new AssertionError();
    }
  }

  private static class ServerSelection {
    final ServerEndpoint server;
    final HttpProtocol protocol;
    final HostAndPort alternative;
    ServerSelection(ServerEndpoint server, HttpProtocol protocol, HostAndPort alternative) {
      this.server = server;
      this.protocol = protocol;
      this.alternative = alternative;
    }
  }

  private static final List<ProtocolFilter> PREFERRED_PROTOCOLS = List.of(ProtocolFilter.H3, ProtocolFilter.H2, ProtocolFilter.HTTP_1_1, ProtocolFilter.HTTP_1_0);

  private ServerSelection selectServer(Endpoint endpoint, Address address, HttpProtocol protocol, String routingKey) {
    ServerEndpoint server;
    HostAndPort alternative = null;
    Origin origin;
    // For HTTPS we must handle SNI to consider an alternative
    if (followAlternativeServices && address instanceof Origin && "https".equals((origin = (Origin) address).scheme) && origin.host.indexOf('.') > 0) {
      if (protocol != null) {
        server = endpoint.selectServer(ProtocolFilter.of(protocol));
      } else {
        server = null;
        Set<String> protocols = endpoint.protocols();
        for (ProtocolFilter candidate : PREFERRED_PROTOCOLS) {
          if (protocols.contains(candidate.protocol.id())) {
            server = endpoint.selectServer(candidate);
            if (server != null) {
              protocol = candidate.protocol;
              break;
            }
          }
        }
      }
      if (server == null) {
        server = endpoint.selectServer();
      } else {
        OriginServer originServer = (OriginServer) server.unwrap();
        alternative = originServer.primary ? null : originServer.authority;
      }
    } else {
      server = endpoint.selectServer(routingKey);
    }
    if (server == null) {
      throw new IllegalStateException("No results for " + address);
    }
    return new ServerSelection(server, protocol, alternative);
  }

  private Future<ConnectionObtainedResult> acquireStream(SharedHttpClientConnectionGroup group,
                                                         ContextInternal streamCtx,
                                                         long connectTimeout,
                                                         ServerEndpoint server,
                                                         HostAndPort authority,
                                                         HostAndPort alternative,
                                                         ProxyOptions proxyOptions) {
    Future<Lease<HttpClientConnection>> fut = group.requestConnection(streamCtx, connectTimeout);
    ServerInteraction interaction;
    if (server != null) {
      interaction = server.newInteraction();
      fut = fut.andThen(ar -> {
        if (ar.failed()) {
          interaction.reportFailure(ar.cause());
        }
      });
    } else {
      interaction = null;
    }
    return fut.compose(lease -> lease.get().createStream(streamCtx).map(stream -> {
      HttpClientStream s = interaction != null ? new StatisticsGatheringHttpClientStream(stream, interaction) : stream;
      return new ConnectionObtainedResult(s, lease, authority, alternative, proxyOptions);
    }));
  }

  Future<?> checkConnect(OriginServer primary, OriginAlternative alternative, OriginServer server, ClientSSLOptions sslOptions) {
    return fromPool(false, true, alternative.protocol.version(), sslOptions, server.address, primary.authority, null, new Function<SharedHttpClientConnectionGroup, Future<Boolean>>() {
      @Override
      public Future<Boolean> apply(SharedHttpClientConnectionGroup group) {
        if (group.size() > 0) {
          return Future.succeededFuture();
        } else {
          // Get something better
          Future<Lease<HttpClientConnection>> f = group.requestConnection(vertx.getOrCreateContext(), 10_000);
          return f.map(lease -> {
            lease.recycle();
            return null;
          });
        }
      }
    });
  }

  <T> Future<T> fromPool(boolean resolveOrigin,
                        boolean useSSL,
                        HttpVersion version,
                        ClientSSLOptions sslOptions,
                        SocketAddress server,
                        HostAndPort authority,
                        ProxyOptions proxyOptions,
                        Function<SharedHttpClientConnectionGroup, Future<T>> function) {
    EndpointKey key;
    if (proxyOptions != null) {
      key = new EndpointKey(useSSL, version, sslOptions, proxyOptions, authority);
    } else {
      SocketAddress serverKey = server;
      if (serverKey.hostAddress() != null && Objects.equals(server.host(), authority.host())) {
        // Ensure that two keys with same authority and distinct IP addresses are distinct
        // such as DNS client side load balancing
        serverKey = SocketAddress.inetSocketAddress(server.port(), server.hostAddress());
      }
      key = new EndpointKey(useSSL, version, sslOptions, serverKey, authority);
    }
    Function<EndpointKey, SharedHttpClientConnectionGroup> provider = httpEndpointProvider(resolveOrigin, server, proxyOptions, version != null ? transport(version) : defaultTransport);
    return resourceManager.withResourceAsync(key, provider, (group, created) -> function.apply(group));
  }

  private Future<HttpClientStream> toStream(Future<ConnectionObtainedResult> future) {
    return future.map(res -> {
      HttpClientStream stream = res.stream;
      stream.closeHandler(v -> {
        res.lease.recycle();
      });
      return stream;
    });
  }

  private Future<HttpClientRequest> toRequest(RequestOptions options, Future<ConnectionObtainedResult> future) {
    return future.map(res -> {
      options.setProxyOptions(res.proxyOptions);
      HttpClientStream stream = res.stream;
      HttpClientRequestImpl request = createRequest(res.authority, stream.connection(), stream, options);
      if (res.alternative != null) {
        String altUsedValue;
        int defaultPort = stream.connection().isSsl() ? 443 : 80;
        if (res.alternative.port() == defaultPort) {
          altUsedValue = res.alternative.host();
        } else {
          altUsedValue = res.alternative.toString();
        }
        request.putHeader(HttpHeaders.ALT_USED, altUsedValue);
      }
      stream.closeHandler(v -> {
        res.lease.recycle();
        request.handleClosed();
      });
      return request;
    });
  }

  private static class ConnectionObtainedResult {
    private final HttpClientStream stream;
    private final Lease<HttpClientConnection> lease;
    private final HostAndPort authority;
    private final HostAndPort alternative;
    private final ProxyOptions proxyOptions;
    public ConnectionObtainedResult(HttpClientStream stream, Lease<HttpClientConnection> lease, HostAndPort authority, HostAndPort alternative, ProxyOptions proxyOptions) {
      this.stream = stream;
      this.lease = lease;
      this.authority = authority;
      this.alternative = alternative;
      this.proxyOptions = proxyOptions;
    }
  }

  HttpClientRequestImpl createRequest(HostAndPort authority, HttpConnection connection, HttpClientStream stream, RequestOptions options) {
    HttpClientRequestImpl request = new HttpClientRequestImpl(authority, connection, stream, validateUri);
    request.init(options);
    request.maxRedirectBufferSize(maxRedirectBufferedSize);
    Function<HttpClientResponse, Future<RequestOptions>> rHandler = redirectHandler;
    if (rHandler != null) {
      request.setMaxRedirects(maxRedirects);
      request.redirectHandler(resp -> {
        Future<RequestOptions> fut_ = rHandler.apply(resp);
        if (fut_ != null) {
          return fut_.compose(o -> {
            o.setProxyOptions(options.getProxyOptions());
            return this.request(o);
          });
        } else {
          return null;
        }
      });
    }
    return request;
  }
}
