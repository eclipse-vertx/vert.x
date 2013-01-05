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

package org.vertx.groovy.core.net

import org.vertx.groovy.core.buffer.Buffer
import org.vertx.groovy.core.streams.ReadStream
import org.vertx.groovy.core.streams.WriteStream
import org.vertx.java.core.Handler
import org.vertx.java.core.net.NetSocket as JNetSocket

/**
 * Represents a socket-like interface to a TCP/SSL connection on either the
 * client or the server side.<p>
 * Instances of this class are created on the client side by an {@link NetClient}
 * when a connection to a server is made, or on the server side by a {@link NetServer}
 * when a server accepts a connection.<p>
 * It implements both {@link ReadStream} and {@link WriteStream} so it can be used with
 * {@link org.vertx.groovy.core.streams.Pump} to pump data with flow control.<p>
 * Instances of this class are not thread-safe.<p>
 * @author Peter Ledbrook
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
class NetSocket implements ReadStream, WriteStream {

  private JNetSocket jSocket

  protected NetSocket(JNetSocket jSocket) {
    this.jSocket = jSocket
  }

  /** {@inheritDoc} */
  void writeBuffer(Buffer data) {
    write(data)
  }

  /**
   * Write a {@link Buffer} to the request body.<p>
   * @return A reference to this, so multiple method calls can be chained.
   */
  NetSocket write(Buffer data) {
    jSocket.write(data.toJavaBuffer())
    this
  }

  /**
   * Write a {@link String} to the connection, encoded in UTF-8.<p>
   * @return A reference to this, so multiple method calls can be chained.
   */
  NetSocket write(String str) {
    jSocket.write(str)
    this
  }

  /**
   * Write a {@link String} to the connection, encoded using the encoding {@code enc}.<p>
   * @return A reference to this, so multiple method calls can be chained.
   */
  NetSocket write(String str, String enc) {
    jSocket.write(str, enc)
    this
  }

  /**
   * Write a {@link Buffer} to the connection. The {@code doneHandler} is called after the buffer is actually written to the wire.<p>
   * @return A reference to this, so multiple method calls can be chained.
   */
  NetSocket write(Buffer data, Closure doneHandler) {
    jSocket.write(data.toJavaBuffer(), doneHandler as Handler)
    this
  }

  /**
   * Write a {@link String} to the connection, encoded in UTF-8. The {@code doneHandler} is called after the buffer is actually written to the wire.<p>
   * @return A reference to this, so multiple method calls can be chained.
   */
  NetSocket write(String str, Closure doneHandler) {
    jSocket.write(str, doneHandler as Handler)
    this
  }

  /**
   * Write a {@link String} to the connection, encoded with encoding {@code enc}. The {@code doneHandler} is called after the buffer is actually written to the wire.<p>
   * @return A reference to this, so multiple method calls can be chained.
   */
  NetSocket write(String str, String enc, Closure doneHandler) {
    jSocket.write(str, enc, doneHandler as Handler)
    this
  }


  /**
   * Tell the kernel to stream a file as specified by {@code filename} directly from disk to the outgoing connection, bypassing userspace altogether
   * (where supported by the underlying operating system. This is a very efficient way to stream files.<p>
   */
  void sendFile(String filename) {
    jSocket.sendFile(filename)
  }

  /**
   * Close the socket
   */
  void close() {
    jSocket.close()
  }


  /**
   * Same as {@link #write(Buffer)}
   */
  NetSocket leftShift(Buffer buff) {
    write(buff)
  }

  /**
   * Same as {@link #write(String)}
   */
  NetSocket leftShift(String str) {
    write(str)
  }

  /**
   * @return When a {@code NetSocket} is created it automatically registers an event handler with the event bus, the ID of that
   * handler is given by {@code writeHandlerID}.<p>
   * Given this ID, a different event loop can send a buffer to that event handler using the event bus and
   * that buffer will be received by this instance in its own event loop and written to the underlying connection. This
   * allows you to write data to other connections which are owned by different event loops.
   */
  String getWriteHandlerID() {
    jSocket.writeHandlerID
  }

  /** {@inheritDoc} */
  void dataHandler(Closure dataHandler) {
    jSocket.dataHandler({
      dataHandler(new Buffer(it))
    } as Handler)
  }

  /** {@inheritDoc} */
  void endHandler(Closure endHandler) {
    jSocket.endHandler(endHandler as Handler)
  }

  /** {@inheritDoc} */
  void drainHandler(Closure drainHandler) {
    jSocket.drainHandler(drainHandler as Handler)
  }

  /** {@inheritDoc} */
  void pause() {
    jSocket.pause()
  }

  /** {@inheritDoc} */
  void resume() {
    jSocket.resume()
  }

  /** {@inheritDoc} */
  void setWriteQueueMaxSize(int size) {
    jSocket.setWriteQueueMaxSize(size)
  }

  /** {@inheritDoc} */
  boolean isWriteQueueFull() {
    jSocket.writeQueueFull()
  }

  /** {@inheritDoc} */
  void exceptionHandler(Closure handler) {
    jSocket.exceptionHandler(handler as Handler)
  }

  /** {@inheritDoc} */
  void closedHandler(Closure handler) {
    jSocket.closedHandler(handler as Handler)
  }




}

