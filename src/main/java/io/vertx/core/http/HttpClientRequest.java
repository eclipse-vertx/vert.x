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

package io.vertx.core.http;

import io.vertx.core.Handler;
import io.vertx.core.MultiMap;
import io.vertx.core.buffer.Buffer;
import io.vertx.codegen.annotations.CacheReturn;
import io.vertx.codegen.annotations.Fluent;
import io.vertx.codegen.annotations.GenIgnore;
import io.vertx.codegen.annotations.VertxGen;
import io.vertx.core.streams.ReadStream;
import io.vertx.core.streams.WriteStream;

/**
 * Represents a client-side HTTP request.
 * <p>
 * Instances are created by an {@link HttpClient} instance, via one of the methods corresponding to the
 * specific HTTP methods, or the generic request methods. On creation the request will not have been written to the
 * wire.
 * <p>
 * Once a request has been obtained, headers can be set on it, and data can be written to its body if required. Once
 * you are ready to send the request, one of the {@link #end()} methods should be called.
 * <p>
 * Nothing is actually sent until the request has been internally assigned an HTTP connection.
 * <p>
 * The {@link HttpClient} instance will return an instance of this class immediately, even if there are no HTTP
 * connections available in the pool. Any requests sent before a connection is assigned will be queued
 * internally and actually sent when an HTTP connection becomes available from the pool.
 * <p>
 * The headers of the request are queued for writing either when the {@link #end()} method is called, or, when the first
 * part of the body is written, whichever occurs first.
 * <p>
 * This class supports both chunked and non-chunked HTTP.
 * <p>
 * It implements {@link io.vertx.core.streams.WriteStream} so it can be used with
 * {@link io.vertx.core.streams.Pump} to pump data with flow control.
 * <p>
 * An example of using this class is as follows:
 * <p>
 *
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
@VertxGen
public interface HttpClientRequest extends WriteStream<Buffer>, ReadStream<HttpClientResponse> {

  @Override
  HttpClientRequest exceptionHandler(Handler<Throwable> handler);

  /**
   * @throws java.lang.IllegalStateException when no response handler is set
   */
  @Override
  HttpClientRequest write(Buffer data);

  @Override
  HttpClientRequest setWriteQueueMaxSize(int maxSize);

  @Override
  HttpClientRequest drainHandler(Handler<Void> handler);

  @Override
  HttpClientRequest handler(Handler<HttpClientResponse> handler);

  @Override
  HttpClientRequest pause();

  @Override
  HttpClientRequest resume();

  @Override
  HttpClientRequest endHandler(Handler<Void> endHandler);

  /**
   * If chunked is true then the request will be set into HTTP chunked mode
   *
   * @param chunked  true if chunked encoding
   * @return a reference to this, so the API can be used fluently
   */
  @Fluent
  HttpClientRequest setChunked(boolean chunked);

  /**
   * @return Is the request chunked?
   */
  boolean isChunked();

  /**
   * The HTTP method for the request.
   */
  HttpMethod method();

  /**
   * @return The URI of the request.
   */
  String uri();

  /**
   * @return The HTTP headers
   */
  @CacheReturn
  MultiMap headers();

  /**
   * Put an HTTP header
   *
   * @param name The header name
   * @param value The header value
   *
   * @return a reference to this, so the API can be used fluently
   */
  @Fluent
  HttpClientRequest putHeader(String name, String value);

  /**
   * Like {@link #putHeader(String, String)} but using CharSequence
   */
  @GenIgnore
  HttpClientRequest putHeader(CharSequence name, CharSequence value);

  /**
   * Put an HTTP header with multiple values
   *
   * @param name The header name
   * @param values The header values
   *
   * @return @return a reference to this, so the API can be used fluently
   */
  @GenIgnore
  HttpClientRequest putHeader(String name, Iterable<String> values);

  /**
   * Like {@link #putHeader(String, Iterable)} but using CharSequence
   */
  @GenIgnore
  HttpClientRequest putHeader(CharSequence name, Iterable<CharSequence> values);

  /**
   * Write a {@link String} to the request body, encoded as UTF-8.
   *
   * @return @return a reference to this, so the API can be used fluently
   * @throws java.lang.IllegalStateException when no response handler is set
   */
  @Fluent
  HttpClientRequest write(String chunk);

  /**
   * Write a {@link String} to the request body, encoded using the encoding {@code enc}.
   *
   * @return @return a reference to this, so the API can be used fluently
   * @throws java.lang.IllegalStateException when no response handler is set
   */
  @Fluent
  HttpClientRequest write(String chunk, String enc);

  /**
   * If you send an HTTP request with the header {@code Expect} set to the value {@code 100-continue}
   * and the server responds with an interim HTTP response with a status code of {@code 100} and a continue handler
   * has been set using this method, then the {@code handler} will be called.
   * <p>
   * You can then continue to write data to the request body and later end it. This is normally used in conjunction with
   * the {@link #sendHead()} method to force the request header to be written before the request has ended.
   *
   * @return a reference to this, so the API can be used fluently
   */
  @Fluent
  HttpClientRequest continueHandler(Handler<Void> handler);

  /**
   * Forces the head of the request to be written before {@link #end()} is called on the request or any data is
   * written to it.
   * <p>
   * This is normally used to implement HTTP 100-continue handling, see {@link #continueHandler(io.vertx.core.Handler)} for
   * more information.
   *
   * @return a reference to this, so the API can be used fluently
   * @throws java.lang.IllegalStateException when no response handler is set
   */
  @Fluent
  HttpClientRequest sendHead();

  /**
   * Same as {@link #end(Buffer)} but writes a String in UTF-8 encoding
   *
   * @throws java.lang.IllegalStateException when no response handler is set
   */
  void end(String chunk);

  /**
   * Same as {@link #end(Buffer)} but writes a String with the specified encoding
   *
   * @throws java.lang.IllegalStateException when no response handler is set
   */
  void end(String chunk, String enc);

  /**
   * Same as {@link #end()} but writes some data to the request body before ending. If the request is not chunked and
   * no other data has been written then the {@code Content-Length} header will be automatically set
   *
   * @throws java.lang.IllegalStateException when no response handler is set
   */
  void end(Buffer chunk);

  /**
   * Ends the request. If no data has been written to the request body, and {@link #sendHead()} has not been called then
   * the actual request won't get written until this method gets called.
   * <p>
   * Once the request has ended, it cannot be used any more,
   *
   * @throws java.lang.IllegalStateException when no response handler is set
   */
  void end();

  /**
    * Set's the amount of time after which if a response is not received {@link java.util.concurrent.TimeoutException}
    * will be sent to the exception handler of this request.
   * <p>
   *  Calling this method more than once
    * has the effect of canceling any existing timeout and starting the timeout from scratch.
    *
    * @param timeoutMs The quantity of time in milliseconds.
    * @return a reference to this, so the API can be used fluently
    */
  @Fluent
  HttpClientRequest setTimeout(long timeoutMs);

}
