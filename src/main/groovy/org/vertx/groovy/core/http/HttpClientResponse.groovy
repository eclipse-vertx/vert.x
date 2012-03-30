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

/**
 * Represents a client-side HTTP response.
 * <p>
 * Instances of this class are not thread-safe
 * <p>
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
class HttpClientResponse implements ReadStream {

  private final org.vertx.java.core.http.HttpClientResponse jResponse
  private final Headers headers = new Headers()
  private final Trailers trailers = new Trailers()

  protected HttpClientResponse(org.vertx.java.core.http.HttpClientResponse jResponse) {
    this.jResponse = jResponse
  }

  /**
   * @return The headers of the response
   */
  Headers getHeaders() {
    return headers
  }

  /**
   * @return The trailers of the response
   */
  Trailers getTrailers() {
    return trailers
  }

  /**
   * @return The HTTP status code of the response
   */
  int getStatusCode() {
    jResponse.statusCode
  }

  /**
   * @return The HTTP status message of the response
   */
  String getStatusMessage() {
    jResponse.statusMessage
  }

  /**
   * Convenience method for receiving the entire request body in one piece. This saves the user having to manually
   * set a data and end handler and append the chunks of the body until the whole body received.
   * Don't use this if your request body is large - you could potentially run out of RAM.
   *
   * @param bodyHandler This handler will be called after all the body has been received
   */
  void bodyHandler(Closure bodyHandler) {
    jResponse.dataHandler({bodyHandler(new Buffer(it))} as Handler)
  }

  /** {@inheritDoc} */
  void dataHandler(Closure dataHandler) {
    jResponse.dataHandler({dataHandler(new Buffer(it))} as Handler)
  }

  /** {@inheritDoc} */
  void endHandler(Closure endHandler) {
    jResponse.endHandler(endHandler as Handler)
  }

  /** {@inheritDoc} */
  void exceptionHandler(Closure exceptionHandler) {
    jResponse.exceptionHandler(exceptionHandler as Handler)
  }

  /** {@inheritDoc} */
  void pause() {
    jResponse.pause()
  }

  /** {@inheritDoc} */
  void resume() {
    jResponse.resume()
  }

  /**
   * Represents the headers of a client response
   */
  class Headers {
    /**
     * @return Return the HTTP request header with the name {@code key} from this request, or null if there is no such header.
     */
    String getHeader(String key) {
      jResponse.getHeader(key)
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
      jResponse.getHeaderNames()
    }

    /**
     * @return Returns a map of all headers in the request, If the request contains multiple headers with the same key, the values
     * will be concatenated together into a single header with the same key value, with each value separated by a comma, as specified
     * <a href="http://www.w3.org/Protocols/rfc2616/rfc2616-sec4.html#sec4.2">here</a>.
     */
    Map<String, String> toMap() {
      jResponse.getAllHeaders()
    }
  }

  /**
   * Represents the trailers of a client response
   */
  class Trailers {

    /**
     * Returns the trailer value for the specified {@code key}, or null, if there is no such header in the response.<p>
     * Trailers will only be available in the response if the server has sent a HTTP chunked response where headers have
     * been inserted by the server on the last chunk. In such a case they won't be available on the client until the last chunk has
     * been received.
     */
    String getTrailer(String key) {
      jResponse.getTrailer(key)
    }

    /**
     * Same as {@link #getTrailer(String)}
     */
    String getAt(String key) {
      getTrailer(key)
    }

    /**
     * Returns a map of all trailers in the response, If the response contains multiple trailers with the same key, the values
     * will be concatenated together into a single header with the same key value, with each value separated by a comma, as specified
     * <a href="http://www.w3.org/Protocols/rfc2616/rfc2616-sec4.html#sec4.2">here</a>.<p>
     * If trailers have been sent by the server, they won't be available on the client side until the last chunk is received.
     */
    Map<String, String> toMap() {
      jResponse.getAllTrailers()
    }

    /**
     * Returns a set of all trailer names in the response.<p>
     * If trailers have been sent by the server, they won't be available on the client side until the last chunk is received.
     */
    Set<String> getNames() {
      jResponse.getTrailerNames()
    }
  }
}
