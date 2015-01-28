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

import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.MultiMap;
import io.vertx.core.buffer.Buffer;
import io.vertx.codegen.annotations.CacheReturn;
import io.vertx.codegen.annotations.Fluent;
import io.vertx.codegen.annotations.GenIgnore;
import io.vertx.codegen.annotations.VertxGen;
import io.vertx.core.streams.WriteStream;

/**
 * Represents a server-side HTTP response.
 * <p>
 * An instance of this is created and associated to every instance of
 * {@link HttpServerRequest} that.
 * <p>
 * It allows the developer to control the HTTP response that is sent back to the
 * client for a particular HTTP request.
 * <p>
 * It contains methods that allow HTTP headers and trailers to be set, and for a body to be written out to the response.
 * <p>
 * It also allows files to be streamed by the kernel directly from disk to the
 * outgoing HTTP connection, bypassing user space altogether (where supported by
 * the underlying operating system). This is a very efficient way of
 * serving files from the server since buffers do not have to be read one by one
 * from the file and written to the outgoing socket.
 * <p>
 * It implements {@link io.vertx.core.streams.WriteStream} so it can be used with
 * {@link io.vertx.core.streams.Pump} to pump data with flow control.
 *
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
@VertxGen
public interface HttpServerResponse extends WriteStream<Buffer> {

  @Override
  HttpServerResponse exceptionHandler(Handler<Throwable> handler);

  @Override
  HttpServerResponse write(Buffer data);

  @Override
  HttpServerResponse setWriteQueueMaxSize(int maxSize);

  @Override
  HttpServerResponse drainHandler(Handler<Void> handler);

  /**
   * @return the HTTP status code of the response. The default is {@code 200} representing {@code OK}.
   */
  int getStatusCode();

  /**
   * Set the status code. If the status message hasn't been explicitly set, a default status message corresponding
   * to the code will be looked-up and used.
   *
   * @return a reference to this, so the API can be used fluently
   */
  @Fluent
  HttpServerResponse setStatusCode(int statusCode);

  /**
   * @return the HTTP status message of the response. If this is not specified a default value will be used depending on what
   * {@link #setStatusCode} has been set to.
   */
  String getStatusMessage();

  /**
   * Set the status message
   *
   * @return a reference to this, so the API can be used fluently
   */
  @Fluent
  HttpServerResponse setStatusMessage(String statusMessage);

  /**
   * If {@code chunked} is {@code true}, this response will use HTTP chunked encoding, and each call to write to the body
   * will correspond to a new HTTP chunk sent on the wire.
   * <p>
   * If chunked encoding is used the HTTP header {@code Transfer-Encoding} with a value of {@code Chunked} will be
   * automatically inserted in the response.
   * <p>
   * If {@code chunked} is {@code false}, this response will not use HTTP chunked encoding, and therefore the total size
   * of any data that is written in the respone body must be set in the {@code Content-Length} header <b>before</b> any
   * data is written out.
   * <p>
   * An HTTP chunked response is typically used when you do not know the total size of the request body up front.
   *
   * @return a reference to this, so the API can be used fluently
   */
  @Fluent
  HttpServerResponse setChunked(boolean chunked);

  /**
   * @return is the response chunked?
   */
  boolean isChunked();

  /**
   * @return The HTTP headers
   */
  @CacheReturn
  MultiMap headers();

  /**
   * Put an HTTP header
   *
   * @param name  the header name
   * @param value  the header value.
   * @return a reference to this, so the API can be used fluently
   */
  @Fluent
  HttpServerResponse putHeader(String name, String value);

  /**
   * Like {@link #putHeader(String, String)} but using CharSequence
   */
  @GenIgnore
  HttpServerResponse putHeader(CharSequence name, CharSequence value);

  /**
   * Like {@link #putHeader(String, String)} but providing multiple values via a String Iterable
   */
  @GenIgnore
  HttpServerResponse putHeader(String name, Iterable<String> values);

  /**
   * Like {@link #putHeader(String, Iterable)} but with CharSequence Iterable
   */
  @GenIgnore
  HttpServerResponse putHeader(CharSequence name, Iterable<CharSequence> values);

  /**
   * @return The HTTP trailers
   */
  @CacheReturn
  MultiMap trailers();

  /**
   * Put an HTTP trailer
   *
   * @param name  the trailer name
   * @param value  the trailer value
   * @return a reference to this, so the API can be used fluently
   */
  @Fluent
  HttpServerResponse putTrailer(String name, String value);

  /**
   * Like {@link #putTrailer(String, String)} but using CharSequence
   */
  @GenIgnore
  HttpServerResponse putTrailer(CharSequence name, CharSequence value);

  /**
   * Like {@link #putTrailer(String, String)} but providing multiple values via a String Iterable
   */
  @GenIgnore
  HttpServerResponse putTrailer(String name, Iterable<String> values);

  /**
   * Like {@link #putTrailer(String, Iterable)} but with CharSequence Iterable
   */
  @GenIgnore
  HttpServerResponse putTrailer(CharSequence name, Iterable<CharSequence> value);

  /**
   * Set a close handler for the response. This will be called if the underlying connection closes before the response
   * is complete.
   *
   * @param handler  the handler
   * @return a reference to this, so the API can be used fluently
   */
  @Fluent
  HttpServerResponse closeHandler(Handler<Void> handler);

  /**
   * Write a {@link String} to the response body, encoded using the encoding {@code enc}.
   *
   * @param chunk  the string to write
   * @param enc  the encoding to use
   * @return a reference to this, so the API can be used fluently
   */
  @Fluent
  HttpServerResponse write(String chunk, String enc);

  /**
   * Write a {@link String} to the response body, encoded in UTF-8.
   *
   * @param chunk  the string to write
   * @return a reference to this, so the API can be used fluently
   */
  @Fluent
  HttpServerResponse write(String chunk);

  /**
   * Same as {@link #end(Buffer)} but writes a String in UTF-8 encoding before ending the response.
   *
   * @param chunk  the string to write before ending the response
   */
  void end(String chunk);

  /**
   * Same as {@link #end(Buffer)} but writes a String with the specified encoding before ending the response.
   *
   * @param chunk  the string to write before ending the response
   * @param enc  the encoding to use
   */
  void end(String chunk, String enc);

  /**
   * Same as {@link #end()} but writes some data to the response body before ending. If the response is not chunked and
   * no other data has been written then the @code{Content-Length} header will be automatically set.
   *
   * @param chunk  the buffer to write before ending the response
   */
  void end(Buffer chunk);

  /**
   * Ends the response. If no data has been written to the response body,
   * the actual response won't get written until this method gets called.
   * <p>
   * Once the response has ended, it cannot be used any more.
   */
  void end();

  /**
   * Ask the OS to stream a file as specified by {@code filename} directly
   * from disk to the outgoing connection, bypassing userspace altogether
   * (where supported by the underlying operating system.
   * This is a very efficient way to serve files.<p>
   * The actual serve is asynchronous and may not complete until some time after this method has returned.
   *
   * @param filename  path to the file to serve
   * @return a reference to this, so the API can be used fluently
   */
  @Fluent
  HttpServerResponse sendFile(String filename);

  /**
   * Like {@link #sendFile(String)} but providing a handler which will be notified once the file has been completely
   * written to the wire.
   *
   * @param filename path to the file to serve
   * @param resultHandler  handler that will be called on completion
   * @return a reference to this, so the API can be used fluently
   */
  @Fluent
  HttpServerResponse sendFile(String filename, Handler<AsyncResult<Void>> resultHandler);

  /**
   * Close the underlying TCP connection corresponding to the request.
   */
  void close();

  /**
   * @return has the response already ended?
   */
  boolean ended();

  /**
   * @return have the headers for the response already been written?
   */
  boolean headWritten();

  /**
   * Provide a handler that will be called just before the headers are written to the wire.<p>
   * This provides a hook allowing you to add any more headers or do any more operations before this occurs.
   *
   * @param handler  the handler
   * @return a reference to this, so the API can be used fluently
   */
  @Fluent
  HttpServerResponse headersEndHandler(Handler<Void> handler);

  /**
   * Provide a handler that will be called just before the last part of the body is written to the wire
   * and the response is ended.<p>
   * This provides a hook allowing you to do any more operations before this occurs.
   *
   * @param handler  the handler
   * @return a reference to this, so the API can be used fluently
   */
  @Fluent
  HttpServerResponse bodyEndHandler(Handler<Void> handler);

}
