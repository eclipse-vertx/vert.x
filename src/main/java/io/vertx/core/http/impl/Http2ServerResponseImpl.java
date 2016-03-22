/*
 * Copyright (c) 2011-2013 The original author or authors
 *  ------------------------------------------------------
 *  All rights reserved. This program and the accompanying materials
 *  are made available under the terms of the Eclipse Public License v1.0
 *  and Apache License v2.0 which accompanies this distribution.
 *
 *      The Eclipse Public License is available at
 *      http://www.eclipse.org/legal/epl-v10.html
 *
 *      The Apache License v2.0 is available at
 *      http://www.opensource.org/licenses/apache2.0.php
 *
 *  You may elect to redistribute this code under either of these licenses.
 */

package io.vertx.core.http.impl;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http2.DefaultHttp2Headers;
import io.netty.handler.codec.http2.Http2Flags;
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

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.channels.ClosedChannelException;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class Http2ServerResponseImpl implements HttpServerResponse {

  private final VertxHttp2Stream stream;
  private final ChannelHandlerContext ctx;
  private final Http2ServerConnection connection;
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
  private long bytesWritten;

  public Http2ServerResponseImpl(Http2ServerConnection connection, VertxHttp2Stream stream, Object metric, boolean push, String contentEncoding, String host) {

    this.metric = metric;
    this.stream = stream;
    this.ctx = connection.handlerContext;
    this.connection = connection;
    this.push = push;
    this.host = host;

    if (contentEncoding != null) {
      putHeader(HttpHeaderNames.CONTENT_ENCODING, contentEncoding);
    }
  }

  public Http2ServerResponseImpl(Http2ServerConnection connection, VertxHttp2Stream stream, boolean push, String contentEncoding) {
    this.stream = stream;
    this.ctx = connection.handlerContext;
    this.connection = connection;
    this.push = push;
    this.host = null;

    if (contentEncoding != null) {
      putHeader(HttpHeaderNames.CONTENT_ENCODING, contentEncoding);
    }

    this.metric = connection.metrics().responsePushed(connection.metric(), this);
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
    if (handleEnded(true)) {
      handleError(new ClosedChannelException());
    }
    if (closeHandler != null) {
      closeHandler.handle(null);
    }
  }

  private void checkHeadWritten() {
    if (headWritten) {
      throw new IllegalStateException("Header already sent");
    }
  }

  @Override
  public HttpServerResponse exceptionHandler(Handler<Throwable> handler) {
    checkEnded();
    exceptionHandler = handler;
    return this;
  }

  @Override
  public int getStatusCode() {
    return statusCode;
  }

  @Override
  public HttpServerResponse setStatusCode(int statusCode) {
    if (statusCode < 0) {
      throw new IllegalArgumentException("code: " + statusCode + " (expected: 0+)");
    }
    checkHeadWritten();
    this.statusCode = statusCode;
    return this;
  }

  @Override
  public String getStatusMessage() {
    if (statusMessage == null) {
      switch (statusCode / 100) {
        case 1:
          return "Informational";
        case 2:
          return "Success";
        case 3:
          return "Redirection";
        case 4:
          return "Client Error";
        case 5:
          return "Server Error";
        default:
          return "Unknown Status";
      }
    }
    return statusMessage;
  }

  @Override
  public HttpServerResponse setStatusMessage(String statusMessage) {
    checkHeadWritten();
    this.statusMessage = statusMessage;
    return this;
  }

  @Override
  public HttpServerResponse setChunked(boolean chunked) {
    checkHeadWritten();
    this.chunked = true;
    return this;
  }

  @Override
  public boolean isChunked() {
    return chunked;
  }

  @Override
  public MultiMap headers() {
    if (headersMap == null) {
      headersMap = new Http2HeadersAdaptor(headers);
    }
    return headersMap;
  }

  @Override
  public HttpServerResponse putHeader(String name, String value) {
    checkHeadWritten();
    headers().add(name, value);
    return this;
  }

  @Override
  public HttpServerResponse putHeader(CharSequence name, CharSequence value) {
    checkHeadWritten();
    headers().add(name, value);
    return this;
  }

  @Override
  public HttpServerResponse putHeader(String name, Iterable<String> values) {
    checkHeadWritten();
    headers().add(name, values);
    return this;
  }

  @Override
  public HttpServerResponse putHeader(CharSequence name, Iterable<CharSequence> values) {
    checkHeadWritten();
    headers().add(name, values);
    return this;
  }

  @Override
  public MultiMap trailers() {
    if (trailedMap == null) {
      trailedMap = new Http2HeadersAdaptor(trailers = new DefaultHttp2Headers());
    }
    return trailedMap;
  }

  @Override
  public HttpServerResponse putTrailer(String name, String value) {
    checkEnded();
    trailers().set(name, value);
    return this;
  }

  @Override
  public HttpServerResponse putTrailer(CharSequence name, CharSequence value) {
    checkEnded();
    trailers().set(name, value);
    return this;
  }

  @Override
  public HttpServerResponse putTrailer(String name, Iterable<String> values) {
    checkEnded();
    trailers().set(name, values);
    return this;
  }

  @Override
  public HttpServerResponse putTrailer(CharSequence name, Iterable<CharSequence> value) {
    checkEnded();
    trailers().set(name, value);
    return this;
  }

  @Override
  public HttpServerResponse closeHandler(@Nullable Handler<Void> handler) {
    checkEnded();
    closeHandler = handler;
    return this;
  }

  @Override
  public HttpServerResponse writeContinue() {
    checkHeadWritten();
    stream.encoder.writeHeaders(ctx, stream.id(), new DefaultHttp2Headers().status("100"), 0, false, ctx.newPromise());
    ctx.flush();
    return this;
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
    end(Unpooled.EMPTY_BUFFER);
  }

  void toNetSocket() {
    checkEnded();
    checkSendHeaders(false);
    handleEnded(false);
  }

  private void end(ByteBuf chunk) {
    if (!chunked && !headers.contains(HttpHeaderNames.CONTENT_LENGTH)) {
      headers().set(HttpHeaderNames.CONTENT_LENGTH, String.valueOf(chunk.readableBytes()));
    }
    write(chunk, true);
  }

  private boolean checkSendHeaders(boolean end) {
    if (!headWritten) {
/*
      if (!headers.contains(HttpHeaderNames.CONTENT_LENGTH) && !chunked) {
        throw new IllegalStateException("You must set the Content-Length header to be the total size of the message "
            + "body BEFORE sending any data if you are not sending an HTTP chunked response.");
      }
*/
      if (headersEndHandler != null) {
        headersEndHandler.handle(null);
      }
      headWritten = true;
      headers.status(Integer.toString(statusCode));
      stream.encoder.writeHeaders(ctx, stream.id(), headers, 0, end, ctx.newPromise());
      if (end) {
        ctx.flush();
      }
      return true;
    } else {
      return false;
    }
  }

  void write(ByteBuf chunk, boolean end) {
    checkEnded();
    if (end) {
      handleEnded(false);
    }
    int len = chunk.readableBytes();
    boolean empty = len == 0;
    boolean sent = checkSendHeaders(end && empty && trailers == null);
    if (!empty || (!sent && end)) {
      stream.writeData(chunk, end && trailers == null);
      bytesWritten += len;
    }
    if (end && trailers != null) {
      stream.encoder.writeHeaders(ctx, stream.id(), trailers, 0, true, ctx.newPromise());
    }
    if (end && bodyEndHandler != null) {
      bodyEndHandler.handle(null);
    }
  }

  @Override
  public HttpServerResponse writeFrame(int type, int flags, Buffer payload) {
    checkEnded();
    checkSendHeaders(false);
    stream.encoder.writeFrame(ctx, (byte) type, stream.id(), new Http2Flags((short) flags), payload.getByteBuf(), ctx.newPromise());
    ctx.flush();
    return this;
  }

  private void checkEnded() {
    if (ended) {
      throw new IllegalStateException("Response has already been written");
    }
  }

  private boolean handleEnded(boolean failed) {
    if (!ended) {
      ended = true;
      if (metric != null) {
        // Null in case of push response : handle this case
        if (failed) {
          connection.metrics().requestReset(metric);
        } else {
          connection.reportBytesWritten(bytesWritten);
          connection.metrics().responseEnd(metric, this);
        }
      }
      return true;
    }
    return false;
  }

  void writabilityChanged() {
    if (!ended && !writeQueueFull() && drainHandler != null) {
      drainHandler.handle(null);
    }
  }

  @Override
  public boolean writeQueueFull() {
    checkEnded();
    return stream.isNotWritable();
  }

  @Override
  public HttpServerResponse setWriteQueueMaxSize(int maxSize) {
    checkEnded();
    // It does not seem to be possible to configure this at the moment
    return this;
  }

  @Override
  public HttpServerResponse drainHandler(Handler<Void> handler) {
    checkEnded();
    drainHandler = handler;
    return this;
  }

  @Override
  public HttpServerResponse sendFile(String filename, long offset, long length) {
    return sendFile(filename, offset, length, null);
  }

  @Override
  public HttpServerResponse sendFile(String filename, long offset, long length, Handler<AsyncResult<Void>> resultHandler) {

    checkEnded();

    Context resultCtx = resultHandler != null ? stream.vertx.getOrCreateContext() : null;

    File file = stream.vertx.resolveFile(filename);
    if (!file.exists()) {
      if (resultHandler != null) {
        resultCtx.runOnContext((v) -> resultHandler.handle(Future.failedFuture(new FileNotFoundException())));
      } else {
        // log.error("File not found: " + filename);
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
        //log.error("Failed to send file", e);
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

    FileStreamChannel fileChannel = new FileStreamChannel(ar -> {
      if (ar.succeeded()) {
        bytesWritten += ar.result();
        end();
      }
      if (resultHandler != null) {
        resultCtx.runOnContext(v -> {
          resultHandler.handle(Future.succeededFuture());
        });
      }
    }, stream, offset, contentLength);
    drainHandler(fileChannel.drainHandler);
    ctx.channel().eventLoop().register(fileChannel);
    fileChannel.pipeline().fireUserEventTriggered(raf);

    return this;
  }

  @Override
  public void close() {
    connection.close();
  }

  @Override
  public boolean ended() {
    return ended;
  }

  @Override
  public boolean closed() {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean headWritten() {
    return headWritten;
  }

  @Override
  public HttpServerResponse headersEndHandler(@Nullable Handler<Void> handler) {
    headersEndHandler = handler;
    return this;
  }

  @Override
  public HttpServerResponse bodyEndHandler(@Nullable Handler<Void> handler) {
    bodyEndHandler = handler;
    return this;
  }

  @Override
  public long bytesWritten() {
    return bytesWritten;
  }

  @Override
  public int streamId() {
    return stream.id();
  }

  @Override
  public void reset(long code) {
    checkEnded();
    handleEnded(true);
    stream.encoder.writeRstStream(ctx, stream.id(), code, ctx.newPromise());
    ctx.flush();
  }

  public HttpServerResponse pushPromise(HttpMethod method, String host, String path, Handler<AsyncResult<HttpServerResponse>> handler) {
    if (push) {
      throw new IllegalStateException("A push response cannot promise another push");
    }
    checkEnded();
    Http2Headers headers = new DefaultHttp2Headers();
    headers.method(method.name());
    headers.path(path);
    if (host != null) {
      headers.authority(host);
    }
    connection.sendPush(stream.id(), headers, handler);
    return this;
  }

  @Override
  public HttpServerResponse pushPromise(HttpMethod method, String path, Handler<AsyncResult<HttpServerResponse>> handler) {
    return pushPromise(method, host, path, handler);
  }
}
