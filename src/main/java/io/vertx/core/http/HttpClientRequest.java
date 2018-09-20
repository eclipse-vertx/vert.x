/*
 * Copyright (c) 2011-2017 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */

package io.vertx.core.http;

import io.vertx.codegen.annotations.Nullable;
import io.vertx.core.Handler;
import io.vertx.core.MultiMap;
import io.vertx.core.buffer.Buffer;
import io.vertx.codegen.annotations.CacheReturn;
import io.vertx.codegen.annotations.Fluent;
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
  HttpClientRequest fetch(long amount);

  @Override
  HttpClientRequest endHandler(Handler<Void> endHandler);

  @Fluent
  HttpClientRequest setFollowRedirects(boolean followRedirects);

  /**
   * If chunked is true then the request will be set into HTTP chunked mode
   *
   * @param chunked true if chunked encoding
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
   * @return the raw value of the method this request sends
   */
  String getRawMethod();

  /**
   * Set the value the method to send when the method {@link HttpMethod#OTHER} is used.
   *
   * @param method the raw method
   * @return a reference to this, so the API can be used fluently
   */
  @Fluent
  HttpClientRequest setRawMethod(String method);

  /**
   * @return the absolute URI corresponding to the the HTTP request
   */
  String absoluteURI();

  /**
   * @return The URI of the request.
   */
  String uri();

  /**
   * @return The path part of the uri. For example /somepath/somemorepath/someresource.foo
   */
  String path();

  /**
   * @return the query part of the uri. For example someparam=32&amp;someotherparam=x
   */
  String query();

  /**
   * Set the request host.<p/>
   *
   * For HTTP/2 it sets the {@literal :authority} pseudo header otherwise it sets the {@literal Host} header
   */
  @Fluent
  HttpClientRequest setHost(String host);

  /**
   * @return the request host. For HTTP/2 it returns the {@literal :authority} pseudo header otherwise it returns the {@literal Host} header
   */
  String getHost();

  /**
   * @return The HTTP headers
   */
  @CacheReturn
  MultiMap headers();

  /**
   * Put an HTTP header
   *
   * @param name  The header name
   * @param value The header value
   * @return a reference to this, so the API can be used fluently
   */
  @Fluent
  HttpClientRequest putHeader(String name, String value);

  /**
   * Like {@link #putHeader(String, String)} but using CharSequence
   */
  @SuppressWarnings("codegen-allow-any-java-type")
  @Fluent
  HttpClientRequest putHeader(CharSequence name, CharSequence value);

  /**
   * Put an HTTP header with multiple values
   *
   * @param name   The header name
   * @param values The header values
   * @return @return a reference to this, so the API can be used fluently
   */
  @SuppressWarnings("codegen-allow-any-java-type")
  @Fluent
  HttpClientRequest putHeader(String name, Iterable<String> values);

  /**
   * Like {@link #putHeader(String, Iterable)} but using CharSequence
   */
  @SuppressWarnings("codegen-allow-any-java-type")
  @Fluent
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
  HttpClientRequest continueHandler(@Nullable Handler<Void> handler);

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
   * Like {@link #sendHead()} but with an handler after headers have been sent. The handler will be called with
   * the {@link HttpVersion} if it can be determined or null otherwise.<p>
   */
  @Fluent
  HttpClientRequest sendHead(Handler<HttpVersion> completionHandler);

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
   * Set's the amount of time after which if the request does not return any data within the timeout period an
   * {@link java.util.concurrent.TimeoutException} will be passed to the exception handler (if provided) and
   * the request will be closed.
   * <p>
   * Calling this method more than once has the effect of canceling any existing timeout and starting
   * the timeout from scratch.
   *
   * @param timeoutMs The quantity of time in milliseconds.
   * @return a reference to this, so the API can be used fluently
   */
  @Fluent
  HttpClientRequest setTimeout(long timeoutMs);

  /**
   * Set a push handler for this request.<p/>
   *
   * The handler is called when the client receives a <i>push promise</i> from the server. The handler can be called
   * multiple times, for each push promise.<p/>
   *
   * The handler is called with a <i>read-only</i> {@link HttpClientRequest}, the following methods can be called:<p/>
   *
   * <ul>
   *   <li>{@link HttpClientRequest#method()}</li>
   *   <li>{@link HttpClientRequest#uri()}</li>
   *   <li>{@link HttpClientRequest#headers()}</li>
   *   <li>{@link HttpClientRequest#getHost()}</li>
   * </ul>
   *
   * In addition the handler should call the {@link HttpClientRequest#handler} method to set an handler to
   * process the response.<p/>
   *
   * @param handler the handler
   * @return a reference to this, so the API can be used fluently
   */
  @Fluent
  HttpClientRequest pushHandler(Handler<HttpClientRequest> handler);

  /**
   * Reset this stream with the error code {@code 0}.
   *
   * @see #reset(long)
   */
  default boolean reset() {
    return reset(0L);
  }

  /**
   * Reset this request:
   * <p/>
   * <ul>
   *   <li>for HTTP/2, this performs send an HTTP/2 reset frame with the specified error {@code code}</li>
   *   <li>for HTTP/1.x, this closes the connection when the current request is inflight</li>
   * </ul>
   * <p/>
   * When the request has not yet been sent, the request will be aborted and false is returned as indicator.
   * <p/>
   *
   * @param code the error code
   * @return true when reset has been performed
   */
  boolean reset(long code);

  /**
   * @return the {@link HttpConnection} associated with this request
   */
  @CacheReturn
  HttpConnection connection();

  /**
   * Set a connection handler called when an HTTP connection has been established.
   *
   * @param handler the handler
   * @return a reference to this, so the API can be used fluently
   */
  @Fluent
  HttpClientRequest connectionHandler(@Nullable Handler<HttpConnection> handler);

  /**
   * Write an HTTP/2 frame to the request, allowing to extend the HTTP/2 protocol.<p>
   *
   * The frame is sent immediatly and is not subject to flow control.<p>
   *
   * This method must be called after the request headers have been sent and only for the protocol HTTP/2.
   * The {@link #sendHead(Handler)} should be used for this purpose.
   *
   * @param type the 8-bit frame type
   * @param flags the 8-bit frame flags
   * @param payload the frame payload
   * @return a reference to this, so the API can be used fluently
   */
  @Fluent
  HttpClientRequest writeCustomFrame(int type, int flags, Buffer payload);

  /**
   * @return the id of the stream of this response, {@literal -1} when it is not yet determined, i.e
   *         the request has not been yet sent or it is not supported HTTP/1.x
   */
  default int streamId() {
    return -1;
  }

  /**
   * Like {@link #writeCustomFrame(int, int, Buffer)} but with an {@link HttpFrame}.
   *
   * @param frame the frame to write
   */
  @Fluent
  default HttpClientRequest writeCustomFrame(HttpFrame frame) {
    return writeCustomFrame(frame.type(), frame.flags(), frame.payload());
  }
}
