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
public class HttpClientImpl extends HttpClientBase implements HttpClientInternal, MetricsProvider {

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

  private final EndpointProvider<EndpointKey, Lease<HttpClientConnection>> httpEndpointProvider;
  private final ConnectionManager<EndpointKey, Lease<HttpClientConnection>> httpCM;
  private final List<HttpVersion> alpnVersions;
  private EndpointResolver<?, EndpointKey, Lease<HttpClientConnection>, ?> endpointResolver;
  private volatile Function<HttpClientResponse, Future<RequestOptions>> redirectHandler = DEFAULT_HANDLER;
  private long timerID;
  private volatile Handler<HttpConnection> connectionHandler;
  private final Function<ContextInternal, EventLoopContext> contextProvider;

  public HttpClientImpl(VertxInternal vertx, HttpClientOptions options) {
    super(vertx, options);
    List<HttpVersion> alpnVersions = options.getAlpnVersions();
    if (alpnVersions == null || alpnVersions.isEmpty()) {
      switch (options.getProtocolVersion()) {
        case HTTP_2:
          this.alpnVersions = Arrays.asList(HttpVersion.HTTP_2, HttpVersion.HTTP_1_1);
          break;
        default:
          this.alpnVersions = Collections.singletonList(options.getProtocolVersion());
          break;
      }
    } else {
      this.alpnVersions = alpnVersions;
    }

    httpEndpointProvider = httpEndpointProvider();
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

  Function<ContextInternal, EventLoopContext> contextProvider() {
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
        timerID = vertx.setTimer(options.getPoolCleanerPeriod(), checker);
      }
    }
    httpCM.checkExpired();
    if (endpointResolver != null) {
      endpointResolver.checkExpired();
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

  public void addressResolver(AddressResolver<?, ?, ?> addressResolver) {
    if (addressResolver != null) {
      this.endpointResolver = new EndpointResolver<>(httpEndpointProvider, addressResolver,
        (key, addr) -> new EndpointKey(key.ssl, key.proxyOptions, addr, addr));
    } else {
      this.endpointResolver = null;
    }
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

  @Override
  public HttpClient connectionHandler(Handler<HttpConnection> handler) {
    connectionHandler = handler;
    return this;
  }

  Handler<HttpConnection> connectionHandler() {
    return connectionHandler;
  }

  @Override
  public Future<HttpClientRequest> request(HttpMethod method, int port, String host, String requestURI) {
    return request(new RequestOptions().setMethod(method).setPort(port).setHost(host).setURI(requestURI));
  }

  @Override
  public Future<HttpClientRequest> request(HttpMethod method, String host, String requestURI) {
    return request(method, options.getDefaultPort(), host, requestURI);
  }

  @Override
  public Future<HttpClientRequest> request(HttpMethod method, String requestURI) {
    return request(method, options.getDefaultPort(), options.getDefaultHost(), requestURI);
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
  public Future<HttpClientRequest> request(Address address, HttpMethod method, int port, String host, String requestURI) {
    return doRequest(address, port, host, new RequestOptions().setMethod(method).setPort(port).setHost(host).setURI(requestURI));
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
}
