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
import io.vertx.codegen.annotations.VertxGen;
import io.vertx.core.net.NetSocket;
import io.vertx.core.streams.ReadStream;

import java.util.List;

/**
 * Represents a client-side HTTP response.<p>
 * An instance is provided to the user via a {@link io.vertx.core.Handler}
 * instance that was specified when one of the HTTP method operations, or the
 * generic {@link HttpClient#request(String, String, io.vertx.core.Handler)}
 * method was called on an instance of {@link HttpClient}.<p>
 * It implements {@link io.vertx.core.streams.ReadStream} so it can be used with
 * {@link io.vertx.core.streams.Pump} to pump data with flow control.<p>
 * Instances of this class are not thread-safe.<p>
 *
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
@VertxGen
public interface HttpClientResponse extends ReadStream<Buffer> {

  @Override
  HttpClientResponse resume();

  @Override
  HttpClientResponse exceptionHandler(Handler<Throwable> handler);

  @Override
  HttpClientResponse handler(Handler<Buffer> handler);

  @Override
  HttpClientResponse pause();

  @Override
  HttpClientResponse endHandler(Handler<Void> endHandler);

  /**
   * The HTTP status code of the response
   */
  int statusCode();

  /**
   * The HTTP status message of the response
   */
  String statusMessage();

  /**
   * @return The HTTP headers
   */
  @CacheReturn
  MultiMap headers();

  /**
   * @return The HTTP trailers
   */
  @CacheReturn
  MultiMap trailers();

  /**
   * @return The Set-Cookie headers (including trailers)
   */
  @CacheReturn
  List<String> cookies();

  /**
   * Convenience method for receiving the entire request body in one piece. This saves the user having to manually
   * set a data and end handler and append the chunks of the body until the whole body received.
   * Don't use this if your request body is large - you could potentially run out of RAM.
   *
   * @param bodyHandler This handler will be called after all the body has been received
   */
  @Fluent
  HttpClientResponse bodyHandler(Handler<Buffer> bodyHandler);

  /**
   * Get a net socket for the underlying connection of this request. USE THIS WITH CAUTION!
   * Writing to the socket directly if you don't know what you're doing can easily break the HTTP protocol
   *
   * One valid use-case for calling this is to receive the {@link io.vertx.core.net.NetSocket} after a HTTP CONNECT was issued to the
   * remote peer and it responded with a status code of 200.
   *
   * @return the net socket
   */
  @CacheReturn
  NetSocket netSocket();

}
