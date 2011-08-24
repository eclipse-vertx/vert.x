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

package org.nodex.core.http;

import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.handler.codec.http.DefaultHttpChunk;
import org.jboss.netty.handler.codec.http.DefaultHttpRequest;
import org.jboss.netty.handler.codec.http.HttpChunk;
import org.jboss.netty.handler.codec.http.HttpHeaders;
import org.jboss.netty.handler.codec.http.HttpMethod;
import org.jboss.netty.handler.codec.http.HttpRequest;
import org.jboss.netty.handler.codec.http.HttpResponse;
import org.jboss.netty.handler.codec.http.HttpVersion;
import org.nodex.core.ExceptionHandler;
import org.nodex.core.buffer.Buffer;
import org.nodex.core.streams.WriteStream;

import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class HttpClientRequest implements WriteStream {

  HttpClientRequest(final HttpClient client, final String method, final String uri,
                    final boolean chunked,
                    final HttpResponseHandler respHandler,
                    final long contextID, final Thread th) {
    this.client = client;
    this.request = new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.valueOf(method), uri);
    this.chunked = chunked;
    this.respHandler = respHandler;
    this.contextID = contextID;
    this.th = th;
  }

  private final HttpClient client;
  private final HttpRequest request;
  private final boolean chunked;
  private final HttpResponseHandler respHandler;
  private Runnable continueHandler;
  private final long contextID;
  final Thread th;

  private ClientConnection conn;
  private Runnable drainHandler;
  private ExceptionHandler exceptionHandler;
  private boolean headWritten;
  private boolean completed;
  private LinkedList<PendingChunk> pendingChunks;
  private int pendingMaxSize = -1;
  private boolean connecting;
  private boolean writeHead;


  public HttpClientRequest putHeader(String key, Object value) {
    checkThread();
    checkComplete();
    request.setHeader(key, value);
    return this;
  }

  public HttpClientRequest putHeader(String key, Iterable<String> values) {
    checkThread();
    checkComplete();
    request.setHeader(key, values);
    return this;
  }

  public HttpClientRequest addHeader(String key, Object value) {
    checkThread();
    checkComplete();
    request.addHeader(key, value);
    return this;
  }

  public HttpClientRequest putAllHeaders(Map<String, ? extends Object> m) {
    checkThread();
    checkComplete();
    for (Map.Entry<String, ? extends Object> entry : m.entrySet()) {
      request.setHeader(entry.getKey(), entry.getValue());
    }
    return this;
  }

  public HttpClientRequest putAllHeaders(List<Map.Entry<String, String>> headers) {
    checkThread();
    checkComplete();
    for (Map.Entry<String, String> entry: headers) {
      addHeader(entry.getKey(), entry.getValue());
    }
    return this;
  }

  public String getHeader(String key) {
    checkThread();
    checkComplete();
    return request.getHeader(key);
  }

  public List<String> getHeaders(String key) {
    checkThread();
    checkComplete();
    return request.getHeaders(key);
  }

  public Set<String> getHeaderNames() {
    checkThread();
    checkComplete();
    return request.getHeaderNames();
  }

  public void writeBuffer(Buffer chunk) {
    checkThread();
    checkComplete();
    write(chunk._getChannelBuffer(), null);
  }

  public HttpClientRequest write(Buffer chunk) {
    checkThread();
    checkComplete();
    return write(chunk._getChannelBuffer(), null);
  }

  public HttpClientRequest write(String chunk) {
    checkThread();
    checkComplete();
    return write(Buffer.create(chunk)._getChannelBuffer(), null);
  }

  public HttpClientRequest write(String chunk, String enc) {
    checkThread();
    checkComplete();
    return write(Buffer.create(chunk, enc)._getChannelBuffer(), null);
  }

  public HttpClientRequest write(Buffer chunk, Runnable done) {
    checkThread();
    checkComplete();
    return write(chunk._getChannelBuffer(), done);
  }

  public HttpClientRequest write(String chunk, Runnable done) {
    checkThread();
    checkComplete();
    return write(Buffer.create(chunk)._getChannelBuffer(), done);
  }

  public HttpClientRequest write(String chunk, String enc, Runnable done) {
    checkThread();
    checkComplete();
    return write(Buffer.create(chunk, enc)._getChannelBuffer(), done);
  }

  public void setWriteQueueMaxSize(int maxSize) {
    checkThread();
    checkComplete();
    if (conn != null) {
      conn.setWriteQueueMaxSize(maxSize);
    } else {
      pendingMaxSize = maxSize;
    }
  }

  public boolean writeQueueFull() {
    checkThread();
    checkComplete();
    if (conn != null) {
      return conn.writeQueueFull();
    } else {
      return false;
    }
  }

  public void drainHandler(Runnable handler) {
    checkThread();
    checkComplete();
    this.drainHandler = handler;
    if (conn != null) {
      conn.handleInterestedOpsChanged(); //If the channel is already drained, we want to call it immediately
    }
  }

  public void exceptionHandler(ExceptionHandler handler) {
    checkThread();
    checkComplete();
    this.exceptionHandler = handler;
  }

  public void continueHandler(Runnable handler) {
    checkThread();
    checkComplete();
    this.continueHandler = handler;
  }

  public HttpClientRequest sendHead() {
    checkThread();
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

  public void end() {
    checkThread();
    completed = true;
    if (conn != null) {
      if (!headWritten) {
        // No body
        writeHead();
      } else {
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
      drainHandler.run();
    }
  }

  void handleException(Exception e) {
    checkThread();
    if (exceptionHandler != null) {
      exceptionHandler.onException(e);
    }
  }

  void handleResponse(HttpClientResponse resp) {
    try {
      if (resp.statusCode == 100 ) {
        if (continueHandler != null) {
          continueHandler.run();
        }
      } else {
        respHandler.onResponse(resp);
      }
    } catch (Throwable t) {
      if (t instanceof Exception) {
        handleException((Exception)t);
      } else {
        t.printStackTrace(System.err);
      }
    }
  }

  private void connect() {
    if (!connecting) {
      //We defer actual connection until the first part of body is written or end is called
      //This gives the user an opportunity to set an exception handler before connecting so
      //they can capture any exceptions on connection
      client.getConnection(new ClientConnectHandler() {
        public void onConnect(ClientConnection conn) {
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
      for (PendingChunk chunk: pendingChunks) {
        sendChunk(chunk.chunk, chunk.completion);
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
    write(body);
    writeEndChunk();
    completed = true;
  }

  private void writeHead() {
    request.setHeader(HttpHeaders.Names.HOST, conn.hostHeader);
    if (chunked) {
      request.setChunked(true);
      request.setHeader(HttpHeaders.Names.TRANSFER_ENCODING, HttpHeaders.Values.CHUNKED);
    }
    conn.write(request);
  }

  private HttpClientRequest write(ChannelBuffer buff, Runnable done) {
    if (conn == null) {
      connect();
      if (pendingChunks == null) {
        pendingChunks = new LinkedList<>();
      }
      pendingChunks.add(new PendingChunk(new DefaultHttpChunk(buff), done));
    } else {
      if (!headWritten) {
        writeHead();
        headWritten = true;
      }
      sendChunk(new DefaultHttpChunk(buff), done);
    }
    return this;
  }

  private void sendChunk(HttpChunk chunk, Runnable completion) {
    ChannelFuture writeFuture = conn.write(chunk);
    if (completion != null) {
      conn.addFuture(completion, writeFuture);
    }
  }

  private void writeEndChunk() {
    conn.write(new DefaultHttpChunk(ChannelBuffers.EMPTY_BUFFER));
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
    final HttpChunk chunk;
    final Runnable completion;
    private PendingChunk(HttpChunk chunk, Runnable completion) {
      this.chunk = chunk;
      this.completion = completion;
    }
  }


}
