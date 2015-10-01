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
import io.vertx.core.logging.LoggerFactory;

import java.io.File;
import java.io.FileNotFoundException;
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
 * It's important we don't have different locks for connection and request/response to avoid deadlock conditions
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
    this.keepAlive = (version == HttpVersion.HTTP_1_1 && !request.headers().contains(io.vertx.core.http.HttpHeaders.CONNECTION, HttpHeaders.CLOSE, true))
      || (version == HttpVersion.HTTP_1_0 && request.headers().contains(io.vertx.core.http.HttpHeaders.CONNECTION, HttpHeaders.KEEP_ALIVE, true));
  }

  @Override
  public MultiMap headers() {
    if (headers == null) {
      headers = new HeadersAdaptor(response.headers());
    }
    return headers;
  }

  @Override
  public MultiMap trailers() {
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
  public HttpServerResponse setStatusMessage(String statusMessage) {
    synchronized (conn) {
      this.statusMessage = statusMessage;
      this.response.setStatus(new HttpResponseStatus(response.getStatus().code(), statusMessage));
      return this;
    }
  }

  @Override
  public HttpServerResponseImpl setChunked(boolean chunked) {
    synchronized (conn) {
      checkWritten();
      // HTTP 1.0 does not support chunking so we ignore this if HTTP 1.0
      if (version != HttpVersion.HTTP_1_0) {
        this.chunked = chunked;
      }
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
  public HttpServerResponseImpl putHeader(String key, String value) {
    synchronized (conn) {
      checkWritten();
      headers().set(key, value);
      return this;
    }
  }

  @Override
  public HttpServerResponseImpl putHeader(String key, Iterable<String> values) {
    synchronized (conn) {
      checkWritten();
      headers().set(key, values);
      return this;
    }
  }

  @Override
  public HttpServerResponseImpl putTrailer(String key, String value) {
    synchronized (conn) {
      checkWritten();
      trailers().set(key, value);
      return this;
    }
  }

  @Override
  public HttpServerResponseImpl putTrailer(String key, Iterable<String> values) {
    synchronized (conn) {
      checkWritten();
      trailers().set(key, values);
      return this;
    }
  }

  @Override
  public HttpServerResponse putHeader(CharSequence name, CharSequence value) {
    synchronized (conn) {
      checkWritten();
      headers().set(name, value);
      return this;
    }
  }

  @Override
  public HttpServerResponse putHeader(CharSequence name, Iterable<CharSequence> values) {
    synchronized (conn) {
      checkWritten();
      headers().set(name, values);
      return this;
    }
  }

  @Override
  public HttpServerResponse putTrailer(CharSequence name, CharSequence value) {
    synchronized (conn) {
      checkWritten();
      trailers().set(name, value);
      return this;
    }
  }

  @Override
  public HttpServerResponse putTrailer(CharSequence name, Iterable<CharSequence> value) {
    synchronized (conn) {
      checkWritten();
      trailers().set(name, value);
      return this;
    }
  }

  @Override
  public HttpServerResponse setWriteQueueMaxSize(int size) {
    synchronized (conn) {
      checkWritten();
      conn.doSetWriteQueueMaxSize(size);
      return this;
    }
  }

  @Override
  public boolean writeQueueFull() {
    synchronized (conn) {
      checkWritten();
      return conn.isNotWritable();
    }
  }

  @Override
  public HttpServerResponse drainHandler(Handler<Void> handler) {
    synchronized (conn) {
      checkWritten();
      this.drainHandler = handler;
      conn.getContext().runOnContext(v -> conn.handleInterestedOpsChanged());
      return this;
    }
  }

  @Override
  public HttpServerResponse exceptionHandler(Handler<Throwable> handler) {
    synchronized (conn) {
      checkWritten();
      this.exceptionHandler = handler;
      return this;
    }
  }

  @Override
  public HttpServerResponse closeHandler(Handler<Void> handler) {
    synchronized (conn) {
      checkWritten();
      this.closeHandler = handler;
      return this;
    }
  }

  @Override
  public HttpServerResponseImpl write(Buffer chunk) {
    ByteBuf buf = chunk.getByteBuf();
    return write(buf);
  }

  @Override
  public HttpServerResponseImpl write(String chunk, String enc) {
    return write(Buffer.buffer(chunk, enc).getByteBuf());
  }

  @Override
  public HttpServerResponseImpl write(String chunk) {
    return write(Buffer.buffer(chunk).getByteBuf());
  }

  @Override
  public HttpServerResponse writeContinue() {
    conn.write100Continue();
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
    synchronized (conn) {
      if (!chunked && !contentLengthSet()) {
        headers().set(HttpHeaders.CONTENT_LENGTH, String.valueOf(chunk.length()));
      }
      ByteBuf buf = chunk.getByteBuf();
      end0(buf);
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
  public void end() {
    synchronized (conn) {
      end0(Unpooled.EMPTY_BUFFER);
    }
  }

  @Override
  public HttpServerResponseImpl sendFile(String filename, long offset, long length) {
    doSendFile(filename, offset, length, null);
    return this;
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

  private void end0(ByteBuf data) {
    checkWritten();
    if (!headWritten) {
      // if the head was not written yet we can write out everything in one go
      // which is cheaper.
      prepareHeaders();
      FullHttpResponse resp;
      if (trailing != null) {
        resp = new AssembledFullHttpResponse(response, data, trailing.trailingHeaders(), trailing.getDecoderResult());
      }  else {
        resp = new AssembledFullHttpResponse(response, data);
      }
      channelFuture = conn.writeToChannel(resp);
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
      closed = true;
    }
    written = true;
    conn.responseComplete();
    if (bodyEndHandler != null) {
      bodyEndHandler.handle(null);
    }
  }

  private void doSendFile(String filename, long offset, long length, Handler<AsyncResult<Void>> resultHandler) {
    synchronized (conn) {
      if (headWritten) {
        throw new IllegalStateException("Head already written");
      }
      checkWritten();
      File file = vertx.resolveFile(filename);

      if (!file.exists()) {
        if (resultHandler != null) {
          ContextImpl ctx = vertx.getOrCreateContext();
          ctx.runOnContext((v) -> resultHandler.handle(Future.failedFuture(new FileNotFoundException())));
        } else {
          log.error("File not found: " + filename);
        }
        return;
      }

      long contentLength = Math.min(length, file.length() - offset);
      if (!contentLengthSet()) {
        putHeader(HttpHeaders.CONTENT_LENGTH, String.valueOf(contentLength));
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

      RandomAccessFile raf = null;
      try {
        raf = new RandomAccessFile(file, "r");
        conn.queueForWrite(response);
        conn.sendFile(raf, Math.min(offset, file.length()), contentLength);
      } catch (IOException e) {
        try {
          if (raf != null) {
            raf.close();
          }
        } catch (IOException ignore) {
        }
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
      written = true;

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
  }

  private boolean contentLengthSet() {
    if (headers == null) {
      return false;
    }
    return response.headers().contains(HttpHeaders.CONTENT_LENGTH);
  }

  private boolean contentTypeSet() {
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

  void handleDrained() {
    synchronized (conn) {
      if (drainHandler != null) {
        drainHandler.handle(null);
      }
    }
  }

  void handleException(Throwable t) {
    synchronized (conn) {
      if (exceptionHandler != null) {
        exceptionHandler.handle(t);
      }
    }
  }

  void handleClosed() {
    synchronized (conn) {
      if (closeHandler != null) {
        closeHandler.handle(null);
      }
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
    } else if (version == HttpVersion.HTTP_1_1 && !keepAlive) {
      response.headers().set(HttpHeaders.CONNECTION, HttpHeaders.CLOSE);
    }
    if (chunked) {
      response.headers().set(HttpHeaders.TRANSFER_ENCODING, HttpHeaders.CHUNKED);
    } else if (keepAlive && !contentLengthSet()) {
      response.headers().set(HttpHeaders.CONTENT_LENGTH, "0");
    }
    if (headersEndHandler != null) {
      headersEndHandler.handle(null);
    }
    headWritten = true;
  }

  private HttpServerResponseImpl write(ByteBuf chunk) {
    synchronized (conn) {
      checkWritten();
      if (!headWritten && version != HttpVersion.HTTP_1_0 && !chunked && !contentLengthSet()) {
        throw new IllegalStateException("You must set the Content-Length header to be the total size of the message "
          + "body BEFORE sending any data if you are not using HTTP chunked encoding.");
      }

      if (!headWritten) {
        prepareHeaders();
        channelFuture = conn.writeToChannel(new AssembledHttpResponse(response, chunk));
      } else {
        channelFuture = conn.writeToChannel(new DefaultHttpContent(chunk));
      }

      return this;
    }
  }
}
