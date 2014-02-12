/*
 * Copyright (c) 2011-2013 The original author or authors
 * ------------------------------------------------------
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * and Apache License v2.0 which accompanies this distribution.
 *
 *     The Eclipse Public License is available at
 *     http://www.eclipse.org/legal/epl-v10.html
 *
 *     The Apache License v2.0 is available at
 *     http://www.opensource.org/licenses/apache2.0.php
 *
 * You may elect to redistribute this code under either of these licenses.
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

  private static final int PAUSED_CHUNKS_PER_TASK = 2;

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
      cookies.addAll(response.headers().getAll(org.vertx.java.core.http.HttpHeaders.SET_COOKIE));
      if (trailer != null) {
        cookies.addAll(trailer.trailingHeaders().getAll(org.vertx.java.core.http.HttpHeaders.SET_COOKIE));
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
      vertx.runOnContext(new VoidHandler() {
        @Override
        protected void handle() {
          handlePausedChunks();
        }
      });
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

  void handlePausedChunks() {
    if (!paused) {
      int processedChunks = 0;
      Buffer data;
      while(processedChunks < PAUSED_CHUNKS_PER_TASK && (data = pausedChunks.poll()) != null) {
        request.dataReceived();
        dataHandler.handle(data);
        processedChunks++;
      }

      if(pausedChunks.isEmpty()) {
        pausedChunks = null;
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
      } else {
        vertx.runOnContext(new VoidHandler() {
          @Override
          protected void handle() {
            handlePausedChunks();
          }
        });
      }
    }
  }

  void handleChunk(Buffer data) {
    if (paused || (pausedChunks !=null && !pausedChunks.isEmpty())) {
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
    if (paused || (pausedChunks !=null && !pausedChunks.isEmpty())) {
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
