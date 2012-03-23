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
import org.vertx.java.core.buffer.Buffer
import org.vertx.groovy.core.streams.ReadStream

class HttpServerRequest implements ReadStream {

  private final org.vertx.java.core.http.HttpServerRequest jRequest
  
  private final HttpServerResponse wrappedResponse

  HttpServerRequest(org.vertx.java.core.http.HttpServerRequest jRequest) {
    this.jRequest = jRequest
    this.wrappedResponse = new HttpServerResponse(jRequest.response)
  }

  HttpServerResponse getResponse() {
    this.wrappedResponse
  }

  String getMethod() {
    jRequest.method
  }

  String getUri() {
    jRequest.uri
  }

  String getPath() {
    jRequest.path
  }

  String getQuery() {
    jRequest.query
  }

  String getHeader(String key) {
    jRequest.getHeader(key)
  }

  Set<String> getHeaderNames() {
    jRequest.getHeaderNames()
  }

  Map<String, String> getAllHeaders() {
    jRequest.getAllHeaders()
  }

  Map<String, String> getAllParams() {
    jRequest.getAllParams()
  }

  void dataHandler(dataHandler) {
    jRequest.dataHandler({dataHandler.call(new Buffer(it))} as Handler)
  }

  void exceptionHandler(handler) {
    jRequest.exceptionHandler(handler as Handler)
  }

  void pause() {
    jRequest.pause()
  }

  void resume() {
    jRequest.resume()
  }

  void endHandler(handler) {
    jRequest.endHandler(handler)
  }

  void bodyHandler(bodyHandler) {
    jRequest.dataHandler({bodyHandler.call(new Buffer(it))} as Handler)
  }

  void toJavaRequest() {
    jRequest
  }

}
