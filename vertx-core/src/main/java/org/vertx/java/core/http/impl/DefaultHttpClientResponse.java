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

import io.netty.handler.codec.http.DefaultHttpHeaders;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.LastHttpContent;
import org.vertx.java.core.Handler;
import org.vertx.java.core.VoidHandler;
import org.vertx.java.core.buffer.Buffer;
import org.vertx.java.core.http.HttpClientResponse;
import org.vertx.java.core.MultiMap;
import org.vertx.java.core.logging.Logger;
import org.vertx.java.core.logging.impl.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 *
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class DefaultHttpClientResponse implements HttpClientResponse  {

  private static final Logger log = LoggerFactory.getLogger(DefaultHttpClientResponse.class);

  private final int statusCode;
  private final String statusMessage;
  private final DefaultHttpClientRequest request;
  private final ClientConnection conn;

  private Handler<Buffer> dataHandler;
  private Handler<Void> endHandler;
  private Handler<Throwable> exceptionHandler;
  private final HttpResponse response;
  private LastHttpContent trailer;

  // Cache these for performance
  private MultiMap headers;
  private MultiMap trailers;
  private List<String> cookies;

  DefaultHttpClientResponse(DefaultHttpClientRequest request, ClientConnection conn, HttpResponse response) {
    statusCode = response.getStatus().code();
    statusMessage = response.getStatus().reasonPhrase();
    this.request = request;
    this.conn = conn;
    this.response = response;
  }

  @Override
  public int statusCode() {
    return statusCode;
  }

  @Override
  public String statusMessage() {
    return statusMessage;
  }

  @Override
  public MultiMap headers() {
    if (headers == null) {
      headers = new HttpHeadersAdapter(response.headers());
    }
    return headers;
  }

  @Override
  public MultiMap trailers() {
    if (trailers == null) {
      trailers = new HttpHeadersAdapter(new DefaultHttpHeaders());
    }
    return trailers;
  }

  @Override
  public List<String> cookies() {
    if (cookies == null) {
      cookies = new ArrayList<String>();
      cookies.addAll(response.headers().getAll("Set-Cookie"));
      if (trailer != null) {
        cookies.addAll(trailer.trailingHeaders().getAll("Set-Cookie"));
      }
    }
    return cookies;
  }

  @Override
  public HttpClientResponse dataHandler(Handler<Buffer> dataHandler) {
    this.dataHandler = dataHandler;
    return this;
  }

  @Override
  public HttpClientResponse endHandler(Handler<Void> endHandler) {
    this.endHandler = endHandler;
    return this;
  }

  @Override
  public HttpClientResponse exceptionHandler(Handler<Throwable> exceptionHandler) {
    this.exceptionHandler = exceptionHandler;
    return this;
  }

  @Override
  public HttpClientResponse pause() {
    conn.doPause();
    return this;
  }

  @Override
  public HttpClientResponse resume() {
    conn.doResume();
    return this;
  }

  @Override
  public HttpClientResponse bodyHandler(final Handler<Buffer> bodyHandler) {
    final Buffer body = new Buffer();
    dataHandler(new Handler<Buffer>() {
      public void handle(Buffer buff) {
        body.appendBuffer(buff);
      }
    });
    endHandler(new VoidHandler() {
      public void handle() {
        bodyHandler.handle(body);
      }
    });
    return this;
  }

  void handleChunk(Buffer data) {
    request.dataReceived();
    if (dataHandler != null) {
      dataHandler.handle(data);
    }
  }

  void handleEnd(LastHttpContent trailer) {
    this.trailer = trailer;
    trailers = new HttpHeadersAdapter(trailer.trailingHeaders());
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
