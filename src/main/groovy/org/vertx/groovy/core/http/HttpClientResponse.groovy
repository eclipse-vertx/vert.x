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
import org.vertx.java.core.Handler

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
class HttpClientResponse implements ReadStream {

  private final org.vertx.java.core.http.HttpClientResponse jResponse

  HttpClientResponse(org.vertx.java.core.http.HttpClientResponse jResponse) {
    this.jResponse = jResponse
  }

  int getStatusCode() {
    jResponse.statusCode
  }

  String getStatusMessage() {
    jResponse.statusMessage
  }

  String getHeader(String key) {
    jResponse.getHeader(key)
  }

  Set<String> getHeaderNames() {
    jResponse.getHeaderNames()
  }

  String getTrailer(String key) {
    jResponse.getTrailer(key)
  }

  Map<String, String> getAllHeaders() {
    jResponse.getAllHeaders()
  }

  Map<String, String> getAllTrailers() {
    jResponse.getAllTrailers()
  }

  Set<String> getTrailerNames() {
    jResponse.getTrailerNames()
  }

  void dataHandler(dataHandler) {
    jResponse.dataHandler({dataHandler.call(new Buffer(it))} as Handler)
  }

  void endHandler(endHandler) {
    jResponse.endHandler(endHandler as Handler)
  }

  void exceptionHandler(exceptionHandler) {
    jResponse.exceptionHandler(exceptionHandler as Handler)
  }

  void pause() {
    jResponse.pause()
  }

  void resume() {
    jResponse.resume()
  }

  void bodyHandler(bodyHandler) {
    jResponse.dataHandler({bodyHandler.call(new Buffer(it))} as Handler)
  }
}
