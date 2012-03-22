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

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
class HttpClientRequest implements WriteStream {

  private final org.vertx.java.core.http.HttpClientRequest jRequest;

  HttpClientRequest(org.vertx.java.core.http.HttpClientRequest jRequest) {
    this.jRequest = jRequest
  }

  HttpClientRequest setChunked(boolean chunked) {
    jRequest.setChunked(chunked)
    this
  }

  HttpClientRequest putHeader(String key, Object value) {
    jRequest.putHeader(key, value)
    this
  }

  HttpClientRequest putAllHeaders(Map<String, ? extends Object> m) {
    jRequest.putAllHeaders(m)
    this
  }

  void writeBuffer(Buffer chunk) {
    jRequest.writeBuffer(chunk.jBuffer)
  }

  HttpClientRequest write(Buffer chunk) {
    writeBuffer(chunk)
    this
  }

  HttpClientRequest write(String chunk) {
    jRequest.write(chunk)
    this
  }

  HttpClientRequest write(String chunk, String enc) {
    jRequest.write(chunk, enc)
    this
  }

  HttpClientRequest write(Buffer chunk, Closure doneHandler) {
    jRequest.write(chunk.jBuffer, doneHandler as Handler)
    this
  }

  HttpClientRequest write(String chunk, Closure doneHandler) {
    jRequest.write(chunk, doneHandler as Handler)
    this
  }

  HttpClientRequest write(String chunk, String enc, Closure doneHandler) {
    jRequest.write(chunk, enc, doneHandler as Handler)
    this
  }

  void setWriteQueueMaxSize(int maxSize) {
    jRequest.setWriteQueueMaxSize(maxSize)
  }

  boolean writeQueueFull() {
    jRequest.writeQueueFull()
  }

  void drainHandler(Closure handler) {
    jRequest.drainHandler(handler as Handler)
  }

  void exceptionHandler(Closure handler) {
    jRequest.exceptionHandler(handler as Handler)
  }

  void continueHandler(Closure handler) {
    jRequest.continueHandler(handler as Handler)
  }

  HttpClientRequest sendHead() {
    jRequest.sendHead()
    this
  }

  void end(String chunk) {
    jRequest.end(chunk)
  }

  void end(String chunk, String enc) {
    jRequest.end(chunk, enc)
  }

  void end(Buffer chunk) {
    jRequest.end(chunk.jBuffer)
  }

  void end() {
    jRequest.end()
  }

  HttpClientRequest leftShift(Buffer buff) {
    jRequest.write(buff.jBuffer)
    this
  }

  HttpClientRequest leftShift(String str) {
    jRequest.write(str)
    this
  }
}
