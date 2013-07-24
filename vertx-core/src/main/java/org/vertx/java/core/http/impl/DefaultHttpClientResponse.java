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
import org.vertx.java.core.MultiMap;
import org.vertx.java.core.Vertx;
import org.vertx.java.core.VoidHandler;
import org.vertx.java.core.buffer.Buffer;
import org.vertx.java.core.http.HttpClientResponse;
import org.vertx.java.core.net.NetSocket;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

/**
 *
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class DefaultHttpClientResponse implements HttpClientResponse  {

  private final int statusCode;
  private final String statusMessage;
  private final DefaultHttpClientRequest request;
  private final Vertx vertx;
  private final ClientConnection conn;

  private Handler<Buffer> dataHandler;
  private Handler<Void> endHandler;
  private Handler<Throwable> exceptionHandler;
  private final HttpResponse response;
  private LastHttpContent trailer;
  private boolean paused;
  private Queue<Buffer> pausedChunks;
  private boolean hasPausedEnd;
  private LastHttpContent pausedTrailer;
  private NetSocket netSocket;

  // Cache these for performance
  private MultiMap headers;
  private MultiMap trailers;
  private List<String> cookies;

  DefaultHttpClientResponse(Vertx vertx, DefaultHttpClientRequest request, ClientConnection conn, HttpResponse response) {
    this.vertx = vertx;
    this.statusCode = response.getStatus().code();
    this.statusMessage = response.getStatus().reasonPhrase();
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
      cookies = new ArrayList<>();
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
    paused = true;
    conn.doPause();
    return this;
  }

  @Override
  public HttpClientResponse resume() {
    paused = false;
    doResume();
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

  private void doResume() {
    if (pausedChunks != null) {
      Buffer chunk;
      while ((chunk = pausedChunks.poll()) != null) {
        final Buffer theChunk = chunk;
        vertx.runOnContext(new VoidHandler() {
          @Override
          protected void handle() {
            handleChunk(theChunk);
          }
        });
      }
    }
    if (hasPausedEnd) {
      final LastHttpContent theTrailer = pausedTrailer;
      vertx.runOnContext(new VoidHandler() {
        @Override
        protected void handle() {
          handleEnd(theTrailer);
        }
      });
      hasPausedEnd = false;
      pausedTrailer = null;
    }
  }

  void handleChunk(Buffer data) {
    if (paused) {
      if (pausedChunks == null) {
        pausedChunks = new LinkedList<>();
      }
      pausedChunks.add(data);
    } else {
      request.dataReceived();
      if (dataHandler != null) {
        dataHandler.handle(data);
      }
    }
  }

  void handleEnd(LastHttpContent trailer) {
    if (paused) {
      hasPausedEnd = true;
      pausedTrailer = trailer;
    } else {
      this.trailer = trailer;
      trailers = new HttpHeadersAdapter(trailer.trailingHeaders());
      if (endHandler != null) {
        endHandler.handle(null);
      }
    }
  }

  void handleException(Throwable e) {
    if (exceptionHandler != null) {
      exceptionHandler.handle(e);
    }
  }

  @Override
  public NetSocket netSocket() {
    if (netSocket == null) {
      netSocket = conn.createNetSocket();
    }
    return netSocket;
  }
}
