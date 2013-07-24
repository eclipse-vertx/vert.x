package org.vertx.java.core.http.impl;

import org.vertx.java.core.Handler;
import org.vertx.java.core.buffer.Buffer;
import org.vertx.java.core.http.WebSocket;
import org.vertx.java.core.impl.VertxInternal;
import org.vertx.java.core.net.impl.ConnectionBase;

/*
 * Copyright 2013 Red Hat, Inc.
 *
 * Red Hat licenses this file to you under the Apache License, version 2.0
 * (the "License"); you may not use this file except in compliance with the
 * License.  You may obtain a copy of the License at:
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the specific language governing permissions and limitations
 * under the License.
 *
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class DefaultWebSocket extends WebSocketImplBase implements WebSocket {

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
