/*
 * Copyright (c) 2011-2017 Contributors to the Eclipse Foundation
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
import io.vertx.core.Promise;
import io.vertx.core.metrics.Measured;
import io.vertx.core.net.SocketAddress;

import java.util.List;
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
   * Like {@link #request(HttpMethod, RequestOptions)} using the {@code serverAddress} parameter to connect to the
   * server instead of the {@code absoluteURI} parameter.
   * <p>
   * The request host header will still be created from the {@code options} parameter.
   * <p>
   * Use {@link SocketAddress#domainSocketAddress(String)} to connect to a unix domain socket server.
   */
  HttpClientRequest request(HttpMethod method, SocketAddress serverAddress, RequestOptions options);

  /**
   * Create an HTTP request to send to the server with the specified options.
   *
   * @param method  the HTTP method
   * @param options  the request options
   * @return  an HTTP client request object
   */
  HttpClientRequest request(HttpMethod method, RequestOptions options);

  /**
   * Create an HTTP request to send to the server at the specified host and port.
   * @param method  the HTTP method
   * @param port  the port
   * @param host  the host
   * @param requestURI  the relative URI
   * @return  an HTTP client request object
   */
  HttpClientRequest request(HttpMethod method, int port, String host, String requestURI);

  /**
   * Like {@link #request(HttpMethod, int, String, String)} using the {@code serverAddress} parameter to connect to the
   * server instead of the {@code absoluteURI} parameter.
   * <p>
   * The request host header will still be created from the {@code host} and {@code port} parameters.
   * <p>
   * Use {@link SocketAddress#domainSocketAddress(String)} to connect to a unix domain socket server.
   */
  HttpClientRequest request(HttpMethod method, SocketAddress serverAddress, int port, String host, String requestURI);

  /**
   * Create an HTTP request to send to the server at the specified host and default port.
   * @param method  the HTTP method
   * @param host  the host
   * @param requestURI  the relative URI
   * @return  an HTTP client request object
   */
  HttpClientRequest request(HttpMethod method, String host, String requestURI);

  /**
   * Create an HTTP request to send to the server with the specified options, specifying a response handler to receive
   *
   * @param method  the HTTP method
   * @param options  the request options
   * @return  an HTTP client request object
   */
  HttpClientRequest request(HttpMethod method, RequestOptions options, Handler<AsyncResult<HttpClientResponse>> responseHandler);

  /**
   * Like {@link #request(HttpMethod, RequestOptions, Handler)} using the {@code serverAddress} parameter to connect to the
   * server instead of the {@code absoluteURI} parameter.
   * <p>
   * The request host header will still be created from the {@code options} parameter.
   * <p>
   * Use {@link SocketAddress#domainSocketAddress(String)} to connect to a unix domain socket server.
   */
  HttpClientRequest request(HttpMethod method, SocketAddress serverAddress, RequestOptions options, Handler<AsyncResult<HttpClientResponse>> responseHandler);

  /**
   * Create an HTTP request to send to the server at the specified host and port, specifying a response handler to receive
   * the response
   * @param method  the HTTP method
   * @param port  the port
   * @param host  the host
   * @param requestURI  the relative URI
   * @param responseHandler  the response handler
   * @return  an HTTP client request object
   */
  HttpClientRequest request(HttpMethod method, int port, String host, String requestURI, Handler<AsyncResult<HttpClientResponse>> responseHandler);

  /**
   * Like {@link #request(HttpMethod, int, String, String, Handler)} using the {@code serverAddress} parameter to connect to the
   * server instead of the {@code absoluteURI} parameter.
   * <p>
   * The request host header will still be created from the {@code host} and {@code port} parameters.
   * <p>
   * Use {@link SocketAddress#domainSocketAddress(String)} to connect to a unix domain socket server.
   */
  HttpClientRequest request(HttpMethod method, SocketAddress serverAddress, int port, String host, String requestURI, Handler<AsyncResult<HttpClientResponse>> responseHandler);

  /**
   * Create an HTTP request to send to the server at the specified host and default port, specifying a response handler to receive
   * the response
   * @param method  the HTTP method
   * @param host  the host
   * @param requestURI  the relative URI
   * @param responseHandler  the response handler
   * @return  an HTTP client request object
   */
  HttpClientRequest request(HttpMethod method, String host, String requestURI, Handler<AsyncResult<HttpClientResponse>> responseHandler);

  /**
   * Create an HTTP request to send to the server at the default host and port.
   * @param method  the HTTP method
   * @param requestURI  the relative URI
   * @return  an HTTP client request object
   */
  HttpClientRequest request(HttpMethod method, String requestURI);

  /**
   * Create an HTTP request to send to the server at the default host and port, specifying a response handler to receive
   * the response
   * @param method  the HTTP method
   * @param requestURI  the relative URI
   * @param responseHandler  the response handler
   * @return  an HTTP client request object
   */
  HttpClientRequest request(HttpMethod method, String requestURI, Handler<AsyncResult<HttpClientResponse>> responseHandler);

  /**
   * Create an HTTP request to send to the server using an absolute URI
   * @param method  the HTTP method
   * @param absoluteURI  the absolute URI
   * @return  an HTTP client request object
   */
  HttpClientRequest requestAbs(HttpMethod method, String absoluteURI);

  /**
   * Like {@link #requestAbs(HttpMethod, String)} using the {@code serverAddress} parameter to connect to the
   * server instead of the {@code absoluteURI} parameter.
   * <p>
   * The request host header will still be created from the {@code absoluteURI} parameter.
   * <p>
   * Use {@link SocketAddress#domainSocketAddress(String)} to connect to a unix domain socket server.
   */
  HttpClientRequest requestAbs(HttpMethod method, SocketAddress serverAddress, String absoluteURI);

  /**
   * Create an HTTP request to send to the server using an absolute URI, specifying a response handler to receive
   * the response
   * @param method  the HTTP method
   * @param absoluteURI  the absolute URI
   * @param responseHandler  the response handler
   * @return  an HTTP client request object
   */
  HttpClientRequest requestAbs(HttpMethod method, String absoluteURI, Handler<AsyncResult<HttpClientResponse>> responseHandler);

  /**
   * Like {@link #requestAbs(HttpMethod, String, Handler)} using the {@code serverAddress} parameter to connect to the
   * server instead of the {@code absoluteURI} parameter.
   * <p>
   * The request host header will still be created from the {@code absoluteURI} parameter.
   * <p>
   * Use {@link SocketAddress#domainSocketAddress(String)} to connect to a unix domain socket server.
   */
  HttpClientRequest requestAbs(HttpMethod method, SocketAddress serverAddress, String absoluteURI, Handler<AsyncResult<HttpClientResponse>> responseHandler);

  /**
   * Create an HTTP GET request to send to the server with the specified options.
   * @param options  the request options
   * @return  an HTTP client request object
   */
  HttpClientRequest get(RequestOptions options);

  /**
   * Create an HTTP GET request to send to the server at the specified host and port.
   * @param port  the port
   * @param host  the host
   * @param requestURI  the relative URI
   * @return  an HTTP client request object
   */
  HttpClientRequest get(int port, String host, String requestURI);

  /**
   * Create an HTTP GET request to send to the server at the specified host and default port.
   * @param host  the host
   * @param requestURI  the relative URI
   * @return  an HTTP client request object
   */
  HttpClientRequest get(String host, String requestURI);

  /**
   * Create an HTTP GET request to send to the server with the specified options, specifying a response handler to receive
   * the response
   * @param options  the request options
   * @param responseHandler  the response handler
   * @return  an HTTP client request object
   */
  HttpClientRequest get(RequestOptions options, Handler<AsyncResult<HttpClientResponse>> responseHandler);

  /**
   * Create an HTTP GET request to send to the server at the specified host and port, specifying a response handler to receive
   * the response
   * @param port  the port
   * @param host  the host
   * @param requestURI  the relative URI
   * @param responseHandler  the response handler
   * @return  an HTTP client request object
   */
  HttpClientRequest get(int port, String host, String requestURI, Handler<AsyncResult<HttpClientResponse>> responseHandler);

  /**
   * Create an HTTP GET request to send to the server at the specified host and default port, specifying a response handler to receive
   * the response
   * @param host  the host
   * @param requestURI  the relative URI
   * @param responseHandler  the response handler
   * @return  an HTTP client request object
   */
  HttpClientRequest get(String host, String requestURI, Handler<AsyncResult<HttpClientResponse>> responseHandler);

  /**
   * Create an HTTP GET request to send to the server at the default host and port.
   * @param requestURI  the relative URI
   * @return  an HTTP client request object
   */
  HttpClientRequest get(String requestURI);

  /**
   * Create an HTTP GET request to send to the server at the default host and port, specifying a response handler to receive
   * the response
   * @param requestURI  the relative URI
   * @param responseHandler  the response handler
   * @return  an HTTP client request object
   */
  HttpClientRequest get(String requestURI, Handler<AsyncResult<HttpClientResponse>> responseHandler);

  /**
   * Create an HTTP GET request to send to the server using an absolute URI
   * @param absoluteURI  the absolute URI
   * @return  an HTTP client request object
   */
  HttpClientRequest getAbs(String absoluteURI);

  /**
   * Create an HTTP GET request to send to the server using an absolute URI, specifying a response handler to receive
   * the response
   * @param absoluteURI  the absolute URI
   * @param responseHandler  the response handler
   * @return  an HTTP client request object
   */
  HttpClientRequest getAbs(String absoluteURI, Handler<AsyncResult<HttpClientResponse>> responseHandler);

  /**
   * Sends an HTTP GET request to the server with the specified options, specifying a response handler to receive
   * the response
   * @param options  the request options
   * @param responseHandler  the response handler
   * @return a reference to this, so the API can be used fluently
   */
  @Fluent
  HttpClient getNow(RequestOptions options, Handler<AsyncResult<HttpClientResponse>> responseHandler);

  /**
   * Like {@link #getNow(RequestOptions, Handler)} but returns a {@code Future} of the asynchronous result
   */
  default Future<HttpClientResponse> getNow(RequestOptions options) {
    Promise<HttpClientResponse> promise = Promise.promise();
    getNow(options, promise);
    return promise.future();
  }

  /**
   * Sends an HTTP GET request to the server at the specified host and port, specifying a response handler to receive
   * the response
   * @param port  the port
   * @param host  the host
   * @param requestURI  the relative URI
   * @param responseHandler  the response handler
   * @return a reference to this, so the API can be used fluently
   */
  @Fluent
  HttpClient getNow(int port, String host, String requestURI, Handler<AsyncResult<HttpClientResponse>> responseHandler);

  /**
   * Like {@link #getNow(int, String, String, Handler)} but returns a {@code Future} of the asynchronous result
   */
  default Future<HttpClientResponse> getNow(int port, String host, String requestURI) {
    Promise<HttpClientResponse> promise = Promise.promise();
    getNow(port, host, requestURI, promise);
    return promise.future();
  }

  /**
   * Sends an HTTP GET request to the server at the specified host and default port, specifying a response handler to receive
   * the response
   * @param host  the host
   * @param requestURI  the relative URI
   * @param responseHandler  the response handler
   * @return a reference to this, so the API can be used fluently
   */
  @Fluent
  HttpClient getNow(String host, String requestURI, Handler<AsyncResult<HttpClientResponse>> responseHandler);

  /**
   * Like {@link #getNow(String, String, Handler)} but returns a {@code Future} of the asynchronous result
   */
  default Future<HttpClientResponse> getNow(String host, String requestURI) {
    Promise<HttpClientResponse> promise = Promise.promise();
    getNow(host, requestURI, promise);
    return promise.future();
  }

  /**
   * Sends an HTTP GET request  to the server at the default host and port, specifying a response handler to receive
   * the response
   * @param requestURI  the relative URI
   * @param responseHandler  the response handler
   * @return a reference to this, so the API can be used fluently
   */
  @Fluent
  HttpClient getNow(String requestURI, Handler<AsyncResult<HttpClientResponse>> responseHandler);

  /**
   * Like {@link #getNow(String, Handler)} but returns a {@code Future} of the asynchronous result
   */
  default Future<HttpClientResponse> getNow(String requestURI) {
    Promise<HttpClientResponse> promise = Promise.promise();
    getNow(requestURI, promise);
    return promise.future();
  }

  /**
   * Create an HTTP POST request to send to the server with the specified options.
   * @param options  the request options
   * @return  an HTTP client request object
   */
  HttpClientRequest post(RequestOptions options);

  /**
   * Create an HTTP POST request to send to the server at the specified host and port.
   * @param port  the port
   * @param host  the host
   * @param requestURI  the relative URI
   * @return  an HTTP client request object
   */
  HttpClientRequest post(int port, String host, String requestURI);

  /**
   * Create an HTTP POST request to send to the server at the specified host and default port.
   * @param host  the host
   * @param requestURI  the relative URI
   * @return  an HTTP client request object
   */
  HttpClientRequest post(String host, String requestURI);

  /**
   * Create an HTTP POST request to send to the server with the specified options, specifying a response handler to receive
   * the response
   * @param options  the request options
   * @param responseHandler  the response handler
   * @return  an HTTP client request object
   */
  HttpClientRequest post(RequestOptions options, Handler<AsyncResult<HttpClientResponse>> responseHandler);

  /**
   * Create an HTTP POST request to send to the server at the specified host and port, specifying a response handler to receive
   * the response
   * @param port  the port
   * @param host  the host
   * @param requestURI  the relative URI
   * @param responseHandler  the response handler
   * @return  an HTTP client request object
   */
  HttpClientRequest post(int port, String host, String requestURI, Handler<AsyncResult<HttpClientResponse>> responseHandler);

  /**
   * Create an HTTP POST request to send to the server at the specified host and default port, specifying a response handler to receive
   * the response
   * @param host  the host
   * @param requestURI  the relative URI
   * @param responseHandler  the response handler
   * @return  an HTTP client request object
   */
  HttpClientRequest post(String host, String requestURI, Handler<AsyncResult<HttpClientResponse>> responseHandler);

  /**
   * Create an HTTP POST request to send to the server at the default host and port.
   * @param requestURI  the relative URI
   * @return  an HTTP client request object
   */
  HttpClientRequest post(String requestURI);

  /**
   * Create an HTTP POST request to send to the server at the default host and port, specifying a response handler to receive
   * the response
   * @param requestURI  the relative URI
   * @param responseHandler  the response handler
   * @return  an HTTP client request object
   */
  HttpClientRequest post(String requestURI, Handler<AsyncResult<HttpClientResponse>> responseHandler);

  /**
   * Create an HTTP POST request to send to the server using an absolute URI
   * @param absoluteURI  the absolute URI
   * @return  an HTTP client request object
   */
  HttpClientRequest postAbs(String absoluteURI);

  /**
   * Create an HTTP POST request to send to the server using an absolute URI, specifying a response handler to receive
   * the response
   * @param absoluteURI  the absolute URI
   * @param responseHandler  the response handler
   * @return  an HTTP client request object
   */
  HttpClientRequest postAbs(String absoluteURI, Handler<AsyncResult<HttpClientResponse>> responseHandler);

  /**
   * Create an HTTP HEAD request to send to the server with the specified options.
   * @param options  the request options
   * @return  an HTTP client request object
   */
  HttpClientRequest head(RequestOptions options);

  /**
   * Create an HTTP HEAD request to send to the server at the specified host and port.
   * @param port  the port
   * @param host  the host
   * @param requestURI  the relative URI
   * @return  an HTTP client request object
   */
  HttpClientRequest head(int port, String host, String requestURI);

  /**
   * Create an HTTP HEAD request to send to the server at the specified host and default port.
   * @param host  the host
   * @param requestURI  the relative URI
   * @return  an HTTP client request object
   */
  HttpClientRequest head(String host, String requestURI);

  /**
   * Create an HTTP HEAD request to send to the server with the specified options, specifying a response handler to receive
   * the response
   * @param options  the request options
   * @param responseHandler  the response handler
   * @return  an HTTP client request object
   */
  HttpClientRequest head(RequestOptions options, Handler<AsyncResult<HttpClientResponse>> responseHandler);

  /**
   * Create an HTTP HEAD request to send to the server at the specified host and port, specifying a response handler to receive
   * the response
   * @param port  the port
   * @param host  the host
   * @param requestURI  the relative URI
   * @param responseHandler  the response handler
   * @return  an HTTP client request object
   */
  HttpClientRequest head(int port, String host, String requestURI, Handler<AsyncResult<HttpClientResponse>> responseHandler);

  /**
   * Create an HTTP HEAD request to send to the server at the specified host and default port, specifying a response handler to receive
   * the response
   * @param host  the host
   * @param requestURI  the relative URI
   * @param responseHandler  the response handler
   * @return  an HTTP client request object
   */
  HttpClientRequest head(String host, String requestURI, Handler<AsyncResult<HttpClientResponse>> responseHandler);

  /**
   * Create an HTTP HEAD request to send to the server at the default host and port.
   * @param requestURI  the relative URI
   * @return  an HTTP client request object
   */
  HttpClientRequest head(String requestURI);

  /**
   * Create an HTTP HEAD request to send to the server at the default host and port, specifying a response handler to receive
   * the response
   * @param requestURI  the relative URI
   * @param responseHandler  the response handler
   * @return  an HTTP client request object
   */
  HttpClientRequest head(String requestURI, Handler<AsyncResult<HttpClientResponse>> responseHandler);

  /**
   * Create an HTTP HEAD request to send to the server using an absolute URI
   * @param absoluteURI  the absolute URI
   * @return  an HTTP client request object
   */
  HttpClientRequest headAbs(String absoluteURI);

  /**
   * Create an HTTP HEAD request to send to the server using an absolute URI, specifying a response handler to receive
   * the response
   * @param absoluteURI  the absolute URI
   * @param responseHandler  the response handler
   * @return  an HTTP client request object
   */
  HttpClientRequest headAbs(String absoluteURI, Handler<AsyncResult<HttpClientResponse>> responseHandler);

  /**
   * Sends an HTTP HEAD request to the server with the specified options, specifying a response handler to receive
   * the response
   * @param options  the request options
   * @param responseHandler  the response handler
   * @return a reference to this, so the API can be used fluently
   */
  @Fluent
  HttpClient headNow(RequestOptions options, Handler<AsyncResult<HttpClientResponse>> responseHandler);

  /**
   * Like {@link #headNow(RequestOptions, Handler)} but returns a {@code Future} of the asynchronous result
   */
  default Future<HttpClientResponse> headNow(RequestOptions options) {
    Promise<HttpClientResponse> promise = Promise.promise();
    headNow(options, promise);
    return promise.future();
  }

  /**
   * Sends an HTTP HEAD request to the server at the specified host and port, specifying a response handler to receive
   * the response
   * @param port  the port
   * @param host  the host
   * @param requestURI  the relative URI
   * @param responseHandler  the response handler
   * @return a reference to this, so the API can be used fluently
   */
  @Fluent
  HttpClient headNow(int port, String host, String requestURI, Handler<AsyncResult<HttpClientResponse>> responseHandler);

  /**
   * Like {@link #headNow(int, String, String, Handler)} but returns a {@code Future} of the asynchronous result
   */
  default Future<HttpClientResponse> headNow(int port, String host, String requestURI) {
    Promise<HttpClientResponse> promise = Promise.promise();
    headNow(port, host, requestURI, promise);
    return promise.future();
  }

  /**
   * Sends an HTTP HEAD request to the server at the specified host and default port, specifying a response handler to receive
   * the response
   * @param host  the host
   * @param requestURI  the relative URI
   * @param responseHandler  the response handler
   * @return a reference to this, so the API can be used fluently
   */
  @Fluent
  HttpClient headNow(String host, String requestURI, Handler<AsyncResult<HttpClientResponse>> responseHandler);

  /**
   * Like {@link #headNow(String, String, Handler)} but returns a {@code Future} of the asynchronous result
   */
  default Future<HttpClientResponse> headNow(String host, String requestURI) {
    Promise<HttpClientResponse> promise = Promise.promise();
    headNow(host, requestURI, promise);
    return promise.future();
  }

  /**
   * Sends an HTTP HEAD request  to the server at the default host and port, specifying a response handler to receive
   * the response
   * @param requestURI  the relative URI
   * @param responseHandler  the response handler
   * @return a reference to this, so the API can be used fluently
   */
  @Fluent
  HttpClient headNow(String requestURI, Handler<AsyncResult<HttpClientResponse>> responseHandler);

  /**
   * Like {@link #headNow(String, Handler)} but returns a {@code Future} of the asynchronous result
   */
  default Future<HttpClientResponse> headNow(String requestURI) {
    Promise<HttpClientResponse> promise = Promise.promise();
    headNow(requestURI, promise);
    return promise.future();
  }

  /**
   * Create an HTTP OPTIONS request to send to the server with the specified options.
   * @param options  the request options
   * @return  an HTTP client request object
   */
  HttpClientRequest options(RequestOptions options);

  /**
   * Create an HTTP OPTIONS request to send to the server at the specified host and port.
   * @param port  the port
   * @param host  the host
   * @param requestURI  the relative URI
   * @return  an HTTP client request object
   */
  HttpClientRequest options(int port, String host, String requestURI);

  /**
   * Create an HTTP OPTIONS request to send to the server at the specified host and default port.
   * @param host  the host
   * @param requestURI  the relative URI
   * @return  an HTTP client request object
   */
  HttpClientRequest options(String host, String requestURI);

  /**
   * Create an HTTP OPTIONS request to send to the server with the specified options, specifying a response handler to receive
   * the response
   * @param options  the request options
   * @param responseHandler  the response handler
   * @return  an HTTP client request object
   */
  HttpClientRequest options(RequestOptions options, Handler<AsyncResult<HttpClientResponse>> responseHandler);

  /**
   * Create an HTTP OPTIONS request to send to the server at the specified host and port, specifying a response handler to receive
   * the response
   * @param port  the port
   * @param host  the host
   * @param requestURI  the relative URI
   * @param responseHandler  the response handler
   * @return  an HTTP client request object
   */
  HttpClientRequest options(int port, String host, String requestURI, Handler<AsyncResult<HttpClientResponse>> responseHandler);

  /**
   * Create an HTTP OPTIONS request to send to the server at the specified host and default port, specifying a response handler to receive
   * the response
   * @param host  the host
   * @param requestURI  the relative URI
   * @param responseHandler  the response handler
   * @return  an HTTP client request object
   */
  HttpClientRequest options(String host, String requestURI, Handler<AsyncResult<HttpClientResponse>> responseHandler);

  /**
   * Create an HTTP OPTIONS request to send to the server at the default host and port.
   * @param requestURI  the relative URI
   * @return  an HTTP client request object
   */
  HttpClientRequest options(String requestURI);

  /**
   * Create an HTTP OPTIONS request to send to the server at the default host and port, specifying a response handler to receive
   * the response
   * @param requestURI  the relative URI
   * @param responseHandler  the response handler
   * @return  an HTTP client request object
   */
  HttpClientRequest options(String requestURI, Handler<AsyncResult<HttpClientResponse>> responseHandler);

  /**
   * Create an HTTP OPTIONS request to send to the server using an absolute URI
   * @param absoluteURI  the absolute URI
   * @return  an HTTP client request object
   */
  HttpClientRequest optionsAbs(String absoluteURI);

  /**
   * Create an HTTP OPTIONS request to send to the server using an absolute URI, specifying a response handler to receive
   * the response
   * @param absoluteURI  the absolute URI
   * @param responseHandler  the response handler
   * @return  an HTTP client request object
   */
  HttpClientRequest optionsAbs(String absoluteURI, Handler<AsyncResult<HttpClientResponse>> responseHandler);

  /**
   * Sends an HTTP OPTIONS request to the server with the specified options, specifying a response handler to receive
   * the response
   * @param options  the request options
   * @param responseHandler  the response handler
   * @return a reference to this, so the API can be used fluently
   */
  @Fluent
  HttpClient optionsNow(RequestOptions options, Handler<AsyncResult<HttpClientResponse>> responseHandler);

  /**
   * Like {@link #optionsNow(RequestOptions, Handler)} but returns a {@code Future} of the asynchronous result
   */
  default Future<HttpClientResponse> optionsNow(RequestOptions options) {
    Promise<HttpClientResponse> promise = Promise.promise();
    options(options, promise);
    return promise.future();
  }

  /**
   * Sends an HTTP OPTIONS request to the server at the specified host and port, specifying a response handler to receive
   * the response
   * @param port  the port
   * @param host  the host
   * @param requestURI  the relative URI
   * @param responseHandler  the response handler
   * @return a reference to this, so the API can be used fluently
   */
  @Fluent
  HttpClient optionsNow(int port, String host, String requestURI, Handler<AsyncResult<HttpClientResponse>> responseHandler);

  /**
   * Like {@link #optionsNow(int, String, String, Handler)} but returns a {@code Future} of the asynchronous result
   */
  default Future<HttpClientResponse> optionsNow(int port, String host, String requestURI) {
    Promise<HttpClientResponse> promise = Promise.promise();
    options(port, host, requestURI, promise);
    return promise.future();
  }

  /**
   * Sends an HTTP OPTIONS request to the server at the specified host and default port, specifying a response handler to receive
   * the response
   * @param host  the host
   * @param requestURI  the relative URI
   * @param responseHandler  the response handler
   * @return a reference to this, so the API can be used fluently
   */
  @Fluent
  HttpClient optionsNow(String host, String requestURI, Handler<AsyncResult<HttpClientResponse>> responseHandler);

  /**
   * Like {@link #optionsNow(String, String, Handler)} but returns a {@code Future} of the asynchronous result
   */
  default Future<HttpClientResponse> optionsNow(String host, String requestURI) {
    Promise<HttpClientResponse> promise = Promise.promise();
    options(host, requestURI, promise);
    return promise.future();
  }

  /**
   * Sends an HTTP OPTIONS request  to the server at the default host and port, specifying a response handler to receive
   * the response
   * @param requestURI  the relative URI
   * @param responseHandler  the response handler
   * @return a reference to this, so the API can be used fluently
   */
  @Fluent
  HttpClient optionsNow(String requestURI, Handler<AsyncResult<HttpClientResponse>> responseHandler);

  /**
   * Like {@link #optionsNow(String, Handler)} but returns a {@code Future} of the asynchronous result
   */
  default Future<HttpClientResponse> optionsNow(String requestURI) {
    Promise<HttpClientResponse> promise = Promise.promise();
    options(requestURI, promise);
    return promise.future();
  }

  /**
   * Create an HTTP PUT request to send to the server with the specified options.
   * @param options  the request options
   * @return  an HTTP client request object
   */
  HttpClientRequest put(RequestOptions options);

  /**
   * Create an HTTP PUT request to send to the server at the specified host and port.
   * @param port  the port
   * @param host  the host
   * @param requestURI  the relative URI
   * @return  an HTTP client request object
   */
  HttpClientRequest put(int port, String host, String requestURI);

  /**
   * Create an HTTP PUT request to send to the server at the specified host and default port.
   * @param host  the host
   * @param requestURI  the relative URI
   * @return  an HTTP client request object
   */
  HttpClientRequest put(String host, String requestURI);

  /**
   * Create an HTTP PUT request to send to the server with the specified options, specifying a response handler to receive
   * the response
   * @param options  the request options
   * @param responseHandler  the response handler
   * @return  an HTTP client request object
   */
  HttpClientRequest put(RequestOptions options, Handler<AsyncResult<HttpClientResponse>> responseHandler);

  /**
   * Create an HTTP PUT request to send to the server at the specified host and port, specifying a response handler to receive
   * the response
   * @param port  the port
   * @param host  the host
   * @param requestURI  the relative URI
   * @param responseHandler  the response handler
   * @return  an HTTP client request object
   */
  HttpClientRequest put(int port, String host, String requestURI, Handler<AsyncResult<HttpClientResponse>> responseHandler);

  /**
   * Create an HTTP PUT request to send to the server at the specified host and default port, specifying a response handler to receive
   * the response
   * @param host  the host
   * @param requestURI  the relative URI
   * @param responseHandler  the response handler
   * @return  an HTTP client request object
   */
  HttpClientRequest put(String host, String requestURI, Handler<AsyncResult<HttpClientResponse>> responseHandler);

  /**
   * Create an HTTP PUT request to send to the server at the default host and port.
   * @param requestURI  the relative URI
   * @return  an HTTP client request object
   */
  HttpClientRequest put(String requestURI);

  /**
   * Create an HTTP PUT request to send to the server at the default host and port, specifying a response handler to receive
   * the response
   * @param requestURI  the relative URI
   * @param responseHandler  the response handler
   * @return  an HTTP client request object
   */
  HttpClientRequest put(String requestURI, Handler<AsyncResult<HttpClientResponse>> responseHandler);

  /**
   * Create an HTTP PUT request to send to the server using an absolute URI
   * @param absoluteURI  the absolute URI
   * @return  an HTTP client request object
   */
  HttpClientRequest putAbs(String absoluteURI);

  /**
   * Create an HTTP PUT request to send to the server using an absolute URI, specifying a response handler to receive
   * the response
   * @param absoluteURI  the absolute URI
   * @param responseHandler  the response handler
   * @return  an HTTP client request object
   */
  HttpClientRequest putAbs(String absoluteURI, Handler<AsyncResult<HttpClientResponse>> responseHandler);

  /**
   * Create an HTTP DELETE request to send to the server with the specified options.
   * @param options  the request options
   * @return  an HTTP client request object
   */
  HttpClientRequest delete(RequestOptions options);

  /**
   * Create an HTTP DELETE request to send to the server at the specified host and port.
   * @param port  the port
   * @param host  the host
   * @param requestURI  the relative URI
   * @return  an HTTP client request object
   */
  HttpClientRequest delete(int port, String host, String requestURI);

  /**
   * Create an HTTP DELETE request to send to the server at the specified host and default port.
   * @param host  the host
   * @param requestURI  the relative URI
   * @return  an HTTP client request object
   */
  HttpClientRequest delete(String host, String requestURI);

  /**
   * Create an HTTP DELETE request to send to the server with the specified options, specifying a response handler to receive
   * the response
   * @param options  the request options
   * @param responseHandler  the response handler
   * @return  an HTTP client request object
   */
  HttpClientRequest delete(RequestOptions options, Handler<AsyncResult<HttpClientResponse>> responseHandler);

  /**
   * Create an HTTP DELETE request to send to the server at the specified host and port, specifying a response handler to receive
   * the response
   * @param port  the port
   * @param host  the host
   * @param requestURI  the relative URI
   * @param responseHandler  the response handler
   * @return  an HTTP client request object
   */
  HttpClientRequest delete(int port, String host, String requestURI, Handler<AsyncResult<HttpClientResponse>> responseHandler);

  /**
   * Create an HTTP DELETE request to send to the server at the specified host and default port, specifying a response handler to receive
   * the response
   * @param host  the host
   * @param requestURI  the relative URI
   * @param responseHandler  the response handler
   * @return  an HTTP client request object
   */
  HttpClientRequest delete(String host, String requestURI, Handler<AsyncResult<HttpClientResponse>> responseHandler);

  /**
   * Create an HTTP DELETE request to send to the server at the default host and port.
   * @param requestURI  the relative URI
   * @return  an HTTP client request object
   */
  HttpClientRequest delete(String requestURI);

  /**
   * Create an HTTP DELETE request to send to the server at the default host and port, specifying a response handler to receive
   * the response
   * @param requestURI  the relative URI
   * @param responseHandler  the response handler
   * @return  an HTTP client request object
   */
  HttpClientRequest delete(String requestURI, Handler<AsyncResult<HttpClientResponse>> responseHandler);

  /**
   * Create an HTTP DELETE request to send to the server using an absolute URI
   * @param absoluteURI  the absolute URI
   * @return  an HTTP client request object
   */
  HttpClientRequest deleteAbs(String absoluteURI);

  /**
   * Create an HTTP DELETE request to send to the server using an absolute URI, specifying a response handler to receive
   * the response
   * @param absoluteURI  the absolute URI
   * @param responseHandler  the response handler
   * @return  an HTTP client request object
   */
  HttpClientRequest deleteAbs(String absoluteURI, Handler<AsyncResult<HttpClientResponse>> responseHandler);

  /**
   * Connect a WebSocket to the specified port, host and relative request URI
   * @param port  the port
   * @param host  the host
   * @param requestURI  the relative URI
   * @param handler  handler that will be called with the websocket when connected
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
   * @param handler  handler that will be called with the websocket when connected
   */
  void webSocket(String host, String requestURI, Handler<AsyncResult<WebSocket>> handler);

  /**
   * Like {@link #webSocket(String, String, Handler)} but returns a {@code Future} of the asynchronous result
   */
  Future<WebSocket> webSocket(String host, String requestURI);

  /**
   * Connect a WebSocket at the relative request URI using the default host and port
   * @param requestURI  the relative URI
   * @param handler  handler that will be called with the websocket when connected
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
   * the specified version of WebSockets, and the specified websocket sub protocols.
   *
   * @param url            the absolute url
   * @param headers        the headers
   * @param version        the websocket version
   * @param subProtocols   the subprotocols to use
   * @param handler handler that will be called if websocket connection fails
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
  HttpClient redirectHandler(Function<HttpClientResponse, Future<HttpClientRequest>> handler);

  /**
   * @return the current redirect handler.
   */
  @GenIgnore
  Function<HttpClientResponse, Future<HttpClientRequest>> redirectHandler();

  /**
   * Close the client. Closing will close down any pooled connections.
   * Clients should always be closed after use.
   */
  void close();
}
