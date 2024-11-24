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

package io.vertx.core.http.impl;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpStatusClass;
import io.vertx.codegen.annotations.Nullable;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.MultiMap;
import io.vertx.core.Promise;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.*;
import io.vertx.core.internal.buffer.BufferInternal;
import io.vertx.core.http.impl.headers.Http2HeadersAdaptor;
import io.vertx.core.internal.PromiseInternal;
import io.vertx.core.net.HostAndPort;
import io.vertx.core.net.NetSocket;
import io.vertx.core.spi.observability.HttpResponse;
import io.vertx.core.streams.ReadStream;

import java.util.Map.Entry;
import java.util.Set;

import static io.vertx.core.http.HttpHeaders.SET_COOKIE;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class Http2ServerResponse implements HttpServerResponse, HttpResponse {

  private final Http2ServerStream stream;
  private final ChannelHandlerContext ctx;
  private final Http2ServerConnection conn;
  private final boolean push;
  private final Http2HeadersAdaptor headers = new Http2HeadersAdaptor();
  private Http2HeadersAdaptor headersMap;
  private Http2HeadersAdaptor trailers;
  private Http2HeadersAdaptor trailedMap;
  private boolean chunked;
  private boolean headWritten;
  private boolean ended;
  private boolean closed;
  private CookieJar cookies;
  private HttpResponseStatus status = HttpResponseStatus.OK;
  private String statusMessage; // Not really used but we keep the message for the getStatusMessage()
  private Handler<Void> drainHandler;
  private Handler<Throwable> exceptionHandler;
  private Handler<Void> headersEndHandler;
  private Handler<Void> bodyEndHandler;
  private Handler<Void> closeHandler;
  private Handler<Void> endHandler;
  private Future<NetSocket> netSocket;

  public Http2ServerResponse(Http2ServerConnection conn,
                             Http2ServerStream stream,
                             boolean push) {
    this.stream = stream;
    this.ctx = conn.handlerContext;
    this.conn = conn;
    this.push = push;
  }

  boolean isPush() {
    return push;
  }

  void handleReset(long code) {
    handleException(new StreamResetException(code));
  }

  void handleException(Throwable cause) {
    Handler<Throwable> handler;
    synchronized (conn) {
      if (ended) {
        return;
      }
      handler = exceptionHandler;
    }
    if (handler != null) {
      handler.handle(cause);
    }
  }

  void handleClose() {
    Handler<Throwable> exceptionHandler;
    Handler<Void> endHandler;
    Handler<Void> closeHandler;
    synchronized (conn) {
      closed = true;
      boolean failed = !ended;
      endHandler = failed ? this.endHandler : null;
      closeHandler = this.closeHandler;
    }
    if (endHandler != null) {
      stream.context.emit(null, endHandler);
    }
    if (closeHandler != null) {
      stream.context.emit(null, closeHandler);
    }
  }

  private void checkHeadWritten() {
    if (headWritten) {
      throw new IllegalStateException("Response head already sent");
    }
  }

  @Override
  public HttpServerResponse exceptionHandler(Handler<Throwable> handler) {
    synchronized (conn) {
      if (handler != null) {
        checkValid();
      }
      exceptionHandler = handler;
      return this;
    }
  }

  @Override
  public int statusCode() {
    return getStatusCode();
  }

  @Override
  public int getStatusCode() {
    synchronized (conn) {
      return status.code();
    }
  }

  @Override
  public HttpServerResponse setStatusCode(int statusCode) {
    if (statusCode < 0) {
      throw new IllegalArgumentException("code: " + statusCode + " (expected: 0+)");
    }
    synchronized (conn) {
      checkHeadWritten();
      this.status = HttpResponseStatus.valueOf(statusCode);
      return this;
    }
  }

  @Override
  public String getStatusMessage() {
    synchronized (conn) {
      if (statusMessage == null) {
        return status.reasonPhrase();
      }
      return statusMessage;
    }
  }

  @Override
  public HttpServerResponse setStatusMessage(String statusMessage) {
    synchronized (conn) {
      checkHeadWritten();
      this.statusMessage = statusMessage;
      return this;
    }
  }

  @Override
  public HttpServerResponse setChunked(boolean chunked) {
    synchronized (conn) {
      checkHeadWritten();
      this.chunked = true;
      return this;
    }
  }

  @Override
  public boolean isChunked() {
    synchronized (conn) {
      return chunked;
    }
  }

  @Override
  public MultiMap headers() {
    synchronized (conn) {
      if (headersMap == null) {
        headersMap = new Http2HeadersAdaptor(headers);
      }
      return headersMap;
    }
  }

  @Override
  public HttpServerResponse putHeader(String name, String value) {
    synchronized (conn) {
      checkHeadWritten();
      headers().set(name, value);
      return this;
    }
  }

  @Override
  public HttpServerResponse putHeader(CharSequence name, CharSequence value) {
    synchronized (conn) {
      checkHeadWritten();
      headers().set(name, value);
      return this;
    }
  }

  @Override
  public HttpServerResponse putHeader(String name, Iterable<String> values) {
    synchronized (conn) {
      checkHeadWritten();
      headers().set(name, values);
      return this;
    }
  }

  @Override
  public HttpServerResponse putHeader(CharSequence name, Iterable<CharSequence> values) {
    synchronized (conn) {
      checkHeadWritten();
      headers().set(name, values);
      return this;
    }
  }

  @Override
  public MultiMap trailers() {
    synchronized (conn) {
      if (trailedMap == null) {
        trailedMap = new Http2HeadersAdaptor(trailers = new Http2HeadersAdaptor());
      }
      return trailedMap;
    }
  }

  @Override
  public HttpServerResponse putTrailer(String name, String value) {
    synchronized (conn) {
      checkValid();
      trailers().set(name, value);
      return this;
    }
  }

  @Override
  public HttpServerResponse putTrailer(CharSequence name, CharSequence value) {
    synchronized (conn) {
      checkValid();
      trailers().set(name, value);
      return this;
    }
  }

  @Override
  public HttpServerResponse putTrailer(String name, Iterable<String> values) {
    synchronized (conn) {
      checkValid();
      trailers().set(name, values);
      return this;
    }
  }

  @Override
  public HttpServerResponse putTrailer(CharSequence name, Iterable<CharSequence> value) {
    synchronized (conn) {
      checkValid();
      trailers().set(name, value);
      return this;
    }
  }

  @Override
  public HttpServerResponse closeHandler(Handler<Void> handler) {
    synchronized (conn) {
      if (handler != null) {
        checkValid();
      }
      closeHandler = handler;
      return this;
    }
  }

  @Override
  public HttpServerResponse endHandler(@Nullable Handler<Void> handler) {
    synchronized (conn) {
      if (handler != null) {
        checkValid();
      }
      endHandler = handler;
      return this;
    }
  }

  @Override
  public Future<Void> writeContinue() {
    Promise<Void> promise = stream.context.promise();
    synchronized (conn) {
      checkHeadWritten();
      Http2HeadersAdaptor http2HeadersAdaptor = new Http2HeadersAdaptor();
      http2HeadersAdaptor.status(HttpResponseStatus.CONTINUE.codeAsText());
      stream.writeHeaders(http2HeadersAdaptor, true, false,
        true, promise);
    }
    return promise.future();
  }

  @Override
  public Future<Void> writeEarlyHints(MultiMap headers) {
    PromiseInternal<Void> promise = stream.context.promise();
    Http2HeadersAdaptor http2Headers = new Http2HeadersAdaptor();
    for (Entry<String, String> header : headers) {
      http2Headers.add(header.getKey(), header.getValue());
    }
    http2Headers.status(HttpResponseStatus.EARLY_HINTS.codeAsText());
    synchronized (conn) {
      checkHeadWritten();
    }
    stream.writeHeaders(http2Headers, true, false, true, promise);
    return promise.future();
  }

  @Override
  public Future<Void> write(Buffer chunk) {
    ByteBuf buf = ((BufferInternal)chunk).getByteBuf();
    return write(buf, false);
  }

  @Override
  public Future<Void> write(String chunk, String enc) {
    return write(BufferInternal.buffer(chunk, enc).getByteBuf(), false);
  }

  @Override
  public Future<Void> write(String chunk) {
    return write(BufferInternal.buffer(chunk).getByteBuf(), false);
  }

  @Override
  public Future<Void> end(String chunk) {
    return end(Buffer.buffer(chunk));
  }

  @Override
  public Future<Void> end(String chunk, String enc) {
    return end(Buffer.buffer(chunk, enc));
  }

  @Override
  public Future<Void> end(Buffer chunk) {
    return write(((BufferInternal)chunk).getByteBuf(), true);
  }

  @Override
  public Future<Void> end() {
    return write(null, true);
  }

  Future<NetSocket> netSocket() {
    synchronized (conn) {
      if (netSocket == null) {
        status = HttpResponseStatus.OK;
        if (!checkSendHeaders(false)) {
          netSocket = stream.context.failedFuture("Response for CONNECT already sent");
        } else {
          HttpNetSocket ns = HttpNetSocket.netSocket(conn, stream.context, (ReadStream<Buffer>) stream.request, this);
          netSocket = Future.succeededFuture(ns);
        }
      }
    }
    return netSocket;
  }

  Future<Void> write(ByteBuf chunk, boolean end) {
    Future<Void> fut;
    Handler<Void> bodyEndHandler;
    Handler<Void> endHandler;
    synchronized (conn) {
      if (ended) {
        throw new IllegalStateException("Response has already been written");
      }
      ended = end;
      boolean hasBody = false;
      if (chunk != null) {
        hasBody = true;
      } else {
        chunk = Unpooled.EMPTY_BUFFER;
      }
      if (end && !headWritten && needsContentLengthHeader()) {
        headers().set(HttpHeaderNames.CONTENT_LENGTH, String.valueOf(chunk.readableBytes()));
      }
      boolean sent = checkSendHeaders(end && !hasBody && trailers == null, !hasBody);
      if (hasBody || (!sent && end)) {
        Promise<Void> p = stream.context.promise();
        fut = p.future();
        stream.writeData(chunk, end && trailers == null, p);
      } else {
        fut = stream.context.succeededFuture();
      }
      if (end && trailers != null) {
        stream.writeHeaders(new Http2HeadersAdaptor(trailers), false, true, true, null);
      }
      bodyEndHandler = this.bodyEndHandler;
      endHandler = this.endHandler;
    }
    if (end) {
      if (bodyEndHandler != null) {
        bodyEndHandler.handle(null);
      }
      if (endHandler != null) {
        endHandler.handle(null);
      }
    }
    return fut;
  }

  private boolean needsContentLengthHeader() {
    return stream.method != HttpMethod.HEAD && status != HttpResponseStatus.NOT_MODIFIED && !headers.contains(HttpHeaderNames.CONTENT_LENGTH);
  }

  private boolean checkSendHeaders(boolean end) {
    return checkSendHeaders(end, true);
  }

  private boolean checkSendHeaders(boolean end, boolean checkFlush) {
    if (!headWritten) {
      if (headersEndHandler != null) {
        headersEndHandler.handle(null);
      }
      if (cookies != null) {
        setCookies();
      }
      prepareHeaders();
      headWritten = true;
      stream.writeHeaders(new Http2HeadersAdaptor(headers), true, end, checkFlush, null);
      return true;
    } else {
      return false;
    }
  }

  private void prepareHeaders() {
    headers.status(status.codeAsText()); // Could be optimized for usual case ?
    // Sanitize
    if (stream.method == HttpMethod.HEAD || status == HttpResponseStatus.NOT_MODIFIED) {
      headers.remove(HttpHeaders.TRANSFER_ENCODING);
    } else if (status == HttpResponseStatus.RESET_CONTENT) {
      headers.remove(HttpHeaders.TRANSFER_ENCODING);
      headers.set(HttpHeaders.CONTENT_LENGTH, "0");
    } else if (status.codeClass() == HttpStatusClass.INFORMATIONAL || status == HttpResponseStatus.NO_CONTENT) {
      headers.remove(HttpHeaders.TRANSFER_ENCODING);
      headers.remove(HttpHeaders.CONTENT_LENGTH);
    }
  }

  private void setCookies() {
    for (ServerCookie cookie: cookies) {
      if (cookie.isChanged()) {
        headers.add(SET_COOKIE, cookie.encode());
      }
    }
  }

  @Override
  public Future<Void> writeCustomFrame(int type, int flags, Buffer payload) {
    Promise<Void> promise = stream.context.promise();
    synchronized (conn) {
      checkValid();
      checkSendHeaders(false);
      stream.writeFrame(type, flags, ((BufferInternal)payload).getByteBuf(), promise);
    }
    return promise.future();
  }

  private void checkValid() {
    if (ended) {
      throw new IllegalStateException("Response has already been written");
    }
  }

  void handleWriteQueueDrained() {
    Handler<Void> handler;
    synchronized (conn) {
      handler = drainHandler;
      if (ended || handler == null) {
        return;
      }
    }
    handler.handle(null);
  }

  @Override
  public boolean writeQueueFull() {
    synchronized (conn) {
      checkValid();
      return stream.isNotWritable();
    }
  }

  @Override
  public HttpServerResponse setWriteQueueMaxSize(int maxSize) {
    synchronized (conn) {
      checkValid();
      // It does not seem to be possible to configure this at the moment
    }
    return this;
  }

  @Override
  public HttpServerResponse drainHandler(Handler<Void> handler) {
    synchronized (conn) {
      if (handler != null) {
        checkValid();
      }
      drainHandler = handler;
      return this;
    }
  }

  @Override
  public Future<Void> sendFile(String filename, long offset, long length) {
    if (offset < 0) {
      return stream.context.failedFuture("offset : " + offset + " (expected: >= 0)");
    }
    if (length < 0) {
      return stream.context.failedFuture("length : " + length + " (expected: >= 0)");
    }
    synchronized (conn) {
      checkValid();
    }
    return HttpUtils
      .resolveFile(stream.context, filename, offset, length)
      .compose(file -> {
        long fileLength = file.getReadLength();
        long contentLength = Math.min(length, fileLength);
        // fail early before status code/headers are written to the response
        if (headers.get(HttpHeaderNames.CONTENT_LENGTH) == null) {
          putHeader(HttpHeaderNames.CONTENT_LENGTH, String.valueOf(contentLength));
        }
        if (headers.get(HttpHeaderNames.CONTENT_TYPE) == null) {
          String contentType = MimeMapping.mimeTypeForFilename(filename);
          if (contentType != null) {
            putHeader(HttpHeaderNames.CONTENT_TYPE, contentType);
          }
        }
        checkSendHeaders(false);
        Future<Void> fut = file.pipeTo(this);
        return fut
          .eventually(file::close);
    });
  }

  @Override
  public boolean ended() {
    synchronized (conn) {
      return ended;
    }
  }

  @Override
  public synchronized boolean closed() {
    synchronized (conn) {
      return closed;
    }
  }

  @Override
  public boolean headWritten() {
    synchronized (conn) {
      return headWritten;
    }
  }

  @Override
  public HttpServerResponse headersEndHandler(@Nullable Handler<Void> handler) {
    synchronized (conn) {
      headersEndHandler = handler;
      return this;
    }
  }

  @Override
  public HttpServerResponse bodyEndHandler(@Nullable Handler<Void> handler) {
    synchronized (conn) {
      bodyEndHandler = handler;
      return this;
    }
  }

  @Override
  public long bytesWritten() {
    return stream.bytesWritten();
  }

  @Override
  public int streamId() {
    return stream.id();
  }

  @Override
  public boolean reset(long code) {
    stream.writeReset(code);
    return true;
  }

  @Override
  public Future<HttpServerResponse> push(HttpMethod method, HostAndPort authority, String path, MultiMap headers) {
    if (push) {
      throw new IllegalStateException("A push response cannot promise another push");
    }
    if (authority == null) {
      authority = stream.authority;
    }
    synchronized (conn) {
      checkValid();
    }
    Promise<HttpServerResponse> promise = stream.context.promise();
    conn.sendPush(stream.id(), authority, method, headers, path, stream.priority(), promise);
    return promise.future();
  }

  @Override
  public Future<HttpServerResponse> push(HttpMethod method, String authority, String path, MultiMap headers) {
    if (push) {
      throw new IllegalStateException("A push response cannot promise another push");
    }
    HostAndPort hostAndPort = null;
    if (authority != null) {
      hostAndPort = HostAndPort.parseAuthority(authority, -1);
    }
    if (hostAndPort == null) {
      hostAndPort = stream.authority;
    }
    synchronized (conn) {
      checkValid();
    }
    Promise<HttpServerResponse> promise = stream.context.promise();
    conn.sendPush(stream.id(), hostAndPort, method, headers, path, stream.priority(), promise);
    return promise.future();
  }

  @Override
  public HttpServerResponse setStreamPriority(StreamPriorityBase priority) {
    stream.updatePriority(priority);
    return this;
  }

  CookieJar cookies() {
    synchronized (conn) {
      // avoid double parsing
      if (cookies == null) {
        CharSequence cookieHeader = stream.headers != null ? stream.headers.get(io.vertx.core.http.HttpHeaders.COOKIE) : null;
        if (cookieHeader == null) {
          cookies = new CookieJar();
        } else {
          cookies = new CookieJar(cookieHeader);
        }
      }
    }
    return cookies;
  }

  @Override
  public HttpServerResponse addCookie(Cookie cookie) {
    synchronized (conn) {
      checkHeadWritten();
      cookies().add((ServerCookie) cookie);
    }
    return this;
  }

  @Override
  public @Nullable Cookie removeCookie(String name, boolean invalidate) {
    synchronized (conn) {
      checkHeadWritten();
      return cookies().removeOrInvalidate(name, invalidate);
    }
  }

  @Override
  public @Nullable Cookie removeCookie(String name, String domain, String path, boolean invalidate) {
    synchronized (conn) {
      checkHeadWritten();
      return cookies().removeOrInvalidate(name, domain, path, invalidate);
    }
  }

  @Override
  public @Nullable Set<Cookie> removeCookies(String name, boolean invalidate) {
    synchronized (conn) {
      checkHeadWritten();
      return (Set) cookies().removeOrInvalidateAll(name, invalidate);
    }
  }
}
