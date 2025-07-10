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
import io.vertx.core.internal.http.HttpClientInternal;
import io.vertx.core.internal.net.endpoint.EndpointResolverInternal;
import io.vertx.core.internal.pool.ConnectionPool;
import io.vertx.core.internal.pool.Lease;
import io.vertx.core.internal.resource.ResourceManager;
import io.vertx.core.net.*;
import io.vertx.core.net.endpoint.Endpoint;
import io.vertx.core.net.endpoint.EndpointResolver;
import io.vertx.core.net.endpoint.ServerInteraction;
import io.vertx.core.net.endpoint.impl.EndpointResolverImpl;
import io.vertx.core.spi.metrics.ClientMetrics;
import io.vertx.core.spi.metrics.MetricsProvider;
import io.vertx.core.spi.metrics.PoolMetrics;

import java.lang.ref.WeakReference;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.regex.Pattern;

import static java.util.concurrent.TimeUnit.MILLISECONDS;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class HttpClientImpl extends HttpClientBase implements HttpClientInternal, MetricsProvider {

  // Pattern to check we are not dealing with an absoluate URI
  static final Pattern ABS_URI_START_PATTERN = Pattern.compile("^\\p{Alpha}[\\p{Alpha}\\p{Digit}+.\\-]*:");

  private final PoolOptions poolOptions;
  private final ResourceManager<EndpointKey, SharedHttpClientConnectionGroup> httpCM;
  private final EndpointResolverInternal endpointResolver;
  private volatile Function<HttpClientResponse, Future<RequestOptions>> redirectHandler = DEFAULT_REDIRECT_HANDLER;
  private long timerID;
  volatile Handler<HttpConnection> connectionHandler;
  private final Function<ContextInternal, ContextInternal> contextProvider;
  private final long maxLifetime;

  public HttpClientImpl(VertxInternal vertx,
                        EndpointResolver endpointResolver,
                        HttpClientOptions options,
                        PoolOptions poolOptions) {
    super(vertx, options);

    this.endpointResolver = (EndpointResolverImpl) endpointResolver;
    this.poolOptions = poolOptions;
    httpCM = new ResourceManager<>();
    if (poolCheckerIsNeeded(options, poolOptions)) {
      PoolChecker checker = new PoolChecker(this);
      ContextInternal timerContext = vertx.createEventLoopContext();
      timerID = timerContext.setTimer(poolOptions.getCleanerPeriod(), checker);
    }
    this.maxLifetime = MILLISECONDS.convert(poolOptions.getMaxLifetime(), poolOptions.getMaxLifetimeUnit());
    int eventLoopSize = poolOptions.getEventLoopSize();
    if (eventLoopSize > 0) {
      ContextInternal[] eventLoops = new ContextInternal[eventLoopSize];
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

  private static boolean poolCheckerIsNeeded(HttpClientOptions options, PoolOptions poolOptions) {
    return poolOptions.getCleanerPeriod() > 0 && (options.getKeepAliveTimeout() > 0L || options.getHttp2KeepAliveTimeout() > 0L || poolOptions.getMaxLifetime() > 0L);
  }

  Function<ContextInternal, ContextInternal> contextProvider() {
    return contextProvider;
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

  protected void checkExpired(Handler<Long> checker) {
    synchronized (this) {
      if (!closeSequence.started()) {
        timerID = vertx.setTimer(poolOptions.getCleanerPeriod(), checker);
      }
    }
    httpCM.checkExpired();
    if (endpointResolver != null) {
      endpointResolver.checkExpired();
    }
  }

  private Function<EndpointKey, SharedHttpClientConnectionGroup> httpEndpointProvider() {
    return (key) -> {
      int maxPoolSize = Math.max(poolOptions.getHttp1MaxSize(), poolOptions.getHttp2MaxSize());
      ClientMetrics clientMetrics = HttpClientImpl.this.metrics != null ? HttpClientImpl.this.metrics.createEndpointMetrics(key.server, maxPoolSize) : null;
      PoolMetrics poolMetrics = HttpClientImpl.this.metrics != null ? vertx.metrics().createPoolMetrics("http", key.server.toString(), maxPoolSize) : null;
      ProxyOptions proxyOptions = key.proxyOptions;
      if (proxyOptions != null && !key.ssl && proxyOptions.getType() == ProxyType.HTTP) {
        SocketAddress server = SocketAddress.inetSocketAddress(proxyOptions.getPort(), proxyOptions.getHost());
        key = new EndpointKey(key.ssl, key.sslOptions, proxyOptions, server, key.authority);
        proxyOptions = null;
      }
      HttpChannelConnector connector = new HttpChannelConnector(HttpClientImpl.this, netClient, key.sslOptions, proxyOptions, clientMetrics, options.getProtocolVersion(), key.ssl, options.isUseAlpn(), key.authority, key.server, true, maxLifetime);
      return new SharedHttpClientConnectionGroup(
        vertx,
        HttpClientImpl.this,
        clientMetrics,
        poolMetrics,
        poolOptions.getMaxWaitQueueSize(),
        poolOptions.getHttp1MaxSize(),
        poolOptions.getHttp2MaxSize(),
        connector);
    };
  }

  @Override
  protected void doShutdown(Completable<Void> p) {
    synchronized (this) {
      if (timerID >= 0) {
        vertx.cancelTimer(timerID);
        timerID = -1;
      }
    }
    httpCM.shutdown();
    super.doShutdown(p);
  }

  @Override
  protected void doClose(Completable<Void> p) {
    httpCM.close();
    super.doClose(p);
  }

  public void redirectHandler(Function<HttpClientResponse, Future<RequestOptions>> handler) {
    if (handler == null) {
      handler = DEFAULT_REDIRECT_HANDLER;
    }
    redirectHandler = handler;
  }

  public Function<HttpClientResponse, Future<RequestOptions>> redirectHandler() {
    return redirectHandler;
  }

  Handler<HttpConnection> connectionHandler() {
    Handler<HttpConnection> handler = connectionHandler;
    return conn -> {
      if (options.getHttp2ConnectionWindowSize() > 0) {
        conn.setWindowSize(options.getHttp2ConnectionWindowSize());
      }
      if (handler != null) {
        handler.handle(conn);
      }
    };
  }

  @Override
  public Future<io.vertx.core.http.HttpClientConnection> connect(HttpConnectOptions connect) {
    Address addr = connect.getServer();
    Integer port = connect.getPort();
    String host = connect.getHost();
    SocketAddress server;
    if (addr == null) {
      if (port == null) {
        port = options.getDefaultPort();
      }
      if (host == null) {
        host = options.getDefaultHost();
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
    HostAndPort authority = HostAndPort.create(host, port);
    ClientSSLOptions sslOptions = sslOptions(connect);
    ProxyOptions proxyOptions = computeProxyOptions(connect.getProxyOptions(), server);
    ClientMetrics clientMetrics = metrics != null ? metrics.createEndpointMetrics(server, 1) : null;
    Boolean ssl = connect.isSsl();
    boolean useSSL = ssl != null ? ssl : this.options.isSsl();
    boolean useAlpn = options.isUseAlpn();
    if (!useAlpn && useSSL && HttpVersion.isFrameBased(this.options.getProtocolVersion())) {
      return vertx.getOrCreateContext().failedFuture("Must enable ALPN when using H2 or H3");
    }
    checkClosed();
    HttpChannelConnector connector = new HttpChannelConnector(
      this,
      netClient,
      sslOptions,
      proxyOptions,
      clientMetrics,
      options.getProtocolVersion(),
      useSSL,
      useAlpn,
      authority,
      server,
      false,
      0);
    return (Future) connector.httpConnect(vertx.getOrCreateContext()).map(conn -> new UnpooledHttpClientConnection(conn).init());
  }

  @Override
  public Future<HttpClientRequest> request(RequestOptions request) {
    Address addr = request.getServer();
    Integer port = request.getPort();
    String host = request.getHost();
    if (addr == null) {
      if (port == null) {
        port = options.getDefaultPort();
      }
      if (host == null) {
        host = options.getDefaultHost();
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
    return doRequest(addr, port, host, request);
  }

  private Future<HttpClientRequest> doRequest(Address server, Integer port, String host, RequestOptions request) {
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
    boolean useAlpn = this.options.isUseAlpn();
    boolean useSSL = ssl != null ? ssl : this.options.isSsl();
    if (!useAlpn && useSSL && HttpVersion.isFrameBased(this.options.getProtocolVersion())) {
      return vertx.getOrCreateContext().failedFuture("Must enable ALPN when using H2 or H3");
    }
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
    ClientSSLOptions sslOptions = sslOptions(request);
    return doRequest(request.getRoutingKey(), method, authority, server, useSSL, requestURI, headers, request.getTraceOperation(), connectTimeout, idleTimeout, followRedirects, sslOptions, request.getProxyOptions());
  }

  private Future<HttpClientRequest> doRequest(
    String routingKey,
    HttpMethod method,
    HostAndPort authority,
    Address server,
    boolean useSSL,
    String requestURI,
    MultiMap headers,
    String traceOperation,
    long connectTimeout,
    long idleTimeout,
    Boolean followRedirects,
    ClientSSLOptions sslOptions,
    ProxyOptions proxyConfig) {
    ContextInternal streamCtx = vertx.getOrCreateContext();
    Future<ConnectionObtainedResult> future;
    if (endpointResolver != null) {
      PromiseInternal<Endpoint> promise = vertx.promise();
      endpointResolver.lookupEndpoint(server, promise);
      future = promise.future()
        .map(endpoint -> endpoint.selectServer(routingKey))
        .compose(lookup -> {
        SocketAddress address = lookup.address();
        ProxyOptions proxyOptions = computeProxyOptions(proxyConfig, address);
        EndpointKey key = new EndpointKey(useSSL, sslOptions, proxyOptions, address, authority != null ? authority : HostAndPort.create(address.host(), address.port()));
        return httpCM.withResourceAsync(key, httpEndpointProvider(), (endpoint, created) -> {
          Future<Lease<HttpClientConnection>> fut2 = endpoint.requestConnection(streamCtx, connectTimeout);
          if (fut2 == null) {
            return null;
          } else {
            ServerInteraction endpointRequest = lookup.newInteraction();
            return fut2.andThen(ar -> {
              if (ar.failed()) {
                endpointRequest.reportFailure(ar.cause());
              }
            }).compose(lease -> {
              HttpClientConnection conn = lease.get();
              return conn.createStream(streamCtx).map(stream -> {
                HttpClientStream wrapped = new StatisticsGatheringHttpClientStream(stream, endpointRequest);
                wrapped.closeHandler(v -> lease.recycle());
                return new ConnectionObtainedResult(proxyOptions, wrapped);
              });
            });
          }
        });
      });
    } else if (server instanceof SocketAddress) {
      ProxyOptions proxyOptions = computeProxyOptions(proxyConfig, (SocketAddress) server);
      EndpointKey key = new EndpointKey(useSSL, sslOptions, proxyOptions, (SocketAddress) server, authority);
      future = httpCM.withResourceAsync(key, httpEndpointProvider(), (endpoint, created) -> {
        Future<Lease<HttpClientConnection>> fut = endpoint.requestConnection(streamCtx, connectTimeout);
        if (fut == null) {
          return null;
        } else {
          return fut.compose(lease -> {
            HttpClientConnection conn = lease.get();
            return conn.createStream(streamCtx).map(stream -> {
              stream.closeHandler(v -> {
                lease.recycle();
              });
              return new ConnectionObtainedResult(proxyOptions, stream);
            });
          });
        }
      });
    } else {
      future = streamCtx.failedFuture("Cannot resolve address " + server);
    }
    if (future == null) {
      return streamCtx.failedFuture("Cannot resolve address " + server);
    } else {
      return future.map(res -> {
        RequestOptions options = new RequestOptions();
        options.setMethod(method);
        options.setHeaders(headers);
        options.setURI(requestURI);
        options.setProxyOptions(res.proxyOptions);
        options.setIdleTimeout(idleTimeout);
        options.setFollowRedirects(followRedirects);
        options.setTraceOperation(traceOperation);
        HttpClientStream stream = res.stream;
        return createRequest(stream.connection(), stream, options);
      });
    }
  }

  private static class ConnectionObtainedResult {
    private final ProxyOptions proxyOptions;
    private final HttpClientStream stream;
    public ConnectionObtainedResult(ProxyOptions proxyOptions, HttpClientStream stream) {
      this.proxyOptions = proxyOptions;
      this.stream = stream;
    }
  }

  HttpClientRequest createRequest(HttpConnection connection, HttpClientStream stream, RequestOptions options) {
    HttpClientRequestImpl request = new HttpClientRequestImpl(connection, stream);
    request.init(options);
    Function<HttpClientResponse, Future<RequestOptions>> rHandler = redirectHandler;
    if (rHandler != null) {
      request.setMaxRedirects(this.options.getMaxRedirects());
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
