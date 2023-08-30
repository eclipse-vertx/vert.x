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

package io.vertx.core.http;

import io.vertx.codegen.annotations.Fluent;
import io.vertx.codegen.annotations.GenIgnore;
import io.vertx.codegen.annotations.VertxGen;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.MultiMap;
import io.vertx.core.metrics.Measured;
import io.vertx.core.net.SSLOptions;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

/**
 * An asynchronous HTTP client.
 * <p>
 * It allows you to make requests to HTTP servers, and a single client can make requests to any server.
 * <p>
 * It also allows you to open WebSockets to servers.
 * <p>
 * The client can also pool HTTP connections.
 * <p>
 * For pooling to occur, keep-alive must be true on the {@link io.vertx.core.http.HttpClientOptions} (default is true).
 * In this case connections will be pooled and re-used if there are pending HTTP requests waiting to get a connection,
 * otherwise they will be closed.
 * <p>
 * This gives the benefits of keep alive when the client is loaded but means we don't keep connections hanging around
 * unnecessarily when there would be no benefits anyway.
 * <p>
 * The client also supports pipe-lining of requests. Pipe-lining means another request is sent on the same connection
 * before the response from the preceding one has returned. Pipe-lining is not appropriate for all requests.
 * <p>
 * To enable pipe-lining, it must be enabled on the {@link io.vertx.core.http.HttpClientOptions} (default is false).
 * <p>
 * When pipe-lining is enabled the connection will be automatically closed when all in-flight responses have returned
 * and there are no outstanding pending requests to write.
 * <p>
 * The client is designed to be reused between requests.
 *
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
@VertxGen
public interface HttpClient extends Measured {

  /**
   * Create an HTTP request to send to the server.
   *
   * @param options    the request options
   * @return a future notified when the request is ready to be sent
   */
  Future<HttpClientRequest> request(RequestOptions options);

  /**
   * Create an HTTP request to send to the server at the {@code host} and {@code port}.
   *
   * @param method     the HTTP method
   * @param port       the port
   * @param host       the host
   * @param requestURI the relative URI
   * @return a future notified when the request is ready to be sent
   */
  Future<HttpClientRequest> request(HttpMethod method, int port, String host, String requestURI);

  /**
   * Create an HTTP request to send to the server at the {@code host} and default port.
   *
   * @param method     the HTTP method
   * @param host       the host
   * @param requestURI the relative URI
   * @return a future notified when the request is ready to be sent
   */
  Future<HttpClientRequest> request(HttpMethod method, String host, String requestURI);

  /**
   * Create an HTTP request to send to the server at the default host and port.
   *
   * @param method     the HTTP method
   * @param requestURI the relative URI
   * @return a future notified when the request is ready to be sent
   */
  Future<HttpClientRequest> request(HttpMethod method, String requestURI);

  /**
   * Create a WebSocket that is not yet connected to the server.
   *
   * @return the client WebSocket
   */
  ClientWebSocket webSocket();

  /**
   * Connect a WebSocket to the specified port, host and relative request URI.
   *
   * @param port  the port
   * @param host  the host
   * @param requestURI  the relative URI
   * @return a future notified when the WebSocket when connected
   */
  default Future<WebSocket> webSocket(int port, String host, String requestURI) {
    return webSocket(new WebSocketConnectOptions().setURI(requestURI).setHost(host).setPort(port));
  }

  /**
   * Connect a WebSocket to the host and relative request URI and default port.
   *
   * @param host  the host
   * @param requestURI  the relative URI
   * @return a future notified when the WebSocket when connected
   */
  Future<WebSocket> webSocket(String host, String requestURI);

  /**
   * Connect a WebSocket at the relative request URI using the default host and port.
   *
   * @param requestURI  the relative URI
   * @return a future notified when the WebSocket when connected
   */
  Future<WebSocket> webSocket(String requestURI);

  /**
   * Connect a WebSocket with the specified options.
   *
   * @param options  the request options
   * @return a future notified when the WebSocket when connected
   */
  Future<WebSocket> webSocket(WebSocketConnectOptions options);

  /**
   * Connect a WebSocket with the specified absolute url, with the specified headers, using
   * the specified version of WebSockets, and the specified WebSocket sub protocols.
   *
   * @param url            the absolute url
   * @param headers        the headers
   * @param version        the WebSocket version
   * @param subProtocols   the subprotocols to use
   * @return a future notified when the WebSocket when connected
   */
  Future<WebSocket> webSocketAbs(String url, MultiMap headers, WebsocketVersion version, List<String> subProtocols);

  /**
   * Update the client SSL options.
   *
   * Update only happens if the SSL options is valid.
   *
   * @param options the new SSL options
   * @return a future signaling the update success
   */
  Future<Void> updateSSLOptions(SSLOptions options);

  /**
   * Set a connection handler for the client. This handler is called when a new connection is established.
   *
   * @return a reference to this, so the API can be used fluently
   */
  @Fluent
  HttpClient connectionHandler(Handler<HttpConnection> handler);

  /**
   * Set a redirect handler for the http client.
   * <p>
   * The redirect handler is called when a {@code 3xx} response is received and the request is configured to
   * follow redirects with {@link HttpClientRequest#setFollowRedirects(boolean)}.
   * <p>
   * The redirect handler is passed the {@link HttpClientResponse}, it can return an {@link HttpClientRequest} or {@code null}.
   * <ul>
   *   <li>when null is returned, the original response is processed by the original request response handler</li>
   *   <li>when a new {@code Future<HttpClientRequest>} is returned, the client will send this new request</li>
   * </ul>
   * The new request will get a copy of the previous request headers unless headers are set. In this case,
   * the client assumes that the redirect handler exclusively managers the headers of the new request.
   * <p>
   * The handler must return a {@code Future<HttpClientRequest>} unsent so the client can further configure it and send it.
   *
   * @param handler the new redirect handler
   * @return a reference to this, so the API can be used fluently
   */
  @Fluent
  HttpClient redirectHandler(Function<HttpClientResponse, Future<RequestOptions>> handler);

  /**
   * @return the current redirect handler.
   */
  @GenIgnore
  Function<HttpClientResponse, Future<RequestOptions>> redirectHandler();

  /**
   * Close the client immediately ({@code close(0, TimeUnit.SECONDS}).
   *
   * @return a future notified when the client is closed
   */
  default Future<Void> close() {
    return close(0, TimeUnit.SECONDS);
  }

  /**
   * Initiate the client close sequence.
   *
   * <p> Connections are taken out of service and closed when all inflight requests are processed, client connection are
   * immediately removed from the pool. When all connections are closed the client is closed. When the timeout
   * expires, all unclosed connections are immediately closed.
   *
   * <ul>
   *   <li>HTTP/2 connections will send a go away frame immediately to signal the other side the connection will close</li>
   *   <li>HTTP/1.x client connection will be closed after the current response is received</li>
   * </ul>
   *
   * @return a future notified when the client is closed
   */
  Future<Void> close(long timeout, TimeUnit timeUnit);

}
