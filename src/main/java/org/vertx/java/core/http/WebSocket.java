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

package org.vertx.java.core.http;

import org.vertx.java.core.Handler;
import org.vertx.java.core.buffer.Buffer;
import org.vertx.java.core.streams.ReadStream;
import org.vertx.java.core.streams.WriteStream;

/**
 * Represents an HTML 5 Websocket
 * <p>
 * Instances of this class are created and provided to the handler of an
 * {@link HttpClient} when a successful websocket connect attempt occurs.
 * <p>
 * On the server side, the subclass {@link ServerWebSocket} is used instead.
 * <p>
 * Instances of this class are not thread-safe
 * <p>
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public abstract class WebSocket implements ReadStream, WriteStream {

  protected WebSocket(String binaryHandlerID, String textHandlerID) {
    this.binaryHandlerID = binaryHandlerID;
    this.textHandlerID = textHandlerID;
  }

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

  /**
   * Write {@code data} to the websocket as binary frame
   */
  public abstract void writeBinaryFrame(Buffer data);

  /**
   * Write {@code str} to the websocket as text frame
   */
  public abstract void writeTextFrame(String str);

  /**
   * Specify a data handler for the websocket. As data is received on the websocket the handler will be called, passing
   * in a Buffer of data
   */
  public abstract void dataHandler(Handler<Buffer> handler);

  /**
   * Specify an end handler for the websocket. The {@code endHandler} is called once there is no more data to be read.
   */
  public abstract void endHandler(Handler<Void> handler);

  /**
   * Specify an exception handler for the websocket. The {@code exceptionHandler} is called if an exception occurs.
   */
  public abstract void exceptionHandler(Handler<Exception> handler);

  /**
   * Set a closed handler on the connection
   */
  public abstract void closedHandler(Handler<Void> handler);

  /**
   * Pause the websocket. Once the websocket has been paused, the system will stop reading any more chunks of data
   * from the wire, thus pushing back to the server.
   * Pause is often used in conjunction with a {@link org.vertx.java.core.streams.Pump} to pump data between streams and implement flow control.
   */
  public abstract void pause() ;

  /**
   * Resume a paused websocket. The websocket will resume receiving chunks of data from the wire.<p>
   * Resume is often used in conjunction with a {@link org.vertx.java.core.streams.Pump} to pump data between streams and implement flow control.
   */
  public abstract void resume();

  /**
   * Data is queued until it is actually sent. To set the point at which the queue is considered "full" call this method
   * specifying the {@code maxSize} in bytes.<p>
   * This method is used by the {@link org.vertx.java.core.streams.Pump} class to pump data
   * between different streams and perform flow control.
   */
  public abstract void setWriteQueueMaxSize(int maxSize);

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
  public abstract boolean writeQueueFull();

  /**
   * Write a {@link Buffer} to the websocket.
   */
  public abstract void writeBuffer(Buffer data);

  /**
   * This method sets a drain handler {@code handler} on the websocket. The drain handler will be called when write queue is no longer
   * full and it is safe to write to it again.<p>
   * The drain handler is actually called when the write queue size reaches <b>half</b> the write queue max size to prevent thrashing.
   * This method is used as part of a flow control strategy, e.g. it is used by the {@link org.vertx.java.core.streams.Pump} class to pump data
   * between different streams.
   *
   * @param handler
   */
  public abstract void drainHandler(Handler<Void> handler);

  /**
   * Close the websocket
   */
  public abstract void close();

}
