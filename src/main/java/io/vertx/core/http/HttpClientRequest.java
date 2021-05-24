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
 * {@link io.vertx.core.streams.Pipe} to pipe data with flow control.
 * <p>
 * An example of using this class is as follows:
 * <p>
 *
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
@VertxGen
public interface HttpClientRequest extends WriteStream<Buffer> {

  @Override
  HttpClientRequest exceptionHandler(Handler<Throwable> handler);

  @Override
  HttpClientRequest setWriteQueueMaxSize(int maxSize);

  @Override
  HttpClientRequest drainHandler(Handler<Void> handler);

  /**
   * Set the host value of the HTTP/1.1 {@code host} header or HTTP/2 {@code authority} pseudo header
   * <p>The initial value is the same than the server socket address host.
   * <p>Keep in mind that changing this value won't change the actual server socket address for this request.
   *
   * @param host the host part of the HTTP/1.1 {@code host} header or HTTP/2 {@code authority} pseudo header
   * @return a reference to this, so the API can be used fluently
   */
  @Fluent
  HttpClientRequest setHost(String host);

  /**
   * @return the host value of the HTTP/1.1 {@code host} header or HTTP/2 {@code authority} pseudo header
   */
  String getHost();

  /**
   * Set the port value of the HTTP/1.1 {@code host} header or HTTP/2 {@code authority} pseudo header
   *
   * <p> Keep in mind that this won't change the actual server socket address for this request.
   * <p>The initial value is the same than the server socket address port.
   *
   * @param port the port part of the HTTP/1.1 {@code host} header or HTTP/2 {@code authority} pseudo header
   * @return a reference to this, so the API can be used fluently
   */
  @Fluent
  HttpClientRequest setPort(int port);

  /**
   * @return the port value of the HTTP/1.1 {@code host} header or HTTP/2 {@code authority} pseudo header
   */
  int getPort();

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
  HttpMethod getMethod();

  /**
   * Set the HTTP method for this request.
   *
   * @param method the HTTP method
   * @return a reference to this, so the API can be used fluently
   */
  @Fluent
  HttpClientRequest setMethod(HttpMethod method);

  /**
   * @return the absolute URI corresponding to the HTTP request
   */
  String absoluteURI();

  /**
   * @return The URI of the request.
   */
  String getURI();

  /**
   * Set the request uri.
   *
   * @param uri the request uri
   * @return a reference to this, so the API can be used fluently
   */
  @Fluent
  HttpClientRequest setURI(String uri);

  /**
   * @return The path part of the uri. For example /somepath/somemorepath/someresource.foo
   */
  String path();

  /**
   * @return the query part of the uri. For example someparam=32&amp;someotherparam=x
   */
  String query();

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
   * @return the HTTP version for this request
   */
  HttpVersion version();

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
  Future<Void> sendHead();

  /**
   * Like {@link #sendHead()} but with an handler after headers have been sent. The handler will be called with
   * the {@link HttpVersion} if it can be determined or null otherwise.<p>
   */
  @Fluent
  HttpClientRequest sendHead(Handler<AsyncResult<Void>> completionHandler);

  /**
   * Create an HTTP tunnel to the server.
   *
   * <p> Send an HTTP request to the server, then configures the transport to exchange
   * raw buffers when the server replies with an appropriate response:
   *
   * <ul>
   *   <li>{@code 200} for HTTP {@code CONNECT} method</li>
   *   <li>{@code 101} for HTTP/1.1 {@code GET} with {@code Upgrade} {@code connection} header</li>
   * </ul>
   *
   * <p> The {@code handler} is called after response headers are received.
   *
   * <p> Use {@link HttpClientResponse#netSocket} to get a {@link NetSocket} for the interacting
   * more conveniently with the server.
   *
   * <p> HTTP/1.1 pipe-lined requests are not supported.
   *
   * @param handler the response completion handler
   */
  void connect(Handler<AsyncResult<HttpClientResponse>> handler);

  /**
   * Like {@link #connect(Handler)} but returns a {@code Future} of the asynchronous result
   */
  Future<HttpClientResponse> connect();

  /**
   * Set a callback for the associated {@link HttpClientResponse}.
   *
   * <p> This method does not modify the current request being sent.
   *
   * @param handler the completion handler
   * @return a reference to this, so the API can be used fluently
   */
  @Fluent
  HttpClientRequest response(Handler<AsyncResult<HttpClientResponse>> handler);

  /**
   * @return a future of the {@link HttpClientResponse}, see {@link #response(Handler)}
   */
  Future<HttpClientResponse> response();

  /**
   * Send the request with an empty body.
   *
   * @param handler the completion handler for the {@link HttpClientResponse}
   */
  default void send(Handler<AsyncResult<HttpClientResponse>> handler) {
    response(handler);
    end();
  }

  /**
   * Like {@link #send(Handler)} but returns a {@code Future} of the asynchronous result
   */
  default Future<HttpClientResponse> send() {
    end();
    return response();
  }

  /**
   * Send the request with a string {@code body}.
   *
   * @param handler the completion handler for the {@link HttpClientResponse}
   */
  default void send(String body, Handler<AsyncResult<HttpClientResponse>> handler) {
    response(handler);
    end(body);
  }

  /**
   * Like {@link #send(String, Handler)} but returns a {@code Future} of the asynchronous result
   */
  default Future<HttpClientResponse> send(String body) {
    end(body);
    return response();
  }

  /**
   * Send the request with a buffer {@code body}.
   *
   * @param handler the completion handler for the {@link HttpClientResponse}
   */
  default void send(Buffer body, Handler<AsyncResult<HttpClientResponse>> handler) {
    response(handler);
    end(body);
  }

  /**
   * Like {@link #send(Buffer, Handler)} but returns a {@code Future} of the asynchronous result
   */
  default Future<HttpClientResponse> send(Buffer body) {
    end(body);
    return response();
  }

  /**
   * Send the request with a stream {@code body}.
   *
   * <p> If the {@link HttpHeaders#CONTENT_LENGTH} is set then the request assumes this is the
   * length of the {stream}, otherwise the request will set a chunked {@link HttpHeaders#CONTENT_ENCODING}.
   *
   * @param handler the completion handler for the {@link HttpClientResponse}
   */
  default void send(ReadStream<Buffer> body, Handler<AsyncResult<HttpClientResponse>> handler) {
    MultiMap headers = headers();
    if (headers == null || !headers.contains(HttpHeaders.CONTENT_LENGTH)) {
      setChunked(true);
    }
    response(handler);
    body.pipeTo(this);
  }

  /**
   * Like {@link #send(ReadStream, Handler)} but returns a {@code Future} of the asynchronous result
   */
  default Future<HttpClientResponse> send(ReadStream<Buffer> body) {
    MultiMap headers = headers();
    if (headers == null || !headers.contains(HttpHeaders.CONTENT_LENGTH)) {
      setChunked(true);
    }
    body.pipeTo(this);
    return response();
  }

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
   *   <li>{@link HttpClientRequest#getMethod()}</li>
   *   <li>{@link HttpClientRequest#getURI()}</li>
   *   <li>{@link HttpClientRequest#headers()}</li>
   * </ul>
   *
   * In addition the handler should call the {@link HttpClientRequest#response(Handler)} method to set an handler to
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
   * @return {@code true} when reset has been performed
   */
  boolean reset(long code);

  /**
   * Reset this request:
   * <p/>
   * <ul>
   *   <li>for HTTP/2, send an HTTP/2 reset frame with the specified error {@code code}</li>
   *   <li>for HTTP/1.x, close the connection when the current request is inflight</li>
   * </ul>
   * <p/>
   * When the request has not yet been sent, the request will be aborted and {@code false} is returned as indicator.
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

}
