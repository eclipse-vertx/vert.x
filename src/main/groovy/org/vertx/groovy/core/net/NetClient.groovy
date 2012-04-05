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
 * NetClient is an asynchronous factory for TCP or SSL connections
 * <p>
 * Multiple connections to different servers can be made using the same instance.
 * <p>
 * This client supports a configurable number of connection attempts and a configurable
 * delay between attempts.
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
abstract class NetClient {
  
  protected org.vertx.java.core.net.NetClient jClient;

  /**
   * Attempt to open a connection to a server at the specific {@code port} and host localhost
   * The connect is done asynchronously and on success, a
   * {@link NetSocket} instance is supplied via the {@code hndlr} instance
   * @return a reference to this so multiple method calls can be chained together
   */
  NetClient connect(int port, hndlr) {
    jClient.connect(port, wrapHandler(hndlr))
    this
  }

  /**
   * Attempt to open a connection to a server at the specific {@code port} and {@code host}.
   * {@code host} can be a valid host name or IP addresss. The connect is done asynchronously and on success, a
   * {@link NetSocket} instance is supplied via the {@code hndlr} instance
   * @return a reference to this so multiple method calls can be chained together
   */
  NetClient connect(int port, String host, Closure hndlr) {
    jClient.connect(port, host, wrapHandler(hndlr))
    this
  }

  private Handler wrapHandler(Closure hndlr) {
    return {hndlr(new NetSocket(it))} as Handler
  }
  
  void close() {
    jClient.close()  
  }
  
  NetClient setReconnectAttempts(int attempts) {
    jClient.setReconnectAttempts(attempts)
    this  
  }
  
  int getReconnectAttempts() {
    return jClient.getReconnectAttempts()  
  }
  
  NetClient setReconnectInterval(long interval) {
    jClient.setReconnectInterval(interval)
    this
  }
  
  long getReconnectInterval() {
    return jClient.getReconnectInterval() 
  }
  
  NetClient setSSL(boolean ssl) {
    jClient.setSSL(ssl)
    this
  }

  NetClient setKeyStorePath(String path) {
    jClient.setKeyStorePath(path)
    this
  }

  NetClient setKeyStorePassword(String pwd) {
    jClient.setKeyStorePassword(pwd)
    this
  }

  NetClient setTrustStorePath(String path) {
    jClient.setTrustStorePath(path)
    this
  }

  NetClient setTrustStorePassword(String pwd) {
    jClient.setTrustStorePassword(pwd)
    this
  }

  NetClient setClientAuthRequired(boolean required) {
    jClient.setClientAuthRequired(required)
    this
  }

  NetClient setTCPNoDelay(boolean tcpNoDelay) {
    jClient.setTCPNoDelay(tcpNoDelay)
    this
  }

  NetClient setSendBufferSize(int size) {
    jClient.setSendBufferSize(size)
    this
  }

  NetClient setReceiveBufferSize(int size) {
    jClient.setReceiveBufferSize(size)
    this
  }

  NetClient setTCPKeepAlive(boolean keepAlive) {
    jClient.setTCPKeepAlive(keepAlive)
  }

  NetClient setReuseAddress(boolean reuse) {
    jClient.setReuseAddress(reuse)
    this
  }

  NetClient setSoLinger(boolean linger) {
    jClient.setSoLinger(linger)
    this
  }

  NetClient setTrafficClass(int trafficClass) {
    jClient.setTrafficClass(trafficClass)
    this
  }

  Boolean isTCPNoDelay() {
    jClient.isTCPNoDelay()
  }

  Integer getSendBufferSize() {
    jClient.getSendBufferSize()
  }

  Integer getReceiveBufferSize() {
    jClient.getReceiveBufferSize()
  }

  Boolean isTCPKeepAlive() {
    return jClient.isTCPKeepAlive()
  }

  Boolean isReuseAddress() {
    jClient.isReuseAddress()
  }

  Boolean isSoLinger() {
    jClient.isSoLinger()
  }

  Integer getTrafficClass() {
    jClient.getTrafficClass()
  }

  boolean isSSL() {
    jClient.isSSL()
  }

  String getKeyStorePath() {
    jClient.getKeyStorePath()
  }

  String getKeyStorePassword() {
    jClient.getKeyStorePassword()
  }

  String getTrustStorePath() {
     jClient.getTrustStorePath()
  }

  String getTrustStorePassword() {
    jClient.getTrustStorePassword()
  }

}
