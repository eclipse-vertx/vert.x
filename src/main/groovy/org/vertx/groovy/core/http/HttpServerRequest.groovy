/*
 * Copyright 2011 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.vertx.groovy.core.http

import org.vertx.java.core.http.ServerConnection

class HttpServerRequest  {

  @Delegate org.vertx.java.core.http.HttpServerRequest wrappedRequest
  private final HttpServerResponse wrappedResponse

  HttpServerRequest(request) {
    this.wrappedRequest = request
    this.wrappedResponse = new HttpServerResponse(request.response)
  }

  def getResponse() {
    return this.wrappedResponse
  }

  def getMethod() {
    return wrappedRequest.method
  }

  def getUri() {
    return wrappedRequest.uri
  }

  def getPath() {
    return wrappedRequest.path
  }

  def getQuery() {
    return wrappedRequest.query
  }

  def getAt(String header) {
    return wrappedRequest.getHeader(header)
  }

}
