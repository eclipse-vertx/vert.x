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

package org.vertx.java.core.http;

import org.vertx.java.core.ClientSSLSupport;
import org.vertx.java.core.Handler;
import org.vertx.java.core.MultiMap;
import org.vertx.java.core.TCPSupport;

import java.util.Set;

/**
 * An HTTP client that maintains a pool of connections to a specific host, at a specific port. The client supports
 * pipelining of requests.<p>
 * As well as HTTP requests, the client can act as a factory for {@code WebSocket websockets}.<p>
 * If an instance is instantiated from an event loop then the handlers
 * of the instance will always be called on that same event loop.
 * If an instance is instantiated from some other arbitrary Java thread (i.e. when running embedded) then
 * and event loop will be assigned to the instance and used when any of its handlers
 * are called.<p>
 * Instances of HttpClient are thread-safe.<p>
 *
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public interface HttpClient extends ClientSSLSupport<HttpClient>, TCPSupport<HttpClient> {

  /**
   * Set an exception handler
   *
   * @return A reference to this, so multiple invocations can be chained together.
   */
  HttpClient exceptionHandler(Handler<Throwable> handler);

  /**
   * Set the maximum pool size<p>
   * The client will maintain up to {@code maxConnections} HTTP connections in an internal pool<p>
   * @return A reference to this, so multiple invocations can be chained together.
   */
  HttpClient setMaxPoolSize(int maxConnections);

  /**
   * Returns the maximum number of connections in the pool
   */
  int getMaxPoolSize();

  /**
   * Set the maximum waiter queue size<p>
   * The client will keep up to {@code maxWaiterQueueSize} requests in an internal waiting queue<p>
   * @return A reference to this, so multiple invocations can be chained together.
   */
  HttpClient setMaxWaiterQueueSize(int maxWaiterQueueSize);

  /**
   * Returns the maximum number of waiting requests
   */
  int getMaxWaiterQueueSize();

  /**
   * Set the maximum outstanding request size for every connection<p>
   * The connection will keep up to {@code connectionMaxOutstandingRequestCount} outstanding requests<p>
   * @return A reference to this, so multiple invocations can be chained together.
   */
  HttpClient setConnectionMaxOutstandingRequestCount(int connectionMaxOutstandingRequestCount);

  /**
   * Returns the maximum number of outstanding requests for each connection
   */
  int getConnectionMaxOutstandingRequestCount();
  
  /**
   * If {@code keepAlive} is {@code true} then the connection will be returned to the pool after the request has ended (if
   * {@code pipelining} is {@code true}), or after both request and response have ended (if {@code pipelining} is
   * {@code false}). In this manner, many HTTP requests can reuse the same HTTP connection.
   * Keep alive connections will not be closed until the {@link #close() close()} method is invoked.<p>
   * If {@code keepAlive} is {@code false} then a new connection will be created for each request and it won't ever go in the pool,
   * the connection will get closed after the response has been received. Even with no keep alive,
   * the client will not allow more than {@link #getMaxPoolSize()} connections to be created at any one time. <p>
   * @return A reference to this, so multiple invocations can be chained together.
   */
  HttpClient setKeepAlive(boolean keepAlive);

  /**
   *
   * @return Is the client keep alive?
   */
  boolean isKeepAlive();

  /**
   * If {@code pipelining} is {@code true} and {@code keepAlive} is also {@code true} then, after the request has ended the
   * connection will be returned to the pool where it can be used by another request. In this manner, many HTTP requests can
   * be pipelined over an HTTP connection. If {@code pipelining} is {@code false} and {@code keepAlive} is {@code true}, then
   * the connection will be returned to the pool when both request and response have ended. If {@code keepAlive} is false,
   * then pipelining value is ignored.
   * @return A reference to this, so multiple invocations can be chained together.
   */
  HttpClient setPipelining(boolean pipelining);

  /**
   *
   * @return Is enabled HTTP pipelining?
   */
  boolean isPipelining();

  /**
   * Set the port that the client will attempt to connect to the server on to {@code port}. The default value is
   * {@code 80}
   * @return A reference to this, so multiple invocations can be chained together.
   */
  HttpClient setPort(int port);

  /**
   *
   * @return The port
   */
  int getPort();

  /**
   * Set the host that the client will attempt to connect to the server on to {@code host}. The default value is
   * {@code localhost}
   * @return A reference to this, so multiple invocations can be chained together.
   */
  HttpClient setHost(String host);

  /**
   *
   * @return The host
   */
  String getHost();

  /**
   * Attempt to connect an HTML5 websocket to the specified URI<p>
   * The connect is done asynchronously and {@code wsConnect} is called back with the websocket
   */
  HttpClient connectWebsocket(String uri, Handler<WebSocket> wsConnect);

  /**
   * Attempt to connect an HTML5 websocket to the specified URI<p>
   * This version of the method allows you to specify the websockets version using the {@code wsVersion parameter}
   * The connect is done asynchronously and {@code wsConnect} is called back with the websocket
   */
  HttpClient connectWebsocket(String uri, WebSocketVersion wsVersion, Handler<WebSocket> wsConnect);

  /**
   * Attempt to connect an HTML5 websocket to the specified URI<p>
   * This version of the method allows you to specify the websockets version using the {@code wsVersion parameter}
   * You can also specify a set of headers to append to the upgrade request
   * The connect is done asynchronously and {@code wsConnect} is called back with the websocket
   */
  HttpClient connectWebsocket(String uri, WebSocketVersion wsVersion, MultiMap headers, Handler<WebSocket> wsConnect);

  /**
   * Attempt to connect an HTML5 websocket to the specified URI<p>
   * This version of the method allows you to specify the websockets version using the {@code wsVersion parameter}
   * You can also specify a set of headers to append to the upgrade request and specify the supported subprotocols.
   * The connect is done asynchronously and {@code wsConnect} is called back with the websocket
   */
  HttpClient connectWebsocket(String uri, WebSocketVersion wsVersion, MultiMap headers, Set<String> subprotocols, Handler<WebSocket> wsConnect);

  /**
   * This is a quick version of the {@link #get(String, org.vertx.java.core.Handler)}
   * method where you do not want to do anything with the request before sending.<p>
   * Normally with any of the HTTP methods you create the request then when you are ready to send it you call
   * {@link HttpClientRequest#end()} on it. With this method the request is immediately sent.<p>
   * When an HTTP response is received from the server the {@code responseHandler} is called passing in the response.
   */
  HttpClient getNow(String uri, Handler<HttpClientResponse> responseHandler);

  /**
   * This method works in the same manner as {@link #getNow(String, org.vertx.java.core.Handler)},
   * except that it allows you specify a set of {@code headers} that will be sent with the request.
   */
  HttpClient getNow(String uri, MultiMap headers, Handler<HttpClientResponse> responseHandler);

  /**
   * This method returns an {@link HttpClientRequest} instance which represents an HTTP OPTIONS request with the specified {@code uri}.<p>
   * When an HTTP response is received from the server the {@code responseHandler} is called passing in the response.
   */
  HttpClientRequest options(String uri, Handler<HttpClientResponse> responseHandler);

  /**
   * This method returns an {@link HttpClientRequest} instance which represents an HTTP GET request with the specified {@code uri}.<p>
   * When an HTTP response is received from the server the {@code responseHandler} is called passing in the response.
   */
  HttpClientRequest get(String uri, Handler<HttpClientResponse> responseHandler);

  /**
   * This method returns an {@link HttpClientRequest} instance which represents an HTTP HEAD request with the specified {@code uri}.<p>
   * When an HTTP response is received from the server the {@code responseHandler} is called passing in the response.
   */
  HttpClientRequest head(String uri, Handler<HttpClientResponse> responseHandler);

  /**
   * This method returns an {@link HttpClientRequest} instance which represents an HTTP POST request with the specified {@code uri}.<p>
   * When an HTTP response is received from the server the {@code responseHandler} is called passing in the response.
   */
  HttpClientRequest post(String uri, Handler<HttpClientResponse> responseHandler);

  /**
   * This method returns an {@link HttpClientRequest} instance which represents an HTTP PUT request with the specified {@code uri}.<p>
   * When an HTTP response is received from the server the {@code responseHandler} is called passing in the response.
   */
  HttpClientRequest put(String uri, Handler<HttpClientResponse> responseHandler);

  /**
   * This method returns an {@link HttpClientRequest} instance which represents an HTTP DELETE request with the specified {@code uri}.<p>
   * When an HTTP response is received from the server the {@code responseHandler} is called passing in the response.
   */
  HttpClientRequest delete(String uri, Handler<HttpClientResponse> responseHandler);

  /**
   * This method returns an {@link HttpClientRequest} instance which represents an HTTP TRACE request with the specified {@code uri}.<p>
   * When an HTTP response is received from the server the {@code responseHandler} is called passing in the response.
   */
  HttpClientRequest trace(String uri, Handler<HttpClientResponse> responseHandler);

  /**
   * This method returns an {@link HttpClientRequest} instance which represents an HTTP CONNECT request with the specified {@code uri}.<p>
   * When an HTTP response is received from the server the {@code responseHandler} is called passing in the response.
   *
   * Because of the nature of CONNECT the connection is automatically upgraded to raw TCP if a response code of 200 is received from the
   * remote peer. In this case you are able to get hold of the raw TCP socket via calling {@link HttpClientResponse#netSocket()}.
   */
  HttpClientRequest connect(String uri, Handler<HttpClientResponse> responseHandler);

  /**
   * This method returns an {@link HttpClientRequest} instance which represents an HTTP PATCH request with the specified {@code uri}.<p>
   * When an HTTP response is received from the server the {@code responseHandler} is called passing in the response.
   */
  HttpClientRequest patch(String uri, Handler<HttpClientResponse> responseHandler);

  /**
   * This method returns an {@link HttpClientRequest} instance which represents an HTTP request with the specified {@code uri}.
   * The specific HTTP method (e.g. GET, POST, PUT etc) is specified using the parameter {@code method}<p>
   * When an HTTP response is received from the server the {@code responseHandler} is called passing in the response.
   */
  HttpClientRequest request(String method, String uri, Handler<HttpClientResponse> responseHandler);

  /**
   * Close the HTTP client. This will cause any pooled HTTP connections to be closed.
   */
  void close();

  /**
   * If {@code verifyHost} is {@code true}, then the client will try to validate the remote server's certificate
   * hostname against the requested host. Should default to 'true'.
   * This method should only be used in SSL mode, i.e. after {@link #setSSL(boolean)} has been set to {@code true}.
   * @return A reference to this, so multiple invocations can be chained together.
   */
  HttpClient setVerifyHost(boolean verifyHost);

  /**
   *
   * @return true if this client will validate the remote server's certificate hostname against the requested host
   */
  boolean isVerifyHost();

  /**
   * Set the connect timeout in milliseconds.
   * @return a reference to this so multiple method calls can be chained together
   */
  HttpClient setConnectTimeout(int timeout);

  /**
   *
   * @return The connect timeout in milliseconds
   */
  int getConnectTimeout();

  /**
   * Set if the {@link HttpClient} should try to use compression.
   */
  HttpClient setTryUseCompression(boolean tryUseCompression);

  /**
   * Returns {@code true} if the {@link HttpClient} should try to use compression.
   */
  boolean getTryUseCompression();

  /**
   * Sets the maximum websocket frame size in bytes. Default is 65536 bytes.
   * @param maxSize The size in bytes
   */
  HttpClient setMaxWebSocketFrameSize(int maxSize);

  /**
   * Get the  maximum websocket frame size in bytes.
   */
  int getMaxWebSocketFrameSize();

}
