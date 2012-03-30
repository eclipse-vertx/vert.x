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
 * Represents  a server-side HTTP request.
 * <p>
 * Instances of this class are not thread-safe
 * <p>
 * @author Peter Ledbrook
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
class HttpServerRequest implements ReadStream {

  private final JHttpServerRequest jRequest
  private final HttpServerResponse wrappedResponse
  private final Headers headers = new Headers()
  private final Params params = new Params()

  protected HttpServerRequest(JHttpServerRequest jRequest) {
    this.jRequest = jRequest
    this.wrappedResponse = new HttpServerResponse(jRequest.response)
  }

  /**
   * @return The headers of the request
   */
  Headers getHeaders() {
    return headers
  }

  /**
   * @return The parameters of the request
   */
  Params getParams() {
    return params
  }

  /**
   * @return The response. Each instance of this class has an {@link HttpServerResponse} instance attached to it. This is used
   * to send the response back to the client.
   */
  HttpServerResponse getResponse() {
    wrappedResponse
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

  /**
   * @return the underlying Java request
   */
  void toJavaRequest() {
    jRequest
  }

  /**
   * Represents the headers of a server request
   */
  class Headers {
    /**
     * @return Return the HTTP request header with the name {@code key} from this request, or null if there is no such header.
     */
    String getHeader(String key) {
      jRequest.getHeader(key)
    }

    /**
     * Same as {@link #getHeader(String)}
     */
    String getAt(String key) {
      getHeader(key)
    }

    /**
     * @return Return a set of all the HTTP header names in this request
     */
    Set<String> getNames() {
      jRequest.getHeaderNames()
    }

    /**
     * @return Returns a map of all headers in the request, If the request contains multiple headers with the same key, the values
     * will be concatenated together into a single header with the same key value, with each value separated by a comma, as specified
     * <a href="http://www.w3.org/Protocols/rfc2616/rfc2616-sec4.html#sec4.2">here</a>.
     */
    Map<String, String> toMap() {
      jRequest.getAllHeaders()
    }
  }

  /**
    Represents the parameters of the request
   */
  class Params {

    /**
     * Get the parameter value with the specified name
     * @param paramName
     * @return The param value
     */
    String getParam(String paramName) {
      jRequest.getAllParams().get(paramName)
    }

    /**
     * Same as {@link #getParam(String)}
     */
    String getAt(String paramName) {
      getParam(paramName)
    }

    /**
     * @return Returns a map of all the parameters in the request
     */
    Map<String, String> toMap() {
      jRequest.getAllParams()
    }

  }

}
