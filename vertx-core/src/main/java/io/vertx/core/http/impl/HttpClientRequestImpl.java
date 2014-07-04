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
import io.netty.buffer.CompositeByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.http.DefaultHttpContent;
import io.netty.handler.codec.http.DefaultHttpRequest;
import io.netty.handler.codec.http.DefaultLastHttpContent;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http.LastHttpContent;
import io.vertx.core.Handler;
import io.vertx.core.MultiMap;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.http.HttpClientResponse;
import io.vertx.core.http.RequestOptions;
import io.vertx.core.impl.VertxInternal;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.impl.LoggerFactory;

import java.util.concurrent.TimeoutException;

/**
 *
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class HttpClientRequestImpl implements HttpClientRequest {

  private static final Logger log = LoggerFactory.getLogger(HttpClientRequestImpl.class);

  private final RequestOptions options;
  private final HttpClientImpl client;
  private final HttpRequest request;
  private final Handler<HttpClientResponse> respHandler;
  private Handler<Void> continueHandler;
  private final VertxInternal vertx;
  private boolean chunked;
  private ClientConnection conn;
  private Handler<Void> drainHandler;
  private Handler<Throwable> exceptionHandler;
  private boolean headWritten;
  private boolean completed;
  private ByteBuf pendingChunks;
  private int pendingMaxSize = -1;
  private boolean connecting;
  private boolean writeHead;
  private long written;
  private long currentTimeoutTimerId = -1;
  private MultiMap headers;
  private boolean exceptionOccurred;
  private long lastDataReceived;

  HttpClientRequestImpl(HttpClientImpl client, String method, RequestOptions options,
                        Handler<HttpClientResponse> respHandler, VertxInternal vertx) {
    this.options = options;
    this.client = client;
    this.request = new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.valueOf(method), options.getRequestURI(), false);
    this.chunked = false;
    this.respHandler = respHandler;
    this.vertx = vertx;
  }

  @Override
  public HttpClientRequestImpl setChunked(boolean chunked) {
    check();
    if (written > 0) {
      throw new IllegalStateException("Cannot set chunked after data has been written on request");
    }
    this.chunked = chunked;
    return this;
  }

  @Override
  public boolean isChunked() {
    return chunked;
  }

  @Override
  public MultiMap headers() {
    if (headers == null) {
      headers = new HttpHeadersAdapter(request.headers());
    }
    return headers;
  }

  @Override
  public HttpClientRequest putHeader(String name, String value) {
    check();
    headers().set(name, value);
    return this;
  }

  @Override
  public HttpClientRequest putHeader(String name, Iterable<String> values) {
    check();
    headers().set(name, values);
    return this;
  }

  @Override
  public HttpClientRequestImpl writeBuffer(Buffer chunk) {
    check();
    ByteBuf buf = chunk.getByteBuf();
    write(buf, false);
    return this;
  }

  @Override
  public HttpClientRequestImpl writeString(String chunk) {
    check();
    return writeBuffer(Buffer.newBuffer(chunk));
  }

  @Override
  public HttpClientRequestImpl writeString(String chunk, String enc) {
    check();
    return writeBuffer(Buffer.newBuffer(chunk, enc));
  }

  @Override
  public HttpClientRequest setWriteQueueMaxSize(int maxSize) {
    check();
    if (conn != null) {
      conn.doSetWriteQueueMaxSize(maxSize);
    } else {
      pendingMaxSize = maxSize;
    }
    return this;
  }

  @Override
  public boolean writeQueueFull() {
    check();
    if (conn != null) {
      return conn.isNotWritable();
    } else {
      return false;
    }
  }

  @Override
  public HttpClientRequest drainHandler(Handler<Void> handler) {
    check();
    this.drainHandler = handler;
    if (conn != null) {
      conn.getContext().execute(conn::handleInterestedOpsChanged, false);
    }
    return this;
  }

  @Override
  public HttpClientRequest exceptionHandler(Handler<Throwable> handler) {
    check();
    this.exceptionHandler = t -> {
      cancelOutstandingTimeoutTimer();
      handler.handle(t);
    };
    return this;
  }

  @Override
  public HttpClientRequest continueHandler(Handler<Void> handler) {
    check();
    this.continueHandler = handler;
    return this;
  }

  @Override
  public HttpClientRequestImpl sendHead() {
    check();
    if (conn != null) {
      if (!headWritten) {
        writeHead();
      }
    } else {
      connect();
      writeHead = true;
    }
    return this;
  }

  @Override
  public void writeStringAndEnd(String chunk) {
    writeBufferAndEnd(Buffer.newBuffer(chunk));
  }

  @Override
  public void writeStringAndEnd(String chunk, String enc) {
    writeBufferAndEnd(Buffer.newBuffer(chunk, enc));
  }

  @Override
  public void writeBufferAndEnd(Buffer chunk) {
    check();
    if (!chunked && !contentLengthSet()) {
      headers().set(io.vertx.core.http.HttpHeaders.CONTENT_LENGTH, String.valueOf(chunk.length()));
    }
    write(chunk.getByteBuf(), true);
  }

  @Override
  public void end() {
    check();
    write(Unpooled.EMPTY_BUFFER, true);
  }

  @Override
  public HttpClientRequest setTimeout(long timeoutMs) {
    cancelOutstandingTimeoutTimer();
    currentTimeoutTimerId = client.getVertx().setTimer(timeoutMs, id ->  handleTimeout(timeoutMs));
    return this;
  }

  @Override
  public HttpClientRequest putHeader(CharSequence name, CharSequence value) {
    check();
    headers().set(name, value);
    return this;
  }

  @Override
  public HttpClientRequest putHeader(CharSequence name, Iterable<CharSequence> values) {
    check();
    headers().set(name, values);
    return this;
  }

  // Data has been received on the response
  void dataReceived() {
    if (currentTimeoutTimerId != -1) {
      lastDataReceived = System.currentTimeMillis();
    }
  }

  void handleDrained() {
    if (drainHandler != null) {
      drainHandler.handle(null);
    }
  }

  void handleException(Throwable t) {
    cancelOutstandingTimeoutTimer();
    exceptionOccurred = true;
    getExceptionHandler().handle(t);
  }

  void handleResponse(HttpClientResponseImpl resp) {
    // If an exception occurred (e.g. a timeout fired) we won't receive the response.
    if (!exceptionOccurred) {
      cancelOutstandingTimeoutTimer();
      try {
        if (resp.statusCode() == 100) {
          if (continueHandler != null) {
            continueHandler.handle(null);
          }
        } else {
          respHandler.handle(resp);
        }
      } catch (Throwable t) {
        handleException(t);
      }
    }
  }

  HttpRequest getRequest() {
    return request;
  }

  private Handler<Throwable> getExceptionHandler() {
    return exceptionHandler != null ? exceptionHandler : log::error;
  }

  private void cancelOutstandingTimeoutTimer() {
    if (currentTimeoutTimerId != -1) {
      client.getVertx().cancelTimer(currentTimeoutTimerId);
      currentTimeoutTimerId = -1;
    }
  }

  private void handleTimeout(long timeoutMs) {
    if (lastDataReceived == 0) {
      timeout(timeoutMs);
    } else {
      long now = System.currentTimeMillis();
      long timeSinceLastData = now - lastDataReceived;
      if (timeSinceLastData >= timeoutMs) {
        timeout(timeoutMs);
      } else {
        // reschedule
        lastDataReceived = 0;
        setTimeout(timeoutMs - timeSinceLastData);
      }
    }
  }

  private void timeout(long timeoutMs) {
    handleException(new TimeoutException("The timeout period of " + timeoutMs + "ms has been exceeded"));
  }

  private void connect() {
    if (!connecting) {
      // We defer actual connection until the first part of body is written or end is called
      // This gives the user an opportunity to set an exception handler before connecting so
      // they can capture any exceptions on connection
      client.getConnection(options.getPort(), options.getHost(), conn -> {
        if (exceptionOccurred) {
          // The request already timed out before it has left the pool waiter queue
          // So return it
          conn.close();
        } else if (!conn.isClosed()) {
          connected(conn);
        } else {
          // The connection has been closed - closed connections can be in the pool
          // Get another connection - Note that we DO NOT call connectionClosed() on the pool at this point
          // that is done asynchronously in the connection closeHandler()
          connect();
        }
      }, exceptionHandler, vertx.getOrCreateContext());

      connecting = true;
    }
  }

  private synchronized void connected(ClientConnection conn) {
    conn.setCurrentRequest(this);
    this.conn = conn;

    // If anything was written or the request ended before we got the connection, then
    // we need to write it now

    if (pendingMaxSize != -1) {
      conn.doSetWriteQueueMaxSize(pendingMaxSize);
    }

    if (pendingChunks != null) {
      ByteBuf pending = pendingChunks;
      pendingChunks = null;

      if (completed) {
        // we also need to write the head so optimize this and write all out in once
        writeHeadWithContent(pending, true);

        conn.endRequest();
      } else {
        writeHeadWithContent(pending, false);
      }
    } else {
      if (completed) {
        // we also need to write the head so optimize this and write all out in once
        writeHeadWithContent(Unpooled.EMPTY_BUFFER, true);
        conn.endRequest();
      } else {
        if (writeHead) {
          writeHead();
        }
      }
    }
  }

  private boolean contentLengthSet() {
    if (headers != null) {
      return request.headers().contains(io.vertx.core.http.HttpHeaders.CONTENT_LENGTH);
    } else {
      return false;
    }
  }

  private void writeHead() {
    prepareHeaders();
    conn.write(request);
    headWritten = true;
  }

  private void writeHeadWithContent(ByteBuf buf, boolean end) {
    prepareHeaders();
    if (end) {
      conn.write(new AssembledFullHttpRequest(request, buf));
    } else {
      conn.write(new AssembledHttpRequest(request, buf));
    }
    headWritten = true;
  }

  private void prepareHeaders() {
    HttpHeaders headers = request.headers();
    headers.remove(io.vertx.core.http.HttpHeaders.TRANSFER_ENCODING);
    if (!headers.contains(io.vertx.core.http.HttpHeaders.HOST)) {
      request.headers().set(io.vertx.core.http.HttpHeaders.HOST, conn.hostHeader);
    }
    if (chunked) {
      HttpHeaders.setTransferEncodingChunked(request);
    }
    if (client.getOptions().isTryUseCompression() && request.headers().get(io.vertx.core.http.HttpHeaders.ACCEPT_ENCODING) == null) {
      // if compression should be used but nothing is specified by the user support deflate and gzip.
      request.headers().set(io.vertx.core.http.HttpHeaders.ACCEPT_ENCODING, io.vertx.core.http.HttpHeaders.DEFLATE_GZIP);
    }
  }

  private synchronized void write(ByteBuf buff, boolean end) {
    int readableBytes = buff.readableBytes();
    if (readableBytes == 0 && !end) {
      // nothing to write to the connection just return
      return;
    }

    if (end) {
      completed = true;
    }
    if (!end && !chunked && !contentLengthSet()) {
      throw new IllegalStateException("You must set the Content-Length header to be the total size of the message "
              + "body BEFORE sending any data if you are not using HTTP chunked encoding.");
    }

    written += buff.readableBytes();
    if (conn == null) {
      if (pendingChunks == null) {
        pendingChunks = buff;
      } else {
        CompositeByteBuf pending;
        if (pendingChunks instanceof CompositeByteBuf) {
          pending = (CompositeByteBuf) pendingChunks;
        } else {
          pending = Unpooled.compositeBuffer();
          pending.addComponent(pendingChunks).writerIndex(pendingChunks.writerIndex());
          pendingChunks = pending;
        }
        pending.addComponent(buff).writerIndex(pending.writerIndex() + buff.writerIndex());
      }
      connect();
    } else {
      if (!headWritten) {
        writeHeadWithContent(buff, end);
      } else {
        if (end) {
          writeEndChunk(buff);
        } else {
          sendChunk(buff);
        }
      }
      if (end) {
        conn.endRequest();
      }
    }
  }

  private void sendChunk(ByteBuf buff) {
    conn.write(new DefaultHttpContent(buff));
  }

  private void writeEndChunk(ByteBuf buf) {
    if (buf.isReadable()) {
      conn.write(new DefaultLastHttpContent(buf, false));
    } else {
      conn.write(LastHttpContent.EMPTY_LAST_CONTENT);
    }
  }

  private void check() {
    checkComplete();
  }

  private void checkComplete() {
    if (completed) {
      throw new IllegalStateException("Request already complete");
    }
  }

}
