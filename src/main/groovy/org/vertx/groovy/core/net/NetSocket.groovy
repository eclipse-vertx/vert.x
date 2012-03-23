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

import org.vertx.java.core.Handler
import org.vertx.groovy.core.streams.WriteStream
import org.vertx.groovy.core.streams.ReadStream
import org.vertx.groovy.core.buffer.Buffer

class NetSocket implements ReadStream, WriteStream {

  private org.vertx.java.core.net.NetSocket jSocket;

  NetSocket(jSocket) {
    this.jSocket = jSocket;
  }

  void writeBuffer(Buffer data) {
    write(data)
  }

  NetSocket write(Buffer data) {
    jSocket.write(data.toJavaBuffer())
    this
  }

  NetSocket write(String str) {
    jSocket.write(str)
    this
  }

  NetSocket write(String str, String enc) {
    jSocket.write(str, enc)
    this
  }

  NetSocket write(Buffer data, doneHandler) {
    jSocket.write(data.toJavaBuffer(), doneHandler as Handler)
    this
  }

  NetSocket write(String str, doneHandler) {
    jSocket.write(str, doneHandler as Handler)
    this
  }

  NetSocket write(String str, String enc, doneHandler) {
    jSocket.write(str, enc, doneHandler as Handler)
    this
  }

  void dataHandler(dataHandler) {
    jSocket.dataHandler({
      dataHandler.call(new Buffer(it))
    } as Handler)
  }

  void endHandler(endHandler) {
    jSocket.endHandler(endHandler as Handler)
  }

  void drainHandler(drainHandler) {
    jSocket.drainHandler(drainHandler as Handler)
  }

  void sendFile(String filename) {
    jSocket.sendFile(filename)
  }

  void pause() {
    jSocket.pause()
  }

  void resume() {
    jSocket.resume()
  }

  void setWriteQueueMaxSize(int size) {
    jSocket.setWriteQueueMaxSize(size)
  }

  boolean writeQueueFull() {
    jSocket.writeQueueFull()
  }

  void close() {
    jSocket.close()
  }

  void exceptionHandler(handler) {
    jSocket.exceptionHandler(handler as Handler)
  }

  void closedHandler(handler) {
    jSocket.closedHandler(handler as Handler)
  }

  /**
   * Alias for {@link #write} so that we can use the left shift operator
   * in Groovy, just as we do with other writables.
   */
  def leftShift(buff) {
    write(buff)
  }


}

