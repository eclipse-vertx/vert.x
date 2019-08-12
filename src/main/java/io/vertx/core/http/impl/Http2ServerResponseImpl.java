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

package io.vertx.core.http.impl;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpStatusClass;
import io.netty.handler.codec.http2.DefaultHttp2Headers;
import io.netty.handler.codec.http2.Http2Headers;
import io.vertx.codegen.annotations.Nullable;
import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.MultiMap;
import io.vertx.core.Promise;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.file.AsyncFile;
import io.vertx.core.file.FileSystem;
import io.vertx.core.file.FileSystemException;
import io.vertx.core.file.OpenOptions;
import io.vertx.core.http.Cookie;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.http.StreamPriority;
import io.vertx.core.http.StreamResetException;
import io.vertx.core.impl.logging.Logger;
import io.vertx.core.impl.logging.LoggerFactory;
import io.vertx.core.net.NetSocket;
import io.vertx.core.net.impl.ConnectionBase;
import io.vertx.core.streams.WriteStream;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.NoSuchFileException;
import java.util.Map;

import static io.vertx.core.http.HttpHeaders.SET_COOKIE;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class Http2ServerResponseImpl implements HttpServerResponse {

  private static final Logger log = LoggerFactory.getLogger(Http2ServerResponseImpl.class);

  private final Http2ServerStream stream;
  private final ChannelHandlerContext ctx;
  private final Http2ServerConnection conn;
  private final boolean head;
  private final boolean push;
  private final String host;
  private final Http2Headers headers = new DefaultHttp2Headers();
  private Http2HeadersAdaptor headersMap;
  private Http2Headers trailers;
  private Http2HeadersAdaptor trailedMap;
  private boolean chunked;
  private boolean headWritten;
  private boolean ended;
  private boolean closed;
  private Map<String, ServerCookie> cookies;
  private HttpResponseStatus status = HttpResponseStatus.OK;
  private String statusMessage; // Not really used but we keep the message for the getStatusMessage()
  private Handler<Void> drainHandler;
  private Handler<Throwable> exceptionHandler;
  private Handler<Void> headersEndHandler;
  private Handler<Void> bodyEndHandler;
  private Handler<Void> closeHandler;
  private Handler<Void> endHandler;
  private NetSocket netSocket;

  public Http2ServerResponseImpl(Http2ServerConnection conn,
                                 Http2ServerStream stream,
                                 HttpMethod method,
                                 boolean push,
                                 String contentEncoding,
                                 String host) {
    this.stream = stream;
    this.ctx = conn.handlerContext;
    this.conn = conn;
    this.head = method == HttpMethod.HEAD;
    this.push = push;
    this.host = host;

    if (contentEncoding != null) {
      putHeader(HttpHeaderNames.CONTENT_ENCODING, contentEncoding);
    }
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
      boolean failed = !ended;
      closed = true;
      exceptionHandler = failed ? this.exceptionHandler : null;
      endHandler = failed ? this.endHandler : null;
      closeHandler = this.closeHandler;
    }
    if (exceptionHandler != null) {
      stream.context.dispatch(ConnectionBase.CLOSED_EXCEPTION, exceptionHandler);
    }
    if (endHandler != null) {
      stream.context.dispatch(endHandler);
    }
    if (closeHandler != null) {
      stream.context.dispatch(closeHandler);
    }
  }

  private void checkHeadWritten() {
    if (headWritten) {
      throw new IllegalStateException("Header already sent");
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
        trailedMap = new Http2HeadersAdaptor(trailers = new DefaultHttp2Headers());
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
  public HttpServerResponse writeContinue() {
    synchronized (conn) {
      checkHeadWritten();
      stream.writeHeaders(new DefaultHttp2Headers().status("100"), false, null);
      ctx.flush();
      return this;
    }
  }

  @Override
  public Future<Void> write(Buffer chunk) {
    ByteBuf buf = chunk.getByteBuf();
    Promise<Void> promise = Promise.promise();
    write(buf, false, promise);
    return promise.future();
  }

  @Override
  public void write(Buffer chunk, Handler<AsyncResult<Void>> handler) {
    ByteBuf buf = chunk.getByteBuf();
    write(buf, false, handler);
  }

  @Override
  public Future<Void> write(String chunk, String enc) {
    Promise<Void> promise = Promise.promise();
    write(chunk, enc, promise);
    return promise.future();
  }

  @Override
  public void write(String chunk, String enc, Handler<AsyncResult<Void>> handler) {
    write(Buffer.buffer(chunk, enc).getByteBuf(), false, handler);
  }

  @Override
  public Future<Void> write(String chunk) {
    Promise<Void> promise = Promise.promise();
    write(chunk, promise);
    return promise.future();
  }

  @Override
  public void write(String chunk, Handler<AsyncResult<Void>> handler) {
    write(Buffer.buffer(chunk).getByteBuf(), false, handler);
  }

  private Http2ServerResponseImpl write(ByteBuf chunk) {
    write(chunk, false, null);
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
    end(Buffer.buffer(chunk, enc));
  }

  @Override
  public Future<Void> end(Buffer chunk) {
    Promise<Void> promose = Promise.promise();
    end(chunk, promose);
    return promose.future();
  }

  @Override
  public void end(Buffer chunk, Handler<AsyncResult<Void>> handler) {
    end(chunk.getByteBuf(), handler);
  }

  @Override
  public Future<Void> end() {
    Promise<Void> promise = Promise.promise();
    end((ByteBuf) null, promise);
    return promise.future();
  }

  @Override
  public void end(Handler<AsyncResult<Void>> handler) {
    end((ByteBuf) null, handler);
  }

  NetSocket netSocket() {
    checkValid();
    synchronized (conn) {
      if (netSocket != null) {
        return netSocket;
      }
      status = HttpResponseStatus.OK;
      if (!checkSendHeaders(false)) {
        throw new IllegalStateException("Response for CONNECT already sent");
      }
      ctx.flush();
      netSocket = conn.toNetSocket(stream);
    }
    return netSocket;
  }

  private void end(ByteBuf chunk, Handler<AsyncResult<Void>> handler) {
    write(chunk, true, handler);
  }

  private void sanitizeHeaders() {
    if (head || status == HttpResponseStatus.NOT_MODIFIED) {
      headers.remove(HttpHeaders.TRANSFER_ENCODING);
    } else if (status == HttpResponseStatus.RESET_CONTENT) {
      headers.remove(HttpHeaders.TRANSFER_ENCODING);
      headers.set(HttpHeaders.CONTENT_LENGTH, "0");
    } else if (status.codeClass() == HttpStatusClass.INFORMATIONAL || status == HttpResponseStatus.NO_CONTENT) {
      headers.remove(HttpHeaders.TRANSFER_ENCODING);
      headers.remove(HttpHeaders.CONTENT_LENGTH);
    }
  }

  private boolean checkSendHeaders(boolean end) {
    if (!headWritten) {
      if (headersEndHandler != null) {
        headersEndHandler.handle(null);
      }
      if (cookies != null) {
        setCookies();
      }
      sanitizeHeaders();
      headWritten = true;
      headers.status(Integer.toString(status.code())); // Could be optimized for usual case ?
      stream.writeHead(headers, end, null);
      if (end) {
        ctx.flush();
      }
      return true;
    } else {
      return false;
    }
  }

  private void setCookies() {
    for (ServerCookie cookie: cookies.values()) {
      if (cookie.isChanged()) {
        headers.add(SET_COOKIE, cookie.encode());
      }
    }
  }

  void write(ByteBuf chunk, boolean end, Handler<AsyncResult<Void>> handler) {
    Handler<Void> bodyEndHandler;
    Handler<Void> endHandler;
    synchronized (conn) {
      if (ended) {
        throw new IllegalStateException("Response has already been written");
      }
      ended |= end;
      boolean hasBody = false;
      if (chunk != null) {
        hasBody = true;
      } else {
        chunk = Unpooled.EMPTY_BUFFER;
      }
      if (end) {
        if (!headWritten && !head && status != HttpResponseStatus.NOT_MODIFIED && !headers.contains(HttpHeaderNames.CONTENT_LENGTH)) {
          headers().set(HttpHeaderNames.CONTENT_LENGTH, String.valueOf(chunk.readableBytes()));
        }
      }
      boolean sent = checkSendHeaders(end && !hasBody && trailers == null);
      if (hasBody || (!sent && end)) {
        stream.writeData(chunk, end && trailers == null, handler);
      }
      if (end && trailers != null) {
        stream.writeHeaders(trailers, true, null);
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
  }

  @Override
  public HttpServerResponse writeCustomFrame(int type, int flags, Buffer payload) {
    synchronized (conn) {
      checkValid();
      checkSendHeaders(false);
      stream.writeFrame(type, flags, payload.getByteBuf());
      ctx.flush();
      return this;
    }
  }

  private void checkValid() {
    if (ended) {
      throw new IllegalStateException("Response has already been written");
    }
  }

  void writabilityChanged() {
    if (!ended && !writeQueueFull() && drainHandler != null) {
      drainHandler.handle(null);
    }
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
  public HttpServerResponse sendFile(String filename, long offset, long length, Handler<AsyncResult<Void>> resultHandler) {
    synchronized (conn) {
      checkValid();

      Handler<AsyncResult<Void>> h;
      if (resultHandler != null) {
        Context resultCtx = stream.vertx.getOrCreateContext();
        h = ar -> {
          resultCtx.runOnContext((v) -> {
            resultHandler.handle(ar);
          });
        };
      } else {
        h = ar -> {};
      }

      File file_ = stream.vertx.resolveFile(filename);
      if (!file_.exists()) {
        if (resultHandler != null) {
          h.handle(Future.failedFuture(new FileNotFoundException()));
        } else {
          log.error("File not found: " + filename);
        }
        return this;
      }
      try(RandomAccessFile raf = new RandomAccessFile(file_, "r")) {
      } catch (IOException e) {
        if (resultHandler != null) {
          h.handle(Future.failedFuture(e));
        } else {
          log.error("Failed to send file", e);
        }
        return this;
      }

      long contentLength = Math.min(length, file_.length() - offset);

      FileSystem fs = conn.vertx().fileSystem();
      fs.open(filename, new OpenOptions().setCreate(false).setWrite(false), ar -> {
        if (ar.succeeded()) {
          AsyncFile file = ar.result();
          if (headers.get(HttpHeaderNames.CONTENT_LENGTH) == null) {
            putHeader(HttpHeaderNames.CONTENT_LENGTH, String.valueOf(contentLength));
          }
          if (headers.get(HttpHeaderNames.CONTENT_TYPE) == null) {
            String contentType = MimeMapping.getMimeTypeForFilename(filename);
            if (contentType != null) {
              putHeader(HttpHeaderNames.CONTENT_TYPE, contentType);
            }
          }
          checkSendHeaders(false);
          file.setReadPos(offset);
          file.setReadLength(contentLength);
          file.pipeTo(this, h);
        } else {
          h.handle(ar.mapEmpty());
        }
      });
    }
    return this;
  }

  @Override
  public void close() {
    conn.close();
  }

  @Override
  public boolean ended() {
    synchronized (conn) {
      return ended;
    }
  }

  @Override
  public boolean closed() {
    return conn.isClosed();
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
  public void reset(long code) {
    /*
    if (!handleEnded(true)) {
      throw new IllegalStateException("Response has already been written");
    }
    */
    checkValid();
    stream.writeReset(code);
    ctx.flush();
  }

  @Override
  public HttpServerResponse push(HttpMethod method, String host, String path, Handler<AsyncResult<HttpServerResponse>> handler) {
    return push(method, host, path, null, handler);
  }

  @Override
  public HttpServerResponse push(HttpMethod method, String path, MultiMap headers, Handler<AsyncResult<HttpServerResponse>> handler) {
    return push(method, null, path, headers, handler);
  }

  @Override
  public HttpServerResponse push(HttpMethod method, String host, String path, MultiMap headers, Handler<AsyncResult<HttpServerResponse>> handler) {
    synchronized (conn) {
      if (push) {
        throw new IllegalStateException("A push response cannot promise another push");
      }
      checkValid();
      conn.sendPush(stream.id(), host, method, headers, path, stream.priority(), handler);
      return this;
    }
  }

  @Override
  public HttpServerResponse push(HttpMethod method, String path, Handler<AsyncResult<HttpServerResponse>> handler) {
    return push(method, host, path, handler);
  }

  @Override
  public HttpServerResponse setStreamPriority(StreamPriority priority) {
    stream.updatePriority(priority);
    return this;
  }

  Map<String, ServerCookie> cookies() {
    if (cookies == null) {
      cookies = CookieImpl.extractCookies(stream.headers != null ? stream.headers.get(io.vertx.core.http.HttpHeaders.COOKIE) : null);
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
    return CookieImpl.removeCookie(cookies, name, invalidate);
  }
}
