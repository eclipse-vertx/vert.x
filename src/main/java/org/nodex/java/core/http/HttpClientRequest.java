/*
 * Copyright 2002-2011 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use
 * this file except in compliance with the License. You may obtain a copy of the
 * License at http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed
 * under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR
 * CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */

package org.nodex.java.core.http;

import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.handler.codec.http.DefaultHttpChunk;
import org.jboss.netty.handler.codec.http.DefaultHttpRequest;
import org.jboss.netty.handler.codec.http.HttpHeaders;
import org.jboss.netty.handler.codec.http.HttpMethod;
import org.jboss.netty.handler.codec.http.HttpRequest;
import org.jboss.netty.handler.codec.http.HttpVersion;
import org.nodex.java.core.EventHandler;
import org.nodex.java.core.buffer.Buffer;
import org.nodex.java.core.streams.WriteStream;

import java.util.LinkedList;
import java.util.Map;

/**
 * <p>Encapsulates a client-side HTTP request.</p>
 *
 * <p>Instances of this class are created by an {@link HttpClient} instance, via one of the methods corresponding to the
 * specific HTTP methods, or the generic {@link HttpClient#request} method</p>
 *
 * <p>Once an instance of this class has been obtained, headers can be set on it, and data can be written to its body,
 * if required. Once you are ready to send the request, the {@link #end()} method must called.</p>
 *
 * <p>Nothing is sent until the request has been internally assigned an HTTP connection. The {@link HttpClient} instance
 * will return an instance of this class immediately, even if there are no HTTP connections available in the pool. Any requests
 * sent before a connection is assigned will be queued internally and actually sent when an HTTP connection becomes
 * available from the pool.</p>
 *
 * <p>The headers of the request are actually sent either when the {@link #end()} method is called, or, when the first
 * part of the body is written, whichever occurs first.</p>
 *
 * <p>This class supports both chunked and non-chunked HTTP.</p>
 *
 * <p>This class can only be used from the event loop that created it.</p>
 *
 * <p>An example of using this class is as follows:</p>
 *
 * <pre>
 *
 * HttpClientRequest req = httpClient.post("/some-url", new EventHandler<HttpClientResponse>() {
 *   public void onEvent(HttpClientResponse response) {
 *     System.out.println("Got response: " + response.statusCode);
 *   }
 * });
 *
 * req.putHeader("some-header", "hello");
 * req.putHeader("Content-Length", 5);
 * req.write(Buffer.create(new byte[]{1, 2, 3, 4, 5}));
 * req.write(Buffer.create(new byte[]{6, 7, 8, 9, 10}));
 * req.end();
 *
 * </pre>
 *
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class HttpClientRequest implements WriteStream {

  HttpClientRequest(final HttpClient client, final String method, final String uri,
                    final EventHandler<HttpClientResponse> respHandler,
                    final long contextID, final Thread th) {
    this.client = client;
    this.request = new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.valueOf(method), uri);
    this.chunked = false;
    this.respHandler = respHandler;
    this.contextID = contextID;
    this.th = th;
  }

  private final HttpClient client;
  private final HttpRequest request;
  private final EventHandler<HttpClientResponse> respHandler;
  private EventHandler<Void> continueHandler;
  private final long contextID;
  final Thread th;

  private boolean chunked;
  private ClientConnection conn;
  private EventHandler<Void> drainHandler;
  private EventHandler<Exception> exceptionHandler;
  private boolean headWritten;
  private boolean completed;
  private LinkedList<PendingChunk> pendingChunks;
  private int pendingMaxSize = -1;
  private boolean connecting;
  private boolean writeHead;
  private long written;
  private long contentLength = 0;

  /**
   * If {@code chunked} is {@code true}, this request will use HTTP chunked encoding, and each call to write to the body
   * will correspond to a new HTTP chunk sent on the wire. If chunked encoding is used the HTTP header
   * {@code Transfer-Encoding} with a value of {@code Chunked} will be automatically inserted in the request.<p>
   * If {@code chunked} is {@code false}, this request will not use HTTP chunked encoding, and therefore if any data is written the
   * body of the request, the total size of that data must be set in the {@code Content-Length} header <b>before</b> any
   * data is written to the request body. If no data is written, then a {@code Content-Length} header with a value of {@code 0}
   * will be automatically inserted when the request is sent.<p>
   * @return A reference to this, so multiple method calls can be chained.
   */
  public HttpClientRequest setChunked(boolean chunked) {
    check();
    if (written > 0) {
      throw new IllegalStateException("Cannot set chunked after data has been written on request");
    }
    this.chunked = chunked;
    return this;
  }

  /**
   * Inserts a header into the request. The {@link Object#toString()} method will be called on {@code value} to determine
   * the String value to actually use for the header value.<p>
   * @return A reference to this, so multiple method calls can be chained.
   */
  public HttpClientRequest putHeader(String key, Object value) {
    check();
    request.setHeader(key, value);
    checkContentLengthChunked(key, value);
    return this;
  }

  /**
   * Inserts all the specified headers into the request. The {@link Object#toString()} method will be called on the header values {@code value} to determine
   * the String value to actually use for the header value.<p>
   * @return A reference to this, so multiple method calls can be chained.
   */
  public HttpClientRequest putAllHeaders(Map<String, ? extends Object> m) {
    check();
    for (Map.Entry<String, ? extends Object> entry : m.entrySet()) {
      request.setHeader(entry.getKey(), entry.getValue().toString());
      checkContentLengthChunked(entry.getKey(), entry.getValue());
    }
    return this;
  }

  /**
   * Write a {@link Buffer} to the request body.
   */
  public void writeBuffer(Buffer chunk) {
    check();
    write(chunk.getChannelBuffer(), null);
  }

  /**
   * Write a {@link Buffer} to the request body.<p>
   * @return A reference to this, so multiple method calls can be chained.
   */
  public HttpClientRequest write(Buffer chunk) {
    check();
    return write(chunk.getChannelBuffer(), null);
  }

  /**
   * Write a {@link String} to the request body, encoded in UTF-8.<p>
   * @return A reference to this, so multiple method calls can be chained.
   */
  public HttpClientRequest write(String chunk) {
    check();
    return write(Buffer.create(chunk).getChannelBuffer(), null);
  }

  /**
   * Write a {@link String} to the request body, encoded using the encoding {@code enc}.<p>
   * @return A reference to this, so multiple method calls can be chained.
   */
  public HttpClientRequest write(String chunk, String enc) {
    check();
    return write(Buffer.create(chunk, enc).getChannelBuffer(), null);
  }

  /**
   * Write a {@link Buffer} to the request body. The {@code doneHandler} is called after the buffer is actually written to the wire.<p>
   * @return A reference to this, so multiple method calls can be chained.
   */
  public HttpClientRequest write(Buffer chunk, EventHandler<Void> doneHandler) {
    check();
    return write(chunk.getChannelBuffer(), doneHandler);
  }

  /**
   * Write a {@link String} to the request body, encoded in UTF-8. The {@code doneHandler} is called after the buffer is actually written to the wire.<p>
   * @return A reference to this, so multiple method calls can be chained.
   */
  public HttpClientRequest write(String chunk, EventHandler<Void> doneHandler) {
    checkThread();
    checkComplete();
    return write(Buffer.create(chunk).getChannelBuffer(), doneHandler);
  }

  /**
   * Write a {@link String} to the request body, encoded with encoding {@code enc}. The {@code doneHandler} is called after the buffer is actually written to the wire.<p>
   * @return A reference to this, so multiple method calls can be chained.
   */
  public HttpClientRequest write(String chunk, String enc, EventHandler<Void> doneHandler) {
    check();
    return write(Buffer.create(chunk, enc).getChannelBuffer(), doneHandler);
  }

  /**
   * Data is queued until it is actually sent. To set the point at which the queue is considered "full" call this method
   * specifying the {@code maxSize} in bytes.<p>
   * This method is used by the {@link org.nodex.java.core.streams.Pump} class to pump data
   * between different streams and perform flow control.
   */
  public void setWriteQueueMaxSize(int maxSize) {
    check();
    if (conn != null) {
      conn.setWriteQueueMaxSize(maxSize);
    } else {
      pendingMaxSize = maxSize;
    }
  }

  /**
   * If the amount of data that is currently queued is greater than the write queue max size see {@link #setWriteQueueMaxSize(int)}
   * then the request queue is considered full.<p>
   * Data can still be written to the request even if the write queue is deemed full, however it should be used as indicator
   * to stop writing and push back on the source of the data, otherwise you risk running out of available RAM.<p>
   * This method is used by the {@link org.nodex.java.core.streams.Pump} class to pump data
   * between different streams and perform flow control.
   * @return {@code true} if the write queue is full, {@code false} otherwise
   */
  public boolean writeQueueFull() {
    check();
    if (conn != null) {
      return conn.writeQueueFull();
    } else {
      return false;
    }
  }

  /**
   * This method sets a drain handler {@code handler} on the request. The drain handler will be called when write queue is no longer
   * full and it is safe to write to it again.<p>
   * The drain handler is actually called when the write queue size reaches <b>half</b> the write queue max size to prevent thrashing.
   * This method is used as part of a flow control strategy, e.g. it is used by the {@link org.nodex.java.core.streams.Pump} class to pump data
   * between different streams.
   * @param handler
   */
  public void drainHandler(EventHandler<Void> handler) {
    check();
    this.drainHandler = handler;
    if (conn != null) {
      conn.handleInterestedOpsChanged(); //If the channel is already drained, we want to call it immediately
    }
  }

  /**
   * Set {@code handler} as an exception handler on the request. Any exceptions that occur, either at connection setup time or later
   * will be notified by calling the handler. If the request has no handler than any exceptions occurring will be
   * output to {@link System#err}
   */
  public void exceptionHandler(EventHandler<Exception> handler) {
    check();
    this.exceptionHandler = handler;
  }

  /**
   * If you send an HTTP request with the header {@code Expect} set to the value {@code 100-continue}
   * and the server responds with an interim HTTP response with a status code of {@code 100} and a continue handler
   * has been set using this method, then the {@code handler} will be called.<p>
   * You can then continue to write data to the request body and later end it. This is normally used in conjunction with
   * the {@link #sendHead()} method to force the request header to be written before the request has ended.
   */
  public void continueHandler(EventHandler<Void> handler) {
    check();
    this.continueHandler = handler;
  }

  /**
   * Forces the head of the request to be written before {@link #end()} is called on the request. This is normally used
   * to implement HTTP 100-continue handling, see {@link #continueHandler(EventHandler)} for more information.
   * @return A reference to this, so multiple method calls can be chained.
   */
  public HttpClientRequest sendHead() {
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

  /**
   * Ends the request. If no data has been written to the request body, and {@link #sendHead()} has not been called then
   * the actual request won't get written until this method gets called.<p>
   * Once the request has ended, it cannot be used any more, and if keep alive is true the underlying connection will
   * be returned to the {@link HttpClient} pool so it can be assigned to another request.
   */
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

  void handleInterestedOpsChanged() {
    checkThread();
    if (drainHandler != null) {
      drainHandler.onEvent(null);
    }
  }

  void handleException(Exception e) {
    checkThread();
    if (exceptionHandler != null) {
      exceptionHandler.onEvent(e);
    } else {
      e.printStackTrace(System.err);
    }
  }

  void handleResponse(HttpClientResponse resp) {
    try {
      if (resp.statusCode == 100) {
        if (continueHandler != null) {
          continueHandler.onEvent(null);
        }
      } else {
        respHandler.onEvent(resp);
      }
    } catch (Throwable t) {
      if (t instanceof Exception) {
        handleException((Exception) t);
      } else {
        t.printStackTrace(System.err);
      }
    }
  }

  private void checkContentLengthChunked(String key, Object value) {
    if (key.equals(HttpHeaders.Names.CONTENT_LENGTH)) {
      contentLength = Integer.parseInt(value.toString());
    } else if (key.equals(HttpHeaders.Names.TRANSFER_ENCODING) && value.equals(HttpHeaders.Values.CHUNKED)) {
      chunked = true;
    }
  }

  private void connect() {
    if (!connecting) {
      //We defer actual connection until the first part of body is written or end is called
      //This gives the user an opportunity to set an exception handler before connecting so
      //they can capture any exceptions on connection
      client.getConnection(new EventHandler<ClientConnection>() {
        public void onEvent(ClientConnection conn) {
          connected(conn);
        }
      }, contextID);

      connecting = true;
    }
  }

  private void connected(ClientConnection conn) {
    checkThread();

    this.conn = conn;

    conn.setCurrentRequest(this);

    request.setHeader(HttpHeaders.Names.CONNECTION, conn.keepAlive ? HttpHeaders.Values.KEEP_ALIVE : HttpHeaders.Values
        .CLOSE);

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

  void sendDirect(ClientConnection conn, Buffer body) {
    this.conn = conn;
    contentLength = body.length();
    putHeader(HttpHeaders.Names.CONTENT_LENGTH, String.valueOf(contentLength));
    write(body);
    writeEndChunk();
    completed = true;
  }

  private void writeHead() {
    request.setHeader(HttpHeaders.Names.HOST, conn.hostHeader);
    request.setChunked(chunked);
    if (chunked) {
      request.setHeader(HttpHeaders.Names.TRANSFER_ENCODING, HttpHeaders.Values.CHUNKED);
    } else if (contentLength == 0) {
      request.setHeader(HttpHeaders.Names.CONTENT_LENGTH, "0");
    }
    conn.write(request);
  }

  private HttpClientRequest write(ChannelBuffer buff, EventHandler<Void> doneHandler) {
    written += buff.readableBytes();

    if (!chunked && written > contentLength) {
      throw new IllegalStateException("You must set the Content-Length header to be the total size of the message "
          + "body BEFORE sending any data if you are not using HTTP chunked encoding. "
          + "Current written: " + written + " Current Content-Length: " + contentLength);
    }

    if (conn == null) {
      connect();
      if (pendingChunks == null) {
        pendingChunks = new LinkedList<>();
      }
      pendingChunks.add(new PendingChunk(buff, doneHandler));
    } else {
      if (!headWritten) {
        writeHead();
        headWritten = true;
      }
      sendChunk(buff, doneHandler);
    }
    return this;
  }

  private void sendChunk(ChannelBuffer buff, EventHandler<Void> doneHandler) {
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
    checkThread();
    checkComplete();
  }

  private void checkComplete() {
    if (completed) {
      throw new IllegalStateException("Request already complete");
    }
  }

  private void checkThread() {
    // All ops must always be invoked on same thread
    if (Thread.currentThread() != th) {
      throw new IllegalStateException("Invoked with wrong thread, actual: " + Thread.currentThread() + " expected: " + th);
    }
  }

  private static class PendingChunk {
    final ChannelBuffer chunk;
    final EventHandler<Void> doneHandler;

    private PendingChunk(ChannelBuffer chunk, EventHandler<Void> doneHandler) {
      this.chunk = chunk;
      this.doneHandler = doneHandler;
    }
  }


}
