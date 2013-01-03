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

import org.vertx.groovy.core.buffer.Buffer
import org.vertx.groovy.core.streams.ReadStream
import org.vertx.java.core.Handler
import org.vertx.java.core.http.HttpServerRequest as JHttpServerRequest

/**
 * Represents a server-side HTTP request.<p>
 * An instance of this class is created for each request that is handled by the server
 * and is passed to the user via the handler instance
 * registered with the {@link HttpServer} using the method {@link HttpServer#requestHandler(Closure)}.<p>
 * Each instance of this class is associated with a corresponding {@link HttpServerResponse} instance via
 * the {@code response} field.<p>
 * It implements {@link org.vertx.groovy.core.streams.ReadStream} so it can be used with
 * {@link org.vertx.groovy.core.streams.Pump} to pump data with flow control.<p>
 * Instances of this class are not thread-safe<p>
 *
 * @author Peter Ledbrook
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
class HttpServerRequest implements ReadStream {

  private final JHttpServerRequest jRequest
  private final HttpServerResponse wrappedResponse

  protected HttpServerRequest(JHttpServerRequest jRequest) {
    this.jRequest = jRequest
    this.wrappedResponse = new HttpServerResponse(jRequest.response)
  }

  /**
   * @return The HTTP method for the request. One of GET, PUT, POST, DELETE, TRACE, CONNECT, OPTIONS, HEAD
   */
  String getMethod() {
    jRequest.method
  }

  /**
   * @return The uri of the request. For example
   * http://www.somedomain.com/somepath/somemorepath/somresource.foo?someparam=32&someotherparam=x
   */
  String getUri() {
    jRequest.uri
  }

  /**
   * @return The path part of the uri. For example /somepath/somemorepath/somresource.foo
   */
  String getPath() {
    jRequest.path
  }

  /**
   * @return The query part of the uri. For example someparam=32&someotherparam=x
   */
  String getQuery() {
    jRequest.query
  }

  /**
   * A map of all headers in the request, If the request contains multiple headers with the same key, the values
   * will be concatenated together into a single header with the same key value, with each value separated by a comma,
   * as specified <a href="http://www.w3.org/Protocols/rfc2616/rfc2616-sec4.html#sec4.2">here</a>.
   * The headers will be automatically lower-cased when they reach the server
   */
  Map<String, String> getHeaders() {
    return jRequest.headers()
  }

  /**
   * @return A map of all query parameters in the request
   */
  Map<String, String> getParams() {
    return jRequest.params()
  }

  /**
   * @return The response. Each instance of this class has an {@link HttpServerResponse} instance attached to it. This is used
   * to send the response back to the client.
   */
  HttpServerResponse getResponse() {
    wrappedResponse
  }



  /**
   * Convenience method for receiving the entire request body in one piece. This saves the user having to manually
   * set a data and end handler and append the chunks of the body until the whole body received.
   * Don't use this if your request body is large - you could potentially run out of RAM.
   *
   * @param bodyHandler This handler will be called after all the body has been received
   */
  void bodyHandler(Closure bodyHandler) {
    jRequest.dataHandler({bodyHandler(new Buffer(it))} as Handler)
  }

  /** {@inheritDoc} */
  void dataHandler(Closure dataHandler) {
    jRequest.dataHandler({dataHandler(new Buffer(it))} as Handler)
  }

  /** {@inheritDoc} */
  void exceptionHandler(Closure handler) {
    jRequest.exceptionHandler(handler as Handler)
  }

  /** {@inheritDoc} */
  void pause() {
    jRequest.pause()
  }

  /** {@inheritDoc} */
  void resume() {
    jRequest.resume()
  }

  /** {@inheritDoc} */
  void endHandler(Closure handler) {
    jRequest.endHandler(handler as Handler)
  }

  org.vertx.java.core.http.HttpServerRequest toJavaRequest() {
    jRequest
  }

}
