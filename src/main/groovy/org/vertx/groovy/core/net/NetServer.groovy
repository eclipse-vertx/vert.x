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

package org.vertx.groovy.core.net

import org.vertx.java.core.Handler

class NetServer {

  private jServer

  NetServer() {
    jServer = new org.vertx.java.core.net.NetServer()
  }

  def connectHandler(hndlr) {
    jServer.connectHandler({ hndlr.call(new NetSocket(it)) } as Handler)
  }

  def listen(int port) {
    jServer.listen(port)
  }

}

class NetSocket {

  private jSocket

  NetSocket(jSocket) {
    this.jSocket = jSocket
  }

  def dataHandler(hndlr) {
    jSocket.dataHandler(hndlr as Handler)
  }

  def write(buff) {
    jSocket.write(buff)
  }

  def leftShift(buff) {
    write(buff)
  }

}
