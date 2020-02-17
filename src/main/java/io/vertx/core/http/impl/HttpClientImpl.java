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

import io.vertx.core.Closeable;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.MultiMap;
import io.vertx.core.*;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.*;
import io.vertx.core.impl.ContextInternal;
import io.vertx.core.impl.VertxInternal;
import io.vertx.core.impl.logging.Logger;
import io.vertx.core.impl.logging.LoggerFactory;
import io.vertx.core.net.NetSocket;
import io.vertx.core.net.ProxyOptions;
import io.vertx.core.net.ProxyType;
import io.vertx.core.net.SocketAddress;
import io.vertx.core.net.impl.SSLHelper;
import io.vertx.core.spi.metrics.HttpClientMetrics;
import io.vertx.core.spi.metrics.Metrics;
import io.vertx.core.spi.metrics.MetricsProvider;
import io.vertx.core.streams.ReadStream;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.regex.Pattern;

/**
 *
 * This class is thread-safe
 *
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class HttpClientImpl implements HttpClient, MetricsProvider {

  // Pattern to check we are not dealing with an absoluate URI
  private static final Pattern ABS_URI_START_PATTERN = Pattern.compile("^\\p{Alpha}[\\p{Alpha}\\p{Digit}+.\\-]*:");

  private final Function<HttpClientResponse, Future<HttpClientRequest>> DEFAULT_HANDLER = resp -> {
    try {
      int statusCode = resp.statusCode();
      String location = resp.getHeader(HttpHeaders.LOCATION);
      if (location != null && (statusCode == 301 || statusCode == 302 || statusCode == 303 || statusCode == 307 || statusCode == 308)) {
        HttpMethod m = resp.request().method();
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
        String query = uri.getQuery();
        if (query != null) {
          requestURI += "?" + query;
        }
        return Future.succeededFuture(createRequest(m, null, uri.getHost(), port, ssl, requestURI, null));
      }
      return null;
    } catch (Exception e) {
      return Future.failedFuture(e);
    }
  };

  private static final Logger log = LoggerFactory.getLogger(HttpClientImpl.class);

  private final VertxInternal vertx;
  private final HttpClientOptions options;
  private final ContextInternal context;
  private final ConnectionManager webSocketCM; // The queue manager for WebSockets
  private final ConnectionManager httpCM; // The queue manager for requests
  private final Closeable closeHook;
  private final ProxyType proxyType;
  private final SSLHelper sslHelper;
  private final HttpClientMetrics metrics;
  private final boolean keepAlive;
  private final boolean pipelining;
  private volatile boolean closed;
  private volatile Handler<HttpConnection> connectionHandler;
  private volatile Function<HttpClientResponse, Future<HttpClientRequest>> redirectHandler = DEFAULT_HANDLER;

  public HttpClientImpl(VertxInternal vertx, HttpClientOptions options) {
    this.vertx = vertx;
    this.metrics = vertx.metricsSPI() != null ? vertx.metricsSPI().createHttpClientMetrics(options) : null;
    this.options = new HttpClientOptions(options);
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
    this.sslHelper = new SSLHelper(options, options.getKeyCertOptions(), options.getTrustOptions()).
        setApplicationProtocols(alpnVersions);
    sslHelper.validate(vertx);
    context = vertx.getOrCreateContext();
    closeHook = completionHandler -> {
      HttpClientImpl.this.close();
      completionHandler.handle(Future.succeededFuture());
    };
    if(options.getProtocolVersion() == HttpVersion.HTTP_2 && Context.isOnWorkerThread()) {
      throw new IllegalStateException("Cannot use HttpClient with HTTP_2 in a worker");
    }
    if (context.deploymentID() != null) {
      context.addCloseHook(closeHook);
    }
    if (!keepAlive && pipelining) {
      throw new IllegalStateException("Cannot have pipelining with no keep alive");
    }
    long maxWeight = options.getMaxPoolSize() * options.getHttp2MaxPoolSize();
    webSocketCM = new ConnectionManager(this, metrics, HttpVersion.HTTP_1_1, maxWeight, options.getMaxWaitQueueSize());

    httpCM = new ConnectionManager(this, metrics, options.getProtocolVersion(), maxWeight, options.getMaxWaitQueueSize());
    proxyType = options.getProxyOptions() != null ? options.getProxyOptions().getType() : null;
    httpCM.start();
    webSocketCM.start();
  }


  private int getPort(Integer port) {
    return port != null ? port : options.getDefaultPort();
  }

  private String getHost(String host) {
    return host != null ? host : options.getDefaultHost();
  }

  public ContextInternal context() {
    return context;
  }

  HttpClientMetrics metrics() {
    return metrics;
  }

  @Override
  public void webSocket(WebSocketConnectOptions connectOptions, Handler<AsyncResult<WebSocket>> handler) {
    int port = getPort(connectOptions.getPort());
    String host = getHost(connectOptions.getHost());
    SocketAddress addr = SocketAddress.inetSocketAddress(port, host);
    webSocketCM.getConnection(
      context,
      addr,
      connectOptions.isSsl() != null ? connectOptions.isSsl() : options.isSsl(),
      addr, ar -> {
        if (ar.succeeded()) {
          Http1xClientConnection conn = (Http1xClientConnection) ar.result();
          conn.toWebSocket(connectOptions.getURI(), connectOptions.getHeaders(), connectOptions.getVersion(), connectOptions.getSubProtocols(), HttpClientImpl.this.options.getMaxWebSocketFrameSize(), handler);
        } else {
          context.schedule(v -> handler.handle(Future.failedFuture(ar.cause())));
        }
      });
  }

  @Override
  public Future<WebSocket> webSocket(int port, String host, String requestURI) {
    Promise<WebSocket> promise = context.promise();
    webSocket(port, host, requestURI, promise);
    return promise.future();
  }

  @Override
  public Future<WebSocket> webSocket(String host, String requestURI) {
    Promise<WebSocket> promise = context.promise();
    webSocket(host, requestURI, promise);
    return promise.future();
  }

  @Override
  public Future<WebSocket> webSocket(String requestURI) {
    Promise<WebSocket> promise = context.promise();
    webSocket(requestURI, promise);
    return promise.future();
  }

  @Override
  public Future<WebSocket> webSocket(WebSocketConnectOptions options) {
    Promise<WebSocket> promise = context.promise();
    webSocket(options, promise);
    return promise.future();
  }

  @Override
  public Future<WebSocket> webSocketAbs(String url, MultiMap headers, WebsocketVersion version, List<String> subProtocols) {
    Promise<WebSocket> promise = context.promise();
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
  public void send(RequestOptions options, Handler<AsyncResult<HttpClientResponse>> responseHandler) {
    send(options, (Buffer) null, responseHandler);
  }

  @Override
  public Future<HttpClientResponse> send(RequestOptions options) {
    return send(options, (Buffer) null);
  }

  @Override
  public void send(RequestOptions options, ReadStream<Buffer> body, Handler<AsyncResult<HttpClientResponse>> responseHandler) {
    send(options.getMethod(), null, getHost(options.getHost()), getPort(options.getPort()), options.getURI(), options.getHeaders(), options.isSsl(), options.getFollowRedirects(), options.getTimeout(), body, responseHandler);
  }

  @Override
  public Future<HttpClientResponse> send(RequestOptions options, ReadStream<Buffer> body) {
    return send(options.getMethod(), null, getHost(options.getHost()), getPort(options.getPort()), options.getURI(), options.getHeaders(), options.isSsl(), options.getFollowRedirects(), options.getTimeout(), body, null);
  }

  @Override
  public void send(RequestOptions options, Buffer body, Handler<AsyncResult<HttpClientResponse>> responseHandler) {
    send(options.getMethod(), null, getHost(options.getHost()), getPort(options.getPort()), options.getURI(), options.getHeaders(), options.isSsl(), options.getFollowRedirects(), options.getTimeout(), body, responseHandler);
  }

  @Override
  public Future<HttpClientResponse> send(RequestOptions options, Buffer body) {
    return send(options.getMethod(), null, getHost(options.getHost()), getPort(options.getPort()), options.getURI(), options.getHeaders(), options.isSsl(), options.getFollowRedirects(), options.getTimeout(), body, null);
  }

  @Override
  public void send(HttpMethod method, int port, String host, String requestURI, MultiMap headers, Handler<AsyncResult<HttpClientResponse>> responseHandler) {
    send(method, port, host, requestURI, headers, (Buffer) null, responseHandler);
  }

  @Override
  public Future<HttpClientResponse> send(HttpMethod method, int port, String host, String requestURI, MultiMap headers) {
    return send(method, port, host, requestURI, headers, (Buffer) null);
  }

  @Override
  public void send(HttpMethod method, int port, String host, String requestURI, MultiMap headers, Buffer body, Handler<AsyncResult<HttpClientResponse>> responseHandler) {
    send(method, "http", host, port, requestURI, headers, null, false, 0, body, responseHandler);
  }

  @Override
  public Future<HttpClientResponse> send(HttpMethod method, int port, String host, String requestURI, MultiMap headers, Buffer body) {
    return send(method, "http", host, port, requestURI, headers, null, false, 0, body, null);
  }

  @Override
  public void send(HttpMethod method, int port, String host, String requestURI, MultiMap headers, ReadStream<Buffer> body, Handler<AsyncResult<HttpClientResponse>> responseHandler) {
    send(method, "http", host, port, requestURI, headers, null, false, 0, body, responseHandler);
  }

  @Override
  public Future<HttpClientResponse> send(HttpMethod method, int port, String host, String requestURI, MultiMap headers, ReadStream<Buffer> body) {
    return send(method, "http", host, port, requestURI, headers, null, false, 0, body, null);
  }

  @Override
  public void send(HttpMethod method, int port, String host, String requestURI, Handler<AsyncResult<HttpClientResponse>> responseHandler) {
    send(method, port, host, requestURI, (Buffer) null, responseHandler);
  }

  @Override
  public Future<HttpClientResponse> send(HttpMethod method, int port, String host, String requestURI) {
    return send(method, port, host, requestURI, (Buffer) null);
  }

  @Override
  public void send(HttpMethod method, int port, String host, String requestURI, Buffer body, Handler<AsyncResult<HttpClientResponse>> responseHandler) {
    send(method, "http", host, port, requestURI, null, null, false, 0, body, responseHandler);
  }

  @Override
  public Future<HttpClientResponse> send(HttpMethod method, int port, String host, String requestURI, ReadStream<Buffer> body) {
    return send(method, "http", host, port, requestURI, null, null, false, 0, body, null);
  }

  @Override
  public void send(HttpMethod method, int port, String host, String requestURI, ReadStream<Buffer> body, Handler<AsyncResult<HttpClientResponse>> responseHandler) {
    send(method, "http", host, port, requestURI, null, null, false, 0, body, responseHandler);
  }

  @Override
  public Future<HttpClientResponse> send(HttpMethod method, int port, String host, String requestURI, Buffer body) {
    return send(method, "http", host, port, requestURI, null, null, false, 0, body, null);
  }

  @Override
  public void send(HttpMethod method, String host, String requestURI, MultiMap headers, Handler<AsyncResult<HttpClientResponse>> responseHandler) {
    send(method, host, requestURI, headers, (Buffer) null, responseHandler);
  }

  @Override
  public Future<HttpClientResponse> send(HttpMethod method, String host, String requestURI, MultiMap headers) {
    return send(method, host, requestURI, headers, (Buffer) null);
  }

  @Override
  public void send(HttpMethod method, String host, String requestURI, MultiMap headers, Buffer body, Handler<AsyncResult<HttpClientResponse>> responseHandler) {
    send(method, "http", host, options.getDefaultPort(), requestURI, headers, null, false, 0, body, responseHandler);
  }

  @Override
  public Future<HttpClientResponse> send(HttpMethod method, String host, String requestURI, MultiMap headers, Buffer body) {
    return send(method, "http", host, options.getDefaultPort(), requestURI, headers, null, false, 0, body, null);
  }

  @Override
  public void send(HttpMethod method, String host, String requestURI, MultiMap headers, ReadStream<Buffer> body, Handler<AsyncResult<HttpClientResponse>> responseHandler) {
    send(method, "http", host, options.getDefaultPort(), requestURI, headers, null, false, 0, body, responseHandler);
  }

  @Override
  public Future<HttpClientResponse> send(HttpMethod method, String host, String requestURI, MultiMap headers, ReadStream<Buffer> body) {
    return send(method, "http", host, options.getDefaultPort(), requestURI, headers, null, false, 0, body, null);
  }

  @Override
  public void send(HttpMethod method, String host, String requestURI, Buffer body, Handler<AsyncResult<HttpClientResponse>> responseHandler) {
    send(method, "http", host, options.getDefaultPort(), requestURI, null, null, false, 0, body, responseHandler);
  }

  @Override
  public Future<HttpClientResponse> send(HttpMethod method, String host, String requestURI, Buffer body) {
    return send(method, "http", host, options.getDefaultPort(), requestURI, null, null, false, 0, body, null);
  }

  @Override
  public void send(HttpMethod method, String host, String requestURI, ReadStream<Buffer> body, Handler<AsyncResult<HttpClientResponse>> responseHandler) {
    send(method, "http", host, options.getDefaultPort(), requestURI, null, null, false, 0, body, responseHandler);
  }

  @Override
  public Future<HttpClientResponse> send(HttpMethod method, String host, String requestURI, ReadStream<Buffer> body) {
    return send(method, "http", host, options.getDefaultPort(), requestURI, null, null, false, 0, body, null);
  }

  @Override
  public void send(HttpMethod method, String host, String requestURI, Handler<AsyncResult<HttpClientResponse>> responseHandler) {
    send(method, "http", host, options.getDefaultPort(), requestURI, null, null, false, 0, null, responseHandler);
  }

  @Override
  public Future<HttpClientResponse> send(HttpMethod method, String host, String requestURI) {
    return send(method, "http", host, options.getDefaultPort(), requestURI, null, null, false, 0, null, null);
  }

  @Override
  public void send(HttpMethod method, String requestURI, MultiMap headers, Handler<AsyncResult<HttpClientResponse>> responseHandler) {
    send(method, requestURI, headers, (Buffer) null, responseHandler);
  }

  @Override
  public Future<HttpClientResponse> send(HttpMethod method, String requestURI, MultiMap headers) {
    return send(method, requestURI, headers, (Buffer) null);
  }

  @Override
  public void send(HttpMethod method, String requestURI, MultiMap headers, Buffer body, Handler<AsyncResult<HttpClientResponse>> responseHandler) {
    send(method, "http", options.getDefaultHost(), options.getDefaultPort(), requestURI, headers, null, false, 0, body, responseHandler);
  }

  @Override
  public Future<HttpClientResponse> send(HttpMethod method, String requestURI, MultiMap headers, Buffer body) {
    return send(method, "http", options.getDefaultHost(), options.getDefaultPort(), requestURI, headers, null, false, 0, body, null);
  }

  @Override
  public void send(HttpMethod method, String requestURI, MultiMap headers, ReadStream<Buffer> body, Handler<AsyncResult<HttpClientResponse>> responseHandler) {
    send(method, "http", options.getDefaultHost(), options.getDefaultPort(), requestURI, headers, null, false, 0, body, responseHandler);
  }

  @Override
  public Future<HttpClientResponse> send(HttpMethod method, String requestURI, MultiMap headers, ReadStream<Buffer> body) {
    return send(method, "http", options.getDefaultHost(), options.getDefaultPort(), requestURI, headers, null, false, 0, body, null);
  }

  @Override
  public void send(HttpMethod method, String requestURI, Buffer body, Handler<AsyncResult<HttpClientResponse>> responseHandler) {
    send(method, "http", options.getDefaultHost(), options.getDefaultPort(), requestURI, null, null, false, 0, body, responseHandler);
  }

  @Override
  public Future<HttpClientResponse> send(HttpMethod method, String requestURI, Buffer body) {
    return send(method, "http", options.getDefaultHost(), options.getDefaultPort(), requestURI, null, null, false, 0, body, null);
  }

  @Override
  public void send(HttpMethod method, String requestURI, ReadStream<Buffer> body, Handler<AsyncResult<HttpClientResponse>> responseHandler) {
    send(method, "http", options.getDefaultHost(), options.getDefaultPort(), requestURI, null, null, false, 0, body, responseHandler);
  }

  @Override
  public Future<HttpClientResponse> send(HttpMethod method, String requestURI, ReadStream<Buffer> body) {
    return send(method, "http", options.getDefaultHost(), options.getDefaultPort(), requestURI, null, null, false, 0, body, null);
  }

  @Override
  public void send(HttpMethod method, String requestURI, Handler<AsyncResult<HttpClientResponse>> responseHandler) {
    send(method, "http", options.getDefaultHost(), options.getDefaultPort(), requestURI, null, null, false, 0, null, responseHandler);
  }

  @Override
  public Future<HttpClientResponse> send(HttpMethod method, String requestURI) {
    return send(method, "http", options.getDefaultHost(), options.getDefaultPort(), requestURI, null, null, false, 0, null, null);
  }

  private Future<HttpClientResponse> send(HttpMethod method,
                    String protocol,
                    String host,
                    int port,
                    String requestURI,
                    MultiMap headers,
                    Boolean ssl,
                    boolean followRedirects,
                    long timeout,
                    Object body,
                    Handler<AsyncResult<HttpClientResponse>> responseHandler) {
    HttpClientRequestBase req = createRequest(method, null, host, port, ssl, requestURI, headers);
    req.setFollowRedirects(followRedirects);
    if (responseHandler != null) {
      req.setHandler(responseHandler);
    }
    if (body instanceof Buffer) {
      req.end((Buffer) body);
    } else if (body instanceof ReadStream<?>) {
      ReadStream<Buffer> stream = (ReadStream<Buffer>) body;
      if (headers == null || !headers.contains(HttpHeaders.CONTENT_LENGTH)) {
        req.setChunked(true);
      }
      stream.pipeTo(req);
    } else {
      req.end();
    }
    if (timeout > 0) {
      req.setTimeout(timeout);
    }
    return req;
  }

  @Override
  public HttpClientRequest request(HttpMethod method, String requestURI) {
    return request(method, options.getDefaultPort(), options.getDefaultHost(), requestURI);
  }

  @Override
  public HttpClientRequest request(HttpMethod method, int port, String host, String requestURI) {
    return createRequest(method, null, host, port, null, requestURI, null);
  }

  @Override
  public HttpClientRequest request(HttpMethod method, SocketAddress serverAddress, int port, String host, String requestURI) {
    return createRequest(method, serverAddress, host, port, null, requestURI, null);
  }

  @Override
  public HttpClientRequest request(SocketAddress serverAddress, RequestOptions options) {
    return createRequest(options.getMethod(), serverAddress, getHost(options.getHost()), getPort(options.getPort()), options.isSsl(), options.getURI(), null);
  }

  @Override
  public HttpClientRequest request(RequestOptions options) {
    return createRequest(options.getMethod(), null, getHost(options.getHost()), getPort(options.getPort()), options.isSsl(), options.getURI(), options.getHeaders());
  }

  @Override
  public HttpClientRequest request(HttpMethod method, String host, String requestURI) {
    return request(method, options.getDefaultPort(), host, requestURI);
  }

  @Override
  public void get(int port, String host, String requestURI, MultiMap headers, Handler<AsyncResult<HttpClientResponse>> responseHandler) {
    send(HttpMethod.GET, port, host, requestURI, headers, responseHandler);
  }

  @Override
  public Future<HttpClientResponse> get(int port, String host, String requestURI, MultiMap headers) {
    return send(HttpMethod.GET, port, host, requestURI, headers);
  }

  @Override
  public void get(String host, String requestURI, MultiMap headers, Handler<AsyncResult<HttpClientResponse>> responseHandler) {
    send(HttpMethod.GET, host, requestURI, headers, responseHandler);
  }

  @Override
  public Future<HttpClientResponse> get(String host, String requestURI, MultiMap headers) {
    return send(HttpMethod.GET, host, requestURI, headers);
  }

  @Override
  public void get(String requestURI, MultiMap headers, Handler<AsyncResult<HttpClientResponse>> responseHandler) {
    send(HttpMethod.GET, requestURI, headers, responseHandler);
  }

  @Override
  public Future<HttpClientResponse> get(String requestURI, MultiMap headers) {
    return send(HttpMethod.GET, requestURI, headers);
  }

  @Override
  public void get(RequestOptions options, Handler<AsyncResult<HttpClientResponse>> responseHandler) {
    send(options.setMethod(HttpMethod.GET), responseHandler);
  }

  @Override
  public Future<HttpClientResponse> get(RequestOptions options) {
    return send(options.setMethod(HttpMethod.GET));
  }

  @Override
  public void get(int port, String host, String requestURI, Handler<AsyncResult<HttpClientResponse>> responseHandler) {
    send(HttpMethod.GET, port, host, requestURI, responseHandler);
  }

  @Override
  public Future<HttpClientResponse> get(int port, String host, String requestURI) {
    return send(HttpMethod.GET, port, host, requestURI);
  }

  @Override
  public void get(String host, String requestURI, Handler<AsyncResult<HttpClientResponse>> responseHandler) {
    send(HttpMethod.GET, host, requestURI, responseHandler);
  }

  @Override
  public Future<HttpClientResponse> get(String host, String requestURI) {
    return send(HttpMethod.GET, host, requestURI);
  }

  @Override
  public void get(String requestURI, Handler<AsyncResult<HttpClientResponse>> responseHandler) {
    send(HttpMethod.GET, requestURI, responseHandler);
  }

  @Override
  public Future<HttpClientResponse> get(String requestURI) {
    return send(HttpMethod.GET, requestURI);
  }

  @Override
  public void post(int port, String host, String requestURI, MultiMap headers, Buffer body, Handler<AsyncResult<HttpClientResponse>> responseHandler) {
    send(HttpMethod.POST, port, host, requestURI, headers, body, responseHandler);
  }

  @Override
  public Future<HttpClientResponse> post(int port, String host, String requestURI, MultiMap headers, Buffer body) {
    return send(HttpMethod.POST, port, host, requestURI, headers, body);
  }

  @Override
  public void post(int port, String host, String requestURI, MultiMap headers, ReadStream<Buffer> body, Handler<AsyncResult<HttpClientResponse>> responseHandler) {
    send(HttpMethod.POST, port, host, requestURI, headers, body, responseHandler);
  }

  @Override
  public Future<HttpClientResponse> post(int port, String host, String requestURI, MultiMap headers, ReadStream<Buffer> body) {
    return send(HttpMethod.POST, port, host, requestURI, headers, body);
  }

  @Override
  public void post(String host, String requestURI, MultiMap headers, Buffer body, Handler<AsyncResult<HttpClientResponse>> responseHandler) {
    send(HttpMethod.POST, host, requestURI, headers, body, responseHandler);
  }

  @Override
  public Future<HttpClientResponse> post(String host, String requestURI, MultiMap headers, Buffer body) {
    return send(HttpMethod.POST, host, requestURI, headers, body);
  }

  @Override
  public void post(String host, String requestURI, MultiMap headers, ReadStream<Buffer> body, Handler<AsyncResult<HttpClientResponse>> responseHandler) {
    send(HttpMethod.POST, host, requestURI, headers, body, responseHandler);
  }

  @Override
  public Future<HttpClientResponse> post(String host, String requestURI, MultiMap headers, ReadStream<Buffer> body) {
    return send(HttpMethod.POST, host, requestURI, headers, body);
  }

  @Override
  public void post(String requestURI, MultiMap headers, Buffer body, Handler<AsyncResult<HttpClientResponse>> responseHandler) {
    send(HttpMethod.POST, requestURI, headers, body, responseHandler);
  }

  @Override
  public Future<HttpClientResponse> post(String requestURI, MultiMap headers, Buffer body) {
    return send(HttpMethod.POST, requestURI, headers, body);
  }

  @Override
  public void post(String requestURI, MultiMap headers, ReadStream<Buffer> body, Handler<AsyncResult<HttpClientResponse>> responseHandler) {
    send(HttpMethod.POST, requestURI, headers, body, responseHandler);
  }

  @Override
  public Future<HttpClientResponse> post(String requestURI, MultiMap headers, ReadStream<Buffer> body) {
    return send(HttpMethod.POST, requestURI, headers, body);
  }

  @Override
  public void post(RequestOptions options, Buffer body, Handler<AsyncResult<HttpClientResponse>> responseHandler) {
    send(options.setMethod(HttpMethod.POST), body, responseHandler);
  }

  @Override
  public Future<HttpClientResponse> post(RequestOptions options, Buffer body) {
    return send(options.setMethod(HttpMethod.POST), body);
  }

  @Override
  public void post(RequestOptions options, ReadStream<Buffer> body, Handler<AsyncResult<HttpClientResponse>> responseHandler) {
    send(options.setMethod(HttpMethod.POST), body, responseHandler);
  }

  @Override
  public Future<HttpClientResponse> post(RequestOptions options, ReadStream<Buffer> body) {
    return send(options.setMethod(HttpMethod.POST), body);
  }

  @Override
  public void post(int port, String host, String requestURI, Buffer body, Handler<AsyncResult<HttpClientResponse>> responseHandler) {
    send(HttpMethod.POST, port, host, requestURI, body, responseHandler);
  }

  @Override
  public Future<HttpClientResponse> post(int port, String host, String requestURI, Buffer body) {
    return send(HttpMethod.POST, port, host, requestURI, body);
  }

  @Override
  public void post(int port, String host, String requestURI, ReadStream<Buffer> body, Handler<AsyncResult<HttpClientResponse>> responseHandler) {
    send(HttpMethod.POST, port, host, requestURI, body, responseHandler);
  }

  @Override
  public Future<HttpClientResponse> post(int port, String host, String requestURI, ReadStream<Buffer> body) {
    return send(HttpMethod.POST, port, host, requestURI, body);
  }

  @Override
  public void post(String host, String requestURI, Buffer body, Handler<AsyncResult<HttpClientResponse>> responseHandler) {
    send(HttpMethod.POST, host, requestURI, body, responseHandler);
  }

  @Override
  public Future<HttpClientResponse> post(String host, String requestURI, Buffer body) {
    return send(HttpMethod.POST, host, requestURI, body);
  }

  @Override
  public void post(String host, String requestURI, ReadStream<Buffer> body, Handler<AsyncResult<HttpClientResponse>> responseHandler) {
    send(HttpMethod.POST, host, requestURI, body, responseHandler);
  }

  @Override
  public Future<HttpClientResponse> post(String host, String requestURI, ReadStream<Buffer> body) {
    return send(HttpMethod.POST, host, requestURI, body);
  }

  @Override
  public void post(String requestURI, Buffer body, Handler<AsyncResult<HttpClientResponse>> responseHandler) {
    send(HttpMethod.POST, requestURI, body, responseHandler);
  }

  @Override
  public Future<HttpClientResponse> post(String requestURI, Buffer body) {
    return send(HttpMethod.POST, requestURI, body);
  }

  @Override
  public void post(String requestURI, ReadStream<Buffer> body, Handler<AsyncResult<HttpClientResponse>> responseHandler) {
    send(HttpMethod.POST, requestURI, body, responseHandler);
  }

  @Override
  public Future<HttpClientResponse> post(String requestURI, ReadStream<Buffer> body) {
    return send(HttpMethod.POST, requestURI, body);
  }

  @Override
  public void put(int port, String host, String requestURI, MultiMap headers, Buffer body, Handler<AsyncResult<HttpClientResponse>> responseHandler) {
    send(HttpMethod.PUT, port, host, requestURI, headers, body, responseHandler);
  }

  @Override
  public Future<HttpClientResponse> put(int port, String host, String requestURI, MultiMap headers, Buffer body) {
    return send(HttpMethod.PUT, port, host, requestURI, headers, body);
  }

  @Override
  public void put(int port, String host, String requestURI, MultiMap headers, ReadStream<Buffer> body, Handler<AsyncResult<HttpClientResponse>> responseHandler) {
    send(HttpMethod.PUT, port, host, requestURI, headers, body, responseHandler);
  }

  @Override
  public Future<HttpClientResponse> put(int port, String host, String requestURI, MultiMap headers, ReadStream<Buffer> body) {
    return send(HttpMethod.PUT, port, host, requestURI, headers, body);
  }

  @Override
  public void put(String host, String requestURI, MultiMap headers, Buffer body, Handler<AsyncResult<HttpClientResponse>> responseHandler) {
    send(HttpMethod.PUT, host, requestURI, headers, body, responseHandler);
  }

  @Override
  public Future<HttpClientResponse> put(String host, String requestURI, MultiMap headers, Buffer body) {
    return send(HttpMethod.PUT, host, requestURI, headers, body);
  }

  @Override
  public void put(String host, String requestURI, MultiMap headers, ReadStream<Buffer> body, Handler<AsyncResult<HttpClientResponse>> responseHandler) {
    send(HttpMethod.PUT, host, requestURI, headers, body, responseHandler);
  }

  @Override
  public Future<HttpClientResponse> put(String host, String requestURI, MultiMap headers, ReadStream<Buffer> body) {
    return send(HttpMethod.PUT, host, requestURI, headers, body);
  }

  @Override
  public void put(String requestURI, MultiMap headers, Buffer body, Handler<AsyncResult<HttpClientResponse>> responseHandler) {
    send(HttpMethod.PUT, requestURI, headers, body, responseHandler);
  }

  @Override
  public Future<HttpClientResponse> put(String requestURI, MultiMap headers, Buffer body) {
    return send(HttpMethod.PUT, requestURI, headers, body);
  }

  @Override
  public void put(String requestURI, MultiMap headers, ReadStream<Buffer> body, Handler<AsyncResult<HttpClientResponse>> responseHandler) {
    send(HttpMethod.PUT, requestURI, headers, body, responseHandler);
  }

  @Override
  public Future<HttpClientResponse> put(String requestURI, MultiMap headers, ReadStream<Buffer> body) {
    return send(HttpMethod.PUT, requestURI, headers, body);
  }

  @Override
  public void put(RequestOptions options, Buffer body, Handler<AsyncResult<HttpClientResponse>> responseHandler) {
    send(options.setMethod(HttpMethod.PUT), body, responseHandler);
  }

  @Override
  public Future<HttpClientResponse> put(RequestOptions options, Buffer body) {
    return send(options.setMethod(HttpMethod.PUT), body);
  }

  @Override
  public void put(RequestOptions options, ReadStream<Buffer> body, Handler<AsyncResult<HttpClientResponse>> responseHandler) {
    send(options.setMethod(HttpMethod.PUT), body, responseHandler);
  }

  @Override
  public Future<HttpClientResponse> put(RequestOptions options, ReadStream<Buffer> body) {
    return send(options.setMethod(HttpMethod.PUT), body);
  }

  @Override
  public void put(int port, String host, String requestURI, Buffer body, Handler<AsyncResult<HttpClientResponse>> responseHandler) {
    send(HttpMethod.PUT, port, host, requestURI, body, responseHandler);
  }

  @Override
  public Future<HttpClientResponse> put(int port, String host, String requestURI, Buffer body) {
    return send(HttpMethod.PUT, port, host, requestURI, body);
  }

  @Override
  public void put(int port, String host, String requestURI, ReadStream<Buffer> body, Handler<AsyncResult<HttpClientResponse>> responseHandler) {
    send(HttpMethod.PUT, port, host, requestURI, body, responseHandler);
  }

  @Override
  public Future<HttpClientResponse> put(int port, String host, String requestURI, ReadStream<Buffer> body) {
    return send(HttpMethod.PUT, port, host, requestURI, body);
  }

  @Override
  public void put(String host, String requestURI, Buffer body, Handler<AsyncResult<HttpClientResponse>> responseHandler) {
    send(HttpMethod.PUT, host, requestURI, body, responseHandler);
  }

  @Override
  public Future<HttpClientResponse> put(String host, String requestURI, Buffer body) {
    return send(HttpMethod.PUT, host, requestURI, body);
  }

  @Override
  public void put(String host, String requestURI, ReadStream<Buffer> body, Handler<AsyncResult<HttpClientResponse>> responseHandler) {
    send(HttpMethod.PUT, host, requestURI, body, responseHandler);
  }

  @Override
  public Future<HttpClientResponse> put(String host, String requestURI, ReadStream<Buffer> body) {
    return send(HttpMethod.PUT, host, requestURI, body);
  }

  @Override
  public void put(String requestURI, Buffer body, Handler<AsyncResult<HttpClientResponse>> responseHandler) {
    send(HttpMethod.PUT, requestURI, body, responseHandler);
  }

  @Override
  public Future<HttpClientResponse> put(String requestURI, Buffer body) {
    return send(HttpMethod.PUT, requestURI, body);
  }

  @Override
  public void put(String requestURI, ReadStream<Buffer> body, Handler<AsyncResult<HttpClientResponse>> responseHandler) {
    send(HttpMethod.PUT, requestURI, body, responseHandler);
  }

  @Override
  public Future<HttpClientResponse> put(String requestURI, ReadStream<Buffer> body) {
    return send(HttpMethod.PUT, requestURI, body);
  }

  @Override
  public void head(int port, String host, String requestURI, MultiMap headers, Handler<AsyncResult<HttpClientResponse>> responseHandler) {
    send(HttpMethod.HEAD, port, host, requestURI, headers, responseHandler);
  }

  @Override
  public Future<HttpClientResponse> head(int port, String host, String requestURI, MultiMap headers) {
    return send(HttpMethod.HEAD, port, host, requestURI, headers);
  }

  @Override
  public void head(String host, String requestURI, MultiMap headers, Handler<AsyncResult<HttpClientResponse>> responseHandler) {
    send(HttpMethod.HEAD, host, requestURI, headers, responseHandler);
  }

  @Override
  public Future<HttpClientResponse> head(String host, String requestURI, MultiMap headers) {
    return send(HttpMethod.HEAD, host, requestURI, headers);
  }

  @Override
  public void head(String requestURI, MultiMap headers, Handler<AsyncResult<HttpClientResponse>> responseHandler) {
    send(HttpMethod.HEAD, requestURI, headers, responseHandler);
  }

  @Override
  public Future<HttpClientResponse> head(String requestURI, MultiMap headers) {
    return send(HttpMethod.HEAD, requestURI, headers);
  }

  @Override
  public void head(RequestOptions options, Handler<AsyncResult<HttpClientResponse>> responseHandler) {
    send(options.setMethod(HttpMethod.HEAD), responseHandler);
  }

  @Override
  public Future<HttpClientResponse> head(RequestOptions options) {
    return send(options.setMethod(HttpMethod.HEAD));
  }

  @Override
  public void head(int port, String host, String requestURI, Handler<AsyncResult<HttpClientResponse>> responseHandler) {
    send(HttpMethod.HEAD, port, host, requestURI, responseHandler);
  }

  @Override
  public Future<HttpClientResponse> head(int port, String host, String requestURI) {
    return send(HttpMethod.HEAD, port, host, requestURI);
  }

  @Override
  public void head(String host, String requestURI, Handler<AsyncResult<HttpClientResponse>> responseHandler) {
    send(HttpMethod.HEAD, host, requestURI, responseHandler);
  }

  @Override
  public Future<HttpClientResponse> head(String host, String requestURI) {
    return send(HttpMethod.HEAD, host, requestURI);
  }

  @Override
  public void head(String requestURI, Handler<AsyncResult<HttpClientResponse>> responseHandler) {
    send(HttpMethod.HEAD, requestURI, responseHandler);
  }

  @Override
  public Future<HttpClientResponse> head(String requestURI) {
    return send(HttpMethod.HEAD, requestURI);
  }

  @Override
  public void options(int port, String host, String requestURI, MultiMap headers, Handler<AsyncResult<HttpClientResponse>> responseHandler) {
    send(HttpMethod.OPTIONS, port, host, requestURI, headers, responseHandler);
  }

  @Override
  public Future<HttpClientResponse> options(int port, String host, String requestURI, MultiMap headers) {
    return send(HttpMethod.OPTIONS, port, host, requestURI, headers);
  }

  @Override
  public void options(String host, String requestURI, MultiMap headers, Handler<AsyncResult<HttpClientResponse>> responseHandler) {
    send(HttpMethod.OPTIONS, host, requestURI, headers, responseHandler);
  }

  @Override
  public Future<HttpClientResponse> options(String host, String requestURI, MultiMap headers) {
    return send(HttpMethod.OPTIONS, host, requestURI, headers);
  }

  @Override
  public void options(String requestURI, MultiMap headers, Handler<AsyncResult<HttpClientResponse>> responseHandler) {
    send(HttpMethod.OPTIONS, requestURI, headers, responseHandler);
  }

  @Override
  public Future<HttpClientResponse> options(String requestURI, MultiMap headers) {
    return send(HttpMethod.OPTIONS, requestURI, headers);
  }

  @Override
  public void options(RequestOptions options, Handler<AsyncResult<HttpClientResponse>> responseHandler) {
    send(options.setMethod(HttpMethod.OPTIONS), responseHandler);
  }

  @Override
  public Future<HttpClientResponse> options(RequestOptions options) {
    return send(options.setMethod(HttpMethod.OPTIONS));
  }

  @Override
  public void options(int port, String host, String requestURI, Handler<AsyncResult<HttpClientResponse>> responseHandler) {
    send(HttpMethod.OPTIONS, port, host, requestURI, responseHandler);
  }

  @Override
  public Future<HttpClientResponse> options(int port, String host, String requestURI) {
    return send(HttpMethod.OPTIONS, port, host, requestURI);
  }

  @Override
  public void options(String host, String requestURI, Handler<AsyncResult<HttpClientResponse>> responseHandler) {
    send(HttpMethod.OPTIONS, host, requestURI, responseHandler);
  }

  @Override
  public Future<HttpClientResponse> options(String host, String requestURI) {
    return send(HttpMethod.OPTIONS, host, requestURI);
  }

  @Override
  public void options(String requestURI, Handler<AsyncResult<HttpClientResponse>> responseHandler) {
    send(HttpMethod.OPTIONS, requestURI, responseHandler);
  }

  @Override
  public Future<HttpClientResponse> options(String requestURI) {
    return send(HttpMethod.OPTIONS, requestURI);
  }

  @Override
  public void delete(int port, String host, String requestURI, MultiMap headers, Handler<AsyncResult<HttpClientResponse>> responseHandler) {
    send(HttpMethod.DELETE, port, host, requestURI, responseHandler);
  }

  @Override
  public Future<HttpClientResponse> delete(int port, String host, String requestURI, MultiMap headers) {
    return send(HttpMethod.DELETE, port, host, requestURI);
  }

  @Override
  public void delete(String host, String requestURI, MultiMap headers, Handler<AsyncResult<HttpClientResponse>> responseHandler) {
    send(HttpMethod.DELETE, host, requestURI, responseHandler);
  }

  @Override
  public Future<HttpClientResponse> delete(String host, String requestURI, MultiMap headers) {
    return send(HttpMethod.DELETE, host, requestURI);
  }

  @Override
  public void delete(String requestURI, MultiMap headers, Handler<AsyncResult<HttpClientResponse>> responseHandler) {
    send(HttpMethod.DELETE, requestURI, responseHandler);
  }

  @Override
  public Future<HttpClientResponse> delete(String requestURI, MultiMap headers) {
    return send(HttpMethod.DELETE, requestURI);
  }

  @Override
  public void delete(RequestOptions options, Handler<AsyncResult<HttpClientResponse>> responseHandler) {
    send(options.setMethod(HttpMethod.DELETE), responseHandler);
  }

  @Override
  public Future<HttpClientResponse> delete(RequestOptions options) {
    return send(options.setMethod(HttpMethod.DELETE));
  }

  @Override
  public void delete(int port, String host, String requestURI, Handler<AsyncResult<HttpClientResponse>> responseHandler) {
    send(HttpMethod.DELETE, port, host, requestURI, responseHandler);
  }

  @Override
  public Future<HttpClientResponse> delete(int port, String host, String requestURI) {
    return send(HttpMethod.DELETE, port, host, requestURI);
  }

  @Override
  public void delete(String host, String requestURI, Handler<AsyncResult<HttpClientResponse>> responseHandler) {
    send(HttpMethod.DELETE, host, requestURI, responseHandler);
  }

  @Override
  public Future<HttpClientResponse> delete(String host, String requestURI) {
    return send(HttpMethod.DELETE, host, requestURI);
  }

  @Override
  public void delete(String requestURI, Handler<AsyncResult<HttpClientResponse>> responseHandler) {
    send(HttpMethod.DELETE, requestURI, responseHandler);
  }

  @Override
  public Future<HttpClientResponse> delete(String requestURI) {
    return send(HttpMethod.DELETE, requestURI);
  }

  @Override
  public void close() {
    synchronized (this) {
      checkClosed();
      closed = true;
    }
    if (context.deploymentID() != null) {
      context.removeCloseHook(closeHook);
    }
    webSocketCM.close();
    httpCM.close();
    if (metrics != null) {
      metrics.close();
    }
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
  public HttpClient redirectHandler(Function<HttpClientResponse, Future<HttpClientRequest>> handler) {
    if (handler == null) {
      handler = DEFAULT_HANDLER;
    }
    redirectHandler = handler;
    return this;
  }

  @Override
  public Function<HttpClientResponse, Future<HttpClientRequest>> redirectHandler() {
    return redirectHandler;
  }

  public HttpClientOptions getOptions() {
    return options;
  }

  void getConnectionForRequest(ContextInternal ctx,
                               SocketAddress peerAddress,
                               HttpClientRequestImpl req,
                               Promise<NetSocket> netSocketPromise,
                               boolean ssl,
                               SocketAddress server,
                               Handler<AsyncResult<HttpClientStream>> handler) {
    httpCM.getConnection(context, peerAddress, ssl, server, ar -> {
      if (ar.succeeded()) {
        ar.result().createStream(ctx, req, netSocketPromise, handler);
      } else {
        ctx.emit(Future.failedFuture(ar.cause()), handler);
      }
    });
  }

  /**
   * @return the vertx, for use in package related classes only.
   */
  public VertxInternal getVertx() {
    return vertx;
  }

  SSLHelper getSslHelper() {
    return sslHelper;
  }

  private HttpClientRequestBase createRequest(HttpMethod method, SocketAddress server, String host, int port, Boolean ssl, String requestURI, MultiMap headers) {
    Objects.requireNonNull(method, "no null method accepted");
    Objects.requireNonNull(host, "no null host accepted");
    Objects.requireNonNull(requestURI, "no null requestURI accepted");
    boolean useAlpn = options.isUseAlpn();
    boolean useSSL = ssl != null ? ssl : options.isSsl();
    if (!useAlpn && useSSL && options.getProtocolVersion() == HttpVersion.HTTP_2) {
      throw new IllegalArgumentException("Must enable ALPN when using H2");
    }
    checkClosed();
    HttpClientRequestBase req;
    boolean useProxy = !useSSL && proxyType == ProxyType.HTTP;

    // Get the request context
    ContextInternal current = vertx.getOrCreateContext();

    if (useProxy) {
      // If the requestURI is as not absolute URI then we do not recompute one for the proxy
      if (!ABS_URI_START_PATTERN.matcher(requestURI).find()) {
        int defaultPort = 80;
        String addPort = (port != -1 && port != defaultPort) ? (":" + port) : "";
        requestURI = (ssl == Boolean.TRUE ? "https://" : "http://") + host + addPort + requestURI;
      }
      ProxyOptions proxyOptions = options.getProxyOptions();
      if (proxyOptions.getUsername() != null && proxyOptions.getPassword() != null) {
        if (headers == null) {
          headers = HttpHeaders.headers();
        }
        headers.add("Proxy-Authorization", "Basic " + Base64.getEncoder()
            .encodeToString((proxyOptions.getUsername() + ":" + proxyOptions.getPassword()).getBytes()));
      }
      req = new HttpClientRequestImpl(this, current, useSSL, method, SocketAddress.inetSocketAddress(proxyOptions.getPort(), proxyOptions.getHost()),
          host, port, requestURI);
    } else {
      if (server == null) {
        server = SocketAddress.inetSocketAddress(port, host);
      }
      req = new HttpClientRequestImpl(this, current, useSSL, method, server, host, port, requestURI);
    }
    if (headers != null) {
      req.headers().setAll(headers);
    }
    return req;
  }

  private synchronized void checkClosed() {
    if (closed) {
      throw new IllegalStateException("Client is closed");
    }
  }

  @Override
  protected void finalize() throws Throwable {
    // Make sure this gets cleaned up if there are no more references to it
    // so as not to leave connections and resources dangling until the system is shutdown
    // which could make the JVM run out of file handles.
    close();
    super.finalize();
  }
}
