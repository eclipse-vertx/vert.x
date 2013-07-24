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

import io.netty.buffer.ByteBuf;
import org.vertx.java.core.Handler;
import org.vertx.java.core.buffer.Buffer;
import org.vertx.java.core.eventbus.Message;
import org.vertx.java.core.http.impl.ws.DefaultWebSocketFrame;
import org.vertx.java.core.http.impl.ws.WebSocketFrame;
import org.vertx.java.core.impl.VertxInternal;
import org.vertx.java.core.net.impl.ConnectionBase;

import java.util.UUID;

/**
 *
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class WebSocketImplBase {

  private final String textHandlerID;
  private final String binaryHandlerID;
  private final VertxInternal vertx;
  protected final ConnectionBase conn;

  protected Handler<Buffer> dataHandler;
  protected Handler<Void> drainHandler;
  protected Handler<Throwable> exceptionHandler;
  protected Handler<Void> closeHandler;
  protected Handler<Void> endHandler;
  protected Handler<Message<Buffer>> binaryHandler;
  protected Handler<Message<String>> textHandler;
  protected boolean closed;

  protected WebSocketImplBase(VertxInternal vertx, ConnectionBase conn) {
    this.vertx = vertx;
    this.textHandlerID = UUID.randomUUID().toString();
    this.binaryHandlerID = UUID.randomUUID().toString();
    this.conn = conn;
    binaryHandler = new Handler<Message<Buffer>>() {
      public void handle(Message<Buffer> msg) {
        writeBinaryFrameInternal(msg.body());
      }
    };
    vertx.eventBus().registerLocalHandler(binaryHandlerID, binaryHandler);
    textHandler = new Handler<Message<String>>() {
      public void handle(Message<String> msg) {
        writeTextFrameInternal(msg.body());
      }
    };
    vertx.eventBus().registerLocalHandler(textHandlerID, textHandler);
  }

  public String binaryHandlerID() {
    return binaryHandlerID;
  }

  public String textHandlerID() {
    return textHandlerID;
  }

  public boolean writeQueueFull() {
    checkClosed();
    return conn.doWriteQueueFull();
  }

  public void close() {
    checkClosed();
    conn.close();
    cleanupHandlers();
  }

  protected void writeBinaryFrameInternal(Buffer data) {
    ByteBuf buf = data.getByteBuf();
    WebSocketFrame frame = new DefaultWebSocketFrame(WebSocketFrame.FrameType.BINARY, buf);
    writeFrame(frame);
  }

  protected void writeTextFrameInternal(String str) {
    WebSocketFrame frame = new DefaultWebSocketFrame(str);
    writeFrame(frame);
  }


  private void cleanupHandlers() {
    if (!closed) {
      vertx.eventBus().unregisterHandler(binaryHandlerID, binaryHandler);
      vertx.eventBus().unregisterHandler(textHandlerID, textHandler);
      closed = true;
    }
  }

  protected void writeFrame(WebSocketFrame frame) {
    checkClosed();
    conn.write(frame);
  }

  protected void checkClosed() {
    if (closed) {
      throw new IllegalStateException("WebSocket is closed");
    }
  }

  void handleFrame(WebSocketFrame frame) {
    if (dataHandler != null) {
      Buffer buff = new Buffer(frame.getBinaryData());
      dataHandler.handle(buff);
    }
  }

  void writable() {
    if (drainHandler != null) {
      Handler<Void> dh = drainHandler;
      drainHandler = null;
      dh.handle(null);
    }
  }

  void handleException(Throwable t) {
    if (exceptionHandler != null) {
      exceptionHandler.handle(t);
    }
  }

  void handleClosed() {
    cleanupHandlers();
    if (endHandler != null) {
      endHandler.handle(null);
    }
    if (closeHandler != null) {
      closeHandler.handle(null);
    }
  }
}
