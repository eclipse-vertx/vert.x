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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 *
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class DefaultHttpClientResponse extends HttpClientResponse {

  private static final Logger log = LoggerFactory.getLogger(DefaultHttpClientResponse.class);

  private final ClientConnection conn;
  private Handler<Buffer> dataHandler;
  private Handler<Void> endHandler;
  private Handler<Exception> exceptionHandler;
  private final HttpResponse response;
  private HttpChunkTrailer trailer;
  // Cache these for performance
  private Map<String, String> headers;
  private Map<String, String> trailers;
  private List<String> cookies;

  DefaultHttpClientResponse(ClientConnection conn, HttpResponse response) {
    super(response.getStatus().getCode(), response.getStatus().getReasonPhrase());
    this.conn = conn;
    this.response = response;
  }

  public Map<String, String> headers() {
    if (headers == null) {
      headers = HeaderUtils.simplifyHeaders(response.getHeaders());
    }
    return headers;
  }

  public Map<String, String> trailers() {
    if (trailers == null) {
      if (trailer == null) {
        trailers = new HashMap<>();
      } else {
        trailers = HeaderUtils.simplifyHeaders(trailer.getHeaders());
      }
    }
    return trailers;
  }

  public List<String> cookies() {
    if (cookies == null) {
      cookies = new ArrayList<>();
      cookies.addAll(response.getHeaders("Set-Cookie"));
      if (trailer != null) {
        cookies.addAll(trailer.getHeaders("Set-Cookie"));
      }
    }
    return cookies;
  }

  public void dataHandler(Handler<Buffer> dataHandler) {
    this.dataHandler = dataHandler;
  }

  public void endHandler(Handler<Void> endHandler) {
    this.endHandler = endHandler;
  }

  public void exceptionHandler(Handler<Exception> exceptionHandler) {
    this.exceptionHandler = exceptionHandler;
  }

  public void pause() {
    conn.pause();
  }

  public void resume() {
    conn.resume();
  }

  void handleChunk(Buffer data) {
    if (dataHandler != null) {
      dataHandler.handle(data);
    }
  }

  void handleEnd(HttpChunkTrailer trailer) {
    this.trailer = trailer;
    if (endHandler != null) {
      endHandler.handle(null);
    }
  }

  void handleException(Exception e) {
    if (exceptionHandler != null) {
      exceptionHandler.handle(e);
    }
  }

}
