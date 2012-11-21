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

import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.handler.codec.http.*;
import org.vertx.java.core.Handler;
import org.vertx.java.core.buffer.Buffer;
import org.vertx.java.core.http.HttpClient;
import org.vertx.java.core.http.HttpClientRequest;
import org.vertx.java.core.http.HttpClientResponse;
import org.vertx.java.core.impl.Context;
import org.vertx.java.core.impl.LowerCaseKeyMap;
import org.vertx.java.core.logging.Logger;
import org.vertx.java.core.logging.impl.LoggerFactory;

import java.util.LinkedList;
import java.util.Map;
import java.util.concurrent.TimeoutException;

/**
 *
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class DefaultHttpClientRequest implements HttpClientRequest {

  private static final Logger log = LoggerFactory.getLogger(HttpClient.class);

  private final DefaultHttpClient client;
  private final HttpRequest request;
  private final Handler<HttpClientResponse> respHandler;
  private Handler<Void> continueHandler;
  private final Context context;
  private final boolean raw;
  private boolean chunked;
  private ClientConnection conn;
  private Handler<Void> drainHandler;
  private Handler<Exception> exceptionHandler;
  private boolean headWritten;
  private boolean completed;
  private LinkedList<PendingChunk> pendingChunks;
  private int pendingMaxSize = -1;
  private boolean connecting;
  private boolean writeHead;
  private long written;
  private long currentTimeoutTimerId = -1;
  private Map<String, Object> headers;
  private boolean exceptionOccurred;

  DefaultHttpClientRequest(final DefaultHttpClient client, final String method, final String uri,
                           final Handler<HttpClientResponse> respHandler,
                           final Context context) {
    this(client, method, uri, respHandler, context, false);
  }

  /*
  Raw request - used by websockets
  Raw requests won't have any headers set automatically, like Content-Length and Connection
  */
  DefaultHttpClientRequest(final DefaultHttpClient client, final String method, final String uri,
                           final Handler<HttpClientResponse> respHandler,
                           final Context context,
                           final ClientConnection conn) {
    this(client, method, uri, respHandler, context, true);
    this.conn = conn;
    conn.setCurrentRequest(this);
  }

  private DefaultHttpClientRequest(final DefaultHttpClient client, final String method, final String uri,
                                   final Handler<HttpClientResponse> respHandler,
                                   final Context context, final boolean raw) {
    this.client = client;
    this.request = new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.valueOf(method), uri);
    this.chunked = false;
    this.respHandler = respHandler;
    this.context = context;
    this.raw = raw;

  }

  public DefaultHttpClientRequest setChunked(boolean chunked) {
    check();
    if (written > 0) {
      throw new IllegalStateException("Cannot set chunked after data has been written on request");
    }
    this.chunked = chunked;
    return this;
  }

  public Map<String, Object> headers() {
    if (headers == null) {
      headers = new LowerCaseKeyMap();
    }
    return headers;
  }

  public HttpClientRequest putHeader(String name, Object value) {
    check();
    headers().put(name, value);
    return this;
  }

  public void writeBuffer(Buffer chunk) {
    check();
    write(chunk.getChannelBuffer(), null);
  }

  public DefaultHttpClientRequest write(Buffer chunk) {
    check();
    return write(chunk.getChannelBuffer(), null);
  }

  public DefaultHttpClientRequest write(String chunk) {
    check();
    return write(new Buffer(chunk).getChannelBuffer(), null);
  }

  public DefaultHttpClientRequest write(String chunk, String enc) {
    check();
    return write(new Buffer(chunk, enc).getChannelBuffer(), null);
  }

  public DefaultHttpClientRequest write(Buffer chunk, Handler<Void> doneHandler) {
    check();
    return write(chunk.getChannelBuffer(), doneHandler);
  }

  public DefaultHttpClientRequest write(String chunk, Handler<Void> doneHandler) {
    checkComplete();
    return write(new Buffer(chunk).getChannelBuffer(), doneHandler);
  }

  public DefaultHttpClientRequest write(String chunk, String enc, Handler<Void> doneHandler) {
    check();
    return write(new Buffer(chunk, enc).getChannelBuffer(), doneHandler);
  }

  public void setWriteQueueMaxSize(int maxSize) {
    check();
    if (conn != null) {
      conn.setWriteQueueMaxSize(maxSize);
    } else {
      pendingMaxSize = maxSize;
    }
  }

  public boolean writeQueueFull() {
    check();
    if (conn != null) {
      return conn.writeQueueFull();
    } else {
      return false;
    }
  }

  public void drainHandler(Handler<Void> handler) {
    check();
    this.drainHandler = handler;
    if (conn != null) {
      conn.handleInterestedOpsChanged(); //If the channel is already drained, we want to call it immediately
    }
  }

  public void exceptionHandler(final Handler<Exception> handler) {
    check();
    this.exceptionHandler = new Handler<Exception>() {
      @Override
      public void handle(Exception event) {
        cancelOutstandingTimeoutTimer();
        handler.handle(event);
      }
    };
  }

  public void continueHandler(Handler<Void> handler) {
    check();
    this.continueHandler = handler;
  }

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

  public void end(String chunk) {
    end(new Buffer(chunk));
  }

  public void end(String chunk, String enc) {
    end(new Buffer(chunk, enc));
  }

  public void end(Buffer chunk) {
    if (!chunked && !contentLengthSet()) {
      headers().put(HttpHeaders.Names.CONTENT_LENGTH, String.valueOf(chunk.length()));
    }
    write(chunk);
    end();
  }

  public void end() {
    check();
    completed = true;
    if (conn != null) {
      if (!headWritten) {
        // No body
        writeHead();
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
        handleException(new TimeoutException("The timeout period of " + timeoutMs + "ms has been exceeded"));
      }
    });
    return this;
  }

  void handleDrained() {
    if (drainHandler != null) {
      drainHandler.handle(null);
    }
  }

  void handleException(Exception e) {
    exceptionOccurred = true;
    if (exceptionHandler != null) {
      exceptionHandler.handle(e); // the handler cancels the timer.
    } else {
      cancelOutstandingTimeoutTimer();
      log.error("Unhandled exception", e);
    }
  }

  void handleResponse(DefaultHttpClientResponse resp) {
    // If an exception occurred (e.g. a timeout fired) we won't receive the response.
    if (!exceptionOccurred) {
      cancelOutstandingTimeoutTimer();
      try {
        if (resp.statusCode == 100) {
          if (continueHandler != null) {
            continueHandler.handle(null);
          }
        } else {
          respHandler.handle(resp);
        }
      } catch (Throwable t) {
        if (t instanceof Exception) {
          handleException((Exception) t);
        } else {
          log.error("Unhandled exception", t);
        }
      }
    }
  }

  private void cancelOutstandingTimeoutTimer() {
    if(currentTimeoutTimerId != -1) {
      client.getVertx().cancelTimer(currentTimeoutTimerId);
      currentTimeoutTimerId = -1;
    }
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
      conn.setWriteQueueMaxSize(pendingMaxSize);
    }

    if (pendingChunks != null || writeHead || completed) {
      writeHead();
      headWritten = true;
    }

    if (pendingChunks != null) {
      for (PendingChunk chunk : pendingChunks) {
        sendChunk(chunk.chunk, chunk.doneHandler);
      }
    }
    if (completed) {
      if (chunked) {
        writeEndChunk();
      }
      conn.endRequest();
    }
  }

  private boolean contentLengthSet() {
    if (headers != null) {
      return headers.containsKey(HttpHeaders.Names.CONTENT_LENGTH);
    } else {
      return false;
    }
  }

  private void writeHead() {
    request.setChunked(chunked);
    if (!raw) {
      request.setHeader(HttpHeaders.Names.HOST, conn.hostHeader);
      if (chunked) {
        request.setHeader(HttpHeaders.Names.TRANSFER_ENCODING, HttpHeaders.Values.CHUNKED);
      }
    }
    writeHeaders();
    conn.write(request);
  }

  private void writeHeaders() {
    if (headers != null) {
      for (Map.Entry<String, Object> header: headers.entrySet()) {
        String key = header.getKey();
        request.setHeader(key, header.getValue());
      }
    }
  }

  private DefaultHttpClientRequest write(ChannelBuffer buff, Handler<Void> doneHandler) {

    written += buff.readableBytes();

    if (!raw && !chunked && !contentLengthSet()) {
      throw new IllegalStateException("You must set the Content-Length header to be the total size of the message "
          + "body BEFORE sending any data if you are not using HTTP chunked encoding.");
    }

    if (conn == null) {
      if (pendingChunks == null) {
        pendingChunks = new LinkedList<>();
      }
      pendingChunks.add(new PendingChunk(buff, doneHandler));
      connect();
    } else {
      if (!headWritten) {
        writeHead();
        headWritten = true;
      }
      sendChunk(buff, doneHandler);
    }
    return this;
  }

  private void sendChunk(ChannelBuffer buff, Handler<Void> doneHandler) {
    Object write = chunked ? new DefaultHttpChunk(buff) : buff;
    ChannelFuture writeFuture = conn.write(write);
    if (doneHandler != null) {
      conn.addFuture(doneHandler, writeFuture);
    }
  }

  private void writeEndChunk() {
    conn.write(new DefaultHttpChunk(ChannelBuffers.EMPTY_BUFFER));
  }

  private void check() {
    checkComplete();
  }

  private void checkComplete() {
    if (completed) {
      throw new IllegalStateException("Request already complete");
    }
  }

  private static class PendingChunk {
    final ChannelBuffer chunk;
    final Handler<Void> doneHandler;

    private PendingChunk(ChannelBuffer chunk, Handler<Void> doneHandler) {
      this.chunk = chunk;
      this.doneHandler = doneHandler;
    }
  }

}
