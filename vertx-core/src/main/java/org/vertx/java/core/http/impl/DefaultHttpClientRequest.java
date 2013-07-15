/*
 * Copyright 2011-2012 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.vertx.java.core.http.impl;

import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.http.*;
import org.vertx.java.core.Handler;
import org.vertx.java.core.MultiMap;
import org.vertx.java.core.buffer.Buffer;
import org.vertx.java.core.http.HttpClientRequest;
import org.vertx.java.core.http.HttpClientResponse;
import org.vertx.java.core.impl.DefaultContext;

import java.util.LinkedList;
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
  private LinkedList<ByteBuf> pendingChunks;
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

  /*
  Raw request - used by websockets
  Raw requests won't have any headers set automatically, like Content-Length and Connection
  */
  DefaultHttpClientRequest(final DefaultHttpClient client, final String method, final String uri,
                           final Handler<HttpClientResponse> respHandler,
                           final DefaultContext context,
                           final ClientConnection conn) {
    this(client, method, uri, respHandler, context, true);
    this.conn = conn;
    conn.setCurrentRequest(this);
  }

  private DefaultHttpClientRequest(final DefaultHttpClient client, final String method, final String uri,
                                   final Handler<HttpClientResponse> respHandler,
                                   final DefaultContext context, final boolean raw) {
    this.client = client;
    this.request = new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.valueOf(method), uri);
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
    return write(buf);
  }

  @Override
  public DefaultHttpClientRequest write(String chunk) {
    check();
    return write(new Buffer(chunk).getByteBuf());
  }

  @Override
  public DefaultHttpClientRequest write(String chunk, String enc) {
    check();
    return write(new Buffer(chunk, enc).getByteBuf());
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
        headWritten = true;
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
    if (!chunked && !contentLengthSet()) {
      headers().set(HttpHeaders.Names.CONTENT_LENGTH, String.valueOf(chunk.length()));
    }
    write(chunk);
    end();
  }

  @Override
  public void end() {
    check();
    completed = true;
    if (conn != null) {
      if (!headWritten) {
        // No body
        prepareHeaders();
        conn.queueForWrite(request);
        writeEndChunk();
      } else if (chunked) {
        //Body written - we use HTTP chunking so must send an empty buffer
        writeEndChunk();
      }
      conn.endRequest();
    } else {
      connect();
    }
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
            if (!conn.isClosed()) {
                connected(conn);
          } else {
            connect();
          }
        }
      }, exceptionHandler, context);

      connecting = true;
    }
  }

  private void connected(ClientConnection conn) {
    conn.setCurrentRequest(this);
    this.conn = conn;

    // If anything was written or the request ended before we got the connection, then
    // we need to write it now

    if (pendingMaxSize != -1) {
      conn.doSetWriteQueueMaxSize(pendingMaxSize);
    }
    if (pendingChunks != null || writeHead || completed) {
      writeHead();
      headWritten = true;
    }

    if (pendingChunks != null) {
      for (ByteBuf chunk : pendingChunks) {
        sendChunk(chunk);
      }
    }
    if (completed) {
      writeEndChunk();
      conn.endRequest();
    }
  }

  private boolean contentLengthSet() {
    if (headers != null) {
      return headers.contains(HttpHeaders.Names.CONTENT_LENGTH);
    } else {
      return false;
    }
  }

  private void writeHead() {
    prepareHeaders();
    conn.write(request);
  }

  private void prepareHeaders() {
    request.headers().remove(HttpHeaders.Names.TRANSFER_ENCODING);
    if (!raw) {
      request.headers().set(HttpHeaders.Names.HOST, conn.hostHeader);
      if (chunked) {
        HttpHeaders.setTransferEncodingChunked(request);
      }
    }
  }

  private DefaultHttpClientRequest write(ByteBuf buff) {

    written += buff.readableBytes();

    if (!raw && !chunked && !contentLengthSet()) {
      throw new IllegalStateException("You must set the Content-Length header to be the total size of the message "
          + "body BEFORE sending any data if you are not using HTTP chunked encoding.");
    }

    if (conn == null) {
      if (pendingChunks == null) {
        pendingChunks = new LinkedList<>();
      }
      pendingChunks.add(buff);
      connect();
    } else {
      if (!headWritten) {
        prepareHeaders();
        conn.queueForWrite(request);
        headWritten = true;
      }
      sendChunk(buff);
    }
    return this;
  }

  private void sendChunk(ByteBuf buff) {
    conn.write(new DefaultHttpContent(buff));
  }

  private void writeEndChunk() {
    conn.write(LastHttpContent.EMPTY_LAST_CONTENT);
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
