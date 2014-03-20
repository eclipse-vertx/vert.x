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

import org.vertx.java.core.Handler;
import org.vertx.java.core.MultiMap;
import org.vertx.java.core.buffer.Buffer;
import org.vertx.java.core.http.ServerWebSocket;
import org.vertx.java.core.http.WebSocketFrame;
import org.vertx.java.core.impl.VertxInternal;
import org.vertx.java.core.net.impl.ConnectionBase;

public class DefaultServerWebSocket extends WebSocketImplBase<ServerWebSocket> implements ServerWebSocket {

  private final String uri;
  private final String path;
  private final String query;
  private final Runnable connectRunnable;
  private boolean connected;
  private boolean rejected;
  private final MultiMap headers;

  public DefaultServerWebSocket(VertxInternal vertx, String uri, String path, String query, MultiMap headers,
                                ConnectionBase conn, Runnable connectRunnable) {
    super(vertx, conn);
    this.uri = uri;
    this.path = path;
    this.query = query;
    this.headers = headers;
    this.connectRunnable = connectRunnable;
  }

  @Override
  public String uri() {
    return uri;
  }

  @Override
  public String path() {
    return path;
  }

  @Override
  public String query() {
    return query;
  }

  @Override
  public MultiMap headers() {
    return headers;
  }

  @Override
  public ServerWebSocket reject() {
    checkClosed();
    if (connectRunnable == null) {
      throw new IllegalStateException("Cannot reject websocket on the client side");
    }
    if (connected) {
      throw new IllegalStateException("Cannot reject websocket, it has already been written to");
    }
    rejected = true;
    return this;
  }

  @Override
  public void close() {
    checkClosed();
    if (this.connectRunnable != null) {
      // Server side
      if (rejected) {
        throw new IllegalStateException("Cannot close websocket, it has been rejected");
      }
      if (!connected && !closed) {
        connect();
      }
    }
    super.close();
  }

  @Override
  public ServerWebSocket dataHandler(Handler<Buffer> handler) {
    checkClosed();
    this.dataHandler = handler;
    return this;
  }

  @Override
  public ServerWebSocket endHandler(Handler<Void> handler) {
    checkClosed();
    this.endHandler = handler;
    return this;
  }

  @Override
  public ServerWebSocket exceptionHandler(Handler<Throwable> handler) {
    checkClosed();
    this.exceptionHandler = handler;
    return this;
  }

  @Override
  public ServerWebSocket closeHandler(Handler<Void> handler) {
    checkClosed();
    this.closeHandler = handler;
    return this;
  }

  @Override
  public ServerWebSocket frameHandler(Handler<WebSocketFrame> handler) {
    checkClosed();
    this.frameHandler = handler;
    return this;
  }

  @Override
  public ServerWebSocket pause() {
    checkClosed();
    conn.doPause();
    return this;
  }

  @Override
  public ServerWebSocket resume() {
    checkClosed();
    conn.doResume();
    return this;
  }

  @Override
  public ServerWebSocket setWriteQueueMaxSize(int maxSize) {
    checkClosed();
    conn.doSetWriteQueueMaxSize(maxSize);
    return this;
  }

  @Override
  public boolean writeQueueFull() {
    checkClosed();
    return conn.doWriteQueueFull();
  }

  @Override
  public ServerWebSocket write(Buffer data) {
    writeBinaryFrame(data);
    return this;
  }

  @Override
  public ServerWebSocket drainHandler(Handler<Void> handler) {
    checkClosed();
    this.drainHandler = handler;
    return this;
  }

  @Override
  public ServerWebSocket writeBinaryFrame(Buffer data) {
    super.writeBinaryFrameInternal(data);
    return this;
  }

  @Override
  public ServerWebSocket writeTextFrame(String str) {
    super.writeTextFrameInternal(str);
    return this;
  }

  @Override
  protected void writeFrame(WebSocketFrame frame) {
    if (connectRunnable != null) {
      if (rejected) {
        throw new IllegalStateException("Cannot write to websocket, it has been rejected");
      }
      if (!connected && !closed) {
        connect();
      }
    }
    super.writeFrame(frame);
  }

  private void connect() {
    connectRunnable.run();
    connected = true;
  }

  // Connect if not already connected
  void connectNow() {
    if (!connected && !rejected) {
      connect();
    }
  }

  boolean isRejected() {
    return rejected;
  }
}
