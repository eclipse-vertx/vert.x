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
import org.vertx.java.core.buffer.Buffer;
import org.vertx.java.core.http.WebSocket;
import org.vertx.java.core.http.WebSocketFrame;
import org.vertx.java.core.impl.VertxInternal;
import org.vertx.java.core.net.impl.ConnectionBase;

public class DefaultWebSocket extends WebSocketImplBase<WebSocket> implements WebSocket {

  public DefaultWebSocket(VertxInternal vertx, ConnectionBase conn) {
    super(vertx, conn);
  }

  @Override
  public WebSocket dataHandler(Handler<Buffer> handler) {
    checkClosed();
    this.dataHandler = handler;
    return this;
  }

  @Override
  public WebSocket endHandler(Handler<Void> handler) {
    checkClosed();
    this.endHandler = handler;
    return this;
  }

  @Override
  public WebSocket exceptionHandler(Handler<Throwable> handler) {
    checkClosed();
    this.exceptionHandler = handler;
    return this;
  }

  @Override
  public WebSocket writeBinaryFrame(Buffer data) {
    super.writeBinaryFrameInternal(data);
    return this;
  }

  @Override
  public WebSocket writeTextFrame(String str) {
    super.writeTextFrameInternal(str);
    return this;
  }

  @Override
  public WebSocket closeHandler(Handler<Void> handler) {
    checkClosed();
    this.closeHandler = handler;
    return this;
  }

  @Override
  public WebSocket frameHandler(Handler<WebSocketFrame> handler) {
    checkClosed();
    this.frameHandler = handler;
    return this;
  }

  @Override
  public WebSocket pause() {
    checkClosed();
    conn.doPause();
    return this;
  }

  @Override
  public WebSocket resume() {
    checkClosed();
    conn.doResume();
    return this;
  }

  @Override
  public WebSocket setWriteQueueMaxSize(int maxSize) {
    checkClosed();
    conn.doSetWriteQueueMaxSize(maxSize);
    return this;
  }

  @Override
  public WebSocket write(Buffer data) {
    writeBinaryFrame(data);
    return this;
  }

  @Override
  public WebSocket drainHandler(Handler<Void> handler) {
    checkClosed();
    this.drainHandler = handler;
    return this;
  }
}
