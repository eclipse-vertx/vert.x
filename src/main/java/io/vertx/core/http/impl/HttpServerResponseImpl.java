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
import io.netty.channel.ChannelPromise;
import io.netty.handler.codec.http.*;
import io.vertx.codegen.annotations.Nullable;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.MultiMap;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.http.impl.headers.VertxHttpHeaders;
import io.vertx.core.impl.ContextInternal;
import io.vertx.core.impl.VertxInternal;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.core.net.NetSocket;
import io.vertx.core.net.impl.ConnectionBase;
import io.vertx.core.spi.metrics.Metrics;

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
  private final Http1xServerConnection conn;
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
  private boolean closed;
  private final VertxHttpHeaders headers;
  private MultiMap trailers;
  private io.netty.handler.codec.http.HttpHeaders trailingHeaders = EmptyHttpHeaders.INSTANCE;
  private String statusMessage;
  private long bytesWritten;
  private NetSocket netSocket;

  HttpServerResponseImpl(final VertxInternal vertx, Http1xServerConnection conn, DefaultHttpRequest request, Object requestMetric) {
    this.vertx = vertx;
    this.conn = conn;
    this.version = request.protocolVersion();
    this.headers = new VertxHttpHeaders();
    this.status = HttpResponseStatus.OK;
    this.requestMetric = requestMetric;
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
  public HttpServerResponseImpl setChunked(boolean chunked) {
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
      return HttpHeaders.CHUNKED.equals(headers.get(HttpHeaders.TRANSFER_ENCODING));
    }
  }

  @Override
  public HttpServerResponseImpl putHeader(String key, String value) {
    synchronized (conn) {
      checkValid();
      headers.set(key, value);
      return this;
    }
  }

  @Override
  public HttpServerResponseImpl putHeader(String key, Iterable<String> values) {
    synchronized (conn) {
      checkValid();
      headers.set(key, values);
      return this;
    }
  }

  @Override
  public HttpServerResponseImpl putTrailer(String key, String value) {
    synchronized (conn) {
      checkValid();
      trailers().set(key, value);
      return this;
    }
  }

  @Override
  public HttpServerResponseImpl putTrailer(String key, Iterable<String> values) {
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
      return conn.isNotWritable();
    }
  }

  @Override
  public HttpServerResponse drainHandler(Handler<Void> handler) {
    synchronized (conn) {
      if (handler != null) {
        checkValid();
      }
      drainHandler = handler;
      conn.getContext().runOnContext(v -> conn.handleInterestedOpsChanged());
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
      end0(chunk.getByteBuf());
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

  private void end0(ByteBuf data) {
    checkValid();
    bytesWritten += data.readableBytes();
    if (!headWritten) {
      // if the head was not written yet we can write out everything in one go
      // which is cheaper.
      prepareHeaders(bytesWritten);
      conn.writeToChannel(new AssembledFullHttpResponse(head, version, status, headers, data, trailingHeaders));
    } else {
      conn.writeToChannel(new AssembledLastHttpContent(data, trailingHeaders));
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
    if (endHandler != null) {
      endHandler.handle(null);
    }
  }

  private void doSendFile(String filename, long offset, long length, Handler<AsyncResult<Void>> resultHandler) {
    synchronized (conn) {
      if (headWritten) {
        throw new IllegalStateException("Head already written");
      }
      checkValid();
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

      RandomAccessFile raf = null;
      try {
        raf = new RandomAccessFile(file, "r");
        conn.writeToChannel(new AssembledHttpResponse(head, version, status, headers));
        conn.sendFile(raf, Math.min(offset, file.length()), contentLength);
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

      // write an empty last content to let the http encoder know the response is complete
      ChannelPromise channelFuture = conn.channelFuture();
      conn.writeToChannel(LastHttpContent.EMPTY_LAST_CONTENT, channelFuture);
      written = true;

      if (resultHandler != null) {
        ContextInternal ctx = vertx.getOrCreateContext();
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

  private void closeConnAfterWrite() {
    ChannelPromise channelFuture = conn.channelFuture();
    conn.writeToChannel(Unpooled.EMPTY_BUFFER, channelFuture);
    channelFuture.addListener(fut -> conn.close());
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
      if (t == Http1xServerConnection.CLOSED_EXCEPTION) {
        handleClosed();
      } else {
        if (exceptionHandler != null) {
          exceptionHandler.handle(t);
        }
      }
    }
  }

  private void handleClosed() {
    if (!closed) {
      closed = true;
      if (!written && exceptionHandler != null) {
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

  private void checkValid() {
    if (written) {
      throw new IllegalStateException("Response has already been written");
    }
    if (closed) {
      throw new IllegalStateException("Response is closed");
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
    if (Metrics.METRICS_ENABLED) {
      reportResponseBegin();
    }
    headWritten = true;
  }

  private void reportResponseBegin() {
    if (conn.metrics != null) {
      conn.metrics.responseBegin(requestMetric, this);
    }
  }

  private HttpServerResponseImpl write(ByteBuf chunk) {
    synchronized (conn) {
      checkValid();
      if (!headWritten && !headers.contains(HttpHeaders.TRANSFER_ENCODING) && !headers.contains(HttpHeaders.CONTENT_LENGTH)) {
        if (version != HttpVersion.HTTP_1_0) {
          throw new IllegalStateException("You must set the Content-Length header to be the total size of the message "
            + "body BEFORE sending any data if you are not using HTTP chunked encoding.");
        }
      }

      bytesWritten += chunk.readableBytes();
      if (!headWritten) {
        prepareHeaders(-1);
        conn.writeToChannel(new AssembledHttpResponse(head, version, status, headers, chunk));
      } else {
        conn.writeToChannel(new DefaultHttpContent(chunk));
      }

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
  public HttpServerResponse push(HttpMethod method, String path, MultiMap headers, Handler<AsyncResult<HttpServerResponse>> handler) {
    return push(method, null, path, headers, handler);
  }

  @Override
  public HttpServerResponse push(io.vertx.core.http.HttpMethod method, String host, String path, Handler<AsyncResult<HttpServerResponse>> handler) {
    return push(method, path, handler);
  }

  @Override
  public HttpServerResponse push(HttpMethod method, String path, Handler<AsyncResult<HttpServerResponse>> handler) {
    return push(method, path, null, null, handler);
  }

  @Override
  public HttpServerResponse push(HttpMethod method, String host, String path, MultiMap headers, Handler<AsyncResult<HttpServerResponse>> handler) {
    handler.handle(Future.failedFuture("Push promise is only supported with HTTP2"));
    return this;
  }

  @Override
  public HttpServerResponse writeCustomFrame(int type, int flags, Buffer payload) {
    return this;
  }
}
