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
import org.jboss.netty.handler.codec.http.HttpHeaders;
import org.jboss.netty.handler.codec.http.HttpMethod;
import org.jboss.netty.handler.codec.http.HttpRequest;
import org.jboss.netty.handler.codec.http.HttpVersion;
import org.nodex.core.ExceptionHandler;
import org.nodex.core.buffer.Buffer;
import org.nodex.core.streams.WriteStream;

import java.util.List;
import java.util.Map;
import java.util.Set;

public class HttpClientRequest implements WriteStream {

  HttpClientRequest(HttpClientConnection conn, final String method, final String uri,
                    final HttpResponseHandler respHandler) {
    this.request = new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.valueOf(method), uri);
    request.setHeader(HttpHeaders.Names.HOST, conn.hostHeader);
    request.setHeader(HttpHeaders.Names.CONNECTION, conn.keepAlive ? HttpHeaders.Values.KEEP_ALIVE : HttpHeaders.Values
        .CLOSE);
    this.conn = conn;
    this.respHandler = respHandler;
  }

  private final HttpRequest request;
  private final HttpClientConnection conn;
  private final HttpResponseHandler respHandler;

  private Runnable drainHandler;
  private ExceptionHandler exceptionHandler;
  private boolean headWritten;
  private boolean sent;

  public HttpClientRequest putHeader(String key, Object value) {
    request.setHeader(key, value);
    return this;
  }

  public HttpClientRequest putHeader(String key, Iterable<String> values) {
    request.setHeader(key, values);
    return this;
  }

  public HttpClientRequest addHeader(String key, Object value) {
    request.addHeader(key, value);
    return this;
  }

  public HttpClientRequest putAllHeaders(Map<String, ? extends Object> m) {
    for (Map.Entry<String, ? extends Object> entry : m.entrySet()) {
      request.setHeader(entry.getKey(), entry.getValue());
    }
    return this;
  }

  public String getHeader(String key) {
    return request.getHeader(key);
  }

  public List<String> getHeaders(String key) {
    return request.getHeaders(key);
  }

  public Set<String> getHeaderNames() {
    return request.getHeaderNames();
  }

  public void writeBuffer(Buffer chunk) {
    write(chunk._getChannelBuffer(), null);
  }

  public HttpClientRequest write(Buffer chunk) {
    return write(chunk._getChannelBuffer(), null);
  }

  public HttpClientRequest write(String chunk) {
    return write(Buffer.createBuffer(chunk)._getChannelBuffer(), null);
  }

  public HttpClientRequest write(String chunk, String enc) {
    return write(Buffer.createBuffer(chunk, enc)._getChannelBuffer(), null);
  }

  public HttpClientRequest write(Buffer chunk, Runnable done) {
    return write(chunk._getChannelBuffer(), done);
  }

  public HttpClientRequest write(String chunk, Runnable done) {
    return write(Buffer.createBuffer(chunk)._getChannelBuffer(), done);
  }

  public HttpClientRequest write(String chunk, String enc, Runnable done) {
    return write(Buffer.createBuffer(chunk, enc)._getChannelBuffer(), done);
  }

  public void end() {
    sent = true;
    if (!headWritten) {
      // No body
      writeHead(false);
    } else {
      //Body written - we use HTTP chunking so must send an empty buffer
      //TODO we could send some trailers at this point
      conn.write(new DefaultHttpChunk(ChannelBuffers.EMPTY_BUFFER), this);
    }
    conn.endRequest(this);
  }

  public void setWriteQueueMaxSize(int maxSize) {
    conn.setWriteQueueMaxSize(maxSize);
  }

  public boolean writeQueueFull() {
    return conn.writeQueueFull();
  }

  public void drainHandler(Runnable handler) {
    this.drainHandler = handler;
    conn.handleInterestedOpsChanged(); //If the channel is already drained, we want to call it immediately
  }

  public void exceptionHandler(ExceptionHandler handler) {
    this.exceptionHandler = handler;
  }

  void handleInterestedOpsChanged() {
    if (drainHandler != null) {
      drainHandler.run();
    }
  }

  void handleException(Exception e) {
    if (exceptionHandler != null) {
      exceptionHandler.onException(e);
    }
  }

  HttpResponseHandler getResponseHandler() {
    return respHandler;
  }

  private void writeHead(boolean chunked) {
    conn.setCurrentRequest(this);
    if (chunked) {
      request.setChunked(true);
      request.setHeader(HttpHeaders.Names.TRANSFER_ENCODING, HttpHeaders.Values.CHUNKED);
    }
    conn.write(request, this);
  }

  private HttpClientRequest write(ChannelBuffer buff, Runnable done) {
    if (sent) {
      throw new IllegalStateException("Response complete");
    }
    if (!headWritten) {
      writeHead(true);
      headWritten = true;
    }
    ChannelFuture writeFuture = conn.write(new DefaultHttpChunk(buff), this);
    if (done != null) {
      conn.addFuture(done, writeFuture);
    }
    return this;
  }
}
