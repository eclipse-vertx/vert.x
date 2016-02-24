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
import io.netty.handler.codec.http2.Http2Connection;
import io.netty.handler.codec.http2.Http2ConnectionEncoder;
import io.netty.handler.codec.http2.Http2Exception;
import io.netty.handler.codec.http2.Http2Headers;
import io.netty.handler.codec.http2.Http2Stream;
import io.vertx.codegen.annotations.Nullable;
import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.MultiMap;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.http.StreamResetException;
import io.vertx.core.impl.VertxInternal;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.channels.ClosedChannelException;

import static io.netty.handler.codec.http.HttpResponseStatus.OK;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class Http2ServerResponseImpl implements HttpServerResponse {

  private final VertxInternal vertx;
  private final ChannelHandlerContext ctx;
  private final VertxHttp2Handler connection;
  private final Http2ConnectionEncoder encoder;
  private final Http2Stream stream;
  private final boolean push;
  private Http2Headers headers = new DefaultHttp2Headers().status(OK.codeAsText());
  private Http2HeadersAdaptor headersMap;
  private Http2Headers trailers;
  private Http2HeadersAdaptor trailedMap;
  private boolean chunked;
  private boolean headWritten;
  private boolean ended;
  private int statusCode = 200;
  private String statusMessage; // Not really used but we keep the message for the getStatusMessage()
  private long bytesWritten;
  private Handler<Void> drainHandler;
  private Handler<Throwable> exceptionHandler;
  private Handler<Void> headersEndHandler;
  private Handler<Void> bodyEndHandler;

  public Http2ServerResponseImpl(VertxInternal vertx, ChannelHandlerContext ctx, VertxHttp2Handler connection, Http2ConnectionEncoder encoder, Http2Stream stream, boolean push) {
    this.vertx = vertx;
    this.ctx = ctx;
    this.connection = connection;
    this.encoder = encoder;
    this.stream = stream;
    this.push = push;
  }

  void handleReset(long code) {
    ended = true;
    handleError(new StreamResetException(code));
  }

  void handleError(Throwable cause) {
    if (exceptionHandler != null) {
      exceptionHandler.handle(cause);
    }
  }

  void handleClose() {
    if (!ended) {
      ended = true;
      if (exceptionHandler != null) {
        exceptionHandler.handle(new ClosedChannelException());
      }
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
    headers.status("" + statusCode);
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
    // Todo : use HttpConnection for this
    return this;
  }

  @Override
  public HttpServerResponse writeContinue() {
    checkHeadWritten();
    throw new UnsupportedOperationException();
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

  private void end(ByteBuf chunk) {
    if (!chunked && !headers.contains(HttpHeaderNames.CONTENT_LENGTH)) {
      headers().set(HttpHeaderNames.CONTENT_LENGTH, String.valueOf(chunk.readableBytes()));
    }
    write(chunk, true);
  }

  private void checkSendHeaders(boolean end) {
    if (!headWritten) {
      if (!headers.contains(HttpHeaderNames.CONTENT_LENGTH) && !chunked) {
        throw new IllegalStateException("You must set the Content-Length header to be the total size of the message "
            + "body BEFORE sending any data if you are not sending an HTTP chunked response.");
      }
      if (headersEndHandler != null) {
        headersEndHandler.handle(null);
      }
      headWritten = true;
      encoder.writeHeaders(ctx, stream.id(), headers, 0, end, ctx.newPromise());
    }
  }

  void write(ByteBuf chunk, boolean end) {
    checkEnded();
    int len = chunk.readableBytes();
    boolean empty = len == 0;
    checkSendHeaders(empty && end);
    if (!empty) {
      bytesWritten += len;
      encoder.writeData(ctx, stream.id(), chunk, 0, end && trailers == null, ctx.newPromise());
    }
    if (trailers != null && end) {
      encoder.writeHeaders(ctx, stream.id(), trailers, 0, true, ctx.newPromise());
    }
    try {
      encoder.flowController().writePendingBytes();
    } catch (Http2Exception e) {
      e.printStackTrace();
    }
    if (end) {
      ended = true;
    }
    ctx.flush();
    if (end && bodyEndHandler != null) {
      bodyEndHandler.handle(null);
    }
  }

  private void checkEnded() {
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
    checkEnded();
    return !encoder.flowController().isWritable(stream);
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

    Context resultCtx = resultHandler != null ? vertx.getOrCreateContext() : null;

    File file = vertx.resolveFile(filename);
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
    if (headers.get("content-length") == null) {
      putHeader("content-length", String.valueOf(contentLength));
    }
    checkSendHeaders(false);

    FileStreamChannel channel = new FileStreamChannel(resultCtx, resultHandler, this, contentLength);
    ctx.channel().eventLoop().register(channel);
    channel.pipeline().fireUserEventTriggered(raf);

    return this;
  }

  @Override
  public void close() {
    throw new UnsupportedOperationException();
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
    ended = true;
    encoder.writeRstStream(ctx, stream.id(), code, ctx.newPromise());
  }

  public HttpServerResponse promisePush(HttpMethod method, String path, Handler<AsyncResult<HttpServerResponse>> handler) {
    if (push) {
      throw new IllegalStateException("A push response cannot promise another push");
    }
    checkEnded();
    Http2Headers headers = new DefaultHttp2Headers();
    headers.method(method.name());
    headers.path(path);
    connection.sendPush(stream.id(), headers, handler);
    return this;
  }
}
