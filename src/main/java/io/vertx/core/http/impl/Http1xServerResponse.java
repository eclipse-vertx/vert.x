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
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelPromise;
import io.netty.handler.codec.http.DefaultHttpContent;
import io.netty.handler.codec.http.EmptyHttpHeaders;
import io.netty.handler.codec.http.HttpObject;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http.LastHttpContent;
import io.vertx.codegen.annotations.Nullable;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.MultiMap;
import io.vertx.core.Promise;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.Cookie;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.http.impl.headers.VertxHttpHeaders;
import io.vertx.core.impl.ContextInternal;
import io.vertx.core.impl.PromiseInternal;
import io.vertx.core.impl.VertxInternal;
import io.vertx.core.impl.logging.Logger;
import io.vertx.core.impl.logging.LoggerFactory;
import io.vertx.core.net.NetSocket;
import io.vertx.core.net.impl.ConnectionBase;
import io.vertx.core.spi.metrics.Metrics;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.Map;

import static io.vertx.core.http.HttpHeaders.SET_COOKIE;

/**
 *
 * This class is optimised for performance when used on the same event loop that is was passed to the handler with.
 * However it can be used safely from other threads.
 *
 * The internal state is protected using the synchronized keyword. If always used on the same event loop, then
 * we benefit from biased locking which makes the overhead of synchronized near zero.
 *
 * It's important we don't have different locks for connection and request/response to avoid deadlock conditions
 *
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class Http1xServerResponse implements HttpServerResponse {

  private static final Buffer EMPTY_BUFFER = Buffer.buffer(Unpooled.EMPTY_BUFFER);
  private static final Logger log = LoggerFactory.getLogger(Http1xServerResponse.class);
  private static final String RESPONSE_WRITTEN = "Response has already been written";

  private final VertxInternal vertx;
  private final HttpRequest request;
  private final Http1xServerConnection conn;
  private final ContextInternal context;
  private HttpResponseStatus status;
  private final HttpVersion version;
  private final boolean keepAlive;
  private final boolean head;
  private final Object requestMetric;

  private boolean headWritten;
  private boolean written;
  private Handler<Void> drainHandler;
  private Handler<Throwable> exceptionHandler;
  private Handler<Void> closeHandler;
  private Handler<Void> endHandler;
  private Handler<Void> headersEndHandler;
  private Handler<Void> bodyEndHandler;
  private boolean writable;
  private boolean closed;
  private final VertxHttpHeaders headers;
  private Map<String, ServerCookie> cookies;
  private MultiMap trailers;
  private io.netty.handler.codec.http.HttpHeaders trailingHeaders = EmptyHttpHeaders.INSTANCE;
  private String statusMessage;
  private long bytesWritten;
  private NetSocket netSocket;

  Http1xServerResponse(final VertxInternal vertx, ContextInternal context, Http1xServerConnection conn, HttpRequest request, Object requestMetric) {
    this.vertx = vertx;
    this.conn = conn;
    this.context = context;
    this.version = request.protocolVersion();
    this.headers = new VertxHttpHeaders();
    this.request = request;
    this.status = HttpResponseStatus.OK;
    this.requestMetric = requestMetric;
    this.writable = !conn.isNotWritable();
    this.keepAlive = (version == HttpVersion.HTTP_1_1 && !request.headers().contains(io.vertx.core.http.HttpHeaders.CONNECTION, HttpHeaders.CLOSE, true))
      || (version == HttpVersion.HTTP_1_0 && request.headers().contains(io.vertx.core.http.HttpHeaders.CONNECTION, HttpHeaders.KEEP_ALIVE, true));
    this.head = request.method() == io.netty.handler.codec.http.HttpMethod.HEAD;
  }

  @Override
  public MultiMap headers() {
    return headers;
  }

  @Override
  public MultiMap trailers() {
    if (trailers == null) {
      VertxHttpHeaders v = new VertxHttpHeaders();
      trailers = v;
      trailingHeaders = v;
    }
    return trailers;
  }

  @Override
  public int getStatusCode() {
    return status.code();
  }

  @Override
  public HttpServerResponse setStatusCode(int statusCode) {
    status = statusMessage != null ? new HttpResponseStatus(statusCode, statusMessage) : HttpResponseStatus.valueOf(statusCode);
    return this;
  }

  @Override
  public String getStatusMessage() {
    return status.reasonPhrase();
  }

  @Override
  public HttpServerResponse setStatusMessage(String statusMessage) {
    synchronized (conn) {
      this.statusMessage = statusMessage;
      this.status = new HttpResponseStatus(status.code(), statusMessage);
      return this;
    }
  }

  @Override
  public Http1xServerResponse setChunked(boolean chunked) {
    synchronized (conn) {
      checkValid();
      // HTTP 1.0 does not support chunking so we ignore this if HTTP 1.0
      if (version != HttpVersion.HTTP_1_0) {
        headers.set(HttpHeaders.TRANSFER_ENCODING, chunked ? "chunked" : null);
      }
      return this;
    }
  }

  @Override
  public boolean isChunked() {
    synchronized (conn) {
      return headers.contains(HttpHeaders.TRANSFER_ENCODING, HttpHeaders.CHUNKED, true);
    }
  }

  @Override
  public Http1xServerResponse putHeader(String key, String value) {
    synchronized (conn) {
      checkValid();
      headers.set(key, value);
      return this;
    }
  }

  @Override
  public Http1xServerResponse putHeader(String key, Iterable<String> values) {
    synchronized (conn) {
      checkValid();
      headers.set(key, values);
      return this;
    }
  }

  @Override
  public Http1xServerResponse putTrailer(String key, String value) {
    synchronized (conn) {
      checkValid();
      trailers().set(key, value);
      return this;
    }
  }

  @Override
  public Http1xServerResponse putTrailer(String key, Iterable<String> values) {
    synchronized (conn) {
      checkValid();
      trailers().set(key, values);
      return this;
    }
  }

  @Override
  public HttpServerResponse putHeader(CharSequence name, CharSequence value) {
    synchronized (conn) {
      checkValid();
      headers.set(name, value);
      return this;
    }
  }

  @Override
  public HttpServerResponse putHeader(CharSequence name, Iterable<CharSequence> values) {
    synchronized (conn) {
      checkValid();
      headers.set(name, values);
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
  public HttpServerResponse putTrailer(CharSequence name, Iterable<CharSequence> value) {
    synchronized (conn) {
      checkValid();
      trailers().set(name, value);
      return this;
    }
  }

  @Override
  public HttpServerResponse setWriteQueueMaxSize(int size) {
    synchronized (conn) {
      checkValid();
      conn.doSetWriteQueueMaxSize(size);
      return this;
    }
  }

  @Override
  public boolean writeQueueFull() {
    synchronized (conn) {
      checkValid();
      return !writable;
    }
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
  public Future<Void> write(Buffer chunk) {
    PromiseInternal<Void> promise = context.promise();
    write(chunk.getByteBuf(), promise);
    return promise.future();
  }

  @Override
  public void write(Buffer chunk, Handler<AsyncResult<Void>> handler) {
    write(chunk.getByteBuf(), handler == null ? null : context.promise(handler));
  }

  @Override
  public Future<Void> write(String chunk, String enc) {
    PromiseInternal<Void> promise = context.promise();
    write(Buffer.buffer(chunk, enc).getByteBuf(), promise);
    return promise.future();
  }

  @Override
  public void write(String chunk, String enc, Handler<AsyncResult<Void>> handler) {
    write(Buffer.buffer(chunk, enc).getByteBuf(), handler == null ? null : context.promise(handler));
  }

  @Override
  public Future<Void> write(String chunk) {
    PromiseInternal<Void> promise = context.promise();
    write(Buffer.buffer(chunk).getByteBuf(), promise);
    return promise.future();
  }

  @Override
  public void write(String chunk, Handler<AsyncResult<Void>> handler) {
    write(Buffer.buffer(chunk).getByteBuf(), handler == null ? null : context.promise(handler));
  }

  @Override
  public HttpServerResponse writeContinue() {
    conn.write100Continue();
    return this;
  }

  @Override
  public Future<Void> end(String chunk) {
    return end(Buffer.buffer(chunk));
  }

  @Override
  public void end(String chunk, Handler<AsyncResult<Void>> handler) {
    end(Buffer.buffer(chunk), handler);
  }

  @Override
  public Future<Void> end(String chunk, String enc) {
    return end(Buffer.buffer(chunk, enc));
  }

  @Override
  public void end(String chunk, String enc, Handler<AsyncResult<Void>> handler) {
    end(Buffer.buffer(chunk, enc), handler);
  }

  @Override
  public Future<Void> end(Buffer chunk) {
    PromiseInternal<Void> promise = context.promise();
    end(chunk, promise);
    return promise.future();
  }

  @Override
  public void end(Buffer chunk, Handler<AsyncResult<Void>> handler) {
    end(chunk, handler == null ? null : context.promise(handler));
  }

  private void end(Buffer chunk, PromiseInternal<Void> listener) {
    synchronized (conn) {
      if (written) {
        throw new IllegalStateException(RESPONSE_WRITTEN);
      }
      ByteBuf data = chunk.getByteBuf();
      bytesWritten += data.readableBytes();
      HttpObject msg;
      if (!headWritten) {
        // if the head was not written yet we can write out everything in one go
        // which is cheaper.
        prepareHeaders(bytesWritten);
        msg = new AssembledFullHttpResponse(head, version, status, headers, data, trailingHeaders);
      } else {
        msg = new AssembledLastHttpContent(data, trailingHeaders);
      }
      conn.writeToChannel(msg, listener);
      written = true;
      conn.responseComplete();
      if (bodyEndHandler != null) {
        bodyEndHandler.handle(null);
      }
      if (!closed && endHandler != null) {
        endHandler.handle(null);
      }
      if (!keepAlive) {
        closeConnAfterWrite();
        closed = true;
      }
    }
  }

  @Override
  public void close() {
    synchronized (conn) {
      if (!closed) {
        if (headWritten) {
          closeConnAfterWrite();
        } else {
          conn.close();
        }
        closed = true;
      }
    }
  }

  @Override
  public Future<Void> end() {
    return end(EMPTY_BUFFER);
  }

  @Override
  public void end(Handler<AsyncResult<Void>> handler) {
    end(EMPTY_BUFFER, handler);
  }

  @Override
  public Future<Void> sendFile(String filename, long offset, long length) {
    Promise<Void> promise = context.promise();
    sendFile(filename, offset, length, promise);
    return promise.future();
  }

  @Override
  public HttpServerResponse sendFile(String filename, long start, long end, Handler<AsyncResult<Void>> resultHandler) {
    doSendFile(filename, start, end, resultHandler);
    return this;
  }

  @Override
  public boolean ended() {
    synchronized (conn) {
      return written;
    }
  }

  @Override
  public boolean closed() {
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
  public long bytesWritten() {
    synchronized (conn) {
      return bytesWritten;
    }
  }

  @Override
  public HttpServerResponse headersEndHandler(Handler<Void> handler) {
    synchronized (conn) {
      this.headersEndHandler = handler;
      return this;
    }
  }

  @Override
  public HttpServerResponse bodyEndHandler(Handler<Void> handler) {
    synchronized (conn) {
      this.bodyEndHandler = handler;
      return this;
    }
  }

  private void doSendFile(String filename, long offset, long length, Handler<AsyncResult<Void>> resultHandler) {
    synchronized (conn) {
      checkValid();
      if (headWritten) {
        throw new IllegalStateException("Head already written");
      }
      File file = vertx.resolveFile(filename);

      if (!file.exists()) {
        if (resultHandler != null) {
          ContextInternal ctx = vertx.getOrCreateContext();
          ctx.runOnContext((v) -> resultHandler.handle(Future.failedFuture(new FileNotFoundException())));
        } else {
          log.error("File not found: " + filename);
        }
        return;
      }

      long contentLength = Math.min(length, file.length() - offset);
      bytesWritten = contentLength;
      if (!headers.contains(HttpHeaders.CONTENT_TYPE)) {
        String contentType = MimeMapping.getMimeTypeForFilename(filename);
        if (contentType != null) {
          headers.set(HttpHeaders.CONTENT_TYPE, contentType);
        }
      }
      prepareHeaders(bytesWritten);

      ChannelFuture channelFuture;
      RandomAccessFile raf = null;
      try {
        raf = new RandomAccessFile(file, "r");
        conn.writeToChannel(new AssembledHttpResponse(head, version, status, headers));
        channelFuture = conn.sendFile(raf, Math.min(offset, file.length()), contentLength);
      } catch (IOException e) {
        try {
          if (raf != null) {
            raf.close();
          }
        } catch (IOException ignore) {
        }
        if (resultHandler != null) {
          ContextInternal ctx = vertx.getOrCreateContext();
          ctx.runOnContext((v) -> resultHandler.handle(Future.failedFuture(e)));
        } else {
          log.error("Failed to send file", e);
        }
        return;
      }
      written = true;

      ContextInternal ctx = vertx.getOrCreateContext();
      channelFuture.addListener(future -> {

        // write an empty last content to let the http encoder know the response is complete
        if (future.isSuccess()) {
          ChannelPromise pr = conn.channelHandlerContext().newPromise();
          conn.writeToChannel(LastHttpContent.EMPTY_LAST_CONTENT, pr);
          if (!keepAlive) {
            pr.addListener(a -> {
              closeConnAfterWrite();
            });
          }
        }

        // signal completion handler when there is one
        if (resultHandler != null) {
          AsyncResult<Void> res;
          if (future.isSuccess()) {
            res = Future.succeededFuture();
          } else {
            res = Future.failedFuture(future.cause());
          }
          ctx.dispatch(null, v -> resultHandler.handle(res));
        }

        // signal body end handler
        Handler<Void> handler;
        synchronized (conn) {
          handler = bodyEndHandler;
        }
        if (handler != null) {
          context.dispatch(v -> {
            handler.handle(null);
          });
        }

        // allow to write next response
        conn.responseComplete();
      });
    }
  }

  private void closeConnAfterWrite() {
    ChannelPromise channelFuture = conn.channelFuture();
    conn.writeToChannel(Unpooled.EMPTY_BUFFER, channelFuture);
    channelFuture.addListener(fut -> conn.close());
  }

  void handleWritabilityChanged(boolean writable) {
    Handler<Void> handler;
    synchronized (conn) {
      boolean skip = this.writable && !writable;
      this.writable = writable;
      handler = drainHandler;
      if (handler == null || skip) {
        return;
      }
    }
    context.emit(null, handler);
  }

  void handleException(Throwable t) {
    if (t == Http1xServerConnection.CLOSED_EXCEPTION) {
      handleClosed();
    } else {
      Handler<Throwable> handler;
      synchronized (conn) {
        handler = exceptionHandler;
        if (handler == null) {
          return;
        }
      }
      context.emit(t, handler);
    }
  }

  private void handleClosed() {
    Handler<Void> closedHandler;
    Handler<Void> endHandler;
    Handler<Throwable> exceptionHandler;
    synchronized (conn) {
      if (closed) {
        return;
      }
      closed = true;
      exceptionHandler = written ? null : this.exceptionHandler;
      endHandler = this.written ? null : this.endHandler;
      closedHandler = this.closeHandler;
    }
    if (exceptionHandler != null) {
      context.emit(ConnectionBase.CLOSED_EXCEPTION, exceptionHandler);
    }
    if (endHandler != null) {
      context.emit(null, endHandler);
    }
    if (closedHandler != null) {
      context.emit(null, closedHandler);
    }
  }

  private void checkValid() {
    if (written) {
      throw new IllegalStateException(RESPONSE_WRITTEN);
    }
  }

  private void prepareHeaders(long contentLength) {
    if (version == HttpVersion.HTTP_1_0 && keepAlive) {
      headers.set(HttpHeaders.CONNECTION, HttpHeaders.KEEP_ALIVE);
    } else if (version == HttpVersion.HTTP_1_1 && !keepAlive) {
      headers.set(HttpHeaders.CONNECTION, HttpHeaders.CLOSE);
    }
    if (head || status == HttpResponseStatus.NOT_MODIFIED) {
      // For HEAD request or NOT_MODIFIED response
      // don't set automatically the content-length
      // and remove the transfer-encoding
      headers.remove(HttpHeaders.TRANSFER_ENCODING);
    } else {
      // Set content-length header automatically
      if (!headers.contains(HttpHeaders.TRANSFER_ENCODING) && !headers.contains(HttpHeaders.CONTENT_LENGTH) && contentLength >= 0) {
        String value = contentLength == 0 ? "0" : String.valueOf(contentLength);
        headers.set(HttpHeaders.CONTENT_LENGTH, value);
      }
    }
    if (headersEndHandler != null) {
      headersEndHandler.handle(null);
    }
    if (cookies != null) {
      setCookies();
    }
    if (Metrics.METRICS_ENABLED) {
      // TODO : DONE SOMEWHERE ELSE FROM EVENT LOOP
      reportResponseBegin();
    }
    headWritten = true;
  }

  private void setCookies() {
    for (ServerCookie cookie: cookies.values()) {
      if (cookie.isChanged()) {
        headers.add(SET_COOKIE, cookie.encode());
      }
    }
  }

  private void reportResponseBegin() {
    if (conn.metrics != null) {
      conn.metrics.responseBegin(requestMetric, this);
    }
  }

  private Http1xServerResponse write(ByteBuf chunk, PromiseInternal<Void> promise) {
    synchronized (conn) {
      if (written) {
        throw new IllegalStateException("Response has already been written");
      } else if (!headWritten && !headers.contains(HttpHeaders.TRANSFER_ENCODING) && !headers.contains(HttpHeaders.CONTENT_LENGTH)) {
        if (version != HttpVersion.HTTP_1_0) {
          throw new IllegalStateException("You must set the Content-Length header to be the total size of the message "
            + "body BEFORE sending any data if you are not using HTTP chunked encoding.");
        }
      }
      bytesWritten += chunk.readableBytes();
      HttpObject msg;
      if (!headWritten) {
        prepareHeaders(-1);
        msg = new AssembledHttpResponse(head, version, status, headers, chunk);
      } else {
        msg = new DefaultHttpContent(chunk);
      }
      conn.writeToChannel(msg, promise);
      return this;
    }
  }

  NetSocket netSocket(boolean isConnect) {
    checkValid();
    if (netSocket == null) {
      if (isConnect) {
        if (headWritten) {
          throw new IllegalStateException("Response for CONNECT already sent");
        }
        status = HttpResponseStatus.OK;
        prepareHeaders(-1);
        conn.writeToChannel(new AssembledHttpResponse(head, version, status, headers));
      }
      written = true;
      netSocket = conn.createNetSocket();
    }
    return netSocket;
  }

  @Override
  public int streamId() {
    return -1;
  }

  @Override
  public void reset(long code) {
  }

  @Override
  public Future<HttpServerResponse> push(HttpMethod method, String host, String path, MultiMap headers) {
    return context.failedFuture("HTTP/1 does not support response push");
  }

  @Override
  public HttpServerResponse writeCustomFrame(int type, int flags, Buffer payload) {
    return this;
  }

  Map<String, ServerCookie> cookies() {
    if (cookies == null) {
      cookies = CookieImpl.extractCookies(request.headers().get(io.vertx.core.http.HttpHeaders.COOKIE));
    }
    return cookies;
  }

  @Override
  public HttpServerResponse addCookie(Cookie cookie) {
    cookies().put(cookie.getName(), (ServerCookie) cookie);
    return this;
  }

  @Override
  public @Nullable Cookie removeCookie(String name, boolean invalidate) {
    return CookieImpl.removeCookie(cookies(), name, invalidate);
  }
}
