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

import org.vertx.java.core.Handler;
import org.vertx.java.core.MultiMap;
import org.vertx.java.core.buffer.Buffer;
import org.vertx.java.core.streams.WriteStream;

/**
 * Represents a client-side HTTP request.<p>
 * Instances are created by an {@link HttpClient} instance, via one of the methods corresponding to the
 * specific HTTP methods, or the generic {@link HttpClient#request} method.<p>
 * Once a request has been obtained, headers can be set on it, and data can be written to its body if required. Once
 * you are ready to send the request, the {@link #end()} method should be called.<p>
 * Nothing is actually sent until the request has been internally assigned an HTTP connection. The {@link HttpClient}
 * instance will return an instance of this class immediately, even if there are no HTTP connections available in the pool. Any requests
 * sent before a connection is assigned will be queued internally and actually sent when an HTTP connection becomes
 * available from the pool.<p>
 * The headers of the request are actually sent either when the {@link #end()} method is called, or, when the first
 * part of the body is written, whichever occurs first.<p>
 * This class supports both chunked and non-chunked HTTP.<p>
 * It implements {@link WriteStream} so it can be used with
 * {@link org.vertx.java.core.streams.Pump} to pump data with flow control.<p>
 * An example of using this class is as follows:
 * <p>
 * <pre>
 *
 * HttpClientRequest req = httpClient.post("/some-url", new Handler&lt;HttpClientResponse&gt;() {
 *   public void handle(HttpClientResponse response) {
 *     System.out.println("Got response: " + response.statusCode);
 *   }
 * });
 *
 * req.headers().putHeader("some-header", "hello")
 *     .putHeader("Content-Length", 5)
 *     .write(new Buffer(new byte[]{1, 2, 3, 4, 5}))
 *     .write(new Buffer(new byte[]{6, 7, 8, 9, 10}))
 *     .end();
 *
 * </pre>
 * Instances of HttpClientRequest are not thread-safe
 *
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public interface HttpClientRequest extends WriteStream<HttpClientRequest> {

  /**
   * If chunked is true then the request will be set into HTTP chunked mode
   * @param chunked
   * @return A reference to this, so multiple method calls can be chained.
   */
  HttpClientRequest setChunked(boolean chunked);

  /**
   *
   * @return Is the request chunked?
   */
  boolean isChunked();

  /**
   * @return The HTTP headers
   */
  MultiMap headers();

  /**
   * Put an HTTP header - fluent API
   * @param name The header name
   * @param value The header value
   * @return A reference to this, so multiple method calls can be chained.
   */
  HttpClientRequest putHeader(String name, String value);

  HttpClientRequest putHeader(CharSequence name, CharSequence value);


  /**
   * Put an HTTP header - fluent API
   * @param name The header name
   * @param values The header values
   * @return A reference to this, so multiple method calls can be chained.
   */
  HttpClientRequest putHeader(String name, Iterable<String> values);

  HttpClientRequest putHeader(CharSequence name, Iterable<CharSequence> values);

  /**
   * Write a {@link Buffer} to the request body.
   *
   * @return A reference to this, so multiple method calls can be chained.
   */
  HttpClientRequest write(Buffer chunk);

  /**
   * Write a {@link String} to the request body, encoded in UTF-8.
   *
   * @return A reference to this, so multiple method calls can be chained.
   */
  HttpClientRequest write(String chunk);

  /**
   * Write a {@link String} to the request body, encoded using the encoding {@code enc}.
   *
   * @return A reference to this, so multiple method calls can be chained.
   */
  HttpClientRequest write(String chunk, String enc);

  /**
   * If you send an HTTP request with the header {@code Expect} set to the value {@code 100-continue}
   * and the server responds with an interim HTTP response with a status code of {@code 100} and a continue handler
   * has been set using this method, then the {@code handler} will be called.<p>
   * You can then continue to write data to the request body and later end it. This is normally used in conjunction with
   * the {@link #sendHead()} method to force the request header to be written before the request has ended.
   * @return A reference to this, so multiple method calls can be chained.
   */
  HttpClientRequest continueHandler(Handler<Void> handler);

  /**
   * Forces the head of the request to be written before {@link #end()} is called on the request or any data is
   * written to it. This is normally used
   * to implement HTTP 100-continue handling, see {@link #continueHandler(org.vertx.java.core.Handler)} for more information.
   *
   * @return A reference to this, so multiple method calls can be chained.
   */
  HttpClientRequest sendHead();

  /**
   * Same as {@link #end(Buffer)} but writes a String with the default encoding
   */
  void end(String chunk);

  /**
   * Same as {@link #end(Buffer)} but writes a String with the specified encoding
   */
  void end(String chunk, String enc);

  /**
   * Same as {@link #end()} but writes some data to the request body before ending. If the request is not chunked and
   * no other data has been written then the Content-Length header will be automatically set
   */
  void end(Buffer chunk);

  /**
   * Ends the request. If no data has been written to the request body, and {@link #sendHead()} has not been called then
   * the actual request won't get written until this method gets called.<p>
   * Once the request has ended, it cannot be used any more, and if keep alive is true the underlying connection will
   * be returned to the {@link HttpClient} pool so it can be assigned to another request.
   */
  void end();

  /**
    * Set's the amount of time after which if a response is not received TimeoutException()
    * will be sent to the exception handler of this request. Calling this method more than once
    * has the effect of canceling any existing timeout and starting the timeout from scratch.
    *
    * @param timeoutMs The quantity of time in milliseconds.
    * @return A reference to this, so multiple method calls can be chained.
    */
   HttpClientRequest setTimeout(long timeoutMs);

}
