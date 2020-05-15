/*
 * Copyright (c) 2011-2019 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */

package io.vertx.core.http;

import io.vertx.codegen.annotations.*;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.MultiMap;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.net.NetSocket;
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
public interface HttpClientRequest extends WriteStream<Buffer>, Future<HttpClientResponse> {

  @Override
  HttpClientRequest exceptionHandler(Handler<Throwable> handler);

  @Override
  HttpClientRequest setWriteQueueMaxSize(int maxSize);

  @Override
  HttpClientRequest drainHandler(Handler<Void> handler);

  /**
   * Set the request to follow HTTP redirects up to {@link HttpClientOptions#getMaxRedirects()}.
   *
   * @param followRedirects {@code true} to follow HTTP redirects
   * @return a reference to this, so the API can be used fluently
   */
  @Fluent
  HttpClientRequest setFollowRedirects(boolean followRedirects);

  /**
   * Set the max number of HTTP redirects this request will follow. The default is {@code 0} which means
   * no redirects.
   *
   * @param maxRedirects the number of HTTP redirect to follow
   * @return a reference to this, so the API can be used fluently
   */
  @Fluent
  HttpClientRequest setMaxRedirects(int maxRedirects);

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
   * For HTTP/2 it sets the {@literal :authority} pseudo header otherwise it sets the {@literal Host} header
   */
  @Fluent
  HttpClientRequest setAuthority(String authority);

  /**
   * @return the request host. For HTTP/2 it returns the {@literal :authority} pseudo header otherwise it returns the {@literal Host} header
   */
  String getAuthority();

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
  @GenIgnore(GenIgnore.PERMITTED_TYPE)
  @Fluent
  HttpClientRequest putHeader(CharSequence name, CharSequence value);

  /**
   * Put an HTTP header with multiple values
   *
   * @param name   The header name
   * @param values The header values
   * @return @return a reference to this, so the API can be used fluently
   */
  @GenIgnore(GenIgnore.PERMITTED_TYPE)
  @Fluent
  HttpClientRequest putHeader(String name, Iterable<String> values);

  /**
   * Like {@link #putHeader(String, Iterable)} but using CharSequence
   */
  @GenIgnore(GenIgnore.PERMITTED_TYPE)
  @Fluent
  HttpClientRequest putHeader(CharSequence name, Iterable<CharSequence> values);

  /**
   * Write a {@link String} to the request body, encoded as UTF-8.
   *
   * @param chunk the data chunk
   * @return a future completed with the result
   * @throws java.lang.IllegalStateException when no response handler is set
   */
  Future<Void> write(String chunk);

  /**
   * Same as {@link #write(String)} but with an {@code handler} called when the operation completes
   */
  void write(String chunk, Handler<AsyncResult<Void>> handler);

  /**
   * Write a {@link String} to the request body, encoded using the encoding {@code enc}.
   *
   * @param chunk the data chunk
   * @param enc the encoding
   * @return a future completed with the result
   * @throws java.lang.IllegalStateException when no response handler is set
   */
  Future<Void> write(String chunk, String enc);

  /**
   * Same as {@link #write(String,String)} but with an {@code handler} called when the operation completes
   */
  void write(String chunk, String enc, Handler<AsyncResult<Void>> handler);

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
  Future<HttpVersion> sendHead();

  /**
   * Like {@link #sendHead()} but with an handler after headers have been sent. The handler will be called with
   * the {@link HttpVersion} if it can be determined or null otherwise.<p>
   */
  @Fluent
  HttpClientRequest sendHead(Handler<AsyncResult<HttpVersion>> completionHandler);

  /**
   * Same as {@link #end(Buffer)} but writes a String in UTF-8 encoding
   *
   * @param chunk the data chunk
   * @return a future completed with the result
   * @throws java.lang.IllegalStateException when no response handler is set
   */
  Future<Void> end(String chunk);

  /**
   * Same as {@link #end(String)} but with an {@code handler} called when the operation completes
   */
  void end(String chunk, Handler<AsyncResult<Void>> handler);

  /**
   * Same as {@link #end(Buffer)} but writes a String with the specified encoding
   *
   * @param chunk the data chunk
   * @param enc the encoding
   * @return a future completed with the result
   * @throws java.lang.IllegalStateException when no response handler is set
   */
  Future<Void> end(String chunk, String enc);

  /**
   * Same as {@link #end(String,String)} but with an {@code handler} called when the operation completes
   */
  void end(String chunk, String enc, Handler<AsyncResult<Void>> handler);

  /**
   * Same as {@link #end()} but writes some data to the request body before ending. If the request is not chunked and
   * no other data has been written then the {@code Content-Length} header will be automatically set
   *
   * @return a future completed with the result
   * @throws java.lang.IllegalStateException when no response handler is set
   */
  @Override
  Future<Void> end(Buffer chunk);

  /**
   * Same as {@link #end(String)} but with an {@code handler} called when the operation completes
   */
  @Override
  void end(Buffer chunk, Handler<AsyncResult<Void>> handler);

  /**
   * Ends the request. If no data has been written to the request body, and {@link #sendHead()} has not been called then
   * the actual request won't get written until this method gets called.
   * <p>
   * Once the request has ended, it cannot be used any more,
   *
   * @return a future completed with the result
   * @throws java.lang.IllegalStateException when no response handler is set
   */
  @Override
  Future<Void> end();

  /**
   * Same as {@link #end()} but with an {@code handler} called when the operation completes
   */
  @Override
  void end(Handler<AsyncResult<Void>> handler);

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
   *   <li>{@link HttpClientRequest#getAuthority()}</li>
   * </ul>
   *
   * In addition the handler should call the {@link HttpClientRequest#setHandler} method to set an handler to
   * process the response.<p/>
   *
   * @param handler the handler
   * @return a reference to this, so the API can be used fluently
   */
  @Fluent
  HttpClientRequest pushHandler(Handler<HttpClientRequest> handler);

  /**
   * Get a {@link NetSocket} for the underlying connection of this request.
   * <p>
   * The {@code handler} is called after the response headers are received.
   * <p>
   * This shall be used when using a {@link HttpMethod#CONNECT} method.
   * <p>
   * HTTP/1.1 pipe-lined requests cannot support net socket upgrade.
   * <p>
   * Pooled connection is removed from the pool.
   *
   * @param handler the handler
   * @return a reference to this, so the API can be used fluently
   */
  @Fluent
  default HttpClientRequest netSocket(Handler<AsyncResult<NetSocket>> handler) {
    Future<NetSocket> fut = netSocket();
    if (handler != null) {
      fut.onComplete(handler);
    }
    return this;
  }

  /**
   * Like {@link #netSocket(Handler)} but returns a {@code Future} of the asynchronous result
   */
  default Future<NetSocket> netSocket() {
    return Future.failedFuture("Cannot use socket connect");
  }

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
   * @param cause an optional cause that can be attached to the error code
   * @return true when reset has been performed
   */
  boolean reset(long code, Throwable cause);

  /**
   * @return the {@link HttpConnection} associated with this request
   */
  @CacheReturn
  HttpConnection connection();

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

  /**
   * Sets the priority of the associated stream.
   * <p/>
   * This is not implemented for HTTP/1.x.
   *
   * @param streamPriority the priority of this request's stream
   */
  @Fluent
  default HttpClientRequest setStreamPriority(StreamPriority streamPriority) {
      return this;
  }

  /**
   * @return the priority of the associated HTTP/2 stream for HTTP/2 otherwise {@code null}
   */
  StreamPriority getStreamPriority();

  @Override
  HttpClientRequest onComplete(Handler<AsyncResult<HttpClientResponse>> handler);

  @Override
  default HttpClientRequest onSuccess(Handler<HttpClientResponse> handler) {
    return (HttpClientRequest) Future.super.onSuccess(handler);
  }

  @Override
  default HttpClientRequest onFailure(Handler<Throwable> handler) {
    return (HttpClientRequest) Future.super.onFailure(handler);
  }
}
