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

import io.vertx.core.AsyncResult;
import io.vertx.core.Closeable;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.MultiMap;
import io.vertx.core.Promise;
import io.vertx.core.http.*;
import io.vertx.core.impl.CloseFuture;
import io.vertx.core.impl.ContextInternal;
import io.vertx.core.impl.VertxInternal;
import io.vertx.core.impl.future.PromiseInternal;
import io.vertx.core.net.*;
import io.vertx.core.net.impl.pool.ConnectionManager;
import io.vertx.core.net.impl.pool.Endpoint;
import io.vertx.core.net.impl.pool.EndpointProvider;
import io.vertx.core.net.impl.pool.Lease;
import io.vertx.core.spi.metrics.ClientMetrics;
import io.vertx.core.spi.metrics.MetricsProvider;

import java.lang.ref.WeakReference;
import java.net.URI;
import java.util.Base64;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.regex.Pattern;

import static io.vertx.core.http.HttpHeaders.*;

/**
 * This class is thread-safe.
 *
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class HttpClientImpl extends HttpClientBase implements HttpClientInternal, MetricsProvider, Closeable {

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

  private static final Consumer<Endpoint<Lease<HttpClientConnection>>> EXPIRED_CHECKER = endpoint -> ((ClientHttpEndpointBase)endpoint).checkExpired();

  private final ConnectionManager<EndpointKey, Lease<HttpClientConnection>> httpCM;
  private final PoolOptions poolOptions;
  private volatile Handler<HttpConnection> connectionHandler;
  private volatile Function<HttpClientResponse, Future<RequestOptions>> redirectHandler = DEFAULT_HANDLER;
  private long timerID;

  public HttpClientImpl(VertxInternal vertx, HttpClientOptions options, PoolOptions poolOptions, CloseFuture closeFuture) {
    super(vertx, options, closeFuture);
    this.poolOptions = new PoolOptions(poolOptions);
    this.httpCM = httpConnectionManager();
    if (poolOptions.getCleanerPeriod() > 0 && (options.getKeepAliveTimeout() > 0L || options.getHttp2KeepAliveTimeout() > 0L)) {
      PoolChecker checker = new PoolChecker(this);
      ContextInternal timerContext = vertx.createEventLoopContext();
      timerID = timerContext.setTimer(options.getPoolCleanerPeriod(), checker);
    }
  }

  public NetClient netClient() {
    return netClient;
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
        timerID = vertx.setTimer(poolOptions.getCleanerPeriod(), checker);
      }
    }
  }

  private ConnectionManager<EndpointKey, Lease<HttpClientConnection>> httpConnectionManager() {
    return new ConnectionManager<>();
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
    ContextInternal context = vertx.getOrCreateContext();
    Promise<HttpClientConnection> promise = context.promise();
    HttpChannelConnector connector = new HttpChannelConnector(this, netClient, null, null, options.getProtocolVersion(), options.isSsl(), options.isUseAlpn(), peer, server);
    connector.httpConnect(context, promise);
    return promise.future();
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
    httpCM.close();
    super.close(completion);
  }

  @Override
  public HttpClient connectionHandler(Handler<HttpConnection> handler) {
    connectionHandler = handler;
    return this;
  }

  public Handler<HttpConnection> connectionHandler() {
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

  private void doRequest(RequestOptions request, PromiseInternal<HttpClientRequest> promise) {
    final String host = getHost(request);
    final int port = getPort(request);
    SocketAddress server = request.getServer();
    if (server == null) {
      server = SocketAddress.inetSocketAddress(port, host);
    }
    HttpMethod method = request.getMethod();
    String requestURI = request.getURI();
    Boolean ssl = request.isSsl();
    MultiMap headers = request.getHeaders();
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
    ProxyOptions proxyOptions = resolveProxyOptions(request.getProxyOptions(), server);

    final String peerHost;
    if (host.charAt(host.length() - 1) == '.') {
      peerHost = host.substring(0, host.length() - 1);
    } else {
      peerHost = host;
    }
    SocketAddress peerAddress = peerAddress(server, peerHost, port);

    EndpointKey key;
    if (proxyOptions != null && !useSSL && proxyOptions.getType() == ProxyType.HTTP) {
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
      key = new EndpointKey(useSSL, proxyOptions, server, peerAddress);
      proxyOptions = null;
    } else {
      key = new EndpointKey(useSSL, proxyOptions, server, peerAddress);
    }
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
    doRequest(method, server, host, port, useSSL, requestURI, headers, request.getTraceOperation(), connectTimeout, idleTimeout, followRedirects, proxyOptions, key, promise);
  }

  private static SocketAddress peerAddress(SocketAddress remoteAddress, final String peerHost, int peerPort) {
    if (remoteAddress.isInetSocket() && peerHost.equals(remoteAddress.host()) && peerPort == remoteAddress.port()) {
      return remoteAddress;
    }
    return SocketAddress.inetSocketAddress(peerPort, peerHost);
  }

  private void doRequest(
    HttpMethod method,
    SocketAddress server,
    String host,
    int port,
    Boolean useSSL,
    String requestURI,
    MultiMap headers,
    String traceOperation,
    long connectTimeout,
    long idleTimeout,
    Boolean followRedirects,
    ProxyOptions proxyOptions,
    EndpointKey key,
    PromiseInternal<HttpClientRequest> requestPromise) {
    ContextInternal ctx = requestPromise.context();
    EndpointProvider<Lease<HttpClientConnection>> provider = new EndpointProvider<Lease<HttpClientConnection>>() {
      @Override
      public Endpoint<Lease<HttpClientConnection>> create(ContextInternal ctx, Runnable dispose) {
        int maxPoolSize = Math.max(poolOptions.getHttp1MaxSize(), poolOptions.getHttp2MaxSize());
        ClientMetrics metrics = HttpClientImpl.this.metrics != null ? HttpClientImpl.this.metrics.createEndpointMetrics(key.serverAddr, maxPoolSize) : null;
        HttpChannelConnector connector = new HttpChannelConnector(HttpClientImpl.this, netClient, proxyOptions, metrics, options.getProtocolVersion(), key.ssl, options.isUseAlpn(), key.peerAddr, key.serverAddr);
        return new SharedClientHttpStreamEndpoint(
          HttpClientImpl.this,
          metrics,
          poolOptions.getMaxWaitQueueSize(),
          poolOptions.getHttp1MaxSize(),
          poolOptions.getHttp2MaxSize(),
          connector,
          dispose);
      }
    };
    long now = System.currentTimeMillis();
    httpCM.getConnection(ctx, key, provider, connectTimeout, ar1 -> {
      if (ar1.succeeded()) {
        Lease<HttpClientConnection> lease = ar1.result();
        HttpClientConnection conn = lease.get();
        conn.createStream(ctx, ar2 -> {
          if (ar2.succeeded()) {
            HttpClientStream stream = ar2.result();
            stream.closeHandler(v -> {
              lease.recycle();
            });
            HttpClientRequest req = createRequest(stream);
            req.setMethod(method);
            req.authority(HostAndPort.create(host, port));
            req.setURI(requestURI);
            req.traceOperation(traceOperation);
            if (headers != null) {
              req.headers().setAll(headers);
            }
            if (followRedirects != null) {
              req.setFollowRedirects(followRedirects);
            }
            if (idleTimeout > 0L) {
              req.setIdleTimeout(idleTimeout);
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

  Future<HttpClientRequest> createRequest(HttpClientConnection conn, ContextInternal context) {
    PromiseInternal<HttpClientStream> promise = context.promise();
    conn.createStream(context, promise);
    return promise.map(this::createRequest);
  }

  private HttpClientRequest createRequest(HttpClientStream stream) {
    HttpClientRequest request = new HttpClientRequestImpl(stream, stream.getContext().promise());
    Function<HttpClientResponse, Future<RequestOptions>> rHandler = redirectHandler;
    if (rHandler != null) {
      request.setMaxRedirects(options.getMaxRedirects());
      request.redirectHandler(resp -> {
        Future<RequestOptions> fut_ = rHandler.apply(resp);
        if (fut_ != null) {
          return fut_.compose(this::request);
        } else {
          return null;
        }
      });
    }
    return request;
  }
}
