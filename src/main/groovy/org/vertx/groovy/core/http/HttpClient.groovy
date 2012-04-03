/*
 * Copyright 2011-2012 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.vertx.groovy.core.http

import org.vertx.java.core.Handler
import org.vertx.java.core.http.WebSocketVersion
import org.vertx.java.core.impl.VertxInternal

/**
 *
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
class HttpClient extends org.vertx.java.core.http.HttpClient {

  public HttpClient(VertxInternal vertx) {
    super(vertx);
  }

  /**
   * Set an exception handler
   */
  void exceptionHandler(Closure handler) {
    super.exceptionHandler(handler as Handler)
  }

  /**
   * Attempt to connect an HTML5 websocket to the specified URI<p>
   * The connect is done asynchronously and {@code wsConnect} is called back with the result
   */
  void connectWebsocket(String uri, Closure handler) {
    connectWebsocket(uri, WebSocketVersion.HYBI_17, handler)
  }

  /**
   * Attempt to connect an HTML5 websocket to the specified URI<p>
   * This version of the method allows you to specify the websockets version using the {@code wsVersion parameter}
   * The connect is done asynchronously and {@code wsConnect} is called back with the result
   */
  void connectWebsocket(String uri, WebSocketVersion version, Closure handler) {
    super.connectWebsocket(uri, version, {handler(new WebSocket(it))} as Handler)
  }

  /**
   * This is a quick version of the {@link #get(String, org.vertx.java.core.Handler) get()}
   * method where you do not want to do anything with the request before sending.<p>
   * Normally with any of the HTTP methods you create the request then when you are ready to send it you call
   * {@link HttpClientRequest#end()} on it. With this method the request is immediately sent.<p>
   * When an HTTP response is received from the server the {@code responseHandler} is called passing in the response.
   */
  void getNow(String uri, Closure responseHandler) {
    super.getNow(uri, wrapResponseHandler(responseHandler))
  }

  /**
   * This method works in the same manner as {@link #getNow(String, org.vertx.java.core.Handler)},
   * except that it allows you specify a set of {@code headers} that will be sent with the request.
   */
  void getNow(String uri, Map<String, ? extends Object> headers, Closure responseHandler) {
    super.getNow(uri, headers, wrapResponseHandler(responseHandler))
  }

  /**
   * This method returns an {@link HttpClientRequest} instance which represents an HTTP OPTIONS request with the specified {@code uri}.<p>
   * When an HTTP response is received from the server the {@code responseHandler} is called passing in the response.
   */
  HttpClientRequest options(String uri, Closure responseHandler) {
    new HttpClientRequest(super.options(uri, wrapResponseHandler(responseHandler)))
  }

  /**
   * This method returns an {@link HttpClientRequest} instance which represents an HTTP GET request with the specified {@code uri}.<p>
   * When an HTTP response is received from the server the {@code responseHandler} is called passing in the response.
   */
  HttpClientRequest get(String uri, Closure responseHandler) {
    request("GET", uri, responseHandler)
  }

  /**
   * This method returns an {@link HttpClientRequest} instance which represents an HTTP HEAD request with the specified {@code uri}.<p>
   * When an HTTP response is received from the server the {@code responseHandler} is called passing in the response.
   */
  HttpClientRequest head(String uri, Closure responseHandler) {
    request("HEAD", uri, responseHandler)
  }

  /**
   * This method returns an {@link HttpClientRequest} instance which represents an HTTP POST request with the specified {@code uri}.<p>
   * When an HTTP response is received from the server the {@code responseHandler} is called passing in the response.
   */
  HttpClientRequest post(String uri, Closure responseHandler) {
    request("POST", uri, responseHandler)
  }

  /**
   * This method returns an {@link HttpClientRequest} instance which represents an HTTP PUT request with the specified {@code uri}.<p>
   * When an HTTP response is received from the server the {@code responseHandler} is called passing in the response.
   */
  HttpClientRequest put(String uri, Closure responseHandler) {
    request("PUT", uri, responseHandler)
  }

  /**
   * This method returns an {@link HttpClientRequest} instance which represents an HTTP DELETE request with the specified {@code uri}.<p>
   * When an HTTP response is received from the server the {@code responseHandler} is called passing in the response.
   */
  HttpClientRequest delete(String uri, Closure responseHandler) {
    request("DELETE", uri, responseHandler)
  }

  /**
   * This method returns an {@link HttpClientRequest} instance which represents an HTTP TRACE request with the specified {@code uri}.<p>
   * When an HTTP response is received from the server the {@code responseHandler} is called passing in the response.
   */
  HttpClientRequest trace(String uri, Closure responseHandler) {
    request("TRACE", uri, responseHandler)
  }

  /**
   * This method returns an {@link HttpClientRequest} instance which represents an HTTP CONNECT request with the specified {@code uri}.<p>
   * When an HTTP response is received from the server the {@code responseHandler} is called passing in the response.
   */
  HttpClientRequest connect(String uri, Closure responseHandler) {
    request("CONNECT", uri, responseHandler)
  }

  /**
   * This method returns an {@link HttpClientRequest} instance which represents an HTTP PATCH request with the specified {@code uri}.<p>
   * When an HTTP response is received from the server the {@code responseHandler} is called passing in the response.
   */
  HttpClientRequest patch(String uri, Closure responseHandler) {
    request("PATCH", uri, responseHandler)
  }

  /**
   * This method returns an {@link HttpClientRequest} instance which represents an HTTP request with the specified {@code uri}. The specific HTTP method
   * (e.g. GET, POST, PUT etc) is specified using the parameter {@code method}<p>
   * When an HTTP response is received from the server the {@code responseHandler} is called passing in the response.
   */
  HttpClientRequest request(String method, String uri, Closure responseHandler) {
    new HttpClientRequest(super.request(method, uri, wrapResponseHandler(responseHandler)))
  }

  private Handler wrapResponseHandler(Closure handler) {
    return {handler(new HttpClientResponse(it))} as Handler
  }

}
