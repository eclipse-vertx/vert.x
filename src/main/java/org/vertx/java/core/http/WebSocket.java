/*
 * Copyright 2011 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.vertx.java.core.http;

import org.vertx.java.core.Handler;
import org.vertx.java.core.VertxInternal;
import org.vertx.java.core.app.VerticleManager;
import org.vertx.java.core.buffer.Buffer;
import org.vertx.java.core.eventbus.EventBus;
import org.vertx.java.core.eventbus.Message;
import org.vertx.java.core.http.ws.DefaultWebSocketFrame;
import org.vertx.java.core.http.ws.WebSocketFrame;
import org.vertx.java.core.streams.ReadStream;
import org.vertx.java.core.streams.WriteStream;

import java.util.UUID;

/**
 * <p>Encapsulation of an HTML 5 Websocket</p>
 * <p/>
 * <p>Instances of this class are either created by an {@link HttpServer}
 * instance when a websocket handshake is accepted on the server, or are create by an {@link HttpClient}
 * instance when a client succeeds in a websocket handshake with a server. Once an instance has been obtained it can
 * be used to send or receive buffers of data from the connection, a bit like a TCP socket.</p>
 * <p/>
 * <p>Instances of this class can only be used from the event loop thread which created it.</p>
 *
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class WebSocket implements ReadStream, WriteStream {

  private final AbstractConnection conn;

  private Handler<Buffer> dataHandler;
  private Handler<Void> drainHandler;
  private Handler<Exception> exceptionHandler;
  private Handler<Void> closedHandler;
  private Handler<Void> endHandler;

  private Handler<Message<Buffer>> binaryHandler;
  private Handler<Message<String>> textHandler;

  protected boolean closed;

  /**
   * When a {@code Websocket} is created it automatically registers an event handler with the eventbus, the ID of that
   * handler is given by {@code binaryHandlerID}.<p>
   * Given this ID, a different event loop can send a binary frame to that event handler using the event bus and
   * that buffer will be received by this instance in its own event loop and writing to the underlying connection. This
   * allows you to write data to other websockets which are owned by different event loops.
   */
  public final String binaryHandlerID;

  /**
   * When a {@code Websocket} is created it automatically registers an event handler with the eventbus, the ID of that
   * handler is given by {@code textHandlerID}.<p>
   * Given this ID, a different event loop can send a text frame to that event handler using the event bus and
   * that buffer will be received by this instance in its own event loop and writing to the underlying connection. This
   * allows you to write data to other websockets which are owned by different event loops.
   */
  public final String textHandlerID;

  WebSocket(AbstractConnection conn) {
    this.conn = conn;
    binaryHandlerID = UUID.randomUUID().toString();
    binaryHandler = new Handler<Message<Buffer>>() {
      public void handle(Message<Buffer> msg) {
        writeBinaryFrame(msg.body);
      }
    };
    EventBus.instance.registerHandler(binaryHandlerID, binaryHandler);
    textHandlerID = UUID.randomUUID().toString();
    textHandler = new Handler<Message<String>>() {
      public void handle(Message<String> msg) {
        writeTextFrame(msg.body);
      }
    };
    EventBus.instance.registerHandler(textHandlerID, textHandler);
  }

  /**
   * Write {@code data} to the websocket as binary frame
   */
  public void writeBinaryFrame(Buffer data) {
    WebSocketFrame frame = new DefaultWebSocketFrame(WebSocketFrame.FrameType.BINARY, data.getChannelBuffer());
    writeFrame(frame);
  }

  /**
   * Write {@code str} to the websocket as text frame
   */
  public void writeTextFrame(String str) {
    WebSocketFrame frame = new DefaultWebSocketFrame(str);
    writeFrame(frame);
  }

  /**
   * Specify a data handler for the websocket. As data is received on the websocket the handler will be called, passing
   * in a Buffer of data
   */
  public void dataHandler(Handler<Buffer> handler) {
    checkClosed();
    this.dataHandler = handler;
  }

  /**
   * Specify an end handler for the websocket. The {@code endHandler} is called once there is no more data to be read.
   */
  public void endHandler(Handler<Void> handler) {
    checkClosed();
    this.endHandler = handler;
  }

  /**
   * Specify an exception handler for the websocket. The {@code exceptionHandler} is called if an exception occurs.
   */
  public void exceptionHandler(Handler<Exception> handler) {
    checkClosed();
    this.exceptionHandler = handler;
  }

  /**
   * Set a closed handler on the connection
   */
  public void closedHandler(Handler<Void> handler) {
    checkClosed();
    this.closedHandler = handler;
  }

  /**
   * Pause the websocket. Once the websocket has been paused, the system will stop reading any more chunks of data
   * from the wire, thus pushing back to the server.
   * Pause is often used in conjunction with a {@link org.vertx.java.core.streams.Pump} to pump data between streams and implement flow control.
   */
  public void pause() {
    checkClosed();
    conn.pause();
  }

  /**
   * Resume a paused websocket. The websocket will resume receiving chunks of data from the wire.<p>
   * Resume is often used in conjunction with a {@link org.vertx.java.core.streams.Pump} to pump data between streams and implement flow control.
   */
  public void resume() {
    checkClosed();
    conn.resume();
  }

  /**
   * Data is queued until it is actually sent. To set the point at which the queue is considered "full" call this method
   * specifying the {@code maxSize} in bytes.<p>
   * This method is used by the {@link org.vertx.java.core.streams.Pump} class to pump data
   * between different streams and perform flow control.
   */
  public void setWriteQueueMaxSize(int maxSize) {
    checkClosed();
    conn.setWriteQueueMaxSize(maxSize);
  }

  /**
   * If the amount of data that is currently queued is greater than the write queue max size see {@link #setWriteQueueMaxSize(int)}
   * then the write queue is considered full.<p>
   * Data can still be written to the websocket even if the write queue is deemed full, however it should be used as indicator
   * to stop writing and push back on the source of the data, otherwise you risk running out of available RAM.<p>
   * This method is used by the {@link org.vertx.java.core.streams.Pump} class to pump data
   * between different streams and perform flow control.
   *
   * @return {@code true} if the write queue is full, {@code false} otherwise
   */
  public boolean writeQueueFull() {
    checkClosed();
    return conn.writeQueueFull();
  }

  /**
   * Write a {@link Buffer} to the websocket.
   */
  public void writeBuffer(Buffer data) {
    writeBinaryFrame(data);
  }

  /**
   * This method sets a drain handler {@code handler} on the websocket. The drain handler will be called when write queue is no longer
   * full and it is safe to write to it again.<p>
   * The drain handler is actually called when the write queue size reaches <b>half</b> the write queue max size to prevent thrashing.
   * This method is used as part of a flow control strategy, e.g. it is used by the {@link org.vertx.java.core.streams.Pump} class to pump data
   * between different streams.
   *
   * @param handler
   */
  public void drainHandler(Handler<Void> handler) {
    checkClosed();
    this.drainHandler = handler;
  }

  /**
   * Close the websocket
   */
  public void close() {
    checkClosed();
    conn.close();
    EventBus.instance.unregisterHandler(binaryHandlerID, binaryHandler);
    EventBus.instance.unregisterHandler(textHandlerID, textHandler);
    closed = true;
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
      drainHandler.handle(null);
    }
  }

  void handleException(Exception e) {
    if (exceptionHandler != null) {
      exceptionHandler.handle(e);
    } else {
      VertxInternal.instance.reportException(e);
    }
  }

  void handleClosed() {
    if (endHandler != null) {
      endHandler.handle(null);
    }
    if (closedHandler != null) {
      closedHandler.handle(null);
    }
  }
}
