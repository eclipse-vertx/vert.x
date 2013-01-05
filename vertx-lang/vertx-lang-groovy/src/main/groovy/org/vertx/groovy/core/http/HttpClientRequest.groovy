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
import org.vertx.groovy.core.streams.WriteStream
import org.vertx.java.core.Handler
import org.vertx.java.core.http.HttpClientRequest as JHttpClientRequest

/**
 * Represents a client-side HTTP request.<p>
 * Instances of this class are created by an {@link HttpClient} instance, via one of the methods corresponding to the
 * specific HTTP methods, or the generic {@link HttpClient#request} method.<p>
 * Once a request has been obtained, headers can be set on it, and data can be written to its body if required. Once
 * you are ready to send the request, the {@code #end()} method should be called.<p>
 * Nothing is actually sent until the request has been internally assigned an HTTP connection. The {@link HttpClient}
 * instance will return an instance of this class immediately, even if there are no HTTP connections available in the pool. Any requests
 * sent before a connection is assigned will be queued internally and actually sent when an HTTP connection becomes
 * available from the pool.<p>
 * The headers of the request are actually sent either when the {@code #end()} method is called, or, when the first
 * part of the body is written, whichever occurs first.<p>
 * This class supports both chunked and non-chunked HTTP.<p>
 * It implements {@link WriteStream} so it can be used with
 * {@link org.vertx.java.core.streams.Pump} to pump data with flow control.<p>
 * Instances of this class are not thread-safe<p>
 * An example of using this class is as follows:
 * <p>
 * <pre>
 *
 * def req = httpClient.post("/some-url") { response ->
 *   println "Got response: ${response.statusCode}"
 * }
 *
 * req.putHeader("some-header", "hello")
 * req.putHeader("Content-Length", 5)
 * req.write << buffer
 * req.write << "some-string"
 * req.end()
 *
 * </pre>
 *
 * The headers are also accessible as a {@code Map} so you can use index notation to access them.
 *
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
class HttpClientRequest implements WriteStream {

  private final JHttpClientRequest jRequest

  protected HttpClientRequest(JHttpClientRequest jRequest) {
    this.jRequest = jRequest
  }

  /**
   * If chunked is true then the request will be set into HTTP chunked mode
   * @param chunked
   * @return A reference to this, so multiple method calls can be chained.
   */
  HttpClientRequest setChunked(boolean chunked) {
    jRequest.setChunked(chunked)
    this
  }

  /**
   * @return The HTTP headers
   */
  Map<String, Object> getHeaders() {
    return jRequest.headers()
  }

  /**
   * Put an HTTP header - fluent API
   * @param name The header name
   * @param value The header value
   * @return A reference to this, so multiple method calls can be chained.
   */
  HttpClientRequest putHeader(String name, Object value) {
    getHeaders().put(name, value)
    this
  }


  /** {@inheritDoc} */
  void writeBuffer(Buffer chunk) {
    jRequest.writeBuffer(chunk.toJavaBuffer())
  }

  /**
   * Write a {@link Buffer} to the request body.
   *
   * @return A reference to this, so multiple method calls can be chained.
   */
  HttpClientRequest write(Buffer chunk) {
    writeBuffer(chunk)
    this
  }

  /**
   * Write a {@link String} to the request body, encoded in UTF-8.
   *
   * @return A reference to this, so multiple method calls can be chained.
   */
  HttpClientRequest write(String chunk) {
    jRequest.write(chunk)
    this
  }

  /**
   * Write a {@link String} to the request body, encoded using the encoding {@code enc}.
   *
   * @return A reference to this, so multiple method calls can be chained.
   */
  HttpClientRequest write(String chunk, String enc) {
    jRequest.write(chunk, enc)
    this
  }

  /**
   * Write a {@link Buffer} to the request body. The {@code doneHandler} is called after the buffer is actually written to the wire.
   *
   * @return A reference to this, so multiple method calls can be chained.
   */
  HttpClientRequest write(Buffer chunk, Closure doneHandler) {
    jRequest.write(chunk.toJavaBuffer(), doneHandler as Handler)
    this
  }

  /**
   * Write a {@link String} to the request body, encoded in UTF-8. The {@code doneHandler} is called after the buffer is actually written to the wire.
   *
   * @return A reference to this, so multiple method calls can be chained.
   */
  HttpClientRequest write(String chunk, Closure doneHandler) {
    jRequest.write(chunk, doneHandler as Handler)
    this
  }

  /**
   * Write a {@link String} to the request body, encoded with encoding {@code enc}. The {@code doneHandler} is called after the buffer is actually written to the wire.
   *
   * @return A reference to this, so multiple method calls can be chained.
   */
  HttpClientRequest write(String chunk, String enc, Closure doneHandler) {
    jRequest.write(chunk, enc, doneHandler as Handler)
    this
  }

  /** {@inheritDoc} */
  void setWriteQueueMaxSize(int maxSize) {
    jRequest.setWriteQueueMaxSize(maxSize)
  }

  /** {@inheritDoc} */
  boolean isWriteQueueFull() {
    jRequest.writeQueueFull()
  }

  /** {@inheritDoc} */
  void drainHandler(Closure handler) {
    jRequest.drainHandler(handler as Handler)
  }

  /** {@inheritDoc} */
  void exceptionHandler(Closure handler) {
    jRequest.exceptionHandler(handler as Handler)
  }

  /**
   * If you send an HTTP request with the header {@code Expect} set to the value {@code 100-continue}
   * and the server responds with an interim HTTP response with a status code of {@code 100} and a continue handler
   * has been set using this method, then the {@code handler} will be called.<p>
   * You can then continue to write data to the request body and later end it. This is normally used in conjunction with
   * the {@link #sendHead()} method to force the request header to be written before the request has ended.
   */
  void continueHandler(Closure handler) {
    jRequest.continueHandler(handler as Handler)
  }

  /**
   * Forces the head of the request to be written before {@link #end()} is called on the request. This is normally used
   * to implement HTTP 100-continue handling, see {@link #continueHandler(Closure)} for more information.
   *
   * @return A reference to this, so multiple method calls can be chained.
   */
  HttpClientRequest sendHead() {
    jRequest.sendHead()
    this
  }

  /**
   * Same as {@link #end(Buffer)} but writes a String with the default encoding
   */
  void end(String chunk) {
    jRequest.end(chunk)
  }

  /**
   * Same as {@link #end(Buffer)} but writes a String with the specified encoding
   */
  void end(String chunk, String enc) {
    jRequest.end(chunk, enc)
  }

  /**
   * Same as {@link #end()} but writes some data to the request body before ending. If the request is not chunked and
   * no other data has been written then the Content-Length header will be automatically set
   */
  void end(Buffer chunk) {
    jRequest.end(chunk.toJavaBuffer())
  }

  /**
   * Ends the request. If no data has been written to the request body, and {@link #sendHead()} has not been called then
   * the actual request won't get written until this method gets called.<p>
   * Once the request has ended, it cannot be used any more, and if keep alive is true the underlying connection will
   * be returned to the {@link HttpClient} pool so it can be assigned to another request.
   */
  void end() {
    jRequest.end()
  }

  /**
   * Same as {@link #write(Buffer)}
   * @param buff The buffer to write
   * @return @return A reference to this, so multiple method calls can be chained.
   */
  HttpClientRequest leftShift(Buffer buff) {
    writeBuffer(buff)
    this
  }

  /**
   * Same as {@link #write(String)}
   * @param buff The buffer to write
   * @return @return A reference to this, so multiple method calls can be chained.
   */
  HttpClientRequest leftShift(String str) {
    jRequest.write(str)
    this
  }

}
