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

import java.util.ArrayDeque;
import java.util.ArrayList;
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
  private Buffer pausedChunk;
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
    if (!paused) {
      paused = true;
      conn.doPause();
    }
    return this;
  }

  @Override
  public HttpClientResponse resume() {
    if (paused) {
      paused = false;
      doResume();
      conn.doResume();
    }
    return this;
  }

  @Override
  public HttpClientResponse bodyHandler(final Handler<Buffer> bodyHandler) {
    final BodyHandler handler = new BodyHandler();
    dataHandler(handler);
    endHandler(new VoidHandler() {
      public void handle() {
        handler.notifyHandler(bodyHandler);
      }
    });
    return this;
  }

  private void doResume() {
    if (hasPausedEnd) {
      if (pausedChunk != null) {
        final Buffer theChunk = pausedChunk;
        conn.getContext().runOnContext(new VoidHandler() {
          @Override
          protected void handle() {
            handleChunk(theChunk);
          }
        });
        pausedChunk = null;
      }
      final LastHttpContent theTrailer = pausedTrailer;
      conn.getContext().runOnContext(new VoidHandler() {
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
      if (pausedChunk == null) {
        pausedChunk = data.copy();
      } else {
        pausedChunk.appendBuffer(data);
      }
    } else {
      request.dataReceived();
      if (pausedChunk != null) {
        data = pausedChunk.appendBuffer(data);
        pausedChunk = null;
      }
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


  private final class BodyHandler implements Handler<Buffer> {
    private Buffer body;

    @Override
    public void handle(Buffer event) {
      body().appendBuffer(event);
    }

    private Buffer body() {
      if (body == null) {
        body = new Buffer();
      }
      return body;
    }

    void notifyHandler(Handler<Buffer> bodyHandler) {
      bodyHandler.handle(body());
      // reset body so it can get GC'ed
      body = null;
    }
  }
}
