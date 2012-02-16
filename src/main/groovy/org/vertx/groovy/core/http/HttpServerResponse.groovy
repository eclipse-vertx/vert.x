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

class HttpServerResponse {

  @Delegate org.vertx.java.core.http.HttpServerResponse wrappedResponse

  HttpServerResponse(response) {
    this.wrappedResponse = response
  }

  def getStatusCode() {
    return wrappedResponse.statusCode
  }

  void setStatusCode(int code) {
    wrappedResponse.statusCode = code
  }

  def getStatusMessage() {
    return wrappedResponse.statusMessage
  }

  void setStatusMessage(String msg) {
    wrappedResponse.statusMessage = msg
  }

  def putAt(String header, value) {
    wrappedResponse.putHeader(header, value)
  }

  def leftShift(buff) {
    wrappedResponse.write(buff)
  }
}
