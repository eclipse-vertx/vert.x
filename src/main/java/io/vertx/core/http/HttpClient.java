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
   * Sends an HTTP request to send to the server with the specified options, specifying a response handler to receive
   * the response
   * @param options  the request options
   * @param responseHandler  the response handler
   */
  void send(RequestOptions options, Handler<AsyncResult<HttpClientResponse>> responseHandler);

  /**
   * Like {@link #send(RequestOptions, Handler)} but returns a {@code Future} of the asynchronous result
   */
  Future<HttpClientResponse> send(RequestOptions options);

  /**
   * Sends an HTTP request to send to the server with the specified options, specifying a response handler to receive
   * the response
   * @param options  the request options
   * @param body  the request body
   * @param responseHandler  the response handler
   */
  void send(RequestOptions options, Buffer body, Handler<AsyncResult<HttpClientResponse>> responseHandler);

  /**
   * Like {@link #send(RequestOptions, Buffer, Handler)} but returns a {@code Future} of the asynchronous result
   */
  Future<HttpClientResponse> send(RequestOptions options, Buffer body);

  /**
   * Sends an HTTP request to send to the server with the specified options, specifying a response handler to receive
   * the response
   * @param options  the request options
   * @param body  the request body
   * @param responseHandler  the response handler
   */
  void send(RequestOptions options, ReadStream<Buffer> body, Handler<AsyncResult<HttpClientResponse>> responseHandler);

  /**
   * Like {@link #send(RequestOptions, ReadStream, Handler)} but returns a {@code Future} of the asynchronous result
   */
  Future<HttpClientResponse> send(RequestOptions options, ReadStream<Buffer> body);

  /**
   * Sends an HTTP request to send to the server at the specified host and port, specifying a response handler to receive
   * the response
   * @param method  the HTTP method
   * @param port  the port
   * @param host  the host
   * @param requestURI  the relative URI
   * @param headers  the headers
   * @param responseHandler  the response handler
   */
  void send(HttpMethod method, int port, String host, String requestURI, MultiMap headers, Handler<AsyncResult<HttpClientResponse>> responseHandler);

  /**
   * Like {@link #send(HttpMethod, int, String, String, MultiMap, Handler)} but returns a {@code Future} of the asynchronous result
   */
  Future<HttpClientResponse> send(HttpMethod method, int port, String host, String requestURI, MultiMap headers);

  /**
   * Sends an HTTP request to send to the server at the specified host and port, specifying a response handler to receive
   * the response
   * @param method  the HTTP method
   * @param port  the port
   * @param host  the host
   * @param requestURI  the relative URI
   * @param headers  the headers
   * @param body  the request body
   * @param responseHandler  the response handler
   */
  void send(HttpMethod method, int port, String host, String requestURI, MultiMap headers, Buffer body, Handler<AsyncResult<HttpClientResponse>> responseHandler);

  /**
   * Like {@link #send(HttpMethod, int, String, String, MultiMap, Buffer, Handler)} but returns a {@code Future} of the asynchronous result
   */
  Future<HttpClientResponse> send(HttpMethod method, int port, String host, String requestURI, MultiMap headers, Buffer body);

  /**
   * Sends an HTTP request to send to the server at the specified host and port, specifying a response handler to receive
   * the response
   * @param method  the HTTP method
   * @param port  the port
   * @param host  the host
   * @param requestURI  the relative URI
   * @param headers  the headers
   * @param body  the request body
   * @param responseHandler  the response handler
   */
  void send(HttpMethod method, int port, String host, String requestURI, MultiMap headers, ReadStream<Buffer> body, Handler<AsyncResult<HttpClientResponse>> responseHandler);

  /**
   * Like {@link #send(HttpMethod, int, String, String, MultiMap, ReadStream, Handler)} but returns a {@code Future} of the asynchronous result
   */
  Future<HttpClientResponse> send(HttpMethod method, int port, String host, String requestURI, MultiMap headers, ReadStream<Buffer> body);

  /**
   * Sends an HTTP request to send to the server at the specified host and port, specifying a response handler to receive
   * the response
   * @param method  the HTTP method
   * @param port  the port
   * @param host  the host
   * @param requestURI  the relative URI
   * @param responseHandler  the response handler
   */
  void send(HttpMethod method, int port, String host, String requestURI, Handler<AsyncResult<HttpClientResponse>> responseHandler);

  /**
   * Like {@link #send(HttpMethod, int, String, String, Handler)} but returns a {@code Future} of the asynchronous result
   */
  Future<HttpClientResponse> send(HttpMethod method, int port, String host, String requestURI);

  /**
   * Sends an HTTP request to send to the server at the specified host and port, specifying a response handler to receive
   * the response
   * @param method  the HTTP method
   * @param port  the port
   * @param host  the host
   * @param requestURI  the relative URI
   * @param body  the request body
   * @param responseHandler  the response handler
   */
  void send(HttpMethod method, int port, String host, String requestURI, Buffer body, Handler<AsyncResult<HttpClientResponse>> responseHandler);

  /**
   * Like {@link #send(HttpMethod, int, String, String, Buffer, Handler)} but returns a {@code Future} of the asynchronous result
   */
  Future<HttpClientResponse> send(HttpMethod method, int port, String host, String requestURI, Buffer body);

  /**
   * Sends an HTTP request to send to the server at the specified host and port, specifying a response handler to receive
   * the response
   * @param method  the HTTP method
   * @param port  the port
   * @param host  the host
   * @param requestURI  the relative URI
   * @param body  the request body
   * @param responseHandler  the response handler
   */
  void send(HttpMethod method, int port, String host, String requestURI, ReadStream<Buffer> body, Handler<AsyncResult<HttpClientResponse>> responseHandler);

  /**
   * Like {@link #send(HttpMethod, int, String, String, ReadStream, Handler)} but returns a {@code Future} of the asynchronous result
   */
  Future<HttpClientResponse> send(HttpMethod method, int port, String host, String requestURI, ReadStream<Buffer> body);

  /**
   * Sends an HTTP request to send to the server at the specified host and default port, specifying a response handler to receive
   * the response
   * @param method  the HTTP method
   * @param host  the host
   * @param requestURI  the relative URI
   * @param headers  the headers
   * @param responseHandler  the response handler
   */
  void send(HttpMethod method, String host, String requestURI, MultiMap headers, Handler<AsyncResult<HttpClientResponse>> responseHandler);

  /**
   * Like {@link #send(HttpMethod, String, String, MultiMap, Handler)} but returns a {@code Future} of the asynchronous result
   */
  Future<HttpClientResponse> send(HttpMethod method, String host, String requestURI, MultiMap headers);

  /**
   * Sends an HTTP request to send to the server at the specified host and default port, specifying a response handler to receive
   * the response
   * @param method  the HTTP method
   * @param host  the host
   * @param requestURI  the relative URI
   * @param headers  the headers
   * @param body  the request body
   * @param responseHandler  the response handler
   */
  void send(HttpMethod method, String host, String requestURI, MultiMap headers, Buffer body, Handler<AsyncResult<HttpClientResponse>> responseHandler);

  /**
   * Like {@link #send(HttpMethod, String, String, MultiMap, Buffer, Handler)} but returns a {@code Future} of the asynchronous result
   */
  Future<HttpClientResponse> send(HttpMethod method, String host, String requestURI, MultiMap headers, Buffer body);

  /**
   * Sends an HTTP request to send to the server at the specified host and default port, specifying a response handler to receive
   * the response
   * @param method  the HTTP method
   * @param host  the host
   * @param requestURI  the relative URI
   * @param headers  the headers
   * @param body  the request body
   * @param responseHandler  the response handler
   */
  void send(HttpMethod method, String host, String requestURI, MultiMap headers, ReadStream<Buffer> body, Handler<AsyncResult<HttpClientResponse>> responseHandler);

  /**
   * Like {@link #send(HttpMethod, String, String, MultiMap, ReadStream, Handler)} but returns a {@code Future} of the asynchronous result
   */
  Future<HttpClientResponse> send(HttpMethod method, String host, String requestURI, MultiMap headers, ReadStream<Buffer> body);

  /**
   * Sends an HTTP request to send to the server at the specified host and default port, specifying a response handler to receive
   * the response
   * @param method  the HTTP method
   * @param host  the host
   * @param requestURI  the relative URI
   * @param responseHandler  the response handler
   */
  void send(HttpMethod method, String host, String requestURI, Handler<AsyncResult<HttpClientResponse>> responseHandler);

  /**
   * Like {@link #send(HttpMethod, String, String, Handler)} but returns a {@code Future} of the asynchronous result
   */
  Future<HttpClientResponse> send(HttpMethod method, String host, String requestURI);

  /**
   * Sends an HTTP request to send to the server at the specified host and default port, specifying a response handler to receive
   * the response
   * @param method  the HTTP method
   * @param host  the host
   * @param requestURI  the relative URI
   * @param body  the request body
   * @param responseHandler  the response handler
   */
  void send(HttpMethod method, String host, String requestURI, Buffer body, Handler<AsyncResult<HttpClientResponse>> responseHandler);

  /**
   * Like {@link #send(HttpMethod, String, String, Buffer, Handler)} but returns a {@code Future} of the asynchronous result
   */
  Future<HttpClientResponse> send(HttpMethod method, String host, String requestURI, Buffer body);

  /**
   * Sends an HTTP request to send to the server at the specified host and default port, specifying a response handler to receive
   * the response
   * @param method  the HTTP method
   * @param host  the host
   * @param requestURI  the relative URI
   * @param body  the request body
   * @param responseHandler  the response handler
   */
  void send(HttpMethod method, String host, String requestURI, ReadStream<Buffer> body, Handler<AsyncResult<HttpClientResponse>> responseHandler);

  /**
   * Like {@link #send(HttpMethod, String, String, ReadStream, Handler)} but returns a {@code Future} of the asynchronous result
   */
  Future<HttpClientResponse> send(HttpMethod method, String host, String requestURI, ReadStream<Buffer> body);

  /**
   * Sends an HTTP request to send to the server at the default host and port, specifying a response handler to receive
   * the response
   * @param method  the HTTP method
   * @param requestURI  the relative URI
   * @param headers  the headers
   * @param responseHandler  the response handler
   */
  void send(HttpMethod method, String requestURI, MultiMap headers, Handler<AsyncResult<HttpClientResponse>> responseHandler);

  /**
   * Like {@link #send(HttpMethod, String, MultiMap, Handler)} but returns a {@code Future} of the asynchronous result
   */
  Future<HttpClientResponse> send(HttpMethod method, String requestURI, MultiMap headers);

  /**
   * Sends an HTTP request to send to the server at the default host and port, specifying a response handler to receive
   * the response
   * @param method  the HTTP method
   * @param requestURI  the relative URI
   * @param headers  the headers
   * @param body  the request body
   * @param responseHandler  the response handler
   */
  void send(HttpMethod method, String requestURI, MultiMap headers, Buffer body, Handler<AsyncResult<HttpClientResponse>> responseHandler);

  /**
   * Like {@link #send(HttpMethod, String, MultiMap, Buffer, Handler)} but returns a {@code Future} of the asynchronous result
   */
  Future<HttpClientResponse> send(HttpMethod method, String requestURI, MultiMap headers, Buffer body);

  /**
   * Sends an HTTP request to send to the server at the default host and port, specifying a response handler to receive
   * the response
   * @param method  the HTTP method
   * @param requestURI  the relative URI
   * @param headers  the headers
   * @param body  the request body
   * @param responseHandler  the response handler
   */
  void send(HttpMethod method, String requestURI, MultiMap headers, ReadStream<Buffer> body, Handler<AsyncResult<HttpClientResponse>> responseHandler);

  /**
   * Like {@link #send(HttpMethod, String, MultiMap, ReadStream, Handler)} but returns a {@code Future} of the asynchronous result
   */
  Future<HttpClientResponse> send(HttpMethod method, String requestURI, MultiMap headers, ReadStream<Buffer> body);

  /**
   * Sends an HTTP request to send to the server at the default host and port, specifying a response handler to receive
   * the response
   * @param method  the HTTP method
   * @param requestURI  the relative URI
   * @param responseHandler  the response handler
   */
  void send(HttpMethod method, String requestURI, Handler<AsyncResult<HttpClientResponse>> responseHandler);

  /**
   * Like {@link #send(HttpMethod, String, Handler)} but returns a {@code Future} of the asynchronous result
   */
  Future<HttpClientResponse> send(HttpMethod method, String requestURI);

  /**
   * Sends an HTTP request to send to the server at the default host and port, specifying a response handler to receive
   * the response
   * @param method  the HTTP method
   * @param requestURI  the relative URI
   * @param body  the request body
   * @param responseHandler  the response handler
   */
  void send(HttpMethod method, String requestURI, Buffer body, Handler<AsyncResult<HttpClientResponse>> responseHandler);

  /**
   * Like {@link #send(HttpMethod, String, Buffer, Handler)} but returns a {@code Future} of the asynchronous result
   */
  Future<HttpClientResponse> send(HttpMethod method, String requestURI, Buffer body);

  /**
   * Sends an HTTP request to send to the server at the default host and port, specifying a response handler to receive
   * the response
   * @param method  the HTTP method
   * @param requestURI  the relative URI
   * @param body  the request body
   * @param responseHandler  the response handler
   */
  void send(HttpMethod method, String requestURI, ReadStream<Buffer> body, Handler<AsyncResult<HttpClientResponse>> responseHandler);

  /**
   * Like {@link #send(HttpMethod, String, ReadStream, Handler)} but returns a {@code Future} of the asynchronous result
   */
  Future<HttpClientResponse> send(HttpMethod method, String requestURI, ReadStream<Buffer> body);

  /**
   * Like {@link #request(RequestOptions)} using the {@code serverAddress} parameter to connect to the
   * server instead of the {@code absoluteURI} parameter.
   * <p>
   * The request host header will still be created from the {@code options} parameter.
   * <p>
   * Use {@link SocketAddress#domainSocketAddress(String)} to connect to a unix domain socket server.
   */
  HttpClientRequest request(SocketAddress serverAddress, RequestOptions options);

  /**
   * Create an HTTP request to send to the server with the specified options.
   *
   * @param options  the request options
   * @return  an HTTP client request object
   */
  HttpClientRequest request(RequestOptions options);

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
   * Create an HTTP request to send to the server at the default host and port.
   * @param method  the HTTP method
   * @param requestURI  the relative URI
   * @return  an HTTP client request object
   */
  HttpClientRequest request(HttpMethod method, String requestURI);

  /**
   * Sends an HTTP GET request to the server at the specified host and port, specifying a response handler to receive
   * the response
   * @param port  the port
   * @param host  the host
   * @param requestURI  the relative URI
   * @param headers  the headers
   * @param responseHandler  the response handler
   */
  void get(int port, String host, String requestURI, MultiMap headers, Handler<AsyncResult<HttpClientResponse>> responseHandler);

  /**
   * Like {@link #get(int, String, String, MultiMap, Handler)} but returns a {@code Future} of the asynchronous result
   */
  Future<HttpClientResponse> get(int port, String host, String requestURI, MultiMap headers);

  /**
   * Sends an HTTP GET request to the server at the specified host and default port, specifying a response handler to receive
   * the response
   * @param host  the host
   * @param requestURI  the relative URI
   * @param headers  the headers
   * @param responseHandler  the response handler
   */
  void get(String host, String requestURI, MultiMap headers, Handler<AsyncResult<HttpClientResponse>> responseHandler);

  /**
   * Like {@link #get(String, String, MultiMap, Handler)} but returns a {@code Future} of the asynchronous result
   */
  Future<HttpClientResponse> get(String host, String requestURI, MultiMap headers);

  /**
   * Sends an HTTP GET request  to the server at the default host and port, specifying a response handler to receive
   * the response
   * @param requestURI  the relative URI
   * @param headers  the headers
   * @param responseHandler  the response handler
   */
  void get(String requestURI, MultiMap headers, Handler<AsyncResult<HttpClientResponse>> responseHandler);

  /**
   * Like {@link #get(String, MultiMap, Handler)} but returns a {@code Future} of the asynchronous result
   */
  Future<HttpClientResponse> get(String requestURI, MultiMap headers);

  /**
   * Sends an HTTP GET request to the server with the specified options, specifying a response handler to receive
   * the response
   * @param options  the request options
   * @param responseHandler  the response handler
   */
  void get(RequestOptions options, Handler<AsyncResult<HttpClientResponse>> responseHandler);

  /**
   * Like {@link #get(RequestOptions, Handler)} but returns a {@code Future} of the asynchronous result
   */
  Future<HttpClientResponse> get(RequestOptions options);

  /**
   * Sends an HTTP GET request to the server at the specified host and port, specifying a response handler to receive
   * the response
   * @param port  the port
   * @param host  the host
   * @param requestURI  the relative URI
   * @param responseHandler  the response handler
   */
  void get(int port, String host, String requestURI, Handler<AsyncResult<HttpClientResponse>> responseHandler);

  /**
   * Like {@link #get(int, String, String, Handler)} but returns a {@code Future} of the asynchronous result
   */
  Future<HttpClientResponse> get(int port, String host, String requestURI);

  /**
   * Sends an HTTP GET request to the server at the specified host and default port, specifying a response handler to receive
   * the response
   * @param host  the host
   * @param requestURI  the relative URI
   * @param responseHandler  the response handler
   */
  void get(String host, String requestURI, Handler<AsyncResult<HttpClientResponse>> responseHandler);

  /**
   * Like {@link #get(String, String, Handler)} but returns a {@code Future} of the asynchronous result
   */
  Future<HttpClientResponse> get(String host, String requestURI);

  /**
   * Sends an HTTP GET request  to the server at the default host and port, specifying a response handler to receive
   * the response
   * @param requestURI  the relative URI
   * @param responseHandler  the response handler
   */
  void get(String requestURI, Handler<AsyncResult<HttpClientResponse>> responseHandler);

  /**
   * Like {@link #get(String, Handler)} but returns a {@code Future} of the asynchronous result
   */
  Future<HttpClientResponse> get(String requestURI);

  /**
   * Sends an HTTP POST request to send to the server at the specified host and port, specifying a response handler to receive
   * the response
   * @param port  the port
   * @param host  the host
   * @param requestURI  the relative URI
   * @param headers  the headers
   * @param body  the request body
   * @param responseHandler  the response handler
   */
  void post(int port, String host, String requestURI, MultiMap headers, Buffer body, Handler<AsyncResult<HttpClientResponse>> responseHandler);

  /**
   * Like {@link #post(int, String, String, MultiMap, Buffer, Handler)} but returns a {@code Future} of the asynchronous result
   */
  Future<HttpClientResponse> post(int port, String host, String requestURI, MultiMap headers, Buffer body);

  /**
   * Sends an HTTP POST request to send to the server at the specified host and port, specifying a response handler to receive
   * the response
   * @param port  the port
   * @param host  the host
   * @param requestURI  the relative URI
   * @param headers  the headers
   * @param body  the request body
   * @param responseHandler  the response handler
   */
  void post(int port, String host, String requestURI, MultiMap headers, ReadStream<Buffer> body, Handler<AsyncResult<HttpClientResponse>> responseHandler);

  /**
   * Like {@link #post(int, String, String, MultiMap, ReadStream, Handler)} but returns a {@code Future} of the asynchronous result
   */
  Future<HttpClientResponse> post(int port, String host, String requestURI, MultiMap headers, ReadStream<Buffer> body);

  /**
   * Sends an HTTP POST request to send to the server at the specified host and default port, specifying a response handler to receive
   * the response
   * @param host  the host
   * @param requestURI  the relative URI
   * @param headers  the headers
   * @param body  the request body
   * @param responseHandler  the response handler
   */
  void post(String host, String requestURI, MultiMap headers, Buffer body, Handler<AsyncResult<HttpClientResponse>> responseHandler);

  /**
   * Like {@link #post(String, String, MultiMap, Buffer, Handler)} but returns a {@code Future} of the asynchronous result
   */
  Future<HttpClientResponse> post(String host, String requestURI, MultiMap headers, Buffer body);

  /**
   * Sends an HTTP POST request to send to the server at the specified host and default port, specifying a response handler to receive
   * the response
   * @param host  the host
   * @param requestURI  the relative URI
   * @param headers  the headers
   * @param body  the request body
   * @param responseHandler  the response handler
   */
  void post(String host, String requestURI, MultiMap headers, ReadStream<Buffer> body, Handler<AsyncResult<HttpClientResponse>> responseHandler);

  /**
   * Like {@link #post(String, String, MultiMap, ReadStream, Handler)} but returns a {@code Future} of the asynchronous result
   */
  Future<HttpClientResponse> post(String host, String requestURI, MultiMap headers, ReadStream<Buffer> body);

  /**
   * Sends an HTTP POST request to send to the server at the default host and port, specifying a response handler to receive
   * the response
   * @param requestURI  the relative URI
   * @param headers  the headers
   * @param body  the request body
   * @param responseHandler  the response handler
   */
  void post(String requestURI, MultiMap headers, Buffer body, Handler<AsyncResult<HttpClientResponse>> responseHandler);

  /**
   * Like {@link #post(String, MultiMap, Buffer, Handler)} but returns a {@code Future} of the asynchronous result
   */
  Future<HttpClientResponse> post(String requestURI, MultiMap headers, Buffer body);

  /**
   * Sends an HTTP POST request to send to the server at the default host and port, specifying a response handler to receive
   * the response
   * @param requestURI  the relative URI
   * @param headers  the headers
   * @param body  the request body
   * @param responseHandler  the response handler
   */
  void post(String requestURI, MultiMap headers, ReadStream<Buffer> body, Handler<AsyncResult<HttpClientResponse>> responseHandler);

  /**
   * Like {@link #post(String, MultiMap, ReadStream, Handler)} but returns a {@code Future} of the asynchronous result
   */
  Future<HttpClientResponse> post(String requestURI, MultiMap headers, ReadStream<Buffer> body);

  /**
   * Sends an HTTP POST request to send to the server with the specified options, specifying a response handler to receive
   * the response
   * @param options  the request options
   * @param body  the request body
   * @param responseHandler  the response handler
   */
  void post(RequestOptions options, Buffer body, Handler<AsyncResult<HttpClientResponse>> responseHandler);

  /**
   * Like {@link #post(RequestOptions, Buffer, Handler)} but returns a {@code Future} of the asynchronous result
   */
  Future<HttpClientResponse> post(RequestOptions options, Buffer body);

  /**
   * Sends an HTTP POST request to send to the server with the specified options, specifying a response handler to receive
   * the response
   * @param options  the request options
   * @param body  the request body
   * @param responseHandler  the response handler
   */
  void post(RequestOptions options, ReadStream<Buffer> body, Handler<AsyncResult<HttpClientResponse>> responseHandler);

  /**
   * Like {@link #post(RequestOptions, ReadStream, Handler)} but returns a {@code Future} of the asynchronous result
   */
  Future<HttpClientResponse> post(RequestOptions options, ReadStream<Buffer> body);

  /**
   * Sends an HTTP POST request to send to the server at the specified host and port, specifying a response handler to receive
   * the response
   * @param port  the port
   * @param host  the host
   * @param requestURI  the relative URI
   * @param body  the request body
   * @param responseHandler  the response handler
   */
  void post(int port, String host, String requestURI, Buffer body, Handler<AsyncResult<HttpClientResponse>> responseHandler);

  /**
   * Like {@link #post(int, String, String, Buffer, Handler)} but returns a {@code Future} of the asynchronous result
   */
  Future<HttpClientResponse> post(int port, String host, String requestURI, Buffer body);

  /**
   * Sends an HTTP POST request to send to the server at the specified host and port, specifying a response handler to receive
   * the response
   * @param port  the port
   * @param host  the host
   * @param requestURI  the relative URI
   * @param body  the request body
   * @param responseHandler  the response handler
   */
  void post(int port, String host, String requestURI, ReadStream<Buffer> body, Handler<AsyncResult<HttpClientResponse>> responseHandler);

  /**
   * Like {@link #post(int, String, String, ReadStream, Handler)} but returns a {@code Future} of the asynchronous result
   */
  Future<HttpClientResponse> post(int port, String host, String requestURI, ReadStream<Buffer> body);

  /**
   * Sends an HTTP POST request to send to the server at the specified host and default port, specifying a response handler to receive
   * the response
   * @param host  the host
   * @param requestURI  the relative URI
   * @param body  the request body
   * @param responseHandler  the response handler
   */
  void post(String host, String requestURI, Buffer body, Handler<AsyncResult<HttpClientResponse>> responseHandler);

  /**
   * Like {@link #post(String, String, Buffer, Handler)} but returns a {@code Future} of the asynchronous result
   */
  Future<HttpClientResponse> post(String host, String requestURI, Buffer body);

  /**
   * Sends an HTTP POST request to send to the server at the specified host and default port, specifying a response handler to receive
   * the response
   * @param host  the host
   * @param requestURI  the relative URI
   * @param body  the request body
   * @param responseHandler  the response handler
   */
  void post(String host, String requestURI, ReadStream<Buffer> body, Handler<AsyncResult<HttpClientResponse>> responseHandler);

  /**
   * Like {@link #post(String, String, ReadStream, Handler)} but returns a {@code Future} of the asynchronous result
   */
  Future<HttpClientResponse> post(String host, String requestURI, ReadStream<Buffer> body);

  /**
   * Sends an HTTP POST request to send to the server at the default host and port, specifying a response handler to receive
   * the response
   * @param requestURI  the relative URI
   * @param body  the request body
   * @param responseHandler  the response handler
   */
  void post(String requestURI, Buffer body, Handler<AsyncResult<HttpClientResponse>> responseHandler);

  /**
   * Like {@link #post(String, Buffer, Handler)} but returns a {@code Future} of the asynchronous result
   */
  Future<HttpClientResponse> post(String requestURI, Buffer body);

  /**
   * Sends an HTTP POST request to send to the server at the default host and port, specifying a response handler to receive
   * the response
   * @param requestURI  the relative URI
   * @param body  the request body
   * @param responseHandler  the response handler
   */
  void post(String requestURI, ReadStream<Buffer> body, Handler<AsyncResult<HttpClientResponse>> responseHandler);

  /**
   * Like {@link #post(String, ReadStream, Handler)} but returns a {@code Future} of the asynchronous result
   */
  Future<HttpClientResponse> post(String requestURI, ReadStream<Buffer> body);

  /**
   * Sends an HTTP PUT request to send to the server at the specified host and port, specifying a response handler to receive
   * the response
   * @param port  the port
   * @param host  the host
   * @param requestURI  the relative URI
   * @param headers  the headers
   * @param body  the request body
   * @param responseHandler  the response handler
   */
  void put(int port, String host, String requestURI, MultiMap headers, Buffer body, Handler<AsyncResult<HttpClientResponse>> responseHandler);

  /**
   * Like {@link #put(int, String, String, MultiMap, Buffer, Handler)} but returns a {@code Future} of the asynchronous result
   */
  Future<HttpClientResponse> put(int port, String host, String requestURI, MultiMap headers, Buffer body);

  /**
   * Sends an HTTP PUT request to send to the server at the specified host and port, specifying a response handler to receive
   * the response
   * @param port  the port
   * @param host  the host
   * @param requestURI  the relative URI
   * @param headers  the headers
   * @param body  the request body
   * @param responseHandler  the response handler
   */
  void put(int port, String host, String requestURI, MultiMap headers, ReadStream<Buffer> body, Handler<AsyncResult<HttpClientResponse>> responseHandler);

  /**
   * Like {@link #put(int, String, String, MultiMap, ReadStream, Handler)} but returns a {@code Future} of the asynchronous result
   */
  Future<HttpClientResponse> put(int port, String host, String requestURI, MultiMap headers, ReadStream<Buffer> body);

  /**
   * Sends an HTTP PUT request to send to the server at the specified host and default port, specifying a response handler to receive
   * the response
   * @param host  the host
   * @param requestURI  the relative URI
   * @param headers  the headers
   * @param body  the request body
   * @param responseHandler  the response handler
   */
  void put(String host, String requestURI, MultiMap headers, Buffer body, Handler<AsyncResult<HttpClientResponse>> responseHandler);

  /**
   * Like {@link #put(String, String, MultiMap, Buffer, Handler)} but returns a {@code Future} of the asynchronous result
   */
  Future<HttpClientResponse> put(String host, String requestURI, MultiMap headers, Buffer body);

  /**
   * Sends an HTTP PUT request to send to the server at the specified host and default port, specifying a response handler to receive
   * the response
   * @param host  the host
   * @param requestURI  the relative URI
   * @param headers  the headers
   * @param body  the request body
   * @param responseHandler  the response handler
   */
  void put(String host, String requestURI, MultiMap headers, ReadStream<Buffer> body, Handler<AsyncResult<HttpClientResponse>> responseHandler);

  /**
   * Like {@link #put(String, String, MultiMap, ReadStream, Handler)} but returns a {@code Future} of the asynchronous result
   */
  Future<HttpClientResponse> put(String host, String requestURI, MultiMap headers, ReadStream<Buffer> body);

  /**
   * Sends an HTTP PUT request to send to the server at the default host and port, specifying a response handler to receive
   * the response
   * @param requestURI  the relative URI
   * @param headers  the headers
   * @param body  the request body
   * @param responseHandler  the response handler
   */
  void put(String requestURI, MultiMap headers, Buffer body, Handler<AsyncResult<HttpClientResponse>> responseHandler);

  /**
   * Like {@link #put(String, MultiMap, Buffer, Handler)} but returns a {@code Future} of the asynchronous result
   */
  Future<HttpClientResponse> put(String requestURI, MultiMap headers, Buffer body);

  /**
   * Sends an HTTP PUT request to send to the server at the default host and port, specifying a response handler to receive
   * the response
   * @param requestURI  the relative URI
   * @param headers  the headers
   * @param body  the request body
   * @param responseHandler  the response handler
   */
  void put(String requestURI, MultiMap headers, ReadStream<Buffer> body, Handler<AsyncResult<HttpClientResponse>> responseHandler);

  /**
   * Like {@link #put(String, MultiMap, ReadStream, Handler)} but returns a {@code Future} of the asynchronous result
   */
  Future<HttpClientResponse> put(String requestURI, MultiMap headers, ReadStream<Buffer> body);

  /**
   * Sends an HTTP PUT request to send to the server with the specified options, specifying a response handler to receive
   * the response
   * @param options  the request options
   * @param body  the request body
   * @param responseHandler  the response handler
   */
  void put(RequestOptions options, Buffer body, Handler<AsyncResult<HttpClientResponse>> responseHandler);

  /**
   * Like {@link #put(RequestOptions, Buffer, Handler)} but returns a {@code Future} of the asynchronous result
   */
  Future<HttpClientResponse> put(RequestOptions options, Buffer body);

  /**
   * Sends an HTTP PUT request to send to the server with the specified options, specifying a response handler to receive
   * the response
   * @param options  the request options
   * @param body  the request body
   * @param responseHandler  the response handler
   */
  void put(RequestOptions options, ReadStream<Buffer> body, Handler<AsyncResult<HttpClientResponse>> responseHandler);

  /**
   * Like {@link #put(RequestOptions, ReadStream, Handler)} but returns a {@code Future} of the asynchronous result
   */
  Future<HttpClientResponse> put(RequestOptions options, ReadStream<Buffer> body);

  /**
   * Sends an HTTP PUT request to send to the server at the specified host and port, specifying a response handler to receive
   * the response
   * @param port  the port
   * @param host  the host
   * @param requestURI  the relative URI
   * @param body  the request body
   * @param responseHandler  the response handler
   */
  void put(int port, String host, String requestURI, Buffer body, Handler<AsyncResult<HttpClientResponse>> responseHandler);

  /**
   * Like {@link #put(int, String, String, Buffer, Handler)} but returns a {@code Future} of the asynchronous result
   */
  Future<HttpClientResponse> put(int port, String host, String requestURI, Buffer body);

  /**
   * Sends an HTTP PUT request to send to the server at the specified host and port, specifying a response handler to receive
   * the response
   * @param port  the port
   * @param host  the host
   * @param requestURI  the relative URI
   * @param body  the request body
   * @param responseHandler  the response handler
   */
  void put(int port, String host, String requestURI, ReadStream<Buffer> body, Handler<AsyncResult<HttpClientResponse>> responseHandler);

  /**
   * Like {@link #put(int, String, String, ReadStream, Handler)} but returns a {@code Future} of the asynchronous result
   */
  Future<HttpClientResponse> put(int port, String host, String requestURI, ReadStream<Buffer> body);

  /**
   * Sends an HTTP PUT request to send to the server at the specified host and default port, specifying a response handler to receive
   * the response
   * @param host  the host
   * @param requestURI  the relative URI
   * @param body  the request body
   * @param responseHandler  the response handler
   */
  void put(String host, String requestURI, Buffer body, Handler<AsyncResult<HttpClientResponse>> responseHandler);

  /**
   * Like {@link #put(String, String, Buffer, Handler)} but returns a {@code Future} of the asynchronous result
   */
  Future<HttpClientResponse> put(String host, String requestURI, Buffer body);

  /**
   * Sends an HTTP PUT request to send to the server at the specified host and default port, specifying a response handler to receive
   * the response
   * @param host  the host
   * @param requestURI  the relative URI
   * @param body  the request body
   * @param responseHandler  the response handler
   */
  void put(String host, String requestURI, ReadStream<Buffer> body, Handler<AsyncResult<HttpClientResponse>> responseHandler);

  /**
   * Like {@link #put(String, String, ReadStream, Handler)} but returns a {@code Future} of the asynchronous result
   */
  Future<HttpClientResponse> put(String host, String requestURI, ReadStream<Buffer> body);

  /**
   * Sends an HTTP PUT request to send to the server at the default host and port, specifying a response handler to receive
   * the response
   * @param requestURI  the relative URI
   * @param body  the request body
   * @param responseHandler  the response handler
   */
  void put(String requestURI, Buffer body, Handler<AsyncResult<HttpClientResponse>> responseHandler);

  /**
   * Like {@link #put(String, Buffer, Handler)} but returns a {@code Future} of the asynchronous result
   */
  Future<HttpClientResponse> put(String requestURI, Buffer body);

  /**
   * Sends an HTTP PUT request to send to the server at the default host and port, specifying a response handler to receive
   * the response
   * @param requestURI  the relative URI
   * @param body  the request body
   * @param responseHandler  the response handler
   */
  void put(String requestURI, ReadStream<Buffer> body, Handler<AsyncResult<HttpClientResponse>> responseHandler);

  /**
   * Like {@link #put(String, ReadStream, Handler)} but returns a {@code Future} of the asynchronous result
   */
  Future<HttpClientResponse> put(String requestURI, ReadStream<Buffer> body);

  /**
   * Sends an HTTP HEAD request to the server at the specified host and port, specifying a response handler to receive
   * the response
   * @param port  the port
   * @param host  the host
   * @param requestURI  the relative URI
   * @param headers  the headers
   * @param responseHandler  the response handler
   */
  void head(int port, String host, String requestURI, MultiMap headers, Handler<AsyncResult<HttpClientResponse>> responseHandler);

  /**
   * Like {@link #head(int, String, String, MultiMap, Handler)} but returns a {@code Future} of the asynchronous result
   */
  Future<HttpClientResponse> head(int port, String host, String requestURI, MultiMap headers);

  /**
   * Sends an HTTP HEAD request to the server at the specified host and default port, specifying a response handler to receive
   * the response
   * @param host  the host
   * @param requestURI  the relative URI
   * @param headers  the headers
   * @param responseHandler  the response handler
   */
  void head(String host, String requestURI, MultiMap headers, Handler<AsyncResult<HttpClientResponse>> responseHandler);

  /**
   * Like {@link #head(String, String, MultiMap, Handler)} but returns a {@code Future} of the asynchronous result
   */
  Future<HttpClientResponse> head(String host, String requestURI, MultiMap headers);

  /**
   * Sends an HTTP HEAD request  to the server at the default host and port, specifying a response handler to receive
   * the response
   * @param requestURI  the relative URI
   * @param headers  the headers
   * @param responseHandler  the response handler
   */
  void head(String requestURI, MultiMap headers, Handler<AsyncResult<HttpClientResponse>> responseHandler);

  /**
   * Like {@link #head(String, MultiMap, Handler)} but returns a {@code Future} of the asynchronous result
   */
  Future<HttpClientResponse> head(String requestURI, MultiMap headers);

  /**
   * Sends an HTTP HEAD request to the server with the specified options, specifying a response handler to receive
   * the response
   * @param options  the request options
   * @param responseHandler  the response handler
   */
  void head(RequestOptions options, Handler<AsyncResult<HttpClientResponse>> responseHandler);

  /**
   * Like {@link #head(RequestOptions, Handler)} but returns a {@code Future} of the asynchronous result
   */
  Future<HttpClientResponse> head(RequestOptions options);

  /**
   * Sends an HTTP HEAD request to the server at the specified host and port, specifying a response handler to receive
   * the response
   * @param port  the port
   * @param host  the host
   * @param requestURI  the relative URI
   * @param responseHandler  the response handler
   */
  void head(int port, String host, String requestURI, Handler<AsyncResult<HttpClientResponse>> responseHandler);

  /**
   * Like {@link #head(int, String, String, Handler)} but returns a {@code Future} of the asynchronous result
   */
  Future<HttpClientResponse> head(int port, String host, String requestURI);

  /**
   * Sends an HTTP HEAD request to the server at the specified host and default port, specifying a response handler to receive
   * the response
   * @param host  the host
   * @param requestURI  the relative URI
   * @param responseHandler  the response handler
   */
  void head(String host, String requestURI, Handler<AsyncResult<HttpClientResponse>> responseHandler);

  /**
   * Like {@link #head(String, String, Handler)} but returns a {@code Future} of the asynchronous result
   */
  Future<HttpClientResponse> head(String host, String requestURI);

  /**
   * Sends an HTTP HEAD request  to the server at the default host and port, specifying a response handler to receive
   * the response
   * @param requestURI  the relative URI
   * @param responseHandler  the response handler
   */
  void head(String requestURI, Handler<AsyncResult<HttpClientResponse>> responseHandler);

  /**
   * Like {@link #head(String, Handler)} but returns a {@code Future} of the asynchronous result
   */
  Future<HttpClientResponse> head(String requestURI);

  /**
   * Sends an HTTP OPTIONS request to send to the server at the specified host and port, specifying a response handler to receive
   * the response
   * @param port  the port
   * @param host  the host
   * @param requestURI  the relative URI
   * @param headers  the headers
   * @param responseHandler  the response handler
   */
  void options(int port, String host, String requestURI, MultiMap headers, Handler<AsyncResult<HttpClientResponse>> responseHandler);

  /**
   * Like {@link #options(int, String, String, MultiMap, Handler)} but returns a {@code Future} of the asynchronous result
   */
  Future<HttpClientResponse> options(int port, String host, String requestURI, MultiMap headers);

  /**
   * Sends an HTTP OPTIONS request to send to the server at the specified host and default port, specifying a response handler to receive
   * the response
   * @param host  the host
   * @param requestURI  the relative URI
   * @param headers  the headers
   * @param responseHandler  the response handler
   */
  void options(String host, String requestURI, MultiMap headers, Handler<AsyncResult<HttpClientResponse>> responseHandler);

  /**
   * Like {@link #options(String, String, MultiMap, Handler)} but returns a {@code Future} of the asynchronous result
   */
  Future<HttpClientResponse> options(String host, String requestURI, MultiMap headers);

  /**
   * Sends an HTTP OPTIONS request to send to the server at the default host and port, specifying a response handler to receive
   * the response
   * @param requestURI  the relative URI
   * @param headers  the headers
   * @param responseHandler  the response handler
   */
  void options(String requestURI, MultiMap headers, Handler<AsyncResult<HttpClientResponse>> responseHandler);

  /**
   * Like {@link #options(String, MultiMap, Handler)} but returns a {@code Future} of the asynchronous result
   */
  Future<HttpClientResponse> options(String requestURI, MultiMap headers);

  /**
   * Sends an HTTP OPTIONS request to send to the server with the specified options, specifying a response handler to receive
   * the response
   * @param options  the request options
   * @param responseHandler  the response handler
   */
  void options(RequestOptions options, Handler<AsyncResult<HttpClientResponse>> responseHandler);

  /**
   * Like {@link #options(RequestOptions, Handler)} but returns a {@code Future} of the asynchronous result
   */
  Future<HttpClientResponse> options(RequestOptions options);

  /**
   * Sends an HTTP OPTIONS request to send to the server at the specified host and port, specifying a response handler to receive
   * the response
   * @param port  the port
   * @param host  the host
   * @param requestURI  the relative URI
   * @param responseHandler  the response handler
   */
  void options(int port, String host, String requestURI, Handler<AsyncResult<HttpClientResponse>> responseHandler);

  /**
   * Like {@link #options(int, String, String, Handler)} but returns a {@code Future} of the asynchronous result
   */
  Future<HttpClientResponse> options(int port, String host, String requestURI);

  /**
   * Sends an HTTP OPTIONS request to send to the server at the specified host and default port, specifying a response handler to receive
   * the response
   * @param host  the host
   * @param requestURI  the relative URI
   * @param responseHandler  the response handler
   */
  void options(String host, String requestURI, Handler<AsyncResult<HttpClientResponse>> responseHandler);

  /**
   * Like {@link #options(String, String, Handler)} but returns a {@code Future} of the asynchronous result
   */
  Future<HttpClientResponse> options(String host, String requestURI);

  /**
   * Sends an HTTP OPTIONS request to send to the server at the default host and port, specifying a response handler to receive
   * the response
   * @param requestURI  the relative URI
   * @param responseHandler  the response handler
   */
  void options(String requestURI, Handler<AsyncResult<HttpClientResponse>> responseHandler);

  /**
   * Like {@link #options(String, Handler)} but returns a {@code Future} of the asynchronous result
   */
  Future<HttpClientResponse> options(String requestURI);

  /**
   * Sends an HTTP DELETE request to send to the server at the specified host and port, specifying a response handler to receive
   * the response
   * @param port  the port
   * @param host  the host
   * @param requestURI  the relative URI
   * @param headers  the headers
   * @param responseHandler  the response handler
   */
  void delete(int port, String host, String requestURI, MultiMap headers, Handler<AsyncResult<HttpClientResponse>> responseHandler);

  /**
   * Like {@link #delete(int, String, String, MultiMap, Handler)} but returns a {@code Future} of the asynchronous result
   */
  Future<HttpClientResponse> delete(int port, String host, String requestURI, MultiMap headers);

  /**
   * Sends an HTTP DELETE request to send to the server at the specified host and default port, specifying a response handler to receive
   * the response
   * @param host  the host
   * @param requestURI  the relative URI
   * @param headers  the headers
   * @param responseHandler  the response handler
   */
  void delete(String host, String requestURI, MultiMap headers, Handler<AsyncResult<HttpClientResponse>> responseHandler);

  /**
   * Like {@link #delete(String, String, MultiMap, Handler)} but returns a {@code Future} of the asynchronous result
   */
  Future<HttpClientResponse> delete(String host, String requestURI, MultiMap headers);

  /**
   * Sends an HTTP DELETE request to send to the server at the default host and port, specifying a response handler to receive
   * the response
   * @param requestURI  the relative URI
   * @param headers  the headers
   * @param responseHandler  the response handler
   */
  void delete(String requestURI, MultiMap headers, Handler<AsyncResult<HttpClientResponse>> responseHandler);

  /**
   * Like {@link #delete(String, MultiMap, Handler)} but returns a {@code Future} of the asynchronous result
   */
  Future<HttpClientResponse> delete(String requestURI, MultiMap headers);

  /**
   * Sends an HTTP DELETE request to send to the server with the specified options, specifying a response handler to receive
   * the response
   * @param options  the request options
   * @param responseHandler  the response handler
   */
  void delete(RequestOptions options, Handler<AsyncResult<HttpClientResponse>> responseHandler);

  /**
   * Like {@link #delete(RequestOptions, Handler)} but returns a {@code Future} of the asynchronous result
   */
  Future<HttpClientResponse> delete(RequestOptions options);

  /**
   * Sends an HTTP DELETE request to send to the server at the specified host and port, specifying a response handler to receive
   * the response
   * @param port  the port
   * @param host  the host
   * @param requestURI  the relative URI
   * @param responseHandler  the response handler
   */
  void delete(int port, String host, String requestURI, Handler<AsyncResult<HttpClientResponse>> responseHandler);

  /**
   * Like {@link #delete(int, String, String, Handler)} but returns a {@code Future} of the asynchronous result
   */
  Future<HttpClientResponse> delete(int port, String host, String requestURI);

  /**
   * Sends an HTTP DELETE request to send to the server at the specified host and default port, specifying a response handler to receive
   * the response
   * @param host  the host
   * @param requestURI  the relative URI
   * @param responseHandler  the response handler
   */
  void delete(String host, String requestURI, Handler<AsyncResult<HttpClientResponse>> responseHandler);

  /**
   * Like {@link #delete(String, String, Handler)} but returns a {@code Future} of the asynchronous result
   */
  Future<HttpClientResponse> delete(String host, String requestURI);

  /**
   * Sends an HTTP DELETE request to send to the server at the default host and port, specifying a response handler to receive
   * the response
   * @param requestURI  the relative URI
   * @param responseHandler  the response handler
   */
  void delete(String requestURI, Handler<AsyncResult<HttpClientResponse>> responseHandler);

  /**
   * Like {@link #delete(String, Handler)} but returns a {@code Future} of the asynchronous result
   */
  Future<HttpClientResponse> delete(String requestURI);

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
  void close(Handler<AsyncResult<Void>> handler);

  /**
   * Like {@link #close(Handler)} but returns a {@code Future} of the asynchronous result
   */
  Future<Void> close();

}
