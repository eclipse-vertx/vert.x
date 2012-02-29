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

import org.jboss.netty.handler.codec.http.HttpChunkTrailer;
import org.jboss.netty.handler.codec.http.HttpResponse;
import org.vertx.java.core.Handler;
import org.vertx.java.core.buffer.Buffer;
import org.vertx.java.core.http.HttpClientResponse;
import org.vertx.java.core.logging.Logger;
import org.vertx.java.core.logging.impl.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 *
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class HttpClientResponseImpl extends HttpClientResponse {

  private static final Logger log = LoggerFactory.getLogger(HttpClientResponseImpl.class);

  private final ClientConnection conn;
  private final Thread th;
  private Handler<Buffer> dataHandler;
  private Handler<Void> endHandler;
  private Handler<Exception> exceptionHandler;
  private final HttpResponse response;
  private HttpChunkTrailer trailer;
  // Cache these for performance
  private Map<String, String> headers;
  // Cache these for performance
  private Map<String, String> trailers;

  HttpClientResponseImpl(ClientConnection conn, HttpResponse response, Thread th) {
    super(response.getStatus().getCode(), response.getStatus().getReasonPhrase());
    this.conn = conn;
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

  public Map<String, String> getAllHeaders() {
    if (headers == null) {
      headers = HeaderUtils.simplifyHeaders(response.getHeaders());
    }
    return headers;
  }

  public Map<String, String> getAllTrailers() {
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

  public void dataHandler(Handler<Buffer> dataHandler) {
    checkThread();
    this.dataHandler = dataHandler;
  }

  public void endHandler(Handler<Void> endHandler) {
    checkThread();
    this.endHandler = endHandler;
  }

  public void exceptionHandler(Handler<Exception> exceptionHandler) {
    checkThread();
    this.exceptionHandler = exceptionHandler;
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
      dataHandler.handle(data);
    }
  }

  void handleEnd(HttpChunkTrailer trailer) {
    checkThread();
    this.trailer = trailer;
    if (endHandler != null) {
      endHandler.handle(null);
    }
  }

  void handleException(Exception e) {
    checkThread();
    if (exceptionHandler != null) {
      exceptionHandler.handle(e);
    }
  }

  private void checkThread() {
    // All ops must always be invoked on same thread
    if (Thread.currentThread() != th) {
      throw new IllegalStateException("Invoked with wrong thread, actual: " + Thread.currentThread() + " expected: " + th);
    }
  }
}
