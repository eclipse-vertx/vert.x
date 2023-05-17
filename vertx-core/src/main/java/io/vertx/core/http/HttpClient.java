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

import io.vertx.codegen.annotations.VertxGen;
import io.vertx.core.Future;

import java.util.concurrent.TimeUnit;
import java.util.function.Function;

/**
 * The API to interacts with an HTTP server.
 *
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
@VertxGen(concrete = false)
public interface HttpClient {

  /**
   * Create an HTTP request to send to the server with the default host and port of the client.
   *
   * @return a future notified when the request is ready to be sent
   */
  default Future<HttpClientRequest> request() {
    return request(new RequestOptions());
  }

  /**
   * Create an HTTP request to send to the server.
   *
   * @param options    the request options
   * @return a future notified when the request is ready to be sent
   */
  Future<HttpClientRequest> request(RequestOptions options);

  default <T> Future<T> request(RequestOptions options, Function<HttpClientRequest, Future<T>> handler) {
    return request(options).compose(handler);
  }

  /**
   * Create an HTTP request to send to the server at the {@code host} and {@code port}.
   *
   * @param method     the HTTP method
   * @param port       the port
   * @param host       the host
   * @param requestURI the relative URI
   * @return a future notified when the request is ready to be sent
   */
  default Future<HttpClientRequest> request(HttpMethod method, int port, String host, String requestURI) {
    return request(new RequestOptions().setMethod(method).setPort(port).setHost(host).setURI(requestURI));
  }

  default <T> Future<T> request(HttpMethod method, int port, String host, String requestURI, Function<HttpClientRequest, Future<T>> handler) {
    return request(method, port, host, requestURI).compose(handler);
  }

  /**
   * Create an HTTP request to send to the server at the {@code host} and default port.
   *
   * @param method     the HTTP method
   * @param host       the host
   * @param requestURI the relative URI
   * @return a future notified when the request is ready to be sent
   */
  default Future<HttpClientRequest> request(HttpMethod method, String host, String requestURI) {
    return request(new RequestOptions().setMethod(method).setHost(host).setURI(requestURI));
  }

  default <T> Future<T> request(HttpMethod method, String host, String requestURI , Function<HttpClientRequest, Future<T>> handler) {
    return request(method, host, requestURI).compose(handler);
  }

  /**
   * Create an HTTP request to send to the server at the default host and port.
   *
   * @param method     the HTTP method
   * @param requestURI the relative URI
   * @return a future notified when the request is ready to be sent
   */
  default Future<HttpClientRequest> request(HttpMethod method, String requestURI) {
    return request(new RequestOptions().setMethod(method).setURI(requestURI));
  }

  default <T> Future<T> request(HttpMethod method, String requestURI, Function<HttpClientRequest, Future<T>> handler) {
    return request(method, requestURI).compose(handler);
  }

  /**
   * Shutdown with a 30 seconds timeout ({@code shutdown(30, TimeUnit.SECONDS)}).
   *
   * @return a future completed when shutdown has completed
   */
  default Future<Void> shutdown() {
    return shutdown(30, TimeUnit.SECONDS);
  }

  /**
   * Close immediately ({@code shutdown(0, TimeUnit.SECONDS}).
   *
   * @return a future notified when the client is closed
   */
  default Future<Void> close() {
    return shutdown(0, TimeUnit.SECONDS);
  }

  /**
   * Initiate the client shutdown sequence.
   *
   * <p> Connections are taken out of service and closed when all inflight requests are processed, client connection are
   * immediately removed from the pool. When all connections are closed the client is closed. When the {@code timeout}
   * expires, all unclosed connections are immediately closed.
   *
   * <ul>
   *   <li>HTTP/2 connections will send a go away frame immediately to signal the other side the connection will close</li>
   *   <li>HTTP/1.x client connection will be closed after the current response is received</li>
   * </ul>
   *
   * @param timeout the amount of time after which all resources are forcibly closed
   * @param unit the of the timeout
   * @return a future notified when the client is closed
   */
  Future<Void> shutdown(long timeout, TimeUnit unit);

}
