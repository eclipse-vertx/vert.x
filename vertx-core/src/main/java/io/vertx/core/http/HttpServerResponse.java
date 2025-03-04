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
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.MultiMap;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.net.HostAndPort;
import io.vertx.core.streams.ReadStream;
import io.vertx.core.streams.WriteStream;

import java.util.Set;

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
 * {@link io.vertx.core.streams.Pipe} to pipe data with flow control.
 *
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
@VertxGen
public interface HttpServerResponse extends WriteStream<Buffer> {

  @Override
  HttpServerResponse exceptionHandler(Handler<Throwable> handler);

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
  @GenIgnore(GenIgnore.PERMITTED_TYPE)
  @Fluent
  HttpServerResponse putHeader(CharSequence name, CharSequence value);

  /**
   * Like {@link #putHeader(String, String)} but providing multiple values via a String Iterable
   */
  @GenIgnore(GenIgnore.PERMITTED_TYPE)
  @Fluent
  HttpServerResponse putHeader(String name, Iterable<String> values);

  /**
   * Like {@link #putHeader(String, Iterable)} but with CharSequence Iterable
   */
  @GenIgnore(GenIgnore.PERMITTED_TYPE)
  @Fluent
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
  @GenIgnore(GenIgnore.PERMITTED_TYPE)
  @Fluent
  HttpServerResponse putTrailer(CharSequence name, CharSequence value);

  /**
   * Like {@link #putTrailer(String, String)} but providing multiple values via a String Iterable
   */
  @GenIgnore(GenIgnore.PERMITTED_TYPE)
  @Fluent
  HttpServerResponse putTrailer(String name, Iterable<String> values);

  /**
   * Like {@link #putTrailer(String, Iterable)} but with CharSequence Iterable
   */
  @GenIgnore(GenIgnore.PERMITTED_TYPE)
  @Fluent
  HttpServerResponse putTrailer(CharSequence name, Iterable<CharSequence> value);

  /**
   * Set a close handler for the response, this is called when the underlying connection is closed and the response
   * was still using the connection.
   * <p>
   * For HTTP/1.x it is called when the connection is closed before {@code end()} is called, therefore it is not
   * guaranteed to be called.
   * <p>
   * For HTTP/2 it is called when the related stream is closed, and therefore it will be always be called.
   *
   * @param handler  the handler
   * @return a reference to this, so the API can be used fluently
   */
  @Fluent
  HttpServerResponse closeHandler(@Nullable Handler<Void> handler);

  /**
   * Set an end handler for the response. This will be called when the response is disposed to allow consistent cleanup
   * of the response.
   *
   * @param handler  the handler
   * @return a reference to this, so the API can be used fluently
   */
  @Fluent
  HttpServerResponse endHandler(@Nullable Handler<Void> handler);

  /**
   * Write a {@link String} to the response body, encoded using the encoding {@code enc}.
   *
   * @param chunk  the string to write
   * @param enc  the encoding to use
   * @return a future completed with the body result
   */
  Future<Void> write(String chunk, String enc);

  /**
   * Write a {@link String} to the response body, encoded in UTF-8.
   *
   * @param chunk  the string to write
   * @return a future completed with the body result
   */
  Future<Void> write(String chunk);

  /**
   * Used to write an interim 100 Continue response to signify that the client should send the rest of the request.
   * Must only be used if the request contains an "Expect:100-Continue" header
   * @return a reference to this, so the API can be used fluently
   */
  Future<Void> writeContinue();

  /**
   * Used to write an interim 103 Early Hints response to return some HTTP headers before the final HTTP message.
   *
   * @param headers  headers to write
   * @return a future
   */
  Future<Void> writeEarlyHints(MultiMap headers);

  /**
   * Same as {@link #end(Buffer)} but writes a String in UTF-8 encoding before ending the response.
   *
   * @param chunk  the string to write before ending the response
   * @return a future completed with the body result
   */
  Future<Void> end(String chunk);

  /**
   * Same as {@link #end(Buffer)} but writes a String with the specified encoding before ending the response.
   *
   * @param chunk  the string to write before ending the response
   * @param enc  the encoding to use
   * @return a future completed with the body result
   */
  Future<Void> end(String chunk, String enc);

  /**
   * Same as {@link #end()} but writes some data to the response body before ending. If the response is not chunked and
   * no other data has been written then the @code{Content-Length} header will be automatically set.
   *
   * @param chunk  the buffer to write before ending the response
   * @return a future completed with the body result
   */
  @Override
  Future<Void> end(Buffer chunk);

  /**
   * Ends the response. If no data has been written to the response body,
   * the actual response won't get written until this method gets called.
   * <p>
   * Once the response has ended, it cannot be used any more.
   *
   * @return a future completed with the body result
   */
  @Override
  Future<Void> end();

  /**
   * Send the request with an empty body.
   *
   * @return a future notified when the response has been written
   */
  default Future<Void> send() {
    return end();
  }

  /**
   * Send the request with a string {@code body}.
   *
   * @return a future notified when the response has been written
   */
  default Future<Void> send(String body) {
    return end(body);
  }

  /**
   * Send the request with a buffer {@code body}.
   *
   * @return a future notified when the response has been written
   */
  default Future<Void> send(Buffer body) {
    return end(body);
  }

  /**
   * Send the request with a stream {@code body}.
   *
   * <p> If the {@link HttpHeaders#CONTENT_LENGTH} is set then the request assumes this is the
   * length of the {stream}, otherwise the request will set a chunked {@link HttpHeaders#CONTENT_ENCODING}.
   *
   * @return a future notified when the last bytes of the response was sent
   */
  default Future<Void> send(ReadStream<Buffer> body) {
    MultiMap headers = headers();
    if (headers == null || !headers.contains(HttpHeaders.CONTENT_LENGTH)) {
      setChunked(true);
    }
    return body.pipeTo(this);
  }

  /**
   * Send the request with a stream {@code body}.
   *
   * <p> If the {@link HttpHeaders#CONTENT_LENGTH} is set then the request assumes this is the
   * length of the {stream}, otherwise the request will set a chunked {@link HttpHeaders#CONTENT_ENCODING}.
   *
   * @return a future notified when the response has been written
   */
  default Future<Void> sendFile(String filename) {
    return sendFile(filename, 0);
  }

  /**
   * Same as {@link #sendFile(String, long, long)} using length @code{Long.MAX_VALUE} which means until the end of the
   * file.
   *
   * @param filename  path to the file to serve
   * @param offset offset to start serving from
   * @return a future completed with the body result
   */
  default Future<Void> sendFile(String filename, long offset) {
    return sendFile(filename, offset, Long.MAX_VALUE);
  }

  /**
   * Ask the OS to stream a file as specified by {@code filename} directly
   * from disk to the outgoing connection, bypassing userspace altogether
   * (where supported by the underlying operating system.
   * This is a very efficient way to serve files.<p>
   * The actual serve is asynchronous and may not complete until some time after this method has returned.
   *
   * @param filename  path to the file to serve
   * @param offset offset to start serving from
   * @param length the number of bytes to send
   * @return a future completed with the body result
   */
  Future<Void> sendFile(String filename, long offset, long length);

  /**
   * @return has the response already ended?
   */
  boolean ended();

  /**
   * @return has the underlying TCP connection corresponding to the request already been closed?
   */
  boolean closed();

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
  HttpServerResponse headersEndHandler(@Nullable Handler<Void> handler);

  /**
   * Provides a handler that will be called after the last part of the body is written to the wire.
   * The handler is called asynchronously of when the response has been received by the client.
   * This provides a hook allowing you to do more operations once the request has been sent over the wire
   * such as resource cleanup.
   *
   * @param handler  the handler
   * @return a reference to this, so the API can be used fluently
   */
  @Fluent
  HttpServerResponse bodyEndHandler(@Nullable Handler<Void> handler);

  /**
   * @return the total number of bytes written for the body of the response.
   */
  long bytesWritten();

  /**
   * @return the id of the stream of this response, {@literal -1} for HTTP/1.x
   */
  int streamId();

  /**
   * Like {@link #push(HttpMethod, HostAndPort, String, MultiMap)} with no headers.
   */
  default Future<HttpServerResponse> push(HttpMethod method, HostAndPort authority, String path) {
    return push(method, authority, path, null);
  }

  /**
   * Like {@link #push(HttpMethod, HostAndPort, String, MultiMap)} with the host copied from the current request.
   */
  default Future<HttpServerResponse> push(HttpMethod method, String path, MultiMap headers) {
    return push(method, null, path, headers);
  }

  /**
   * Like {@link #push(HttpMethod, HostAndPort, String, MultiMap)} with the host copied from the current request.
   */
  default Future<HttpServerResponse> push(HttpMethod method, String path) {
    return push(method, null, path);
  }

  /**
   * Push a response to the client.<p/>
   *
   * The {@code handler} will be notified with a <i>success</i> when the push can be sent and with
   * a <i>failure</i> when the client has disabled push or reset the push before it has been sent.<p/>
   *
   * The {@code handler} may be queued if the client has reduced the maximum number of streams the server can push
   * concurrently.<p/>
   *
   * Push can be sent only for peer initiated streams and if the response is not ended.
   *
   * @param method the method of the promised request
   * @param authority the authority of the promised request
   * @param path the path of the promised request
   * @param headers the headers of the promised request
   * @return a future notified when the response can be written
   */
  Future<HttpServerResponse> push(HttpMethod method, HostAndPort authority, String path, MultiMap headers);

  /**
   * Reset this HTTP/2 stream with the error code {@code 0}.
   */
  default Future<Void> reset() {
    return reset(0L);
  }

  /**
   * Reset this response:
   * <p/>
   * <ul>
   *   <li>for HTTP/2, send an HTTP/2 reset frame with the specified error {@code code}</li>
   *   <li>for HTTP/1.x, close the connection when the current response has not yet been sent</li>
   * </ul>
   * <p/>
   * When the response has already been sent nothing happens and {@code false} is returned as indicator.
   *
   * @param code the error code
   * @return {@code true} when reset has been performed
   */
  Future<Void> reset(long code);

  /**
   * Write an HTTP/2 frame to the response, allowing to extend the HTTP/2 protocol.<p>
   *
   * The frame is sent immediatly and is not subject to flow control.
   *
   * @param type the 8-bit frame type
   * @param flags the 8-bit frame flags
   * @param payload the frame payload
   * @return a reference to this, so the API can be used fluently
   */
  Future<Void> writeCustomFrame(int type, int flags, Buffer payload);

  /**
   * Like {@link #writeCustomFrame(int, int, Buffer)} but with an {@link HttpFrame}.
   *
   * @param frame the frame to write
   */
  default Future<Void> writeCustomFrame(HttpFrame frame) {
    return writeCustomFrame(frame.type(), frame.flags(), frame.payload());
  }

  /**
   * Sets the priority of the associated stream
   * <p/>
   * This is not implemented for HTTP/1.x.
   *
   * @param streamPriority the priority for this request's stream
   */
  @Fluent
  default HttpServerResponse setStreamPriority(StreamPriorityBase streamPriority) {
      return this;
  }

  /**
   * Add a cookie. This will be sent back to the client in the response.
   *
   * @param cookie  the cookie
   * @return a reference to this, so the API can be used fluently
   */
  @Fluent
  HttpServerResponse addCookie(Cookie cookie);


  /**
   * Expire a cookie, notifying a User Agent to remove it from its cookie jar.
   *
   * NOTE: This method will only remove the first occurrence of the given name. Users probably may want to use:
   * {@link #removeCookies(String)}
   *
   * @param name  the name of the cookie
   * @return the cookie, if it existed, or null
   */
  default @Nullable Cookie removeCookie(String name) {
    return removeCookie(name, true);
  }

  /**
   * Remove a cookie from the cookie set. If invalidate is {@code true} then it will expire a cookie, notifying a User
   * Agent to remove it from its cookie jar.
   *
   * NOTE: This method will only expire the first occurrence of the given name. Users probably may want to use:
   * {@link #removeCookies(String,boolean)}
   *
   * @param name  the name of the cookie
   * @return the cookie, if it existed, or {@code null}
   */
  @Nullable Cookie removeCookie(String name, boolean invalidate);

  /**
   * Expire all cookies, notifying a User Agent to remove it from its cookie jar.
   *
   * NOTE: the returned {@link Set} is read-only. This means any attempt to modify (add or remove to the set), will
   * throw {@link UnsupportedOperationException}.
   *
   * @param name  the name of the cookie
   * @return a read only set of affected cookies, if they existed, or an empty set.
   */
  default Set<Cookie> removeCookies(String name) {
    return removeCookies(name, true);
  }

  /**
   * Remove all cookies from the cookie set. If invalidate is {@code true} then it will expire a cookie, notifying a
   * User Agent to remove it from its cookie jar.
   *
   * NOTE: the returned {@link Set} is read-only. This means any attempt to modify (add or remove to the set), will
   * throw {@link UnsupportedOperationException}.
   *
   * @param name  the name of the cookie
   * @param invalidate invalidate from the user agent
   * @return a read only set of affected cookies, if they existed, or an empty set.
   */
  Set<Cookie> removeCookies(String name, boolean invalidate);

  /**
   * Expires a cookie from the cookie set. This will notify a User Agent to remove it from its cookie jar.
   *
   * @param name  the name of the cookie
   * @param domain  the domain of the cookie
   * @param path  the path of the cookie
   * @return the cookie, if it existed, or {@code null}
   */
  default @Nullable Cookie removeCookie(String name, String domain, String path) {
    return removeCookie(name, domain, path, true);
  }

  /**
   * Remove a cookie from the cookie set. If invalidate is {@code true} then it will expire a cookie, notifying a User
   * Agent to remove it from its cookie jar.
   *
   * @param name  the name of the cookie
   * @param domain  the domain of the cookie
   * @param path  the path of the cookie
   * @return the cookie, if it existed, or {@code null}
   */
  @Nullable Cookie removeCookie(String name, String domain, String path, boolean invalidate);
}
