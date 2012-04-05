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

/**
 * Represents a TCP or SSL server
 * <p>
 * This class is a thread safe and can safely be used by different threads.
 * <p>
 * If an instance is instantiated from an event loop then the handlers
 * of the instance will always be called on that same event loop.
 * If an instance is instantiated from some other arbitrary Java thread then
 * and event loop will be assigned to the instance and used when any of its handlers
 * are called.
 * <p>
 * Instances cannot be used from worker verticles
 * @author Peter Ledbrook
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
abstract class NetServer {
  
  protected org.vertx.java.core.net.NetServer jServer;

  /**
   * Supply a connect handler for this server. The server can only have at most one connect handler at any one time.
   * As the server accepts TCP or SSL connections it creates an instance of {@link org.vertx.java.core.net.NetSocket} and passes it to the
   * connect handler.
   * @return a reference to this so multiple method calls can be chained together
   */
  NetServer connectHandler(Closure hndlr) {
    jServer.connectHandler(wrapHandler(hndlr))
    this
  }

  /**
   * Close the server. This will close any currently open connections. The event handler {@code hndlr} will be called
   * when the close is complete.
   */
  void close(Closure hndlr) {
    jServer.close(hndlr as Handler)
  }

  private Handler wrapHandler(Closure hndlr) {
    return {hndlr(new NetSocket(it))} as Handler
  }
  
  NetServer listen(int port) {
    jServer.listen(port)
    this
  }
  
  NetServer listen(int port, String host) {
    jServer.listen(port, host)
    this
  }
  
  void close() {
    jServer.close()    
  }
  
  NetServer setSSL(boolean ssl) {
    jServer.setSSL(ssl)
    this
  }

  NetServer setKeyStorePath(String path) {
    jServer.setKeyStorePath(path)
    this
  }

  NetServer setKeyStorePassword(String pwd) {
    jServer.setKeyStorePassword(pwd)
    this
  }

  NetServer setTrustStorePath(String path) {
    jServer.setTrustStorePath(path)
    this
  }

  NetServer setTrustStorePassword(String pwd) {
    jServer.setTrustStorePassword(pwd)
    this
  }

  NetServer setClientAuthRequired(boolean required) {
    jServer.setClientAuthRequired(required)
    this
  }

  NetServer setTCPNoDelay(boolean tcpNoDelay) {
    jServer.setTCPNoDelay(tcpNoDelay)
    this
  }

  NetServer setSendBufferSize(int size) {
    jServer.setSendBufferSize(size)
    this
  }

  NetServer setReceiveBufferSize(int size) {
    jServer.setReceiveBufferSize(size)
    this
  }

  NetServer setTCPKeepAlive(boolean keepAlive) {
    jServer.setTCPKeepAlive(keepAlive)
  }

  NetServer setReuseAddress(boolean reuse) {
    jServer.setReuseAddress(reuse)
    this
  }

  NetServer setSoLinger(boolean linger) {
    jServer.setSoLinger(linger)
    this
  }

  NetServer setTrafficClass(int trafficClass) {
    jServer.setTrafficClass(trafficClass)
    this
  }

  Boolean isTCPNoDelay() {
    jServer.isTCPNoDelay()
  }

  Integer getSendBufferSize() {
    jServer.getSendBufferSize()
  }

  Integer getReceiveBufferSize() {
    jServer.getReceiveBufferSize()
  }

  Boolean isTCPKeepAlive() {
    return jServer.isTCPKeepAlive()
  }

  Boolean isReuseAddress() {
    jServer.isReuseAddress()
  }

  Boolean isSoLinger() {
    jServer.isSoLinger()
  }

  Integer getTrafficClass() {
    jServer.getTrafficClass()
  }

  boolean isSSL() {
    jServer.isSSL()
  }

  String getKeyStorePath() {
    jServer.getKeyStorePath()
  }

  String getKeyStorePassword() {
    jServer.getKeyStorePassword()
  }

  String getTrustStorePath() {
     jServer.getTrustStorePath()
  }

  String getTrustStorePassword() {
    jServer.getTrustStorePassword()
  }
  

}
