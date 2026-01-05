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
import io.vertx.core.internal.ContextInternal;
import io.vertx.core.internal.PromiseInternal;
import io.vertx.core.internal.VertxInternal;
import io.vertx.core.internal.http.HttpChannelConnector;
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
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.regex.Pattern;

import static io.vertx.core.http.impl.OriginEndpoint.ALPN_KEY;
import static io.vertx.core.http.impl.OriginEndpoint.AUTHORITY_KEY;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class HttpClientImpl extends HttpClientBase implements HttpClientInternal, MetricsProvider {

  // Pattern to check we are not dealing with an absoluate URI
  static final Pattern ABS_URI_START_PATTERN = Pattern.compile("^\\p{Alpha}[\\p{Alpha}\\p{Digit}+.\\-]*:");

  private final PoolOptions poolOptions;
  private final ResourceManager<EndpointKey, SharedHttpClientConnectionGroup> resourceManager;
  private final Function<HttpClientResponse, Future<RequestOptions>> redirectHandler;
  private long timerID;
  private final Function<ContextInternal, ContextInternal> contextProvider;
  private final long maxLifetime;
  private final Transport transport;
  private final EndpointResolverInternal resolver;
  private final OriginResolver<Object> originEndpoints;
  private final EndpointResolverInternal originResolver;
  private final boolean followAlternativeServices;

  HttpClientImpl(VertxInternal vertx,
                 EndpointResolver resolver,
                 Function<HttpClientResponse, Future<RequestOptions>> redirectHandler,
                 HttpClientMetrics<?, ?, ?> metrics,
                 PoolOptions poolOptions,
                 ProxyOptions defaultProxyOptions,
                 List<String> nonProxyHosts,
                 Transport transport,
                 LoadBalancer loadBalancer,
                 boolean followAlternativeServices,
                 Duration resolverKeepAlive) {
    super(vertx, metrics, defaultProxyOptions, nonProxyHosts);

    boolean resolveAll = loadBalancer != null;

    this.originEndpoints = new OriginResolver<>(vertx, resolveAll);
    this.transport = transport;
    this.resolver = (EndpointResolverInternal) resolver;
    this.originResolver = new EndpointResolverImpl<>(vertx, originEndpoints, resolveAll ? loadBalancer : LoadBalancer.FIRST, resolverKeepAlive.toMillis());
    this.poolOptions = poolOptions;
    this.resourceManager = new ResourceManager<>();
    this.maxLifetime = MILLISECONDS.convert(poolOptions.getMaxLifetime(), poolOptions.getMaxLifetimeUnit());
    this.redirectHandler = redirectHandler != null ? redirectHandler : DEFAULT_REDIRECT_HANDLER;
    this.followAlternativeServices = followAlternativeServices;
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

  private Function<EndpointKey, SharedHttpClientConnectionGroup> httpEndpointProvider(boolean resolveOrigin, Transport transport) {
    return (key) -> {
      int maxPoolSize = Math.max(poolOptions.getHttp1MaxSize(), poolOptions.getHttp2MaxSize());
      SocketAddress address = SocketAddress.inetSocketAddress(key.authority.port(), key.authority.host());
      ClientMetrics clientMetrics = HttpClientImpl.this.metrics != null ? HttpClientImpl.this.metrics.createEndpointMetrics(address, maxPoolSize) : null;
      PoolMetrics poolMetrics = HttpClientImpl.this.metrics != null ? vertx.metrics().createPoolMetrics("http", key.authority.toString(), maxPoolSize) : null;
      ProxyOptions proxyOptions = key.proxyOptions;
      if (proxyOptions != null && !key.ssl && proxyOptions.getType() == ProxyType.HTTP) {
        SocketAddress server = SocketAddress.inetSocketAddress(proxyOptions.getPort(), proxyOptions.getHost());
        key = new EndpointKey(key.ssl, key.protocol, key.sslOptions, proxyOptions, server, key.authority);
        proxyOptions = null;
      }
      HttpVersion protocol = key.protocol;
      HttpConnectParams params = new HttpConnectParams(key.protocol, key.sslOptions, proxyOptions, key.ssl);
      Function<SharedHttpClientConnectionGroup, SharedHttpClientConnectionGroup.Pool> p = group -> {
        int queueMaxSize = poolOptions.getMaxWaitQueueSize();
        int http1MaxSize = poolOptions.getHttp1MaxSize();
        int http2MaxSize = poolOptions.getHttp2MaxSize();
        int initialPoolKind = (protocol == HttpVersion.HTTP_1_1 || protocol == HttpVersion.HTTP_1_0) ? 0 : 1;
        return new SharedHttpClientConnectionGroup.Pool(group, transport.connector, queueMaxSize, http1MaxSize, http2MaxSize, maxLifetime, initialPoolKind, params, contextProvider);
      };
      return new SharedHttpClientConnectionGroup(
        clientMetrics,
        connection -> {
          if (transport.connectHandler != null) {
            transport.connectHandler.handle(connection);
          }
          if (resolveOrigin) {
            ((HttpClientConnection)connection).alternativeServicesHandler(evt -> {
              AltSvc altSvc = evt.altSvc;
              if (altSvc instanceof AltSvc.Clear) {
                originEndpoints.clearAlternatives(evt.origin);
              } else if (altSvc instanceof AltSvc.ListOfValue) {
                originEndpoints.updateAlternatives(evt.origin, (AltSvc.ListOfValue)altSvc);
              }
            });
          }
        },
        p,
        poolMetrics,
        key.authority,
        key.server);
    };
  }

  @Override
  public HttpChannelConnector channelConnector() {
    return transport.connector;
  }

  protected void setDefaultSslOptions(ClientSSLOptions options) {
    configureSSLOptions(transport.verifyHost, options);
    transport.sslOptions = options;
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
    transport.connector.shutdown(timeout).onComplete(p);
  }

  @Override
  protected void doClose(Completable<Void> p) {
    resourceManager.close();
    transport.connector.close().onComplete(p);
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
      if (port == null) {
        port = transport.defaultPort;
      }
      if (host == null) {
        host = transport.defaultHost;
      }
      server = SocketAddress.inetSocketAddress(port, host);
    } else if (addr instanceof SocketAddress) {
      server = (SocketAddress) addr;
      if (port == null) {
        port = connect.getPort();
      }
      if (host == null) {
        host = connect.getHost();
      }
      if (port == null) {
        port = server.port();
      }
      if (host == null) {
        host = server.host();
      }
    } else {
      throw new IllegalArgumentException("Only socket address are currently supported");
    }
    HttpVersion protocol = connect.getProtocolVersion();
    if (protocol == null) {
      protocol = transport.defaultProtocol;
    }
    HostAndPort authority = HostAndPort.create(host, port);
    ClientSSLOptions sslOptions = sslOptions(transport.verifyHost, connect, transport.sslOptions);
    ProxyOptions proxyOptions = computeProxyOptions(connect.getProxyOptions(), server);
    ClientMetrics clientMetrics = metrics != null ? metrics.createEndpointMetrics(server, 1) : null;
    Boolean ssl = connect.isSsl();
    boolean useSSL = ssl != null ? ssl : transport.defaultSsl;
    checkClosed();
    HttpConnectParams params = new HttpConnectParams(protocol, sslOptions, proxyOptions, useSSL);
    return transport.connector.httpConnect(vertx.getOrCreateContext(), server, authority, params, 0L, clientMetrics)
      .map(conn -> new UnpooledHttpClientConnection(conn).init());
  }

  @Override
  public Future<HttpClientRequest> request(RequestOptions request) {
    Address addr = request.getServer();
    Integer port = request.getPort();
    String host = request.getHost();
    if (addr == null) {
      if (port == null) {
        port = transport.defaultPort;
      }
      if (host == null) {
        host = transport.defaultHost;
      }
      addr = SocketAddress.inetSocketAddress(port, host);
    } else if (addr instanceof SocketAddress) {
      SocketAddress socketAddr = (SocketAddress) addr;
      if (port == null) {
        port = request.getPort();
      }
      if (host == null) {
        host = request.getHost();
      }
      if (port == null) {
        port = socketAddr.port();
      }
      if (host == null) {
        host = socketAddr.host();
      }
    }
    return doRequest(transport, addr, port, host, request);
  }

  private Future<HttpClientRequest> doRequest(Transport transport, Address server, Integer port, String host, RequestOptions request) {
    if (server == null) {
      throw new NullPointerException();
    }
    HttpMethod method = request.getMethod();
    String requestURI = request.getURI();
    Boolean ssl = request.isSsl();
    MultiMap headers = request.getHeaders();
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
    Boolean followRedirects = request.getFollowRedirects();
    Objects.requireNonNull(method, "no null method accepted");
    Objects.requireNonNull(requestURI, "no null requestURI accepted");
    boolean useSSL = ssl != null ? ssl : transport.defaultSsl;
    checkClosed();
    HostAndPort authority;
    // should we do that here ? it might create issues with address resolver that resolves this later
    if (host != null && port != null) {
      String peerHost = host;
//      if (peerHost.endsWith(".")) {
//        peerHost = peerHost.substring(0, peerHost.length() -  1);
//      }
      authority = HostAndPort.create(peerHost, port);
    } else {
      authority = null;
    }
    HttpVersion protocolVersion = request.getProtocolVersion();
    if (protocolVersion == null) {
      protocolVersion = transport.defaultProtocol;
    }
    ClientSSLOptions sslOptions = sslOptions(transport.verifyHost, request, transport.sslOptions);
    if (server instanceof SocketAddress) {
      SocketAddress serverSocketAddress = (SocketAddress) server;
      ProxyOptions proxyOptions = computeProxyOptions(request.getProxyOptions(), serverSocketAddress);
      if (proxyOptions != null || serverSocketAddress.isDomainSocket()) {
        return doRequestDirectly(protocolVersion, method, requestURI, headers, request.getTraceOperation(), idleTimeout, followRedirects, proxyOptions, serverSocketAddress, useSSL,
          sslOptions, authority, connectTimeout);
      }
    }
    HttpProtocol protocol;
    switch (protocolVersion) {
      case HTTP_1_0:
        protocol = HttpProtocol.HTTP_1_0;
        break;
      case HTTP_1_1:
        protocol = HttpProtocol.HTTP_1_1;
        break;
      case HTTP_2:
        protocol = useSSL ? HttpProtocol.H2 : HttpProtocol.H2C;
        break;
      default:
        throw new AssertionError();
    }
    return doRequest(transport, protocol, method, authority, server, useSSL, requestURI, headers, request.getTraceOperation(), request.getRoutingKey(), connectTimeout, idleTimeout, followRedirects, sslOptions);
  }

  private Future<HttpClientRequest> doRequestDirectly(
    HttpVersion protocol,
    HttpMethod httpMethod,
    String requestURI,
    MultiMap headers,
    String traceOperation,
    long idleTimeout,
    boolean followRedirects,
    ProxyOptions proxyOptions, SocketAddress server, boolean useSSL, ClientSSLOptions sslOptions,
                                 HostAndPort authority, long connectTimeout) {
    ContextInternal streamCtx = vertx.getOrCreateContext();
    EndpointKey key = new EndpointKey(useSSL, protocol, sslOptions, proxyOptions, server, authority);
    Future<ConnectionObtainedResult> fut2 = resourceManager.withResourceAsync(key, httpEndpointProvider(false, transport), (endpoint, created) -> {
      Future<Lease<HttpClientConnection>> fut = endpoint.requestConnection(streamCtx, connectTimeout);
      return fut.compose(lease -> {
        HttpClientConnection conn = lease.get();
        return conn.createStream(streamCtx).map(stream -> {
          stream.closeHandler(v -> {
            lease.recycle();
          });
          return new ConnectionObtainedResult(stream, lease, null);
        });
      });
    });
    return wrap(httpMethod, requestURI, headers, traceOperation, idleTimeout, followRedirects, proxyOptions, fut2);
  }

  private Future<HttpClientRequest> doRequest(
    Transport transport,
    HttpProtocol protocol,
    HttpMethod method,
    HostAndPort authority,
    Address server,
    boolean useSSL,
    String requestURI,
    MultiMap headers,
    String traceOperation,
    String routingKey,
    long connectTimeout,
    long idleTimeout,
    Boolean followRedirects,
    ClientSSLOptions sslOptions) {
    if (server instanceof SocketAddress && (resolver == null || !resolver.resolves(server))) {
      SocketAddress serverSocketAddress = (SocketAddress) server;
      return doRequest(
        originResolver,
        transport,
        protocol,
        method,
        authority,
        new Origin(useSSL ? "https" : "http", serverSocketAddress.host(), serverSocketAddress.port()),
        useSSL,
        requestURI,
        headers,
        traceOperation,
        routingKey,
        connectTimeout,
        idleTimeout,
        followRedirects,
        sslOptions
      );
    } else {
      return doRequest(
        resolver,
        transport,
        protocol,
        method,
        authority,
        server,
        useSSL,
        requestURI,
        headers,
        traceOperation,
        routingKey,
        connectTimeout,
        idleTimeout,
        followRedirects,
        sslOptions
      );
    }
  }

  private Future<HttpClientRequest> doRequest(
    EndpointResolverInternal resolver,
    Transport transport,
    HttpProtocol protocol_,
    HttpMethod method,
    HostAndPort authority,
    Address server,
    boolean useSSL,
    String requestURI,
    MultiMap headers,
    String traceOperation,
    String routingKey,
    long connectTimeout,
    long idleTimeout,
    Boolean followRedirects,
    ClientSSLOptions sslOptions) {
    ContextInternal streamCtx = vertx.getOrCreateContext();
    Future<ConnectionObtainedResult> future;
    PromiseInternal<Endpoint> promise = vertx.promise();
    resolver.lookupEndpoint(server, promise);
    future = promise.future()
      .compose(endpoint -> {
        ServerEndpoint lookup;
        HttpProtocol protocol;
        Origin originServer;
        // For HTTPS we must handle SNI to consider an alternative
        HostAndPort altUsed;
        if (followAlternativeServices && server instanceof Origin && ("http".equals((originServer = (Origin)server).scheme) || originServer.host.indexOf('.') > 0)) {
          lookup = endpoint.selectServer(s -> {
            Map<String, ?> properties = s.properties();
            String alpn = (String)properties.get(ALPN_KEY);
            return alpn != null && protocol_ == HttpProtocol.fromId(alpn);
          });
          protocol = protocol_;
          if (lookup == null) {
            altUsed = null;
            lookup = endpoint.selectServer();
          } else {
            Map<String, ?> props = lookup.properties();
            altUsed = (HostAndPort) props.get(AUTHORITY_KEY);
          }
        } else {
          protocol = protocol_;
          lookup = endpoint.selectServer(routingKey);
          altUsed = null;
        }
        ServerEndpoint lookup2 = lookup;
        if (lookup2 == null) {
          throw new IllegalStateException("No results for " + server);
        }
        SocketAddress address = lookup2.address();
        EndpointKey key = new EndpointKey(useSSL, protocol.version(), sslOptions, null, address, authority != null ? authority : HostAndPort.create(address.host(), address.port()));
        return resourceManager.withResourceAsync(key, httpEndpointProvider(followAlternativeServices, transport), (e, created) -> {
          Future<Lease<HttpClientConnection>> fut2 = e.requestConnection(streamCtx, connectTimeout);
          ServerInteraction endpointRequest = lookup2.newInteraction();
          return fut2.andThen(ar -> {
            if (ar.failed()) {
              endpointRequest.reportFailure(ar.cause());
            }
          }).compose(lease -> {
            HttpClientConnection conn = lease.get();
            return conn.createStream(streamCtx).map(stream -> {
              HttpClientStream wrapped = new StatisticsGatheringHttpClientStream(stream, endpointRequest);
              wrapped.closeHandler(v -> lease.recycle());
              return new ConnectionObtainedResult(wrapped, lease, altUsed);
            });
          });
        });
      });
    if (future == null) {
      // I think this is not possible - so remove it
      return streamCtx.failedFuture("Cannot resolve address " + server);
    } else {
      return wrap(method, requestURI, headers, traceOperation, idleTimeout, followRedirects, null, future);
    }
  }

  private Future<HttpClientRequest> wrap(HttpMethod method,
                                         String requestURI,
                                         MultiMap headers,
                                         String traceOperation,
                                         long idleTimeout,
                                         Boolean followRedirects,
                                         ProxyOptions proxyOptions,
                                         Future<ConnectionObtainedResult> future) {
    return future.map(res -> {
      RequestOptions options = new RequestOptions();
      options.setMethod(method);
      options.setHeaders(headers);
      options.setURI(requestURI);
      options.setProxyOptions(proxyOptions);
      options.setIdleTimeout(idleTimeout);
      options.setFollowRedirects(followRedirects);
      options.setTraceOperation(traceOperation);
      HttpClientStream stream = res.stream;
      HttpClientRequestImpl request = createRequest(stream.connection(), stream, options);
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
    private final HostAndPort alternative;
    public ConnectionObtainedResult(HttpClientStream stream, Lease<HttpClientConnection> lease, HostAndPort alternative) {
      this.stream = stream;
      this.lease = lease;
      this.alternative = alternative;
    }
  }

  HttpClientRequestImpl createRequest(HttpConnection connection, HttpClientStream stream, RequestOptions options) {
    HttpClientRequestImpl request = new HttpClientRequestImpl(connection, stream);
    request.init(options);
    Function<HttpClientResponse, Future<RequestOptions>> rHandler = redirectHandler;
    if (rHandler != null) {
      request.setMaxRedirects(transport.maxRedirects);
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

  /**
   * Transport encapsulate settings and services for a given transport (TCP or QUIC)
   */
  static class Transport {

    private final Handler<HttpConnection> connectHandler;
    private final HttpChannelConnector connector;
    private final boolean verifyHost;
    private final boolean defaultSsl;
    private final String defaultHost;
    private final int defaultPort;
    private final int maxRedirects;
    private final HttpVersion defaultProtocol;
    private volatile ClientSSLOptions sslOptions;

    Transport(Handler<HttpConnection> connectHandler, HttpChannelConnector connector,
              boolean verifyHost, boolean defaultSsl, String defaultHost, int defaultPort, int maxRedirects,
              HttpVersion defaultProtocol, ClientSSLOptions sslOptions) {

      if (sslOptions != null) {
        configureSSLOptions(verifyHost, sslOptions);
      }

      this.connectHandler = connectHandler;
      this.connector = connector;
      this.verifyHost = verifyHost;
      this.defaultSsl = defaultSsl;
      this.defaultHost = defaultHost;
      this.defaultPort = defaultPort;
      this.maxRedirects = maxRedirects;
      this.defaultProtocol = defaultProtocol;
      this.sslOptions = sslOptions;
    }
  }
}
