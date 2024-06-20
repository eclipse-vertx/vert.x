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
import io.vertx.core.internal.ContextInternal;
import io.vertx.core.internal.VertxInternal;
import io.vertx.core.internal.pool.ConnectionPool;
import io.vertx.core.internal.pool.Lease;
import io.vertx.core.net.endpoint.impl.EndpointResolverImpl;
import io.vertx.core.http.*;
import io.vertx.core.net.*;
import io.vertx.core.net.endpoint.impl.EndpointResolverInternal;
import io.vertx.core.net.impl.endpoint.EndpointProvider;
import io.vertx.core.net.endpoint.EndpointInteraction;
import io.vertx.core.net.endpoint.EndpointNode;
import io.vertx.core.spi.metrics.ClientMetrics;
import io.vertx.core.spi.metrics.MetricsProvider;
import io.vertx.core.net.endpoint.EndpointResolver;

import java.lang.ref.WeakReference;
import java.net.URI;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.regex.Pattern;

import static io.vertx.core.http.HttpHeaders.*;

/**
 * This class is thread-safe.
 *
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class HttpClientImpl extends HttpClientBase implements HttpClientInternal, MetricsProvider {

  // Pattern to check we are not dealing with an absoluate URI
  static final Pattern ABS_URI_START_PATTERN = Pattern.compile("^\\p{Alpha}[\\p{Alpha}\\p{Digit}+.\\-]*:");

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

  private final PoolOptions poolOptions;
  private final io.vertx.core.net.impl.endpoint.EndpointManager<EndpointKey, SharedClientHttpStreamEndpoint> httpCM;
  private final EndpointResolverInternal endpointResolver;
  private volatile Function<HttpClientResponse, Future<RequestOptions>> redirectHandler = DEFAULT_HANDLER;
  private long timerID;
  private volatile Handler<HttpConnection> connectionHandler;
  private final Function<ContextInternal, ContextInternal> contextProvider;

  public HttpClientImpl(VertxInternal vertx,
                        EndpointResolver endpointResolver,
                        HttpClientOptions options,
                        PoolOptions poolOptions) {
    super(vertx, options);

    this.endpointResolver = (EndpointResolverImpl) endpointResolver;
    this.poolOptions = poolOptions;
    httpCM = new io.vertx.core.net.impl.endpoint.EndpointManager<>();
    if (poolOptions.getCleanerPeriod() > 0 && (options.getKeepAliveTimeout() > 0L || options.getHttp2KeepAliveTimeout() > 0L)) {
      PoolChecker checker = new PoolChecker(this);
      ContextInternal timerContext = vertx.createEventLoopContext();
      timerID = timerContext.setTimer(poolOptions.getCleanerPeriod(), checker);
    }
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

  private EndpointProvider<EndpointKey, SharedClientHttpStreamEndpoint> httpEndpointProvider() {
    return (key, dispose) -> {
      int maxPoolSize = Math.max(poolOptions.getHttp1MaxSize(), poolOptions.getHttp2MaxSize());
      ClientMetrics metrics = HttpClientImpl.this.metrics != null ? HttpClientImpl.this.metrics.createEndpointMetrics(key.server, maxPoolSize) : null;
      ProxyOptions proxyOptions = key.proxyOptions;
      if (proxyOptions != null && !key.ssl && proxyOptions.getType() == ProxyType.HTTP) {
        SocketAddress server = SocketAddress.inetSocketAddress(proxyOptions.getPort(), proxyOptions.getHost());
        key = new EndpointKey(key.ssl, key.sslOptions, proxyOptions, server, key.authority);
        proxyOptions = null;
      }
      HttpChannelConnector connector = new HttpChannelConnector(HttpClientImpl.this, netClient, key.sslOptions, proxyOptions, metrics, options.getProtocolVersion(), key.ssl, options.isUseAlpn(), key.authority, key.server, true);
      return new SharedClientHttpStreamEndpoint(
        HttpClientImpl.this,
        metrics,
        poolOptions.getMaxWaitQueueSize(),
        poolOptions.getHttp1MaxSize(),
        poolOptions.getHttp2MaxSize(),
        connector,
        dispose);
    };
  }

  @Override
  protected void doShutdown(Promise<Void> p) {
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
  protected void doClose(Promise<Void> p) {
    httpCM.close();
    super.doClose(p);
  }

  public void redirectHandler(Function<HttpClientResponse, Future<RequestOptions>> handler) {
    if (handler == null) {
      handler = DEFAULT_HANDLER;
    }
    redirectHandler = handler;
  }

  public Function<HttpClientResponse, Future<RequestOptions>> redirectHandler() {
    return redirectHandler;
  }

  public void connectionHandler(Handler<HttpConnection> handler) {
    connectionHandler = handler;
  }

  Handler<HttpConnection> connectionHandler() {
    return connectionHandler;
  }

  @Override
  public Future<HttpClientConnection> connect(HttpConnectOptions connect) {
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
    if (!useAlpn && useSSL && this.options.getProtocolVersion() == HttpVersion.HTTP_2) {
      return vertx.getOrCreateContext().failedFuture("Must enable ALPN when using H2");
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
      false);
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
    if (!useAlpn && useSSL && this.options.getProtocolVersion() == HttpVersion.HTTP_2) {
      return vertx.getOrCreateContext().failedFuture("Must enable ALPN when using H2");
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
    ContextInternal ctx = vertx.getOrCreateContext();
    ContextInternal connCtx = ctx.isEventLoopContext() ? ctx : vertx.createEventLoopContext(ctx.nettyEventLoop(), ctx.workerPool(), ctx.classLoader());
    Promise<HttpClientRequest> promise = ctx.promise();
    Future<ConnectionObtainedResult> future;
    if (endpointResolver != null) {
      Future<EndpointNode> fut = endpointResolver
        .lookupEndpoint(ctx, server)
        .map(endpoint -> endpoint.selectNode(routingKey));
      future = fut.compose(lookup -> {
        SocketAddress address = lookup.address();
        ProxyOptions proxyOptions = computeProxyOptions(proxyConfig, address);
        EndpointKey key = new EndpointKey(useSSL, sslOptions, proxyOptions, address, authority != null ? authority : HostAndPort.create(address.host(), address.port()));
        return httpCM.withEndpointAsync(key, httpEndpointProvider(), (endpoint, created) -> {
          Future<Lease<HttpClientConnectionInternal>> fut2 = endpoint.requestConnection(connCtx, connectTimeout);
          if (fut2 == null) {
            return null;
          } else {
            EndpointInteraction endpointRequest = lookup.newInteraction();
            return fut2.andThen(ar -> {
              if (ar.failed()) {
                endpointRequest.reportFailure(ar.cause());
              }
            }).compose(lease -> {
              HttpClientConnectionInternal conn = lease.get();
              return conn.createStream(ctx).map(stream -> {
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
      future = httpCM.withEndpointAsync(key, httpEndpointProvider(), (endpoint, created) -> {
        Future<Lease<HttpClientConnectionInternal>> fut = endpoint.requestConnection(connCtx, connectTimeout);
        if (fut == null) {
          return null;
        } else {
          return fut.compose(lease -> {
            HttpClientConnectionInternal conn = lease.get();
            return conn.createStream(ctx).map(stream -> {
              stream.closeHandler(v -> {
                lease.recycle();
              });
              return new ConnectionObtainedResult(proxyOptions, stream);
            });
          });
        }
      });
    } else {
      return ctx.failedFuture("Cannot resolve address " + server);
    }
    if (future == null) {
      return connCtx.failedFuture("Cannot resolve address " + server);
    } else {
      future.map(res -> {
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
      }).onComplete(promise);
      return promise.future();
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
