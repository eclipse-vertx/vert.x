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
import io.netty.handler.codec.http.HttpStatusClass;
import io.netty.handler.codec.http2.DefaultHttp2Headers;
import io.netty.handler.codec.http2.Http2Headers;
import io.vertx.codegen.annotations.Nullable;
import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.MultiMap;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.http.StreamPriority;
import io.vertx.core.http.StreamResetException;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.core.net.NetSocket;
import io.vertx.core.net.impl.ConnectionBase;
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
  private final boolean head;
  private final boolean push;
  private final String host;
  private Http2Headers headers = new DefaultHttp2Headers();
  private Object metric;
  private Http2HeadersAdaptor headersMap;
  private Http2Headers trailers;
  private Http2HeadersAdaptor trailedMap;
  private boolean chunked;
  private boolean headWritten;
  private boolean ended;
  private HttpResponseStatus status = HttpResponseStatus.OK;
  private String statusMessage; // Not really used but we keep the message for the getStatusMessage()
  private Handler<Void> drainHandler;
  private Handler<Throwable> exceptionHandler;
  private Handler<Void> headersEndHandler;
  private Handler<Void> bodyEndHandler;
  private Handler<Void> closeHandler;
  private Handler<Void> endHandler;
  private long bytesWritten;
  private NetSocket netSocket;

  public Http2ServerResponseImpl(Http2ServerConnection conn,
                                 VertxHttp2Stream stream,
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

  void metric(Object metric) {
    this.metric = metric;
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
      conn.getContext().dispatch(cause, handler);
    }
  }

  void handleClose() {
    Handler<Throwable> exceptionHandler;
    Handler<Void> endHandler;
    Handler<Void> closeHandler;
    synchronized (conn) {
      boolean failed = !ended;
      ended = true;
      if (METRICS_ENABLED && metric != null) {
        // Null in case of push response : handle this case
        conn.reportBytesWritten(bytesWritten);
        if (failed) {
          conn.metrics().requestReset(metric);
        } else {
          conn.metrics().responseEnd(metric, this);
        }
      }
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
        checkEnded();
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

  private void end(ByteBuf chunk) {
    synchronized (conn) {
      write(chunk, true);
    }
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
        conn.getContext().dispatch(headersEndHandler);
      }
      sanitizeHeaders();
      if (Metrics.METRICS_ENABLED && metric != null) {
        conn.metrics().responseBegin(metric, this);
      }
      headWritten = true;
      headers.status(Integer.toString(status.code())); // Could be optimized for usual case ?
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
    Handler<Void> bodyEndHandler;
    Handler<Void> endHandler;
    synchronized (conn) {
      checkEnded();
      ended |= end;
      boolean hasBody = false;
      if (chunk != null) {
        hasBody = true;
        bytesWritten += chunk.readableBytes();
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
        stream.writeData(chunk, end && trailers == null);
      }
      if (end && trailers != null) {
        stream.writeHeaders(trailers, true);
      }
      bodyEndHandler = this.bodyEndHandler;
      endHandler = this.endHandler;
    }
    if (end) {
      if (bodyEndHandler != null) {
        conn.getContext().dispatch(bodyEndHandler);
      }
      if (endHandler != null) {
        conn.getContext().dispatch(null, endHandler);
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

  void writabilityChanged() {
    if (!ended && !writeQueueFull() && drainHandler != null) {
      conn.getContext().dispatch(drainHandler);
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
    /*
    if (!handleEnded(true)) {
      throw new IllegalStateException("Response has already been written");
    }
    */
    checkEnded();
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
      checkEnded();
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
}
