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
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http2.DefaultHttp2Headers;
import io.netty.handler.codec.http2.Http2Headers;
import io.vertx.codegen.annotations.Nullable;
import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.MultiMap;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.http.StreamResetException;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.core.net.NetSocket;
import io.vertx.core.net.impl.ConnectionBase;
import io.vertx.core.spi.metrics.HttpServerMetrics;
import io.vertx.core.spi.metrics.Metrics;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;

import static io.vertx.core.spi.metrics.Metrics.METRICS_ENABLED;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class Http2ServerResponseImpl implements HttpServerResponse {

  private static final Logger log = LoggerFactory.getLogger(Http2ServerResponseImpl.class);

  private final VertxHttp2Stream stream;
  private final ChannelHandlerContext ctx;
  private final Http2ServerConnection conn;
  private final boolean push;
  private final Object metric;
  private final String host;
  private Http2Headers headers = new DefaultHttp2Headers();
  private Http2HeadersAdaptor headersMap;
  private Http2Headers trailers;
  private Http2HeadersAdaptor trailedMap;
  private boolean chunked;
  private boolean headWritten;
  private boolean ended;
  private int statusCode = 200;
  private String statusMessage; // Not really used but we keep the message for the getStatusMessage()
  private Handler<Void> drainHandler;
  private Handler<Throwable> exceptionHandler;
  private Handler<Void> headersEndHandler;
  private Handler<Void> bodyEndHandler;
  private Handler<Void> closeHandler;
  private Handler<Void> endHandler;
  private long bytesWritten;
  private int numPush;
  private boolean inHandler;
  private NetSocket netSocket;

  public Http2ServerResponseImpl(Http2ServerConnection conn, VertxHttp2Stream stream, Object metric, boolean push, String contentEncoding, String host) {

    this.metric = metric;
    this.stream = stream;
    this.ctx = conn.handlerContext;
    this.conn = conn;
    this.push = push;
    this.host = host;

    if (contentEncoding != null) {
      putHeader(HttpHeaderNames.CONTENT_ENCODING, contentEncoding);
    }
  }

  public Http2ServerResponseImpl(
      Http2ServerConnection conn,
      VertxHttp2Stream stream,
      HttpMethod method,
      String path,
      boolean push,
      String contentEncoding) {
    this.stream = stream;
    this.ctx = conn.handlerContext;
    this.conn = conn;
    this.push = push;
    this.host = null;

    if (contentEncoding != null) {
      putHeader(HttpHeaderNames.CONTENT_ENCODING, contentEncoding);
    }

    HttpServerMetrics metrics = conn.metrics();
    this.metric = (METRICS_ENABLED && metrics != null) ? metrics.responsePushed(conn.metric(), method, path, this) : null;
  }

  synchronized void beginRequest() {
    inHandler = true;
  }

  synchronized boolean endRequest() {
    inHandler = false;
    return numPush > 0;
  }

  void callReset(long code) {
    handleEnded(true);
    handleError(new StreamResetException(code));
  }

  void handleError(Throwable cause) {
    if (exceptionHandler != null) {
      exceptionHandler.handle(cause);
    }
  }

  void handleClose() {
    handleEnded(true);
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
        checkEnded();
      }
      exceptionHandler = handler;
      return this;
    }
  }

  @Override
  public int getStatusCode() {
    synchronized (conn) {
      return statusCode;
    }
  }

  @Override
  public HttpServerResponse setStatusCode(int statusCode) {
    if (statusCode < 0) {
      throw new IllegalArgumentException("code: " + statusCode + " (expected: 0+)");
    }
    synchronized (conn) {
      checkHeadWritten();
      this.statusCode = statusCode;
      return this;
    }
  }

  @Override
  public String getStatusMessage() {
    synchronized (conn) {
      if (statusMessage == null) {
        return HttpResponseStatus.valueOf(statusCode).reasonPhrase();
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
      checkEnded();
      trailers().set(name, value);
      return this;
    }
  }

  @Override
  public HttpServerResponse putTrailer(CharSequence name, CharSequence value) {
    synchronized (conn) {
      checkEnded();
      trailers().set(name, value);
      return this;
    }
  }

  @Override
  public HttpServerResponse putTrailer(String name, Iterable<String> values) {
    synchronized (conn) {
      checkEnded();
      trailers().set(name, values);
      return this;
    }
  }

  @Override
  public HttpServerResponse putTrailer(CharSequence name, Iterable<CharSequence> value) {
    synchronized (conn) {
      checkEnded();
      trailers().set(name, value);
      return this;
    }
  }

  @Override
  public HttpServerResponse closeHandler(Handler<Void> handler) {
    synchronized (conn) {
      if (handler != null) {
        checkEnded();
      }
      closeHandler = handler;
      return this;
    }
  }

  @Override
  public HttpServerResponse endHandler(@Nullable Handler<Void> handler) {
    synchronized (conn) {
      if (handler != null) {
        checkEnded();
      }
      endHandler = handler;
      return this;
    }
  }

  @Override
  public HttpServerResponse writeContinue() {
    synchronized (conn) {
      checkHeadWritten();
      stream.writeHeaders(new DefaultHttp2Headers().status("100"), false);
      ctx.flush();
      return this;
    }
  }

  @Override
  public HttpServerResponse write(Buffer chunk) {
    ByteBuf buf = chunk.getByteBuf();
    return write(buf);
  }

  @Override
  public HttpServerResponse write(String chunk, String enc) {
    return write(Buffer.buffer(chunk, enc).getByteBuf());
  }

  @Override
  public HttpServerResponse write(String chunk) {
    return write(Buffer.buffer(chunk).getByteBuf());
  }

  private Http2ServerResponseImpl write(ByteBuf chunk) {
    write(chunk, false);
    return this;
  }

  @Override
  public void end(String chunk) {
    end(Buffer.buffer(chunk));
  }

  @Override
  public void end(String chunk, String enc) {
    end(Buffer.buffer(chunk, enc));
  }

  @Override
  public void end(Buffer chunk) {
    end(chunk.getByteBuf());
  }

  @Override
  public void end() {
    end((ByteBuf) null);
  }

  NetSocket netSocket() {
    checkEnded();
    if (netSocket == null) {
      statusCode = 200;
      if (!checkSendHeaders(false)) {
        throw new IllegalStateException("Response for CONNECT already sent");
      }
      ctx.flush();
      handleEnded(false);
      netSocket = conn.toNetSocket(stream);
    }
    return netSocket;
  }

  private void end(ByteBuf chunk) {
    synchronized (conn) {
      write(chunk, true);
    }
  }

  private boolean checkSendHeaders(boolean end) {
    if (!headWritten) {
      if (headersEndHandler != null) {
        headersEndHandler.handle(null);
      }
      if (Metrics.METRICS_ENABLED && metric != null) {
        conn.metrics().responseBegin(metric, this);
      }
      headWritten = true;
      headers.status(Integer.toString(statusCode));
      stream.writeHeaders(headers, end);
      if (end) {
        ctx.flush();
      }
      return true;
    } else {
      return false;
    }
  }

  void write(ByteBuf chunk, boolean end) {
    synchronized (conn) {
      checkEnded();
      boolean hasBody = false;
      if (chunk != null) {
        hasBody = true;
        bytesWritten += chunk.readableBytes();
      } else {
        chunk = Unpooled.EMPTY_BUFFER;
      }
      if (end) {
        if (!headWritten && !headers.contains(HttpHeaderNames.CONTENT_LENGTH)) {
          headers().set(HttpHeaderNames.CONTENT_LENGTH, String.valueOf(chunk.readableBytes()));
        }
        handleEnded(false);
      }
      boolean sent = checkSendHeaders(end && !hasBody && trailers == null);
      if (hasBody || (!sent && end)) {
        stream.writeData(chunk, end && trailers == null);
      }
      if (end && trailers != null) {
        stream.writeHeaders(trailers, true);
      }
      if (end && bodyEndHandler != null) {
        bodyEndHandler.handle(null);
      }
    }
  }

  @Override
  public HttpServerResponse writeCustomFrame(int type, int flags, Buffer payload) {
    synchronized (conn) {
      checkEnded();
      checkSendHeaders(false);
      stream.writeFrame(type, flags, payload.getByteBuf());
      ctx.flush();
      return this;
    }
  }

  private void checkEnded() {
    if (ended) {
      throw new IllegalStateException("Response has already been written");
    }
  }

  private void handleEnded(boolean failed) {
    if (!ended) {
      ended = true;
      if (METRICS_ENABLED && metric != null) {
        // Null in case of push response : handle this case
        if (failed) {
          conn.metrics().requestReset(metric);
        } else {
          conn.reportBytesWritten(bytesWritten);
          conn.metrics().responseEnd(metric, this);
        }
      }
      if (exceptionHandler != null) {
        conn.getContext().runOnContext(v -> exceptionHandler.handle(ConnectionBase.CLOSED_EXCEPTION));
      }
      if (endHandler != null) {
        conn.getContext().runOnContext(endHandler);
      }
      if (closeHandler != null) {
        conn.getContext().runOnContext(closeHandler);
      }
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
      checkEnded();
      return stream.isNotWritable();
    }
  }

  @Override
  public HttpServerResponse setWriteQueueMaxSize(int maxSize) {
    synchronized (conn) {
      checkEnded();
      // It does not seem to be possible to configure this at the moment
    }
    return this;
  }

  @Override
  public HttpServerResponse drainHandler(Handler<Void> handler) {
    synchronized (conn) {
      if (handler != null) {
        checkEnded();
      }
      drainHandler = handler;
      return this;
    }
  }

  @Override
  public HttpServerResponse sendFile(String filename, long offset, long length) {
    return sendFile(filename, offset, length, null);
  }

  @Override
  public HttpServerResponse sendFile(String filename, long offset, long length, Handler<AsyncResult<Void>> resultHandler) {
    synchronized (conn) {
      checkEnded();

      Context resultCtx = resultHandler != null ? stream.vertx.getOrCreateContext() : null;

      File file = stream.vertx.resolveFile(filename);
      if (!file.exists()) {
        if (resultHandler != null) {
          resultCtx.runOnContext((v) -> resultHandler.handle(Future.failedFuture(new FileNotFoundException())));
        } else {
           log.error("File not found: " + filename);
        }
        return this;
      }

      RandomAccessFile raf;
      try {
        raf = new RandomAccessFile(file, "r");
      } catch (IOException e) {
        if (resultHandler != null) {
          resultCtx.runOnContext((v) -> resultHandler.handle(Future.failedFuture(e)));
        } else {
          log.error("Failed to send file", e);
        }
        return this;
      }

      long contentLength = Math.min(length, file.length() - offset);
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

      Future<Long> result = Future.future();
      result.setHandler(ar -> {
        if (ar.succeeded()) {
          bytesWritten += ar.result();
          end();
        }
        if (resultHandler != null) {
          resultCtx.runOnContext(v -> {
            resultHandler.handle(Future.succeededFuture());
          });
        }
      });

      FileStreamChannel fileChannel = new FileStreamChannel(result, stream, offset, contentLength);
      drainHandler(fileChannel.drainHandler);
      ctx.channel()
        .eventLoop()
        .register(fileChannel)
        .addListener((ChannelFutureListener) future -> {
        if (future.isSuccess()) {
          fileChannel.pipeline().fireUserEventTriggered(raf);
        } else {
          result.tryFail(future.cause());
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
    synchronized (conn) {
      return bytesWritten;
    }
  }

  @Override
  public int streamId() {
    return stream.id();
  }

  @Override
  public void reset(long code) {
    synchronized (conn) {
      checkEnded();
      handleEnded(true);
      stream.writeReset(code);
      ctx.flush();
    }
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
      checkEnded();
      conn.sendPush(stream.id(), host, method, headers, path, handler);
      if (!inHandler) {
        ctx.flush();
      }
      numPush++;
      return this;
    }
  }

  @Override
  public HttpServerResponse push(HttpMethod method, String path, Handler<AsyncResult<HttpServerResponse>> handler) {
    return push(method, host, path, handler);
  }
}
