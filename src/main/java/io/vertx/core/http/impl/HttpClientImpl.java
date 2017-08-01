/*
 * Copyright (c) 2011-2013 The original author or authors
 * ------------------------------------------------------
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * and Apache License v2.0 which accompanies this distribution.
 *
 *     The Eclipse Public License is available at
 *     http://www.eclipse.org/legal/epl-v10.html
 *
 *     The Apache License v2.0 is available at
 *     http://www.opensource.org/licenses/apache2.0.php
 *
 * You may elect to redistribute this code under either of these licenses.
 */

package io.vertx.core.http.impl;

import io.vertx.core.Closeable;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.MultiMap;
import io.vertx.core.VertxException;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.http.HttpClientResponse;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpVersion;
import io.vertx.core.http.RequestOptions;
import io.vertx.core.http.WebSocket;
import io.vertx.core.http.WebsocketVersion;
import io.vertx.core.impl.ContextImpl;
import io.vertx.core.impl.VertxInternal;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.core.net.ProxyOptions;
import io.vertx.core.net.ProxyType;
import io.vertx.core.net.impl.SSLHelper;
import io.vertx.core.spi.metrics.Metrics;
import io.vertx.core.spi.metrics.MetricsProvider;
import io.vertx.core.spi.metrics.VertxMetrics;
import io.vertx.core.streams.ReadStream;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

/**
 *
 * This class is thread-safe
 *
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class HttpClientImpl implements HttpClient, MetricsProvider {

  private final Function<HttpClientResponse, Future<HttpClientRequest>> DEFAULT_HANDLER = resp -> {
    try {
      int statusCode = resp.statusCode();
      String location = resp.getHeader(HttpHeaders.LOCATION);
      if (location != null && (statusCode == 301 || statusCode == 302 || statusCode == 303 || statusCode == 307)) {
        HttpMethod m = resp.request().method();
        if (statusCode == 301 || statusCode == 302 || statusCode == 303) {
          m = HttpMethod.GET;
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
        if (uri.getQuery() != null) {
          requestURI += "?" + uri.getQuery();
        }
        return Future.succeededFuture(createRequest(m, uri.getHost(), port, ssl, requestURI, null));
      }
      return null;
    } catch (Exception e) {
      return Future.failedFuture(e);
    }
  };

  private static final Logger log = LoggerFactory.getLogger(HttpClientImpl.class);

  private final VertxInternal vertx;
  private final HttpClientOptions options;
  private final ContextImpl creatingContext;
  private final ConnectionManager connectionManager;
  private final Closeable closeHook;
  private final ProxyType proxyType;
  private final SSLHelper sslHelper;
  private volatile boolean closed;
  private volatile Function<HttpClientResponse, Future<HttpClientRequest>> redirectHandler = DEFAULT_HANDLER;

  public HttpClientImpl(VertxInternal vertx, HttpClientOptions options) {
    if (options.isUseAlpn() && !options.isSsl()) {
      throw new IllegalArgumentException("Must enable SSL when using ALPN");
    }
    this.vertx = vertx;
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
    this.sslHelper = new SSLHelper(options, options.getKeyCertOptions(), options.getTrustOptions()).
        setApplicationProtocols(alpnVersions);
    this.creatingContext = vertx.getContext();
    closeHook = completionHandler -> {
      HttpClientImpl.this.close();
      completionHandler.handle(Future.succeededFuture());
    };
    if (creatingContext != null) {
      if (creatingContext.isMultiThreadedWorkerContext()) {
        throw new IllegalStateException("Cannot use HttpClient in a multi-threaded worker verticle");
      }
      if(options.getProtocolVersion() == HttpVersion.HTTP_2 && Context.isOnWorkerThread()) {
        throw new IllegalStateException("Cannot use HttpClient with HTTP_2 in a worker");
      }
      creatingContext.addCloseHook(closeHook);
    }
    VertxMetrics metrics = vertx.metricsSPI();
    connectionManager = new ConnectionManager(this, metrics != null ? metrics.createMetrics(this, options) : null);
    proxyType = options.getProxyOptions() != null ? options.getProxyOptions().getType() : null;
  }

  @Override
  public HttpClient websocket(RequestOptions options, Handler<WebSocket> wsConnect) {
    return websocket(options, null, wsConnect);
  }

  @Override
  public HttpClient websocket(int port, String host, String requestURI, Handler<WebSocket> wsConnect) {
    websocketStream(port, host, requestURI, null, null).handler(wsConnect);
    return this;
  }

  @Override
  public HttpClient websocket(RequestOptions options, Handler<WebSocket> wsConnect, Handler<Throwable> failureHandler) {
    return websocket(options, null, wsConnect, failureHandler);
  }

  public HttpClient websocket(int port, String host, String requestURI, Handler<WebSocket> wsConnect, Handler<Throwable> failureHandler){
    websocketStream(port, host, requestURI, null, null).exceptionHandler(failureHandler).handler(wsConnect);
    return this;
  }

  @Override
  public HttpClient websocket(String host, String requestURI, Handler<WebSocket> wsConnect) {
    return websocket(options.getDefaultPort(), host, requestURI, wsConnect);
  }

  @Override
  public HttpClient websocket(String host, String requestURI, Handler<WebSocket> wsConnect, Handler<Throwable> failureHandler) {
    return websocket(options.getDefaultPort(), host, requestURI, wsConnect, failureHandler);
  }

  @Override
  public HttpClient websocket(RequestOptions options, MultiMap headers, Handler<WebSocket> wsConnect) {
    return websocket(options, headers, null, wsConnect);
  }

  @Override
  public HttpClient websocket(int port, String host, String requestURI, MultiMap headers, Handler<WebSocket> wsConnect) {
    websocketStream(port, host, requestURI, headers, null).handler(wsConnect);
    return this;
  }

  @Override
  public HttpClient websocket(RequestOptions options, MultiMap headers, Handler<WebSocket> wsConnect, Handler<Throwable> failureHandler) {
    return websocket(options, headers, null, wsConnect, failureHandler);
  }

  @Override
  public HttpClient websocket(int port, String host, String requestURI, MultiMap headers, Handler<WebSocket> wsConnect, Handler<Throwable> failureHandler) {
    websocketStream(port, host, requestURI, headers, null).exceptionHandler(failureHandler).handler(wsConnect);
    return this;
  }

  @Override
  public HttpClient websocket(String host, String requestURI, MultiMap headers, Handler<WebSocket> wsConnect) {
    return websocket(options.getDefaultPort(), host, requestURI, headers, wsConnect);
  }

  @Override
  public HttpClient websocket(String host, String requestURI, MultiMap headers, Handler<WebSocket> wsConnect, Handler<Throwable> failureHandler) {
    return websocket(options.getDefaultPort(), host, requestURI, headers, wsConnect, failureHandler);
  }

  @Override
  public HttpClient websocket(RequestOptions options, MultiMap headers, WebsocketVersion version, Handler<WebSocket> wsConnect) {
    return websocket(options, headers, version, null, wsConnect);
  }

  @Override
  public HttpClient websocket(int port, String host, String requestURI, MultiMap headers, WebsocketVersion version, Handler<WebSocket> wsConnect) {
    websocketStream(port, host, requestURI, headers, version, null).handler(wsConnect);
    return this;
  }

  @Override
  public HttpClient websocket(RequestOptions options, MultiMap headers, WebsocketVersion version, Handler<WebSocket> wsConnect, Handler<Throwable> failureHandler) {
    return websocket(options, headers, version, null, wsConnect, failureHandler);
  }

  @Override
  public HttpClient websocket(int port, String host, String requestURI, MultiMap headers, WebsocketVersion version
          , Handler<WebSocket> wsConnect, Handler<Throwable> failureHandler) {
    websocketStream(port, host, requestURI, headers, version, null).exceptionHandler(failureHandler).handler(wsConnect);
    return this;
  }

  @Override
  public HttpClient websocket(String host, String requestURI, MultiMap headers, WebsocketVersion version, Handler<WebSocket> wsConnect) {
    return websocket(options.getDefaultPort(), host, requestURI, headers, version, wsConnect);
  }

  @Override
  public HttpClient websocket(String host, String requestURI, MultiMap headers, WebsocketVersion version
          , Handler<WebSocket> wsConnect, Handler<Throwable> failureHandler) {
    return websocket(options.getDefaultPort(), host, requestURI, headers, version, wsConnect, failureHandler);
  }

  @Override
  public HttpClient websocket(RequestOptions options, MultiMap headers, WebsocketVersion version, String subProtocols, Handler<WebSocket> wsConnect) {
    websocketStream(options, headers, version, subProtocols).handler(wsConnect);
    return this;
  }

  @Override
  public HttpClient websocket(int port, String host, String requestURI, MultiMap headers, WebsocketVersion version,
                              String subProtocols, Handler<WebSocket> wsConnect) {
    websocketStream(port, host, requestURI, headers, version, subProtocols).handler(wsConnect);
    return this;
  }

  @Override
  public HttpClient websocketAbs(String url, MultiMap headers, WebsocketVersion version, String subProtocols, Handler<WebSocket> wsConnect, Handler<Throwable> failureHandler) {
    websocketStreamAbs(url, headers, version, subProtocols).exceptionHandler(failureHandler).handler(wsConnect);
    return this;
  }

  @Override
  public HttpClient websocket(RequestOptions options, MultiMap headers, WebsocketVersion version, String subProtocols, Handler<WebSocket> wsConnect, Handler<Throwable> failureHandler) {
    websocketStream(options, headers, version, subProtocols).exceptionHandler(failureHandler).handler(wsConnect);
    return this;
  }

  @Override
  public HttpClient websocket(int port, String host, String requestURI, MultiMap headers, WebsocketVersion version,
                              String subProtocols, Handler<WebSocket> wsConnect, Handler<Throwable> failureHandler) {
    websocketStream(port, host, requestURI, headers, version, subProtocols).exceptionHandler(failureHandler).handler(wsConnect);
    return this;
  }

  @Override
  public HttpClient websocket(String host, String requestURI, MultiMap headers, WebsocketVersion version, String subProtocols, Handler<WebSocket> wsConnect) {
    return websocket(options.getDefaultPort(), host, requestURI, headers, version, subProtocols, wsConnect);
  }

  @Override
  public HttpClient websocket(String host, String requestURI, MultiMap headers, WebsocketVersion version, String subProtocols
          , Handler<WebSocket> wsConnect, Handler<Throwable> failureHandler) {
    return websocket(options.getDefaultPort(), host, requestURI, headers, version, subProtocols, wsConnect, failureHandler);
  }

  @Override
  public HttpClient websocket(String requestURI, Handler<WebSocket> wsConnect) {
    return websocket(options.getDefaultPort(), options.getDefaultHost(), requestURI, wsConnect);
  }

  @Override
  public HttpClient websocket(String requestURI, Handler<WebSocket> wsConnect, Handler<Throwable> failureHandler) {
    return websocket(options.getDefaultPort(), options.getDefaultHost(), requestURI, wsConnect, failureHandler);
  }

  @Override
  public HttpClient websocket(String requestURI, MultiMap headers, Handler<WebSocket> wsConnect) {
    return websocket(options.getDefaultPort(), options.getDefaultHost(), requestURI, headers, wsConnect);
  }

  @Override
  public HttpClient websocket(String requestURI, MultiMap headers, Handler<WebSocket> wsConnect, Handler<Throwable> failureHandler) {
    return websocket(options.getDefaultPort(), options.getDefaultHost(), requestURI, headers, wsConnect, failureHandler);
  }

  @Override
  public HttpClient websocket(String requestURI, MultiMap headers, WebsocketVersion version, Handler<WebSocket> wsConnect) {
    return websocket(options.getDefaultPort(), options.getDefaultHost(), requestURI, headers, version, wsConnect);
  }

  @Override
  public HttpClient websocket(String requestURI, MultiMap headers, WebsocketVersion version, Handler<WebSocket> wsConnect, Handler<Throwable> failureHandler) {
    return websocket(options.getDefaultPort(), options.getDefaultHost(), requestURI, headers, version, wsConnect, failureHandler);
  }

  @Override
  public HttpClient websocket(String requestURI, MultiMap headers, WebsocketVersion version, String subProtocols, Handler<WebSocket> wsConnect) {
    return websocket(options.getDefaultPort(), options.getDefaultHost(), requestURI, headers, version, subProtocols, wsConnect);
  }
  @Override
  public HttpClient websocket(String requestURI, MultiMap headers, WebsocketVersion version, String subProtocols
          , Handler<WebSocket> wsConnect, Handler<Throwable> failureHandler) {
    return websocket(options.getDefaultPort(), options.getDefaultHost(), requestURI, headers, version, subProtocols
            , wsConnect, failureHandler);
  }

  @Override
  public WebSocketStream websocketStream(RequestOptions options) {
    return websocketStream(options, null);
  }

  @Override
  public WebSocketStream websocketStream(int port, String host, String requestURI) {
    return websocketStream(port, host, requestURI, null, null);
  }

  @Override
  public WebSocketStream websocketStream(String host, String requestURI) {
    return websocketStream(options.getDefaultPort(), host, requestURI);
  }

  @Override
  public WebSocketStream websocketStream(RequestOptions options, MultiMap headers) {
    return websocketStream(options, headers, null);
  }

  @Override
  public WebSocketStream websocketStream(int port, String host, String requestURI, MultiMap headers) {
    return websocketStream(port, host, requestURI, headers, null);
  }

  @Override
  public WebSocketStream websocketStream(String host, String requestURI, MultiMap headers) {
    return websocketStream(options.getDefaultPort(), host, requestURI, headers);
  }

  @Override
  public WebSocketStream websocketStream(RequestOptions options, MultiMap headers, WebsocketVersion version) {
    return websocketStream(options, headers, version, null);
  }

  @Override
  public WebSocketStream websocketStream(int port, String host, String requestURI, MultiMap headers, WebsocketVersion version) {
    return websocketStream(port, host, requestURI, headers, version, null);
  }

  @Override
  public WebSocketStream websocketStream(String host, String requestURI, MultiMap headers, WebsocketVersion version) {
    return websocketStream(options.getDefaultPort(), host, requestURI, headers, version);
  }

  @Override
  public WebSocketStream websocketStreamAbs(String url, MultiMap headers, WebsocketVersion version, String subProtocols) {
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
    RequestOptions options = new RequestOptions().setHost(uri.getHost()).setPort(port).setSsl(ssl).setURI(relativeUri.toString());
    return websocketStream(options, headers, version, subProtocols);
  }

  @Override
  public WebSocketStream websocketStream(RequestOptions options, MultiMap headers, WebsocketVersion version, String subProtocols) {
    return new WebSocketStream(options.getPort(), options.getHost(), options.getURI(), headers, version, subProtocols, options.isSsl());
  }

  @Override
  public WebSocketStream websocketStream(int port, String host, String requestURI, MultiMap headers, WebsocketVersion version,
                                         String subProtocols) {
    return new WebSocketStream(port, host, requestURI, headers, version, subProtocols, null);
  }

  @Override
  public WebSocketStream websocketStream(String host, String requestURI, MultiMap headers, WebsocketVersion version, String subProtocols) {
    return websocketStream(options.getDefaultPort(), host, requestURI, headers, version, subProtocols);
  }

  @Override
  public WebSocketStream websocketStream(String requestURI) {
    return websocketStream(options.getDefaultPort(), options.getDefaultHost(), requestURI);
  }

  @Override
  public WebSocketStream websocketStream(String requestURI, MultiMap headers) {
    return websocketStream(options.getDefaultPort(), options.getDefaultHost(), requestURI, headers);
  }

  @Override
  public WebSocketStream websocketStream(String requestURI, MultiMap headers, WebsocketVersion version) {
    return websocketStream(options.getDefaultPort(), options.getDefaultHost(), requestURI, headers, version);
  }

  @Override
  public WebSocketStream websocketStream(String requestURI, MultiMap headers, WebsocketVersion version, String subProtocols) {
    return websocketStream(options.getDefaultPort(), options.getDefaultHost(), requestURI, headers, version, subProtocols);
  }

  @Override
  public HttpClientRequest requestAbs(HttpMethod method, String absoluteURI, Handler<HttpClientResponse> responseHandler) {
    Objects.requireNonNull(responseHandler, "no null responseHandler accepted");
    return requestAbs(method, absoluteURI).handler(responseHandler);
  }

  @Override
  public HttpClientRequest get(RequestOptions options) {
    return request(HttpMethod.GET, options);
  }

  @Override
  public HttpClientRequest request(HttpMethod method, int port, String host, String requestURI, Handler<HttpClientResponse> responseHandler) {
    Objects.requireNonNull(responseHandler, "no null responseHandler accepted");
    return request(method, port, host, requestURI).handler(responseHandler);
  }

  @Override
  public HttpClientRequest request(HttpMethod method, String host, String requestURI, Handler<HttpClientResponse> responseHandler) {
    return request(method, options.getDefaultPort(), host, requestURI, responseHandler);
  }

  @Override
  public HttpClientRequest request(HttpMethod method, String requestURI) {
    return request(method, options.getDefaultPort(), options.getDefaultHost(), requestURI);
  }

  @Override
  public HttpClientRequest request(HttpMethod method, String requestURI, Handler<HttpClientResponse> responseHandler) {
    return request(method, options.getDefaultPort(), options.getDefaultHost(), requestURI, responseHandler);
  }

  @Override
  public HttpClientRequest requestAbs(HttpMethod method, String absoluteURI) {
    URL url = parseUrl(absoluteURI);
    Boolean ssl = false;
    int port = url.getPort();
    String protocol = url.getProtocol();
    if ("ftp".equals(protocol)) {
      if (port == -1) {
        port = 21;
      }
    } else {
      char chend = protocol.charAt(protocol.length() - 1);
      if (chend == 'p') {
        if (port == -1) {
          port = 80;
        }
      } else if (chend == 's'){
        ssl = true;
        if (port == -1) {
          port = 443;
        }
      }
    }
    // if we do not know the protocol, the port still may be -1, we will handle that below
    return createRequest(method, protocol, url.getHost(), port, ssl, url.getFile(), null);
  }

  @Override
  public HttpClientRequest request(HttpMethod method, int port, String host, String requestURI) {
    return createRequest(method, host, port, null, requestURI, null);
  }

  @Override
  public HttpClientRequest request(HttpMethod method, RequestOptions options, Handler<HttpClientResponse> responseHandler) {
    return request(method, options).handler(responseHandler);
  }

  @Override
  public HttpClientRequest request(HttpMethod method, RequestOptions options) {
    return createRequest(method, options.getHost(), options.getPort(), options.isSsl(), options.getURI(), null);
  }

  @Override
  public HttpClientRequest request(HttpMethod method, String host, String requestURI) {
    return request(method, options.getDefaultPort(), host, requestURI);
  }

  @Override
  public HttpClientRequest get(int port, String host, String requestURI) {
    return request(HttpMethod.GET, port, host, requestURI);
  }

  @Override
  public HttpClientRequest get(String host, String requestURI) {
    return get(options.getDefaultPort(), host, requestURI);
  }

  @Override
  public HttpClientRequest get(RequestOptions options, Handler<HttpClientResponse> responseHandler) {
    return request(HttpMethod.GET, options, responseHandler);
  }

  @Override
  public HttpClientRequest get(int port, String host, String requestURI, Handler<HttpClientResponse> responseHandler) {
    return request(HttpMethod.GET, port, host, requestURI, responseHandler);
  }

  @Override
  public HttpClientRequest get(String host, String requestURI, Handler<HttpClientResponse> responseHandler) {
    return get(options.getDefaultPort(), host, requestURI, responseHandler);
  }

  @Override
  public HttpClientRequest get(String requestURI) {
    return request(HttpMethod.GET, requestURI);
  }

  @Override
  public HttpClientRequest get(String requestURI, Handler<HttpClientResponse> responseHandler) {
    return request(HttpMethod.GET, requestURI, responseHandler);
  }

  @Override
  public HttpClientRequest getAbs(String absoluteURI) {
    return requestAbs(HttpMethod.GET, absoluteURI);
  }

  @Override
  public HttpClientRequest getAbs(String absoluteURI, Handler<HttpClientResponse> responseHandler) {
    return requestAbs(HttpMethod.GET, absoluteURI, responseHandler);
  }

  @Override
  public HttpClient getNow(RequestOptions options, Handler<HttpClientResponse> responseHandler) {
    return requestNow(HttpMethod.GET, options, responseHandler);
  }

  @Override
  public HttpClient getNow(int port, String host, String requestURI, Handler<HttpClientResponse> responseHandler) {
    get(port, host, requestURI, responseHandler).end();
    return this;
  }

  @Override
  public HttpClient getNow(String host, String requestURI, Handler<HttpClientResponse> responseHandler) {
    return getNow(options.getDefaultPort(), host, requestURI, responseHandler);
  }

  @Override
  public HttpClient getNow(String requestURI, Handler<HttpClientResponse> responseHandler) {
    get(requestURI, responseHandler).end();
    return this;
  }

  @Override
  public HttpClientRequest post(RequestOptions options) {
    return request(HttpMethod.POST, options);
  }

  @Override
  public HttpClientRequest post(int port, String host, String requestURI) {
    return request(HttpMethod.POST, port, host, requestURI);
  }

  @Override
  public HttpClientRequest post(String host, String requestURI) {
    return post(options.getDefaultPort(), host, requestURI);
  }

  @Override
  public HttpClientRequest post(RequestOptions options, Handler<HttpClientResponse> responseHandler) {
    return request(HttpMethod.POST, options, responseHandler);
  }

  @Override
  public HttpClientRequest post(int port, String host, String requestURI, Handler<HttpClientResponse> responseHandler) {
    return request(HttpMethod.POST, port, host, requestURI, responseHandler);
  }

  @Override
  public HttpClientRequest post(String host, String requestURI, Handler<HttpClientResponse> responseHandler) {
    return post(options.getDefaultPort(), host, requestURI, responseHandler);
  }

  @Override
  public HttpClientRequest post(String requestURI) {
    return request(HttpMethod.POST, requestURI);
  }

  @Override
  public HttpClientRequest post(String requestURI, Handler<HttpClientResponse> responseHandler) {
    return request(HttpMethod.POST, requestURI, responseHandler);
  }

  @Override
  public HttpClientRequest postAbs(String absoluteURI) {
    return requestAbs(HttpMethod.POST, absoluteURI);
  }

  @Override
  public HttpClientRequest postAbs(String absoluteURI, Handler<HttpClientResponse> responseHandler) {
    return requestAbs(HttpMethod.POST, absoluteURI, responseHandler);
  }

  @Override
  public HttpClientRequest head(RequestOptions options) {
    return request(HttpMethod.HEAD, options);
  }

  @Override
  public HttpClientRequest head(int port, String host, String requestURI) {
    return request(HttpMethod.HEAD, port, host, requestURI);
  }

  @Override
  public HttpClientRequest head(String host, String requestURI) {
    return head(options.getDefaultPort(), host, requestURI);
  }

  @Override
  public HttpClientRequest head(RequestOptions options, Handler<HttpClientResponse> responseHandler) {
    return request(HttpMethod.HEAD, options, responseHandler);
  }

  @Override
  public HttpClientRequest head(int port, String host, String requestURI, Handler<HttpClientResponse> responseHandler) {
    return request(HttpMethod.HEAD, port, host, requestURI, responseHandler);
  }

  @Override
  public HttpClientRequest head(String host, String requestURI, Handler<HttpClientResponse> responseHandler) {
    return head(options.getDefaultPort(), host, requestURI, responseHandler);
  }

  @Override
  public HttpClientRequest head(String requestURI) {
    return request(HttpMethod.HEAD, requestURI);
  }

  @Override
  public HttpClientRequest head(String requestURI, Handler<HttpClientResponse> responseHandler) {
    return request(HttpMethod.HEAD, requestURI, responseHandler);
  }

  @Override
  public HttpClientRequest headAbs(String absoluteURI) {
    return requestAbs(HttpMethod.HEAD, absoluteURI);
  }

  @Override
  public HttpClientRequest headAbs(String absoluteURI, Handler<HttpClientResponse> responseHandler) {
    return requestAbs(HttpMethod.HEAD, absoluteURI, responseHandler);
  }

  @Override
  public HttpClient headNow(RequestOptions options, Handler<HttpClientResponse> responseHandler) {
    return requestNow(HttpMethod.HEAD, options, responseHandler);
  }

  @Override
  public HttpClient headNow(int port, String host, String requestURI, Handler<HttpClientResponse> responseHandler) {
    head(port, host, requestURI, responseHandler).end();
    return this;
  }

  @Override
  public HttpClient headNow(String host, String requestURI, Handler<HttpClientResponse> responseHandler) {
    return headNow(options.getDefaultPort(), host, requestURI, responseHandler);
  }

  @Override
  public HttpClient headNow(String requestURI, Handler<HttpClientResponse> responseHandler) {
    head(requestURI, responseHandler).end();
    return this;
  }

  @Override
  public HttpClientRequest options(RequestOptions options) {
    return request(HttpMethod.OPTIONS, options);
  }

  @Override
  public HttpClientRequest options(int port, String host, String requestURI) {
    return request(HttpMethod.OPTIONS, port, host, requestURI);
  }

  @Override
  public HttpClientRequest options(String host, String requestURI) {
    return options(options.getDefaultPort(), host, requestURI);
  }

  @Override
  public HttpClientRequest options(RequestOptions options, Handler<HttpClientResponse> responseHandler) {
    return request(HttpMethod.OPTIONS, options, responseHandler);
  }

  @Override
  public HttpClientRequest options(int port, String host, String requestURI, Handler<HttpClientResponse> responseHandler) {
    return request(HttpMethod.OPTIONS, port, host, requestURI, responseHandler);
  }

  @Override
  public HttpClientRequest options(String host, String requestURI, Handler<HttpClientResponse> responseHandler) {
    return options(options.getDefaultPort(), host, requestURI, responseHandler);
  }

  @Override
  public HttpClientRequest options(String requestURI) {
    return request(HttpMethod.OPTIONS, requestURI);
  }

  @Override
  public HttpClientRequest options(String requestURI, Handler<HttpClientResponse> responseHandler) {
    return request(HttpMethod.OPTIONS, requestURI, responseHandler);
  }

  @Override
  public HttpClientRequest optionsAbs(String absoluteURI) {
    return requestAbs(HttpMethod.OPTIONS, absoluteURI);
  }

  @Override
  public HttpClientRequest optionsAbs(String absoluteURI, Handler<HttpClientResponse> responseHandler) {
    return requestAbs(HttpMethod.OPTIONS, absoluteURI, responseHandler);
  }

  @Override
  public HttpClient optionsNow(RequestOptions options, Handler<HttpClientResponse> responseHandler) {
    return requestNow(HttpMethod.OPTIONS, options, responseHandler);
  }

  @Override
  public HttpClient optionsNow(int port, String host, String requestURI, Handler<HttpClientResponse> responseHandler) {
    options(port, host, requestURI, responseHandler).end();
    return this;
  }

  @Override
  public HttpClient optionsNow(String host, String requestURI, Handler<HttpClientResponse> responseHandler) {
    return optionsNow(options.getDefaultPort(), host, requestURI, responseHandler);
  }

  @Override
  public HttpClient optionsNow(String requestURI, Handler<HttpClientResponse> responseHandler) {
    options(requestURI, responseHandler).end();
    return this;
  }

  @Override
  public HttpClientRequest put(RequestOptions options) {
    return request(HttpMethod.PUT, options);
  }

  @Override
  public HttpClientRequest put(int port, String host, String requestURI) {
    return request(HttpMethod.PUT, port, host, requestURI);
  }

  @Override
  public HttpClientRequest put(String host, String requestURI) {
    return put(options.getDefaultPort(), host, requestURI);
  }

  @Override
  public HttpClientRequest put(RequestOptions options, Handler<HttpClientResponse> responseHandler) {
    return request(HttpMethod.PUT, options, responseHandler);
  }

  @Override
  public HttpClientRequest put(int port, String host, String requestURI, Handler<HttpClientResponse> responseHandler) {
    return request(HttpMethod.PUT, port, host, requestURI, responseHandler);
  }

  @Override
  public HttpClientRequest put(String host, String requestURI, Handler<HttpClientResponse> responseHandler) {
    return put(options.getDefaultPort(), host, requestURI, responseHandler);
  }

  @Override
  public HttpClientRequest put(String requestURI) {
    return request(HttpMethod.PUT, requestURI);
  }

  @Override
  public HttpClientRequest put(String requestURI, Handler<HttpClientResponse> responseHandler) {
    return request(HttpMethod.PUT, requestURI, responseHandler);
  }

  @Override
  public HttpClientRequest putAbs(String absoluteURI) {
    return requestAbs(HttpMethod.PUT, absoluteURI);
  }

  @Override
  public HttpClientRequest putAbs(String absoluteURI, Handler<HttpClientResponse> responseHandler) {
    return requestAbs(HttpMethod.PUT, absoluteURI, responseHandler);
  }

  @Override
  public HttpClientRequest delete(RequestOptions options) {
    return request(HttpMethod.DELETE, options);
  }

  @Override
  public HttpClientRequest delete(int port, String host, String requestURI) {
    return request(HttpMethod.DELETE, port, host, requestURI);
  }

  @Override
  public HttpClientRequest delete(String host, String requestURI) {
    return delete(options.getDefaultPort(), host, requestURI);
  }

  @Override
  public HttpClientRequest delete(RequestOptions options, Handler<HttpClientResponse> responseHandler) {
    return request(HttpMethod.DELETE, options, responseHandler);
  }

  @Override
  public HttpClientRequest delete(int port, String host, String requestURI, Handler<HttpClientResponse> responseHandler) {
    return request(HttpMethod.DELETE, port, host, requestURI, responseHandler);
  }

  @Override
  public HttpClientRequest delete(String host, String requestURI, Handler<HttpClientResponse> responseHandler) {
    return delete(options.getDefaultPort(), host, requestURI, responseHandler);
  }

  @Override
  public HttpClientRequest delete(String requestURI) {
    return request(HttpMethod.DELETE, requestURI);
  }

  @Override
  public HttpClientRequest delete(String requestURI, Handler<HttpClientResponse> responseHandler) {
    return request(HttpMethod.DELETE, requestURI, responseHandler);
  }

  @Override
  public HttpClientRequest deleteAbs(String absoluteURI) {
    return requestAbs(HttpMethod.DELETE, absoluteURI);
  }

  @Override
  public HttpClientRequest deleteAbs(String absoluteURI, Handler<HttpClientResponse> responseHandler) {
    return requestAbs(HttpMethod.DELETE, absoluteURI, responseHandler);
  }

  @Override
  public synchronized void close() {
    synchronized (this) {
      checkClosed();
      closed = true;
    }
    if (creatingContext != null) {
      creatingContext.removeCloseHook(closeHook);
    }
    connectionManager.close();
  }

  @Override
  public boolean isMetricsEnabled() {
    return getMetrics() != null;
  }

  @Override
  public Metrics getMetrics() {
    return connectionManager.metrics();
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

  void getConnectionForWebsocket(boolean ssl,
                                 int port,
                                 String host,
                                 Handler<ClientConnection> handler,
                                 Handler<Throwable> connectionExceptionHandler,
                                 ContextImpl context) {
    connectionManager.getConnectionForWebsocket(ssl, port, host, new Waiter(null, context) {
      @Override
      void handleConnection(HttpClientConnection conn) {
      }
      @Override
      void handleStream(HttpClientStream stream) {
        // Use some variance for this
        handler.handle((ClientConnection) stream);
      }
      @Override
      void handleFailure(Throwable failure) {
        connectionExceptionHandler.handle(failure);
      }
      @Override
      boolean isCancelled() {
        return false;
      }
    });
  }

  void getConnectionForRequest(String peerHost, boolean ssl, int port, String host, Waiter waiter) {
    connectionManager.getConnectionForRequest(options.getProtocolVersion(), peerHost, ssl, port, host, waiter);
  }

  /**
   * @return the vertx, for use in package related classes only.
   */
  VertxInternal getVertx() {
    return vertx;
  }

  SSLHelper getSslHelper() {
    return sslHelper;
  }

  private URL parseUrl(String surl) {
    // Note - parsing a URL this way is slower than specifying host, port and relativeURI
    try {
      return new URL(surl);
    } catch (MalformedURLException e) {
      throw new VertxException("Invalid url: " + surl, e);
    }
  }

  private HttpClient requestNow(HttpMethod method, RequestOptions options, Handler<HttpClientResponse> responseHandler) {
    createRequest(method, options.getHost(), options.getPort(), options.isSsl(), options.getURI(), null).handler(responseHandler).end();
    return this;
  }

  private HttpClientRequest createRequest(HttpMethod method, String host, int port, Boolean ssl, String relativeURI, MultiMap headers) {
    return createRequest(method, ssl==null || ssl==false ? "http" : "https", host, port, ssl, relativeURI, headers);
  }

  private HttpClientRequest createRequest(HttpMethod method, String protocol, String host, int port, Boolean ssl, String relativeURI, MultiMap headers) {
    Objects.requireNonNull(method, "no null method accepted");
    Objects.requireNonNull(protocol, "no null protocol accepted");
    Objects.requireNonNull(host, "no null host accepted");
    Objects.requireNonNull(relativeURI, "no null relativeURI accepted");
    checkClosed();
    HttpClientRequest req;
    boolean useSSL = ssl != null ? ssl : options.isSsl();
    boolean useProxy = !useSSL && proxyType == ProxyType.HTTP;
    if (useProxy) {
      final int defaultPort = protocol.equals("ftp") ? 21 : 80;
      final String addPort = (port != -1 && port != defaultPort) ? (":" + port) : "";
      relativeURI = protocol + "://" + host + addPort + relativeURI;
      ProxyOptions proxyOptions = options.getProxyOptions();
      if (proxyOptions.getUsername() != null && proxyOptions.getPassword() != null) {
        if (headers == null) {
          headers = MultiMap.caseInsensitiveMultiMap();
        }
        headers.add("Proxy-Authorization", "Basic " + Base64.getEncoder()
            .encodeToString((proxyOptions.getUsername() + ":" + proxyOptions.getPassword()).getBytes()));
      }
      req = new HttpClientRequestImpl(this, useSSL, method, proxyOptions.getHost(), proxyOptions.getPort(),
          relativeURI, vertx);
      req.setHost(host + addPort);
    } else {
      req = new HttpClientRequestImpl(this, useSSL, method, host, port, relativeURI, vertx);
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

  private class WebSocketStream implements ReadStream<WebSocket> {

    final int port;
    final String host;
    final String requestURI;
    final MultiMap headers;
    final WebsocketVersion version;
    final String subProtocols;
    private Handler<WebSocket> handler;
    private Handler<Throwable> exceptionHandler;
    private Handler<Void> endHandler;
    private Boolean ssl;

    WebSocketStream(int port, String host, String requestURI, MultiMap headers, WebsocketVersion version, String subProtocols, Boolean ssl) {
      this.port = port;
      this.host = host;
      this.requestURI = requestURI;
      this.headers = headers;
      this.version = version;
      this.subProtocols = subProtocols;
      this.ssl = ssl;
    }

    @Override
    public synchronized ReadStream<WebSocket> exceptionHandler(Handler<Throwable> handler) {
      exceptionHandler = handler;
      return this;
    }

    @Override
    public synchronized ReadStream<WebSocket> handler(Handler<WebSocket> handler) {
      if (this.handler == null && handler != null) {
        this.handler = handler;
        checkClosed();
        ContextImpl context = vertx.getOrCreateContext();
        Handler<Throwable> connectionExceptionHandler;
        if (exceptionHandler == null) {
          connectionExceptionHandler = log::error;
        } else {
          connectionExceptionHandler = exceptionHandler;
        }
        Handler<WebSocket> wsConnect;
        if (endHandler != null) {
          Handler<Void> endCallback = endHandler;
          wsConnect = ws -> {
            handler.handle(ws);
            endCallback.handle(null);
          };
        } else {
          wsConnect = handler;
        }
        getConnectionForWebsocket(ssl != null ? ssl : options.isSsl(), port, host, conn -> {
          conn.exceptionHandler(connectionExceptionHandler);
          if (conn.isValid()) {
            conn.toWebSocket(requestURI, headers, version, subProtocols, options.getMaxWebsocketFrameSize(), wsConnect);
          } else {
            websocket(port, host, requestURI, headers, version, subProtocols, wsConnect);
          }
        }, connectionExceptionHandler, context);
      }
      return this;
    }

    @Override
    public synchronized ReadStream<WebSocket> endHandler(Handler<Void> endHandler) {
      this.endHandler = endHandler;
      return this;
    }

    @Override
    public ReadStream<WebSocket> pause() {
      return this;
    }

    @Override
    public ReadStream<WebSocket> resume() {
      return this;
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
