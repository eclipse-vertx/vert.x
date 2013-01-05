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

package org.vertx.groovy.core.http

import org.vertx.groovy.core.buffer.Buffer
import org.vertx.groovy.core.streams.ReadStream
import org.vertx.groovy.core.streams.WriteStream
import org.vertx.java.core.Handler
import org.vertx.java.core.http.WebSocket as JWebSocket

/**
 * Represents an HTML 5 Websocket<p>
 * Instances of this class are created and provided to the handler of an
 * {@link HttpClient} when a successful websocket connect attempt occurs.<p>
 * On the server side, the subclass {@link ServerWebSocket} is used instead.<p>
 * It implements both {@link ReadStream} and {@link WriteStream} so it can be used with
 * {@link org.vertx.groovy.core.streams.Pump} to pump data with flow control.<p>
 * Instances of this class are not thread-safe<p>
 *
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
class WebSocket implements ReadStream, WriteStream {

  private final JWebSocket jWS

  protected WebSocket(JWebSocket jWS) {
    this.jWS = jWS
  }

  /**
   * When a {@code Websocket} is created it automatically registers an event handler with the eventbus, the ID of that
   * handler is given by {@code binaryHandlerID}.<p>
   * Given this ID, a different event loop can send a binary frame to that event handler using the event bus and
   * that buffer will be received by this instance in its own event loop and written to the underlying connection. This
   * allows you to write data to other websockets which are owned by different event loops.
   */
  String getBinaryHandlerID() {
    jWS.binaryHandlerID
  }

  /**
   * When a {@code Websocket} is created it automatically registers an event handler with the eventbus, the ID of that
   * handler is given by {@code textHandlerID}.<p>
   * Given this ID, a different event loop can send a text frame to that event handler using the event bus and
   * that buffer will be received by this instance in its own event loop and written to the underlying connection. This
   * allows you to write data to other websockets which are owned by different event loops.
   */
  String getTextHandlerID() {
    jWS.textHandlerID
  }

  /**
   * Write {@code data} to the websocket as binary frame
   */
  void writeBinaryFrame(Buffer data) {
    jWS.writeBinaryFrame(data.toJavaBuffer())
  }

  /**
   * Write {@code str} to the websocket as text frame
   */
  void writeTextFrame(String str) {
    jWS.writeTextFrame(str)
  }

  /**
   * Same as {@link #writeBinaryFrame(Buffer)}
   */
  void leftShift(Buffer buff) {
    writeBuffer(buff)
    this
  }

  /**
   * Same as {@link #writeTextFrame(String)}
   */
  void leftShift(String str) {
    writeTextFrame(str)
    this
  }

  /**
   * Set a closed handler on the connection
   */
  void closedHandler(handler) {
    jWS.closedHandler(handler as Handler)
  }

  /**
   * Close the websocket
   */
  void close() {
    jWS.close()
  }

  /** {@inheritDoc} */
  void dataHandler(Closure handler) {
    jWS.dataHandler({handler(new Buffer(it))} as Handler)
  }

  /** {@inheritDoc} */
  void pause() {
    jWS.pause()
  }

  /** {@inheritDoc} */
  void resume() {
    jWS.resume()
  }

  /** {@inheritDoc} */
  void exceptionHandler(Closure handler) {
    jWS.exceptionHandler(handler as Handler)
  }

  /** {@inheritDoc} */
  void endHandler(Closure endHandler) {
    jWS.endHandler(endHandler as Handler)
  }

  /** {@inheritDoc} */
  void writeBuffer(Buffer data) {
    jWS.writeBuffer(data.toJavaBuffer())
  }

  /** {@inheritDoc} */
  void setWriteQueueMaxSize(int maxSize) {
    jWS.setWriteQueueMaxSize(maxSize)
  }

  /** {@inheritDoc} */
  boolean isWriteQueueFull() {
    jWS.writeQueueFull()
  }

  /** {@inheritDoc} */
  void drainHandler(Closure handler) {
    jWS.drainHandler(handler as Handler)
  }

}
