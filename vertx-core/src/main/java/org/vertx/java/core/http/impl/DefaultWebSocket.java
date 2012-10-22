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

import org.vertx.java.core.Handler;
import org.vertx.java.core.buffer.Buffer;
import org.vertx.java.core.eventbus.Message;
import org.vertx.java.core.http.ServerWebSocket;
import org.vertx.java.core.http.impl.ws.DefaultWebSocketFrame;
import org.vertx.java.core.http.impl.ws.WebSocketFrame;
import org.vertx.java.core.impl.VertxInternal;

import java.util.UUID;

/**
 *
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class DefaultWebSocket extends ServerWebSocket {

  private final VertxInternal vertx;
  private final AbstractConnection conn;

  private Handler<Buffer> dataHandler;
  private Handler<Void> drainHandler;
  private Handler<Exception> exceptionHandler;
  private Handler<Void> closedHandler;
  private Handler<Void> endHandler;

  private Handler<Message<Buffer>> binaryHandler;
  private Handler<Message<String>> textHandler;

  private final Runnable connectRunnable;
  protected boolean closed;
  boolean rejected;
  private boolean connected;

  protected DefaultWebSocket(VertxInternal vertx, String path, AbstractConnection conn, Runnable connectRunnable) {
    super(path, UUID.randomUUID().toString(), UUID.randomUUID().toString());
    this.vertx = vertx;
    this.conn = conn;
    binaryHandler = new Handler<Message<Buffer>>() {
      public void handle(Message<Buffer> msg) {
        writeBinaryFrame(msg.body);
      }
    };

    vertx.eventBus().registerLocalHandler(binaryHandlerID, binaryHandler);
    textHandler = new Handler<Message<String>>() {
      public void handle(Message<String> msg) {
        writeTextFrame(msg.body);
      }
    };
    vertx.eventBus().registerLocalHandler(textHandlerID, textHandler);
    this.connectRunnable = connectRunnable;
  }

  public void reject() {
    checkClosed();
    if (connectRunnable == null) {
      throw new IllegalStateException("Cannot reject websocket on the client side");
    }
    if (connected) {
      throw new IllegalStateException("Cannot reject websocket, it has already been written to");
    }
    rejected = true;
  }

  public void writeBinaryFrame(Buffer data) {
    WebSocketFrame frame = new DefaultWebSocketFrame(WebSocketFrame.FrameType.BINARY, data.getChannelBuffer());
    writeFrame(frame);
  }

  public void writeTextFrame(String str) {
    WebSocketFrame frame = new DefaultWebSocketFrame(str);
    writeFrame(frame);
  }

  public void dataHandler(Handler<Buffer> handler) {
    checkClosed();
    this.dataHandler = handler;
  }

  public void endHandler(Handler<Void> handler) {
    checkClosed();
    this.endHandler = handler;
  }

  public void exceptionHandler(Handler<Exception> handler) {
    checkClosed();
    this.exceptionHandler = handler;
  }

  public void closedHandler(Handler<Void> handler) {
    checkClosed();
    this.closedHandler = handler;
  }

  public void pause() {
    checkClosed();
    conn.pause();
  }

  public void resume() {
    checkClosed();
    conn.resume();
  }

  public void setWriteQueueMaxSize(int maxSize) {
    checkClosed();
    conn.setWriteQueueMaxSize(maxSize);
  }

  public boolean writeQueueFull() {
    checkClosed();
    return conn.writeQueueFull();
  }

  public void writeBuffer(Buffer data) {
    writeBinaryFrame(data);
  }

  public void drainHandler(Handler<Void> handler) {
    checkClosed();
    this.drainHandler = handler;
  }

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
    conn.close();
    cleanupHandlers();
  }

  private void cleanupHandlers() {
    if (!closed) {
      vertx.eventBus().unregisterHandler(binaryHandlerID, binaryHandler);
      vertx.eventBus().unregisterHandler(textHandlerID, textHandler);
      closed = true;
    }
  }

  protected void writeFrame(WebSocketFrame frame) {
    if (connectRunnable != null) {
      if (rejected) {
        throw new IllegalStateException("Cannot write to websocket, it has been rejected");
      }
      if (!connected && !closed) {
        connect();
      }
    }
    checkClosed();
    conn.write(frame);
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

  void handleException(Exception e) {
    if (exceptionHandler != null) {
      exceptionHandler.handle(e);
    }
  }

  void handleClosed() {
    cleanupHandlers();
    if (endHandler != null) {
      endHandler.handle(null);
    }
    if (closedHandler != null) {
      closedHandler.handle(null);
    }
  }
}
