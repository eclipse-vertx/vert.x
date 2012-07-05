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
 * Represents a client-side HTTP response.<p>
 * An instance of this class is provided to the user via a handler
 * that was specified when one of the HTTP method operations, or the
 * generic {@link HttpClient#request(String, String, Closure)}
 * method was called on an instance of {@link HttpClient}.<p>
 * It implements {@link org.vertx.groovy.core.streams.ReadStream} so it can be used with
 * {@link org.vertx.groovy.core.streams.Pump} to pump data with flow control.<p>
 * Instances of this class are not thread-safe<p>
 *
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
class HttpClientResponse implements ReadStream {

  private final org.vertx.java.core.http.HttpClientResponse jResponse

  protected HttpClientResponse(org.vertx.java.core.http.HttpClientResponse jResponse) {
    this.jResponse = jResponse
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
   * @return The headers of the response
   */
  Map<String, String> getHeaders() {
    return jResponse.headers()
  }

  /**
   * @return The trailers of the response
   */
  Map<String, String> getTrailers() {
    return jResponse.trailers()
  }

  /**
   * Convenience method for receiving the entire request body in one piece. This saves the user having to manually
   * set a data and end handler and append the chunks of the body until the whole body received.<p>
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

}
