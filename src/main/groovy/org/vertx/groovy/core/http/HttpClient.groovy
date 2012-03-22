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
import org.vertx.java.core.http.WebSocketVersion


/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
class HttpClient extends org.vertx.java.core.http.HttpClient {

  void exceptionHandler(Closure handler) {
    super.exceptionHandler(handler as Handler)
  }

  void connectWebsocket(String uri, Closure handler) {
    connectWebsocket(uri, WebSocketVersion.HYBI_17, handler)
  }

  void connectWebsocket(String uri, WebSocketVersion version, Closure handler) {
    super.connectWebsocket(uri, version, {handler.call(new WebSocket(it))} as Handler)
  }

  void getNow(String uri, Closure responseHandler) {
    super.getNow(uri, wrapResponseHandler(responseHandler))
  }

  void getNow(String uri, Map<String, ? extends Object> headers, Closure responseHandler) {
    super.getNow(uri, headers, wrapResponseHandler(responseHandler))
  }

  HttpClientRequest options(String uri, Closure responseHandler) {
    new HttpClientRequest(super.options(uri, wrapResponseHandler(responseHandler)))
  }

  HttpClientRequest get(String uri, Closure responseHandler) {
    request("GET", uri, responseHandler)
  }

  HttpClientRequest head(String uri, Closure responseHandler) {
    request("HEAD", uri, responseHandler)
  }

  HttpClientRequest post(String uri, Closure responseHandler) {
    request("POST", uri, responseHandler)
  }

  HttpClientRequest put(String uri, Closure responseHandler) {
    request("PUT", uri, responseHandler)
  }

  HttpClientRequest delete(String uri, Closure responseHandler) {
    request("DELETE", uri, responseHandler)
  }

  HttpClientRequest trace(String uri, Closure responseHandler) {
    request("TRACE", uri, responseHandler)
  }

  HttpClientRequest connect(String uri, Closure responseHandler) {
    request("CONNECT", uri, responseHandler)
  }

  HttpClientRequest patch(String uri, Closure responseHandler) {
    request("PATCH", uri, responseHandler)
  }

  HttpClientRequest request(String method, String uri, Closure responseHandler) {
    new HttpClientRequest(super.request(method, uri, wrapResponseHandler(responseHandler)))
  }

  private Handler wrapResponseHandler(Closure handler) {
    return {handler.call(new HttpClientResponse(it))} as Handler
  }

}
