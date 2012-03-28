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
import org.vertx.java.core.Handler

class WebSocket  {

  private final org.vertx.java.core.http.WebSocket jWS;

  WebSocket(org.vertx.java.core.http.WebSocket jWS) {
    this.jWS = jWS
  }

  void writeBinaryFrame(Buffer data) {
    jWS.writeBinaryFrame(data.toJavaBuffer())
  }

  void writeTextFrame(String str) {
    jWS.writeTextFrame(str)
  }

  void closedHandler(handler) {
    jWS.closedHandler(handler as Handler)
  }

  void close() {
    jWS.close();
  }

  void dataHandler(handler) {
    jWS.dataHandler({handler.call(new Buffer(it))} as Handler)
  }

  void pause() {
    jWS.pause()
  }

  void resume() {
    jWS.resume()
  }

  void exceptionHandler(handler) {
    jWS.exceptionHandler(handler as Handler)
  }

  void endHandler(endHandler) {
    jWS.endHandler(endHandler as Handler)
  }

  void writeBuffer(Buffer data) {
    jWS.writeBuffer(data.toJavaBuffer())
  }

  void setWriteQueueMaxSize(int maxSize) {
    jWS.setWriteQueueMaxSize(maxSize)
  }

  boolean writeQueueFull() {
    jWS.writeQueueFull()
  }

  void drainHandler(handler) {
    jWS.drainHandler(handler as Handler)
  }

  void leftShift(Buffer buff) {
    writeBuffer(buff)
    this
  }

  void leftShift(String str) {
    writeTextFrame(str)
    this
  }

  String getBinaryHandlerID() {
    jWS.binaryHandlerID
  }

  String getTextHandlerID() {
    jWS.textHandlerID
  }


}
