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
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http2.DefaultHttp2Headers;
import io.netty.handler.codec.http2.Http2ConnectionEncoder;
import io.netty.handler.codec.http2.Http2Exception;
import io.netty.handler.codec.http2.Http2Headers;
import io.vertx.codegen.annotations.Nullable;
import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.MultiMap;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpServerResponse;

import static io.netty.handler.codec.http.HttpResponseStatus.OK;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class Http2ServerResponseImpl implements HttpServerResponse {

  private final ChannelHandlerContext ctx;
  private final Http2ConnectionEncoder encoder;
  private final int streamId;
  private Http2Headers headers = new DefaultHttp2Headers().status(OK.codeAsText());
  private Http2HeadersAdaptor headersMap;

  public Http2ServerResponseImpl(ChannelHandlerContext ctx, Http2ConnectionEncoder encoder, int streamId) {
    this.ctx = ctx;
    this.encoder = encoder;
    this.streamId = streamId;
  }

  @Override
  public HttpServerResponse exceptionHandler(Handler<Throwable> handler) {
    throw new UnsupportedOperationException();
  }

  @Override
  public HttpServerResponse write(Buffer data) {
    throw new UnsupportedOperationException();
  }

  @Override
  public HttpServerResponse setWriteQueueMaxSize(int maxSize) {
    throw new UnsupportedOperationException();
  }

  @Override
  public HttpServerResponse drainHandler(Handler<Void> handler) {
    throw new UnsupportedOperationException();
  }

  @Override
  public int getStatusCode() {
    throw new UnsupportedOperationException();
  }

  @Override
  public HttpServerResponse setStatusCode(int statusCode) {
    throw new UnsupportedOperationException();
  }

  @Override
  public String getStatusMessage() {
    throw new UnsupportedOperationException();
  }

  @Override
  public HttpServerResponse setStatusMessage(String statusMessage) {
    throw new UnsupportedOperationException();
  }

  @Override
  public HttpServerResponse setChunked(boolean chunked) {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean isChunked() {
    throw new UnsupportedOperationException();
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
    headers.add(name, value);
    return this;
  }

  @Override
  public HttpServerResponse putHeader(CharSequence name, CharSequence value) {
    headers.add(name, value);
    return this;
  }

  @Override
  public HttpServerResponse putHeader(String name, Iterable<String> values) {
    headers.add(name, values);
    return this;
  }

  @Override
  public HttpServerResponse putHeader(CharSequence name, Iterable<CharSequence> values) {
    headers.add(name, values);
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
  public HttpServerResponse write(String chunk, String enc) {
    throw new UnsupportedOperationException();
  }

  @Override
  public HttpServerResponse write(String chunk) {
    throw new UnsupportedOperationException();
  }

  @Override
  public HttpServerResponse writeContinue() {
    throw new UnsupportedOperationException();
  }

  @Override
  public void end(String chunk) {
    end(Buffer.buffer(chunk));
  }

  @Override
  public void end(String chunk, String enc) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void end(Buffer chunk) {
    ByteBuf buf = chunk.getByteBuf();
    end0(buf);
  }

  @Override
  public void end() {
    throw new UnsupportedOperationException();
  }

  private void end0(ByteBuf data) {
    encoder.writeHeaders(ctx, streamId, headers, 0, false, ctx.newPromise());
    encoder.writeData(ctx, streamId, data, 0, true, ctx.newPromise());
    try {
      encoder.flowController().writePendingBytes();
    } catch (Http2Exception e) {
      e.printStackTrace();
    }
    ctx.flush();
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
    throw new UnsupportedOperationException();
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
  public boolean writeQueueFull() {
    throw new UnsupportedOperationException();
  }
}
