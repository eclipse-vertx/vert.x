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
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.net.ClientSSLOptions;
import io.vertx.core.net.SSLOptions;

import java.util.concurrent.TimeUnit;

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
public interface HttpClient extends io.vertx.core.metrics.Measured {

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
  default Future<HttpClientRequest> request(HttpMethod method, int port, String host, String requestURI) {
    return request(new RequestOptions().setMethod(method).setPort(port).setHost(host).setURI(requestURI));
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

  /**
   * Close the client immediately ({@code close(0, TimeUnit.SECONDS}).
   *
   * @return a future notified when the client is closed
   */
  default Future<Void> close() {
    return shutdown(0, TimeUnit.SECONDS);
  }

  /**
   * Initiate the close sequence with a 30 seconds timeout.
   *
   * see {@link #shutdown(long, TimeUnit)}
   */
  default Future<Void> shutdown() {
    return shutdown(30, TimeUnit.SECONDS);
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
  Future<Void> shutdown(long timeout, TimeUnit timeUnit);

  /**
   * <p>Update the client with new SSL {@code options}, the update happens if the options object is valid and different
   * from the existing options object.
   *
   * <p>The boolean succeeded future result indicates whether the update occurred.
   *
   * @param options the new SSL options
   * @return a future signaling the update success
   */
  default Future<Boolean> updateSSLOptions(ClientSSLOptions options) {
    return updateSSLOptions(options, false);
  }

  /**
   * <p>Update the client with new SSL {@code options}, the update happens if the options object is valid and different
   * from the existing options object.
   *
   * <p>The {@code options} object is compared using its {@code equals} method against the existing options to prevent
   * an update when the objects are equals since loading options can be costly, this can happen for share TCP servers.
   * When object are equals, setting {@code force} to {@code true} forces the update.
   *
   * <p>The boolean succeeded future result indicates whether the update occurred.
   *
   * @param options the new SSL options
   * @param force force the update when options are equals
   * @return a future signaling the update success
   */
  Future<Boolean> updateSSLOptions(ClientSSLOptions options, boolean force);
}
