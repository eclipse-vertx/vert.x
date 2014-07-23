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

package org.vertx.java.core.http.impl;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.CompositeByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.http.*;
import org.vertx.java.core.Handler;
import org.vertx.java.core.MultiMap;
import org.vertx.java.core.buffer.Buffer;
import org.vertx.java.core.http.HttpClientRequest;
import org.vertx.java.core.http.HttpClientResponse;
import org.vertx.java.core.impl.DefaultContext;

import java.util.concurrent.TimeoutException;

/**
 *
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class DefaultHttpClientRequest implements HttpClientRequest {
  private final DefaultHttpClient client;
  private final HttpRequest request;
  private final Handler<HttpClientResponse> respHandler;
  private Handler<Void> continueHandler;
  private final DefaultContext context;
  private final boolean raw;
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

  DefaultHttpClientRequest(final DefaultHttpClient client, final String method, final String uri,
                           final Handler<HttpClientResponse> respHandler,
                           final DefaultContext context) {
    this(client, method, uri, respHandler, context, false);
  }

  private DefaultHttpClientRequest(final DefaultHttpClient client, final String method, final String uri,
                                   final Handler<HttpClientResponse> respHandler,
                                   final DefaultContext context, final boolean raw) {
    this.client = client;
    this.request = new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.valueOf(method), uri, false);
    this.chunked = false;
    this.respHandler = respHandler;
    this.context = context;
    this.raw = raw;

  }

  @Override
  public DefaultHttpClientRequest setChunked(boolean chunked) {
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
  public DefaultHttpClientRequest write(Buffer chunk) {
    check();
    ByteBuf buf = chunk.getByteBuf();
    return write(buf, false);
  }

  @Override
  public DefaultHttpClientRequest write(String chunk) {
    check();
    return write(new Buffer(chunk));
  }

  @Override
  public DefaultHttpClientRequest write(String chunk, String enc) {
    check();
    return write(new Buffer(chunk, enc));
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
      return conn.doWriteQueueFull();
    } else {
      return false;
    }
  }

  @Override
  public HttpClientRequest drainHandler(Handler<Void> handler) {
    check();
    this.drainHandler = handler;
    if (conn != null) {
      conn.handleInterestedOpsChanged(); //If the channel is already drained, we want to call it immediately
    }
    return this;
  }

  @Override
  public HttpClientRequest exceptionHandler(final Handler<Throwable> handler) {
    check();
    this.exceptionHandler = new Handler<Throwable>() {
      @Override
      public void handle(Throwable event) {
        cancelOutstandingTimeoutTimer();
        handler.handle(event);
      }
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
  public DefaultHttpClientRequest sendHead() {
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
  public void end(String chunk) {
    end(new Buffer(chunk));
  }

  @Override
  public void end(String chunk, String enc) {
    end(new Buffer(chunk, enc));
  }

  @Override
  public void end(Buffer chunk) {
    check();
    if (!chunked && !contentLengthSet()) {
      headers().set(org.vertx.java.core.http.HttpHeaders.CONTENT_LENGTH, String.valueOf(chunk.length()));
    }
    write(chunk.getByteBuf(), true);
  }

  @Override
  public void end() {
    check();
    write(Unpooled.EMPTY_BUFFER, true);
  }


  @Override
  public HttpClientRequest setTimeout(final long timeoutMs) {
    cancelOutstandingTimeoutTimer();
    currentTimeoutTimerId = client.getVertx().setTimer(timeoutMs, new Handler<Long>() {
      @Override
      public void handle(Long event) {
        handleTimeout(timeoutMs);
      }
    });
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
    if (exceptionHandler != null) {
      exceptionHandler.handle(t);
    } else {
      context.reportException(t);
    }
  }

  boolean hasExceptionOccurred() {
      return exceptionOccurred;
  }

  void handleResponse(DefaultHttpClientResponse resp) {
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
      //We defer actual connection until the first part of body is written or end is called
      //This gives the user an opportunity to set an exception handler before connecting so
      //they can capture any exceptions on connection
      client.getConnection(new Handler<ClientConnection>() {
        public void handle(ClientConnection conn) {
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
            connecting = false;
            connect();
          }
        }
      }, exceptionHandler, context);

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
      return request.headers().contains(org.vertx.java.core.http.HttpHeaders.CONTENT_LENGTH);
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
    headers.remove(org.vertx.java.core.http.HttpHeaders.TRANSFER_ENCODING);
    if (!raw) {
      if (!headers.contains(org.vertx.java.core.http.HttpHeaders.HOST)) {
        request.headers().set(org.vertx.java.core.http.HttpHeaders.HOST, conn.hostHeader);
      }
      if (chunked) {
        HttpHeaders.setTransferEncodingChunked(request);
      }
    }
    if (client.getTryUseCompression() && request.headers().get(org.vertx.java.core.http.HttpHeaders.ACCEPT_ENCODING) == null) {
      // if compression should be used but nothing is specified by the user support deflate and gzip.
      request.headers().set(org.vertx.java.core.http.HttpHeaders.ACCEPT_ENCODING, org.vertx.java.core.http.HttpHeaders.DEFLATE_GZIP);

    }
  }

  private synchronized DefaultHttpClientRequest write(ByteBuf buff, boolean end) {
    int readableBytes = buff.readableBytes();
    if (readableBytes == 0 && !end) {
      // nothing to write to the connection just return
      return this;
    }

    if (end) {
      completed = true;
    }

    written += buff.readableBytes();

    if (!end && !raw && !chunked && !contentLengthSet()) {
      throw new IllegalStateException("You must set the Content-Length header to be the total size of the message "
          + "body BEFORE sending any data if you are not using HTTP chunked encoding.");
    }

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
    return this;
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
