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

import org.jboss.netty.handler.codec.http.HttpChunkTrailer;
import org.jboss.netty.handler.codec.http.HttpResponse;
import org.nodex.java.core.EventHandler;
import org.nodex.java.core.buffer.Buffer;
import org.nodex.java.core.streams.ReadStream;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 *
 *
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class HttpClientResponse implements ReadStream {

  private final ClientConnection conn;
  private final Thread th;
  private EventHandler<Buffer> dataHandler;
  private EventHandler<Void> endHandler;
  private EventHandler<Exception> exceptionHandler;
  private final HttpResponse response;
  private HttpChunkTrailer trailer;
  // Cache these for performance
  private Map<String, String> headers;
  // Cache these for performance
  private Map<String, String> trailers;

  public final int statusCode;
  public final String statusMessage;

  HttpClientResponse(ClientConnection conn, HttpResponse response, Thread th) {
    this.conn = conn;
    this.statusCode = response.getStatus().getCode();
    this.statusMessage = response.getStatus().getReasonPhrase();
    this.response = response;
    this.th = th;
  }

  public String getHeader(String key) {
    checkThread();
    return response.getHeader(key);
  }

  public Set<String> getHeaderNames() {
    checkThread();
    return response.getHeaderNames();
  }

  public String getTrailer(String key) {
    checkThread();
    return trailer.getHeader(key);
  }

  public Map<String, String> getHeaders() {
    if (headers == null) {
      headers = HeaderUtils.simplifyHeaders(response.getHeaders());
    }
    return headers;
  }

  public Map<String, String> getTrailers() {
    if (trailers == null) {
      if (trailer == null) {
        trailers = new HashMap<>();
      } else {
        trailers = HeaderUtils.simplifyHeaders(trailer.getHeaders());
      }
    }
    return trailers;
  }

  public Set<String> getTrailerNames() {
    checkThread();
    return trailer.getHeaderNames();
  }

  public void dataHandler(EventHandler<Buffer> dataHandler) {
    checkThread();
    this.dataHandler = dataHandler;
  }

  public void endHandler(EventHandler<Void> endHandler) {
    checkThread();
    this.endHandler = endHandler;
  }

  public void exceptionHandler(EventHandler<Exception> handler) {
    checkThread();
    this.exceptionHandler = handler;
  }

  public void pause() {
    checkThread();
    conn.pause();
  }

  public void resume() {
    checkThread();
    conn.resume();
  }

  void handleChunk(Buffer data) {
    checkThread();
    if (dataHandler != null) {
      dataHandler.onEvent(data);
    }
  }

  void handleEnd(HttpChunkTrailer trailer) {
    checkThread();
    this.trailer = trailer;
    if (endHandler != null) {
      endHandler.onEvent(null);
    }
  }

  void handleException(Exception e) {
    checkThread();
    if (exceptionHandler != null) {
      exceptionHandler.onEvent(e);
    }
  }

  private void checkThread() {
    // All ops must always be invoked on same thread
    if (Thread.currentThread() != th) {
      throw new IllegalStateException("Invoked with wrong thread, actual: " + Thread.currentThread() + " expected: " + th);
    }
  }
}
