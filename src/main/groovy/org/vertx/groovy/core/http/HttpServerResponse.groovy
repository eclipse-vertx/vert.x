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

/**
 * Represents a server-side HTTP response.<p>
 * An instance of this class is created and associated to every instance of
 * {@link HttpServerRequest} that is created.<p>
 * It allows the developer to control the HTTP response that is sent back to the
 * client for a partcularHTTP request. It contains methods that allow HTTP
 * headers and trailers to be set, and for a body to be written out to the response.<p>
 * It also allows files to be streamed by the kernel directly from disk to the
 * outgoing HTTP connection, bypassing user space altogether (where supported by
 * the underlying operating system). This is a very efficient way of
 * serving files from the server since buffers do not have to be read one by one
 * from the file and written to the outgoing socket.<p>
 * It implements {@link WriteStream} so it can be used with
 * {@link org.vertx.groovy.core.streams.Pump} to pump data with flow control.<p>
 * Instances of this class are not thread-safe<p>
 * @author Peter Ledbrook
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
class HttpServerResponse implements WriteStream {

  private final org.vertx.java.core.http.HttpServerResponse jResponse

  protected HttpServerResponse(org.vertx.java.core.http.HttpServerResponse jResponse) {
    this.jResponse = jResponse
  }

  /**
   * @return The HTTP status code of the response. The default is {@code 200} representing {@code OK}.
   */
  int getStatusCode() {
    return jResponse.statusCode
  }

  /**
   * Set the status code
   * @param code The code
   */
  void setStatusCode(int code) {
    jResponse.statusCode = code
  }

  /**
   * @return The HTTP status message of the response. If this is not specified a default value will be used depending on what
   * statusCode has been set to.
   */
  String getStatusMessage() {
    return jResponse.statusMessage
  }

  /**
   * Set the status message
   * @param msg The message
   */
  void setStatusMessage(String msg) {
    jResponse.statusMessage = msg
  }

  /**
   * If {@code chunked} is {@code true}, this response will use HTTP chunked encoding, and each call to write to the body
   * will correspond to a new HTTP chunk sent on the wire. If chunked encoding is used the HTTP header
   * {@code Transfer-Encoding} with a value of {@code Chunked} will be automatically inserted in the response.<p>
   * If {@code chunked} is {@code false}, this response will not use HTTP chunked encoding, and therefore if any data is written the
   * body of the response, the total size of that data must be set in the {@code Content-Length} header <b>before</b> any
   * data is written to the response body.<p>
   * An HTTP chunked response is typically used when you do not know the total size of the request body up front.
   *
   * @return A reference to this, so multiple method calls can be chained.
   */
  HttpServerResponse setChunked(boolean chunked) {
    jResponse.setChunked(true)
    this
  }

  /**
   * @return The headers of the response
   */
  Map<String, Object> getHeaders() {
    return jResponse.headers()
  }

  /**
   * Put an HTTP header - fluent API
   * @param name The header name
   * @param value The header value
   * @return A reference to this, so multiple method calls can be chained.
   */
  HttpServerResponse putHeader(String name, Object value) {
    getHeaders().put(name, value)
    this
  }

  /**
   * @return The trailers of the response
   */
  Map<String, Object> getTrailers() {
    return jResponse.trailers()
  }

  /**
   * Set a close handler for the response. This will be called if the underlying connection closes before the response
   * is complete.
   * @param handler
   */
  void closeHandler(Closure handler) {
    jResponse.closeHandler(handler as Handler)
  }

  /**
   * Write a {@link Buffer} to the response body.<p>
   *
   * @return A reference to this, so multiple method calls can be chained.
   */
  HttpServerResponse write(Buffer chunk) {
    jResponse.write(chunk.toJavaBuffer())
    this
  }

  /**
   * Write a {@link String} to the response body, encoded using the encoding {@code enc}.<p>
   *
   * @return A reference to this, so multiple method calls can be chained.
   */
  HttpServerResponse write(String chunk, String enc) {
    jResponse.write(chunk, enc)
    this
  }

  /**
   * Write a {@link String} to the response body, encoded in UTF-8.<p>
   *
   * @return A reference to this, so multiple method calls can be chained.
   */
  HttpServerResponse write(String chunk) {
    jResponse.write(chunk)
    this
  }

  /**
   * Write a {@link Buffer} to the response body. The {@code doneHandler} is called after the buffer is actually written to the wire.<p>
   *
   * @return A reference to this, so multiple method calls can be chained.
   */
  HttpServerResponse write(Buffer chunk, Closure doneHandler) {
    jResponse.write(chunk, doneHandler as Handler)
    this
  }

  /**
   * Write a {@link String} to the response body, encoded with encoding {@code enc}. The {@code doneHandler} is called after the buffer is actually written to the wire.<p>
   *
   * @return A reference to this, so multiple method calls can be chained.
   */
  HttpServerResponse write(String chunk, String enc, Closure doneHandler) {
    jResponse.write(chunk, enc, doneHandler as Handler)
    this
  }

  /**
   * Write a {@link String} to the response body, encoded in UTF-8. The {@code doneHandler} is called after the buffer is actually written to the wire.<p>
   *
   * @return A reference to this, so multiple method calls can be chained.
   */
  HttpServerResponse write(String chunk, Closure doneHandler) {
    jResponse.write(chunk, doneHandler as Handler)
    this
  }

  /**
   * Same as {@link #end(Buffer)} but writes a String with the default encoding
   */
  void end(String chunk) {
    jResponse.end(chunk)
  }

  /**
   * Same as {@link #end(Buffer)} but writes a String with the specified encoding
   */
  void end(String chunk, String enc) {
    jResponse.end(chunk, enc)
  }

  /**
   * Same as {@link #end()} but writes some data to the response body before ending. If the response is not chunked and
   * no other data has been written then the Content-Length header will be automatically set
   */
  void end(Buffer chunk) {
    jResponse.end(chunk.toJavaBuffer())
  }

  /**
   * Ends the response. If no data has been written to the response body,
   * the actual response won't get written until this method gets called.<p>
   * Once the response has ended, it cannot be used any more.
   */
  void end() {
    jResponse.end()
  }

  /**
   * Tell the kernel to stream a file as specified by {@code filename} directly
   * from disk to the outgoing connection, bypassing userspace altogether
   * (where supported by the underlying operating system.
   * This is a very efficient way to serve files.<p>
   */
  HttpServerResponse sendFile(String filename) {
    jResponse.sendFile(filename)
    this
  }

  /**
   * Close the underlying TCP connection
   */
  void close() {
    jResponse.close()
  }

  /**
   * Same as {@link #write(Buffer)}
   */
  HttpServerResponse leftShift(Buffer buff) {
    writeBuffer(buff)
    this
  }

  /**
   * Same as {@link #write(String)}
   */
  HttpServerResponse leftShift(String str) {
    jResponse.write(str)
    this
  }

  /** {@inheritDoc} */
  void setWriteQueueMaxSize(int size) {
    jResponse.setWriteQueueMaxSize(size)
  }

  /** {@inheritDoc} */
  boolean isWriteQueueFull() {
    jResponse.writeQueueFull()
  }

  /** {@inheritDoc} */
  void drainHandler(Closure handler) {
    jResponse.drainHandler(handler as Handler)
  }

  /** {@inheritDoc} */
  void exceptionHandler(Closure handler) {
    jResponse.exceptionHandler(handler as Handler)
  }

  /** {@inheritDoc} */
  void writeBuffer(Buffer chunk) {
    jResponse.writeBuffer(chunk.toJavaBuffer())
  }



}
