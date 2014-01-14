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

import org.vertx.java.core.AsyncResult;
import org.vertx.java.core.Handler;
import org.vertx.java.core.MultiMap;
import org.vertx.java.core.buffer.Buffer;
import org.vertx.java.core.streams.WriteStream;

/**
 * Represents a server-side HTTP response.<p>
 * Instances of this class are created and associated to every instance of
 * {@link HttpServerRequest} that is created.<p>
 * It allows the developer to control the HTTP response that is sent back to the
 * client for a particular HTTP request. It contains methods that allow HTTP
 * headers and trailers to be set, and for a body to be written out to the response.<p>
 * It also allows files to be streamed by the kernel directly from disk to the
 * outgoing HTTP connection, bypassing user space altogether (where supported by
 * the underlying operating system). This is a very efficient way of
 * serving files from the server since buffers do not have to be read one by one
 * from the file and written to the outgoing socket.<p>
 * It implements {@link WriteStream} so it can be used with
 * {@link org.vertx.java.core.streams.Pump} to pump data with flow control.<p>
 * Instances of this class are not thread-safe<p>
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public interface HttpServerResponse extends WriteStream<HttpServerResponse> {

  /**
   * The HTTP status code of the response. The default is {@code 200} representing {@code OK}.
   */
  int getStatusCode();

  /**
   * Set the status code
   * @return A reference to this, so multiple method calls can be chained.
   */
  HttpServerResponse setStatusCode(int statusCode);

  /**
   * The HTTP status message of the response. If this is not specified a default value will be used depending on what
   * {@link #setStatusCode} has been set to.
   */
  String getStatusMessage();

  /**
   * Set the status message
   * @return A reference to this, so multiple method calls can be chained.
   */
  HttpServerResponse setStatusMessage(String statusMessage);

  /**
   * If {@code chunked} is {@code true}, this response will use HTTP chunked encoding, and each call to write to the body
   * will correspond to a new HTTP chunk sent on the wire.<p>
   * If chunked encoding is used the HTTP header {@code Transfer-Encoding} with a value of {@code Chunked} will be
   * automatically inserted in the response.<p>
   * If {@code chunked} is {@code false}, this response will not use HTTP chunked encoding, and therefore if any data is written the
   * body of the response, the total size of that data must be set in the {@code Content-Length} header <b>before</b> any
   * data is written to the response body.<p>
   * An HTTP chunked response is typically used when you do not know the total size of the request body up front.
   *
   * @return A reference to this, so multiple method calls can be chained.
   */
  HttpServerResponse setChunked(boolean chunked);

  /**
   * Is the response chunked?
   */
  boolean isChunked();

  /**
   * @return The HTTP headers
   */
  MultiMap headers();

  /**
   * Put an HTTP header - fluent API
   * @param name The header name
   * @param value The header value.
   * @return A reference to this, so multiple method calls can be chained.
   */
  HttpServerResponse putHeader(String name, String value);
  HttpServerResponse putHeader(CharSequence name, CharSequence value);

  /**
   * Put an HTTP header - fluent API
   * @param name    The header name
   * @param values  The header values.
   * @return A reference to this, so multiple method calls can be chained.
   */
  HttpServerResponse putHeader(String name, Iterable<String> values);
  HttpServerResponse putHeader(CharSequence name, Iterable<CharSequence> values);


  /**
   * @return The HTTP trailers
   */
  MultiMap trailers();

  /**
   * Put an HTTP trailer - fluent API
   * @param name The trailer name
   * @param value The trailer value
   * @return A reference to this, so multiple method calls can be chained.
   */
  HttpServerResponse putTrailer(String name, String value);
  HttpServerResponse putTrailer(CharSequence name, CharSequence value);

  /**
   * Put an HTTP trailer - fluent API
   * @param name    The trailer name
   * @param values  The trailer values
   * @return A reference to this, so multiple method calls can be chained.
   */
  HttpServerResponse putTrailer(String name, Iterable<String> values);
  HttpServerResponse putTrailer(CharSequence name, Iterable<CharSequence> value);

  /**
   * Set a close handler for the response. This will be called if the underlying connection closes before the response
   * is complete.
   * @param handler
   */
  HttpServerResponse closeHandler(Handler<Void> handler);

  /**
   * Write a {@link Buffer} to the response body.
   *
   * @return A reference to this, so multiple method calls can be chained.
   */
  HttpServerResponse write(Buffer chunk);

  /**
   * Write a {@link String} to the response body, encoded using the encoding {@code enc}.
   *
   * @return A reference to this, so multiple method calls can be chained.
   */
  HttpServerResponse write(String chunk, String enc);

  /**
   * Write a {@link String} to the response body, encoded in UTF-8.
   *
   * @return A reference to this, so multiple method calls can be chained.
   */
  HttpServerResponse write(String chunk);

  /**
   * Same as {@link #end(Buffer)} but writes a String with the default encoding before ending the response.
   */
  void end(String chunk);

  /**
   * Same as {@link #end(Buffer)} but writes a String with the specified encoding before ending the response.
   */
  void end(String chunk, String enc);

  /**
   * Same as {@link #end()} but writes some data to the response body before ending. If the response is not chunked and
   * no other data has been written then the Content-Length header will be automatically set
   */
  void end(Buffer chunk);

  /**
   * Ends the response. If no data has been written to the response body,
   * the actual response won't get written until this method gets called.<p>
   * Once the response has ended, it cannot be used any more.
   */
  void end();

  /**
   * Tell the kernel to stream a file as specified by {@code filename} directly
   * from disk to the outgoing connection, bypassing userspace altogether
   * (where supported by the underlying operating system.
   * This is a very efficient way to serve files.<p>
   */
  HttpServerResponse sendFile(String filename);

  /**
   * Same as {@link #sendFile(String)} but also takes the path to a resource to serve if the resource is not found
   */
  HttpServerResponse sendFile(String filename, String notFoundFile);

  /**
   * Same as {@link #sendFile(String)} but also takes a handler that will be called when the send has completed or
   * a failure has occurred
   */
  HttpServerResponse sendFile(String filename, Handler<AsyncResult<Void>> resultHandler);

  /**
   * Same as {@link #sendFile(String, String)} but also takes a handler that will be called when the send has completed or
   * a failure has occurred
   */
  HttpServerResponse sendFile(String filename, String notFoundFile, Handler<AsyncResult<Void>> resultHandler);

  /**
   * Close the underlying TCP connection
   */
  void close();

}
