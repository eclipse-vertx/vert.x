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

import org.vertx.java.core.Handler
import org.vertx.groovy.core.buffer.Buffer
import org.vertx.groovy.core.streams.WriteStream

class HttpServerResponse implements WriteStream {

  private final org.vertx.java.core.http.HttpServerResponse jResponse

  HttpServerResponse(org.vertx.java.core.http.HttpServerResponse jResponse) {
    this.jResponse = jResponse
  }

  int getStatusCode() {
    return jResponse.statusCode
  }

  void setStatusCode(int code) {
    jResponse.statusCode = code
  }

  String getStatusMessage() {
    return jResponse.statusMessage
  }

  void setStatusMessage(String msg) {
    jResponse.statusMessage = msg
  }

  HttpServerResponse setChunked(boolean chunked) {
    jResponse.setChunked(true)
    this
  }

  HttpServerResponse putAllHeaders(Map<String, ? extends Object> m) {
    jResponse.putAllHeaders(m)
    this
  }

  HttpServerResponse putTrailer(String key, Object value) {
    jResponse.putTrailer(key, value)
    this
  }

  HttpServerResponse putAllTrailers(Map<String, ? extends Object> m) {
    jResponse.putAllTrailers(m);
    this
  }

  void setWriteQueueMaxSize(int size) {
    jResponse.setWriteQueueMaxSize(size)
  }

  boolean writeQueueFull() {
    jResponse.writeQueueFull()
  }

  void drainHandler(handler) {
    jResponse.drainHandler(handler as Handler)
  }

  void exceptionHandler(handler) {
    jResponse.exceptionHandler(handler as Handler)
  }

  void closeHandler(handler) {
    jResponse.closeHandler(handler as Handler)
  }

  void writeBuffer(Buffer chunk) {
    jResponse.writeBuffer(chunk.toJavaBuffer())
  }

  HttpServerResponse write(Buffer chunk) {
    jResponse.write(chunk.toJavaBuffer())
    this
  }

  HttpServerResponse write(String chunk, String enc) {
    jResponse.write(chunk, enc)
    this
  }

  HttpServerResponse write(String chunk) {
    jResponse.write(chunk)
    this
  }

  HttpServerResponse write(Buffer chunk, doneHandler) {
    jResponse.write(chunk, doneHandler as Handler)
    this
  }

  HttpServerResponse write(String chunk, String enc, doneHandler) {
    jResponse.write(chunk, enc, doneHandler as Handler)
    this
  }

  HttpServerResponse write(String chunk, doneHandler) {
    jResponse.write(chunk, doneHandler as Handler)
    this
  }

  void end(String chunk) {
    jResponse.end(chunk)
  }

  void end(String chunk, String enc) {
    jResponse.end(chunk, enc)
  }

  void end(Buffer chunk) {
    jResponse.end(chunk.toJavaBuffer())
  }

  void end() {
    jResponse.end()
  }

  HttpServerResponse sendFile(String filename) {
    jResponse.sendFile(filename)
    this
  }

  void close() {
    jResponse.close()
  }

  HttpServerResponse leftShift(Buffer buff) {
    jResponse.write(buff.toJavaBuffer())
    this
  }

  HttpServerResponse leftShift(String str) {
    jResponse.write(str)
    this
  }

}
