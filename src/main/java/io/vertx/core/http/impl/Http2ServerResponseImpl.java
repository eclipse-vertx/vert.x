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
import io.netty.handler.codec.http2.Http2ConnectionEncoder;
import io.netty.handler.codec.http2.Http2Exception;
import io.netty.handler.codec.http2.Http2Headers;
import io.netty.handler.codec.http2.Http2Stream;
import io.vertx.codegen.annotations.Nullable;
import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.MultiMap;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.http.StreamResetException;

import static io.netty.handler.codec.http.HttpResponseStatus.OK;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class Http2ServerResponseImpl implements HttpServerResponse {

  private final ChannelHandlerContext ctx;
  private final Http2ConnectionEncoder encoder;
  private final Http2Stream stream;
  private Http2Headers headers = new DefaultHttp2Headers().status(OK.codeAsText());
  private Http2HeadersAdaptor headersMap;
  private boolean chunked;
  private boolean headWritten;
  private int statusCode = 200;
  private String statusMessage; // Not really used but we keep the message for the getStatusMessage()
  private Handler<Void> drainHandler;
  private Handler<Throwable> exceptionHandler;

  public Http2ServerResponseImpl(ChannelHandlerContext ctx, Http2ConnectionEncoder encoder, Http2Stream stream) {
    this.ctx = ctx;
    this.encoder = encoder;
    this.stream = stream;
  }

  void handleReset(long code) {
    if (exceptionHandler != null) {
      exceptionHandler.handle(new StreamResetException(code));
    }
  }

  @Override
  public HttpServerResponse exceptionHandler(Handler<Throwable> handler) {
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
    this.statusMessage = statusMessage;
    return this;
  }

  @Override
  public HttpServerResponse setChunked(boolean chunked) {
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
    headers().add(name, value);
    return this;
  }

  @Override
  public HttpServerResponse putHeader(CharSequence name, CharSequence value) {
    headers().add(name, value);
    return this;
  }

  @Override
  public HttpServerResponse putHeader(String name, Iterable<String> values) {
    headers().add(name, values);
    return this;
  }

  @Override
  public HttpServerResponse putHeader(CharSequence name, Iterable<CharSequence> values) {
    headers().add(name, values);
    return this;
  }

  @Override
  public MultiMap trailers() {
    throw new UnsupportedOperationException();
  }

  @Override
  public HttpServerResponse putTrailer(String name, String value) {
    throw new UnsupportedOperationException();
  }

  @Override
  public HttpServerResponse putTrailer(CharSequence name, CharSequence value) {
    throw new UnsupportedOperationException();
  }

  @Override
  public HttpServerResponse putTrailer(String name, Iterable<String> values) {
    throw new UnsupportedOperationException();
  }

  @Override
  public HttpServerResponse putTrailer(CharSequence name, Iterable<CharSequence> value) {
    throw new UnsupportedOperationException();
  }

  @Override
  public HttpServerResponse closeHandler(@Nullable Handler<Void> handler) {
    throw new UnsupportedOperationException();
  }

  @Override
  public HttpServerResponse writeContinue() {
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
    return write(chunk, false);
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

  private void checkSendHeaders() {
    if (!headWritten) {
      headWritten = true;
      if (!headers.contains(HttpHeaderNames.CONTENT_LENGTH) && !chunked) {
        throw new IllegalStateException("You must set the Content-Length header to be the total size of the message "
            + "body BEFORE sending any data if you are not sending an HTTP chunked response.");
      }
      encoder.writeHeaders(ctx, stream.id(), headers, 0, false, ctx.newPromise());
      headWritten = true;
    }
  }

  private Http2ServerResponseImpl write(ByteBuf chunk, boolean last) {
    checkSendHeaders();
    encoder.writeData(ctx, stream.id(), chunk, 0, last, ctx.newPromise());
    try {
      encoder.flowController().writePendingBytes();
    } catch (Http2Exception e) {
      e.printStackTrace();
    }
    ctx.flush();
    return this;
  }

  @Override
  public boolean writeQueueFull() {
    return !encoder.flowController().isWritable(stream);
  }

  @Override
  public HttpServerResponse setWriteQueueMaxSize(int maxSize) {
    // It does not seem to be possible to configure this at the moment
    return this;
  }

  @Override
  public HttpServerResponse drainHandler(Handler<Void> handler) {
    drainHandler = handler;
    return this;
  }

  void writabilityChanged() {
    if (!writeQueueFull() && drainHandler != null) {
      drainHandler.handle(null);
    }
  }

  @Override
  public HttpServerResponse sendFile(String filename, long offset, long length) {
    throw new UnsupportedOperationException();
  }

  @Override
  public HttpServerResponse sendFile(String filename, long offset, long length, Handler<AsyncResult<Void>> resultHandler) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void close() {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean ended() {
    throw new UnsupportedOperationException();
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
    throw new UnsupportedOperationException();
  }

  @Override
  public HttpServerResponse bodyEndHandler(@Nullable Handler<Void> handler) {
    throw new UnsupportedOperationException();
  }

  @Override
  public long bytesWritten() {
    throw new UnsupportedOperationException();
  }

  @Override
  public void reset(long code) {
    encoder.writeRstStream(ctx, stream.id(), code, ctx.newPromise());
  }
}
