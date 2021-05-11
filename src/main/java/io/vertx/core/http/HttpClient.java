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
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.MultiMap;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.metrics.Measured;
import io.vertx.core.net.SocketAddress;
import io.vertx.core.streams.ReadStream;

import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;

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
   * Create an HTTP request to send to the server. The {@code handler}
   * is called when the request is ready to be sent.
   *
   * @param options    the request options
   * @param handler    the handler called when the request is ready to be sent
   */
  void request(RequestOptions options, Handler<AsyncResult<HttpClientRequest>> handler);

  /**
   * Like {@link #request(RequestOptions, Handler)} but returns a {@code Future} of the asynchronous result
   */
  Future<HttpClientRequest> request(RequestOptions options);

  /**
   * Create an HTTP request to send to the server at the {@code host} and {@code port}. The {@code handler}
   * is called when the request is ready to be sent.
   *
   * @param method     the HTTP method
   * @param port       the port
   * @param host       the host
   * @param requestURI the relative URI
   * @param handler    the handler called when the request is ready to be sent
   */
  void request(HttpMethod method, int port, String host, String requestURI, Handler<AsyncResult<HttpClientRequest>> handler);

  /**
   * Like {@link #request(HttpMethod, int, String, String, Handler)} but returns a {@code Future} of the asynchronous result
   */
  Future<HttpClientRequest> request(HttpMethod method, int port, String host, String requestURI);

  /**
   * Create an HTTP request to send to the server at the {@code host} and default port. The {@code handler}
   * is called when the request is ready to be sent.
   *
   * @param method     the HTTP method
   * @param host       the host
   * @param requestURI the relative URI
   * @param handler    the handler called when the request is ready to be sent
   */
  void request(HttpMethod method, String host, String requestURI, Handler<AsyncResult<HttpClientRequest>> handler);

  /**
   * Like {@link #request(HttpMethod, String, String, Handler)} but returns a {@code Future} of the asynchronous result
   */
  Future<HttpClientRequest> request(HttpMethod method, String host, String requestURI);

  /**
   * Create an HTTP request to send to the server at the default host and port. The {@code handler}
   * is called when the request is ready to be sent.
   *
   * @param method     the HTTP method
   * @param requestURI the relative URI
   * @param handler    the handler called when the request is ready to be sent
   */
  void request(HttpMethod method, String requestURI, Handler<AsyncResult<HttpClientRequest>> handler);

  /**
   * Like {@link #request(HttpMethod, String, Handler)} but returns a {@code Future} of the asynchronous result
   */
  Future<HttpClientRequest> request(HttpMethod method, String requestURI);

  /**
   * Connect a WebSocket to the specified port, host and relative request URI
   * @param port  the port
   * @param host  the host
   * @param requestURI  the relative URI
   * @param handler  handler that will be called with the WebSocket when connected
   */
  void webSocket(int port, String host, String requestURI, Handler<AsyncResult<WebSocket>> handler);

  /**
   * Like {@link #webSocket(int, String, String, Handler)} but returns a {@code Future} of the asynchronous result
   */
  Future<WebSocket> webSocket(int port, String host, String requestURI);

  /**
   * Connect a WebSocket to the host and relative request URI and default port
   * @param host  the host
   * @param requestURI  the relative URI
   * @param handler  handler that will be called with the WebSocket when connected
   */
  void webSocket(String host, String requestURI, Handler<AsyncResult<WebSocket>> handler);

  /**
   * Like {@link #webSocket(String, String, Handler)} but returns a {@code Future} of the asynchronous result
   */
  Future<WebSocket> webSocket(String host, String requestURI);

  /**
   * Connect a WebSocket at the relative request URI using the default host and port
   * @param requestURI  the relative URI
   * @param handler  handler that will be called with the WebSocket when connected
   */
  void webSocket(String requestURI, Handler<AsyncResult<WebSocket>> handler);

  /**
   * Like {@link #webSocket(String, Handler)} but returns a {@code Future} of the asynchronous result
   */
  Future<WebSocket> webSocket(String requestURI);

  /**
   * Connect a WebSocket with the specified options.
   *
   * @param options  the request options
   */
  void webSocket(WebSocketConnectOptions options, Handler<AsyncResult<WebSocket>> handler);

  /**
   * Like {@link #webSocket(WebSocketConnectOptions, Handler)} but returns a {@code Future} of the asynchronous result
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
   * @param handler handler that will be called if WebSocket connection fails
   */
  void webSocketAbs(String url, MultiMap headers, WebsocketVersion version, List<String> subProtocols, Handler<AsyncResult<WebSocket>> handler);

  /**
   * Like {@link #webSocketAbs(String, MultiMap, WebsocketVersion, List, Handler)} but returns a {@code Future} of the asynchronous result
   */
  Future<WebSocket> webSocketAbs(String url, MultiMap headers, WebsocketVersion version, List<String> subProtocols);

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
   * Close the client. Closing will close down any pooled connections.
   * Clients should always be closed after use.
   */
  void close(Handler<AsyncResult<Void>> handler);

  /**
   * Like {@link #close(Handler)} but returns a {@code Future} of the asynchronous result
   */
  Future<Void> close();

}
