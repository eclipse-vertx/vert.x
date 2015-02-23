/*
 * Copyright (c) 2011-2013 The original author or authors
 * ------------------------------------------------------
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * and Apache License v2.0 which accompanies this distribution.
 *
 *     The Eclipse Public License is available at
 *     http://www.eclipse.org/legal/epl-v10.html
 *
 *     The Apache License v2.0 is available at
 *     http://www.opensource.org/licenses/apache2.0.php
 *
 * You may elect to redistribute this code under either of these licenses.
 */

package io.vertx.core.http.impl;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFuture;
import io.netty.handler.codec.http.*;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.MultiMap;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.impl.ContextImpl;
import io.vertx.core.impl.VertxInternal;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.impl.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;

/**
 *
 * This class is optimised for performance when used on the same event loop that is was passed to the handler with.
 * However it can be used safely from other threads.
 *
 * The internal state is protected using the synchronized keyword. If always used on the same event loop, then
 * we benefit from biased locking which makes the overhead of synchronized near zero.
 *
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class HttpServerResponseImpl implements HttpServerResponse {

  private static final Logger log = LoggerFactory.getLogger(HttpServerResponseImpl.class);

  private final VertxInternal vertx;
  private final ServerConnection conn;
  private final HttpResponse response;
  private final HttpVersion version;
  private final boolean keepAlive;

  private boolean headWritten;
  private boolean written;
  private Handler<Void> drainHandler;
  private Handler<Throwable> exceptionHandler;
  private Handler<Void> closeHandler;
  private Handler<Void> headersEndHandler;
  private Handler<Void> bodyEndHandler;
  private boolean chunked;
  private boolean closed;
  private ChannelFuture channelFuture;
  private MultiMap headers;
  private LastHttpContent trailing;
  private MultiMap trailers;
  private String statusMessage;

  HttpServerResponseImpl(final VertxInternal vertx, ServerConnection conn, HttpRequest request) {
  	this.vertx = vertx;
  	this.conn = conn;
    this.version = request.getProtocolVersion();
    this.response = new DefaultHttpResponse(version, HttpResponseStatus.OK, false);
    this.keepAlive = version == HttpVersion.HTTP_1_1 ||
        (version == HttpVersion.HTTP_1_0 && request.headers().contains(io.vertx.core.http.HttpHeaders.CONNECTION, HttpHeaders.KEEP_ALIVE, true));
  }

  @Override
  public synchronized MultiMap headers() {
    if (headers == null) {
      headers = new HeadersAdaptor(response.headers());
    }
    return headers;
  }

  @Override
  public synchronized MultiMap trailers() {
    if (trailers == null) {
      if (trailing == null) {
        trailing = new DefaultLastHttpContent(Unpooled.EMPTY_BUFFER, false);
      }
      trailers = new HeadersAdaptor(trailing.trailingHeaders());
    }
    return trailers;
  }

  @Override
  public int getStatusCode() {
    return response.getStatus().code();
  }

  @Override
  public HttpServerResponse setStatusCode(int statusCode) {
    HttpResponseStatus status = statusMessage != null ? new HttpResponseStatus(statusCode, statusMessage) : HttpResponseStatus.valueOf(statusCode);
    this.response.setStatus(status);
    return this;
  }

  @Override
  public String getStatusMessage() {
    return response.getStatus().reasonPhrase();
  }

  @Override
  public synchronized HttpServerResponse setStatusMessage(String statusMessage) {
    this.statusMessage = statusMessage;
    this.response.setStatus(new HttpResponseStatus(response.getStatus().code(), statusMessage));
    return this;
  }

  @Override
  public synchronized HttpServerResponseImpl setChunked(boolean chunked) {
    checkWritten();
    // HTTP 1.0 does not support chunking so we ignore this if HTTP 1.0
    if (version != HttpVersion.HTTP_1_0) {
      this.chunked = chunked;
    }
    return this;
  }

  @Override
  public synchronized boolean isChunked() {
    return chunked;
  }

  @Override
  public synchronized HttpServerResponseImpl putHeader(String key, String value) {
    checkWritten();
    headers().set(key, value);
    return this;
  }

  @Override
  public synchronized HttpServerResponseImpl putHeader(String key, Iterable<String> values) {
    checkWritten();
    headers().set(key, values);
    return this;
  }

  @Override
  public synchronized HttpServerResponseImpl putTrailer(String key, String value) {
    checkWritten();
    trailers().set(key, value);
    return this;
  }

  @Override
  public synchronized HttpServerResponseImpl putTrailer(String key, Iterable<String> values) {
    checkWritten();
    trailers().set(key, values);
    return this;
  }

  @Override
  public synchronized HttpServerResponse putHeader(CharSequence name, CharSequence value) {
    checkWritten();
    headers().set(name, value);
    return this;
  }

  @Override
  public synchronized HttpServerResponse putHeader(CharSequence name, Iterable<CharSequence> values) {
    checkWritten();
    headers().set(name, values);
    return this;
  }

  @Override
  public synchronized HttpServerResponse putTrailer(CharSequence name, CharSequence value) {
    checkWritten();
    trailers().set(name, value);
    return this;
  }

  @Override
  public synchronized HttpServerResponse putTrailer(CharSequence name, Iterable<CharSequence> value) {
    checkWritten();
    trailers().set(name, value);
    return this;
  }

  @Override
  public synchronized HttpServerResponse setWriteQueueMaxSize(int size) {
    checkWritten();
    conn.doSetWriteQueueMaxSize(size);
    return this;
  }

  @Override
  public synchronized boolean writeQueueFull() {
    checkWritten();
    return conn.isNotWritable();
  }

  @Override
  public synchronized HttpServerResponse drainHandler(Handler<Void> handler) {
    checkWritten();
    this.drainHandler = handler;
    conn.getContext().runOnContext(v -> conn.handleInterestedOpsChanged());
    return this;
  }

  @Override
  public synchronized HttpServerResponse exceptionHandler(Handler<Throwable> handler) {
    checkWritten();
    this.exceptionHandler = handler;
    return this;
  }

  @Override
  public synchronized HttpServerResponse closeHandler(Handler<Void> handler) {
    checkWritten();
    this.closeHandler = handler;
    return this;
  }

  @Override
  public HttpServerResponseImpl write(Buffer chunk) {
    ByteBuf buf = chunk.getByteBuf();
    return write(buf, null);
  }

  @Override
  public HttpServerResponseImpl write(String chunk, String enc) {
    return write(Buffer.buffer(chunk, enc).getByteBuf(),  null);
  }

  @Override
  public HttpServerResponseImpl write(String chunk) {
    return write(Buffer.buffer(chunk).getByteBuf(), null);
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
  public synchronized void end(Buffer chunk) {
    if (!chunked && !contentLengthSet()) {
      headers().set(HttpHeaders.CONTENT_LENGTH, String.valueOf(chunk.length()));
    }
    ByteBuf buf = chunk.getByteBuf();
    end0(buf);
  }

  @Override
  public synchronized void close() {
    if (!closed) {
      if (headWritten) {
        closeConnAfterWrite();
      } else {
        conn.close();
      }
      closed = true;
    }
  }

  @Override
  public synchronized void end() {
    end0(Unpooled.EMPTY_BUFFER);
  }

  private void end0(ByteBuf data) {
    checkWritten();
    if (!headWritten) {
      // if the head was not written yet we can write out everything in on go
      // which is cheaper.
      prepareHeaders();
      FullHttpResponse resp;
      if (trailing != null) {
        resp = new AssembledFullHttpResponse(response, data, trailing.trailingHeaders(), trailing.getDecoderResult());
      }  else {
        resp = new AssembledFullHttpResponse(response, data);
      }
      channelFuture = conn.writeToChannel(resp);
      headWritten = true;
    } else {
      if (!data.isReadable()) {
        if (trailing == null) {
          channelFuture = conn.writeToChannel(LastHttpContent.EMPTY_LAST_CONTENT);
        } else {
          channelFuture = conn.writeToChannel(trailing);
        }
      } else {
        LastHttpContent content;
        if (trailing != null) {
          content = new AssembledLastHttpContent(data, trailing.trailingHeaders(), trailing.getDecoderResult());
        } else {
          content = new DefaultLastHttpContent(data, false);
        }
        channelFuture = conn.writeToChannel(content);
      }
    }

    if (!keepAlive) {
      closeConnAfterWrite();
    }
    written = true;
    conn.responseComplete();
    if (bodyEndHandler != null) {
      bodyEndHandler.handle(null);
    }
  }

  @Override
  public HttpServerResponseImpl sendFile(String filename) {
    doSendFile(filename, null);
    return this;
  }

  @Override
  public HttpServerResponse sendFile(String filename, Handler<AsyncResult<Void>> resultHandler) {
    doSendFile(filename, resultHandler);
    return this;
  }

  @Override
  public boolean ended() {
    return written;
  }

  @Override
  public boolean headWritten() {
    return headWritten;
  }

  @Override
  public synchronized HttpServerResponse headersEndHandler(Handler<Void> handler) {
    this.headersEndHandler = handler;
    return this;
  }

  @Override
  public synchronized HttpServerResponse bodyEndHandler(Handler<Void> handler) {
    this.bodyEndHandler = handler;
    return this;
  }

  private synchronized void doSendFile(String filename, Handler<AsyncResult<Void>> resultHandler) {
    if (headWritten) {
      throw new IllegalStateException("Head already written");
    }
    checkWritten();
    File file = vertx.resolveFile(filename);
    long fileLength = file.length();
    if (!contentLengthSet()) {
      putHeader(HttpHeaders.CONTENT_LENGTH, String.valueOf(fileLength));
    }
    if (!contentTypeSet()) {
      int li = filename.lastIndexOf('.');
      if (li != -1 && li != filename.length() - 1) {
        String ext = filename.substring(li + 1, filename.length());
        String contentType = MimeMapping.getMimeTypeForExtension(ext);
        if (contentType != null) {
          putHeader(HttpHeaders.CONTENT_TYPE, contentType);
        }
      }
    }
    prepareHeaders();

    RandomAccessFile raf;
    try {
      raf = new RandomAccessFile(file, "r");
      conn.queueForWrite(response);
      conn.sendFile(raf, fileLength);
    } catch (IOException e) {
      if (resultHandler != null) {
        ContextImpl ctx = vertx.getOrCreateContext();
        ctx.runOnContext((v) -> resultHandler.handle(Future.failedFuture(e)));
      } else {
        log.error("Failed to send file", e);
      }
      return;
    }

    // write an empty last content to let the http encoder know the response is complete
    channelFuture = conn.writeToChannel(LastHttpContent.EMPTY_LAST_CONTENT);
    headWritten = written = true;

    if (resultHandler != null) {
      ContextImpl ctx = vertx.getOrCreateContext();
      channelFuture.addListener(future -> {
        AsyncResult<Void> res;
        if (future.isSuccess()) {
          res = Future.succeededFuture();
        } else {
          res = Future.failedFuture(future.cause());
        }
        ctx.runOnContext((v) -> resultHandler.handle(res));
      });
    }

    if (!keepAlive) {
      closeConnAfterWrite();
    }
    conn.responseComplete();
    if (bodyEndHandler != null) {
      bodyEndHandler.handle(null);
    }
  }

  private synchronized boolean contentLengthSet() {
    if (headers == null) {
      return false;
    }
    return response.headers().contains(HttpHeaders.CONTENT_LENGTH);
  }

  private synchronized boolean contentTypeSet() {
    if (headers == null) {
      return false;
    }
    return response.headers().contains(HttpHeaders.CONTENT_TYPE);
  }

  private void closeConnAfterWrite() {
    if (channelFuture != null) {
      channelFuture.addListener(fut -> conn.close());
    }
  }

  synchronized void handleDrained() {
    if (drainHandler != null) {
      drainHandler.handle(null);
    }
  }

  synchronized void handleException(Throwable t) {
    if (exceptionHandler != null) {
      exceptionHandler.handle(t);
    }
  }

  synchronized void handleClosed() {
    if (closeHandler != null) {
      closeHandler.handle(null);
    }
  }

  private void checkWritten() {
    if (written) {
      throw new IllegalStateException("Response has already been written");
    }
  }

  private void prepareHeaders() {
    if (version == HttpVersion.HTTP_1_0 && keepAlive) {
      response.headers().set(HttpHeaders.CONNECTION, HttpHeaders.KEEP_ALIVE);
    }
    if (chunked) {
      response.headers().set(HttpHeaders.TRANSFER_ENCODING, HttpHeaders.CHUNKED);
    } else if (version != HttpVersion.HTTP_1_0 && !contentLengthSet()) {
      response.headers().set(HttpHeaders.CONTENT_LENGTH, "0");
    }
    if (headersEndHandler != null) {
      headersEndHandler.handle(null);
    }
  }

  private synchronized HttpServerResponseImpl write(ByteBuf chunk, final Handler<AsyncResult<Void>> completionHandler) {
    checkWritten();
    if (!headWritten && version != HttpVersion.HTTP_1_0 && !chunked && !contentLengthSet()) {
      throw new IllegalStateException("You must set the Content-Length header to be the total size of the message "
                                              + "body BEFORE sending any data if you are not using HTTP chunked encoding.");
    }

    if (!headWritten) {
      prepareHeaders();
      channelFuture = conn.writeToChannel(new AssembledHttpResponse(response, chunk));
      headWritten = true;
    }  else {
      channelFuture = conn.writeToChannel(new DefaultHttpContent(chunk));
    }

    conn.addFuture(completionHandler, channelFuture);
    return this;
  }
}
