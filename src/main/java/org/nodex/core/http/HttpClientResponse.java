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

import org.jboss.netty.handler.codec.http.HttpChunkTrailer;
import org.jboss.netty.handler.codec.http.HttpResponse;
import org.nodex.core.ExceptionHandler;
import org.nodex.core.buffer.Buffer;
import org.nodex.core.buffer.DataHandler;
import org.nodex.core.streams.ReadStream;

import java.util.List;
import java.util.Set;

public class HttpClientResponse implements ReadStream {

  private final HttpClientConnection conn;
  private DataHandler dataHandler;
  private Runnable endHandler;
  private ExceptionHandler exceptionHandler;
  private final HttpResponse response;
  private HttpChunkTrailer trailer;

  public final int statusCode;
  public final String statusMessage;

  public String getHeader(String key) {
    return response.getHeader(key);
  }

  public List<String> getHeaders(String key) {
    return response.getHeaders(key);
  }

  public Set<String> getHeaderNames() {
    return response.getHeaderNames();
  }

  public String getTrailer(String key) {
    return trailer.getHeader(key);
  }

  public List<String> getTrailers(String key) {
    return trailer.getHeaders(key);
  }

  public Set<String> getTrailerNames() {
    return trailer.getHeaderNames();
  }

  public void dataHandler(DataHandler dataHandler) {
    this.dataHandler = dataHandler;
  }

  public void endHandler(Runnable end) {
    this.endHandler = end;
  }

  public void exceptionHandler(ExceptionHandler handler) {
    this.exceptionHandler = handler;
  }

  public void pause() {
    conn.pause();
  }

  public void resume() {
    conn.resume();
  }

  HttpClientResponse(HttpClientConnection conn, HttpResponse response) {
    this.conn = conn;
    this.statusCode = response.getStatus().getCode();
    this.statusMessage = response.getStatus().getReasonPhrase();
    this.response = response;
  }

  void handleChunk(Buffer data) {
    if (dataHandler != null) {
      dataHandler.onData(data);
    }
  }

  void handleEnd(HttpChunkTrailer trailer) {
    this.trailer = trailer;
    if (endHandler != null) {
      endHandler.run();
    }
  }

  void handleException(Exception e) {
    if (exceptionHandler != null) {
      exceptionHandler.onException(e);
    }
  }
}
