package org.vertx.java.core.http;

import org.vertx.java.core.Handler;

import java.util.Map;

/**
 * An HTTP client designed to be shared and that maintains a pool of connections to a specific host, at a specific port.
 * The client supports pipelining of requests.<p>
 * As well as HTTP requests, the client can act as a factory for {@code WebSocket websockets}.<p>
 * If an instance is instantiated from an event loop then the handlers
 * of the instance will always be called on that same event loop.
 * If an instance is instantiated from some other arbitrary Java thread then
 * and event loop will be assigned to the instance and used when any of its handlers
 * are called.<p>
 * Instances cannot be used from worker verticles
 *
 * @author Nathan Pahucki, <a href="mailto:nathan@gmail.com"> npahucki@gmail.com</a>
 */
public interface SharedHttpClient {

  /**
   * Returns the maximum number of connections in the pool
   */
  int getMaxPoolSize();

  /**
   * Attempt to connect an HTML5 websocket to the specified URI<p>
   * The connect is done asynchronously and {@code wsConnect} is called back with the websocket
   */
  void connectWebsocket(String uri, Handler<WebSocket> wsConnect);

  /**
   * Attempt to connect an HTML5 websocket to the specified URI<p>
   * This version of the method allows you to specify the websockets version using the {@code wsVersion parameter}
   * The connect is done asynchronously and {@code wsConnect} is called back with the websocket
   */
  void connectWebsocket(String uri, WebSocketVersion wsVersion, Handler<WebSocket> wsConnect);

  /**
   * This is a quick version of the {@link #get(String, org.vertx.java.core.Handler)}
   * method where you do not want to do anything with the request before sending.<p>
   * Normally with any of the HTTP methods you create the request then when you are ready to send it you call
   * {@link org.vertx.java.core.http.HttpClientRequest#end()} on it. With this method the request is immediately sent.<p>
   * When an HTTP response is received from the server the {@code responseHandler} is called passing in the response.
   */
  void getNow(String uri, Handler<HttpClientResponse> responseHandler);

  /**
   * This method works in the same manner as {@link #getNow(String, org.vertx.java.core.Handler)},
   * except that it allows you specify a set of {@code headers} that will be sent with the request.
   */
  void getNow(String uri, Map<String, ? extends Object> headers, Handler<HttpClientResponse> responseHandler);

  /**
   * This method returns an {@link org.vertx.java.core.http.HttpClientRequest} instance which represents an HTTP OPTIONS request with the specified {@code uri}.<p>
   * When an HTTP response is received from the server the {@code responseHandler} is called passing in the response.
   */
  HttpClientRequest options(String uri, Handler<HttpClientResponse> responseHandler);

  /**
   * This method returns an {@link org.vertx.java.core.http.HttpClientRequest} instance which represents an HTTP GET request with the specified {@code uri}.<p>
   * When an HTTP response is received from the server the {@code responseHandler} is called passing in the response.
   */
  HttpClientRequest get(String uri, Handler<HttpClientResponse> responseHandler);

  /**
   * This method returns an {@link org.vertx.java.core.http.HttpClientRequest} instance which represents an HTTP HEAD request with the specified {@code uri}.<p>
   * When an HTTP response is received from the server the {@code responseHandler} is called passing in the response.
   */
  HttpClientRequest head(String uri, Handler<HttpClientResponse> responseHandler);

  /**
   * This method returns an {@link org.vertx.java.core.http.HttpClientRequest} instance which represents an HTTP POST request with the specified {@code uri}.<p>
   * When an HTTP response is received from the server the {@code responseHandler} is called passing in the response.
   */
  HttpClientRequest post(String uri, Handler<HttpClientResponse> responseHandler);

  /**
   * This method returns an {@link org.vertx.java.core.http.HttpClientRequest} instance which represents an HTTP PUT request with the specified {@code uri}.<p>
   * When an HTTP response is received from the server the {@code responseHandler} is called passing in the response.
   */
  HttpClientRequest put(String uri, Handler<HttpClientResponse> responseHandler);

  /**
   * This method returns an {@link org.vertx.java.core.http.HttpClientRequest} instance which represents an HTTP DELETE request with the specified {@code uri}.<p>
   * When an HTTP response is received from the server the {@code responseHandler} is called passing in the response.
   */
  HttpClientRequest delete(String uri, Handler<HttpClientResponse> responseHandler);

  /**
   * This method returns an {@link org.vertx.java.core.http.HttpClientRequest} instance which represents an HTTP TRACE request with the specified {@code uri}.<p>
   * When an HTTP response is received from the server the {@code responseHandler} is called passing in the response.
   */
  HttpClientRequest trace(String uri, Handler<HttpClientResponse> responseHandler);

  /**
   * This method returns an {@link org.vertx.java.core.http.HttpClientRequest} instance which represents an HTTP CONNECT request with the specified {@code uri}.<p>
   * When an HTTP response is received from the server the {@code responseHandler} is called passing in the response.
   */
  HttpClientRequest connect(String uri, Handler<HttpClientResponse> responseHandler);

  /**
   * This method returns an {@link org.vertx.java.core.http.HttpClientRequest} instance which represents an HTTP PATCH request with the specified {@code uri}.<p>
   * When an HTTP response is received from the server the {@code responseHandler} is called passing in the response.
   */
  HttpClientRequest patch(String uri, Handler<HttpClientResponse> responseHandler);

  /**
   * This method returns an {@link org.vertx.java.core.http.HttpClientRequest} instance which represents an HTTP request with the specified {@code uri}.
   * The specific HTTP method (e.g. GET, POST, PUT etc) is specified using the parameter {@code method}<p>
   * When an HTTP response is received from the server the {@code responseHandler} is called passing in the response.
   */
  HttpClientRequest request(String method, String uri, Handler<HttpClientResponse> responseHandler);

  /**
   * Close the HTTP client. This will cause any pooled HTTP connections to be closed.
   */
  void close();

  /**
   * @return true if Nagle's algorithm is disabled.
   */
  Boolean isTCPNoDelay();

  /**
   * @return The TCP send buffer size
   */
  Integer getSendBufferSize();

  /**
   * @return The TCP receive buffer size
   */
  Integer getReceiveBufferSize();

  /**
   *
   * @return true if TCP keep alive is enabled
   */
  Boolean isTCPKeepAlive();

  /**
   *
   * @return The value of TCP reuse address
   */
  Boolean isReuseAddress();

  /**
   *
   * @return the value of TCP so linger
   */
  Boolean isSoLinger();

  /**
   *
   * @return the value of TCP traffic class
   */
  Integer getTrafficClass();

  /**
   *
   * @return The connect timeout in milliseconds
   */
  Long getConnectTimeout();

  /**
   *
   * @return The number of boss threads
   */
  Integer getBossThreads();

  /**
   *
   * @return true if this client will make SSL connections
   */
  boolean isSSL();

  /**
   *
   * @return true if this client will validate the remote server's certificate hostname against the requested host
   */
  boolean isVerifyHost();

    /**
   *
   * @return true if this client will trust all server certificates.
   */
  boolean isTrustAll();

  /**
   *
   * @return The path to the key store
   */
  String getKeyStorePath();

  /**
   *
   * @return The keystore password
   */
  String getKeyStorePassword();

  /**
   *
   * @return The trust store path
   */
  String getTrustStorePath();

  /**
   *
   * @return The trust store password
   */
  String getTrustStorePassword();
}
