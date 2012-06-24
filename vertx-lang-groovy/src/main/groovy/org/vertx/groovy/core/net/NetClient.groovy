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
 * A TCP/SSL client.<p>
 * Multiple connections to different servers can be made using the same instance.<p>
 * This client supports a configurable number of connection attempts and a configurable
 * delay between attempts.<p>
 * If an instance is instantiated from an event loop then the handlers
 * of the instance will always be called on that same event loop.
 * If an instance is instantiated from some other arbitrary Java thread then
 * an event loop will be assigned to the instance and used when any of its handlers
 * are called.<p>
 * Instances cannot be used from worker verticles
 *
 * @author Peter Ledbrook
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
abstract class NetClient {
  
  protected org.vertx.java.core.net.NetClient jClient

  /**
   * Attempt to open a connection to a server at the specific {@code port} and host {@code localhost}
   * The connect is done asynchronously and on success, a
   * {@link NetSocket} instance is supplied via the {@code connectHandler} instance
   * @return a reference to this so multiple method calls can be chained together
   */
  NetClient connect(int port, hndlr) {
    jClient.connect(port, wrapHandler(hndlr))
    this
  }

  /**
   * Attempt to open a connection to a server at the specific {@code port} and {@code host}.
   * {@code host} can be a valid host name or IP addresss. The connect is done asynchronously and on success, a
   * {@link NetSocket} instance is supplied via the {@code connectHandler} instance
   * @return a reference to this so multiple method calls can be chained together
   */
  NetClient connect(int port, String host, Closure hndlr) {
    jClient.connect(port, host, wrapHandler(hndlr))
    this
  }

  /**
   * Close the client. Any sockets which have not been closed manually will be closed here.
   */
  void close() {
    jClient.close()  
  }

  /**
   * Set the number of reconnection attempts. In the event a connection attempt fails, the client will attempt
   * to connect a further number of times, before it fails. Default value is zero.
   */
  NetClient setReconnectAttempts(int attempts) {
    jClient.setReconnectAttempts(attempts)
    this  
  }

  /**
   * Get the number of reconnect attempts
   */
  int getReconnectAttempts() {
    return jClient.getReconnectAttempts()  
  }

  /**
   * Set the reconnect interval, in milliseconds
   */
  NetClient setReconnectInterval(long interval) {
    jClient.setReconnectInterval(interval)
    this
  }

  /**
   * Get the reconnect interval, in milliseconds.
   */
  long getReconnectInterval() {
    return jClient.getReconnectInterval() 
  }

  /**
   * If {@code ssl} is {@code true}, this signifies that any connections will be SSL connections.
   * @return A reference to this, so multiple invocations can be chained together.
   */
  NetClient setSSL(boolean ssl) {
    jClient.setSSL(ssl)
    this
  }

  /**
   * Set the path to the SSL key store. This method should only be used in SSL mode, i.e. after {@link #setSSL(boolean)}
   * has been set to {@code true}.<p>
   * The SSL key store is a standard Java Key Store, and will contain the client certificate. Client certificates are
   * only required if the server requests client authentication.<p>
   * @return A reference to this, so multiple invocations can be chained together.
   */
  NetClient setKeyStorePath(String path) {
    jClient.setKeyStorePath(path)
    this
  }

  /**
   * Set the password for the SSL key store. This method should only be used in SSL mode, i.e. after {@link #setSSL(boolean)}
   * has been set to {@code true}.<p>
   * @return A reference to this, so multiple invocations can be chained together.
   */
  NetClient setKeyStorePassword(String pwd) {
    jClient.setKeyStorePassword(pwd)
    this
  }

  /**
   * Set the path to the SSL trust store. This method should only be used in SSL mode, i.e. after {@link #setSSL(boolean)}
   * has been set to {@code true}.<p>
   * The trust store is a standard Java Key Store, and should contain the certificates of any servers that the client trusts.
   * If you wish the client to trust all server certificates you can use the {@link #setTrustAll(boolean)} method.<p>
   * @return A reference to this, so multiple invocations can be chained together.
   */
  NetClient setTrustStorePath(String path) {
    jClient.setTrustStorePath(path)
    this
  }

  /**
   * Set the password for the SSL trust store. This method should only be used in SSL mode, i.e. after {@link #setSSL(boolean)}
   * has been set to {@code true}.<p>
   * @return A reference to this, so multiple invocations can be chained together.
   */
  NetClient setTrustStorePassword(String pwd) {
    jClient.setTrustStorePassword(pwd)
    this
  }

  /**
   * If you want an SSL client to trust *all* server certificates rather than match them
   * against those in its trust store, you can set this to true.<p>
   * Use this with caution as you may be exposed to "main in the middle" attacks
   * @param trustAll Set to true if you want to trust all server certificates
   */
  NetClient setTrustAll(boolean trustAll) {
    jClient.setTrustAll(trustAll)
    this
  }

  /**
   * If {@code tcpNoDelay} is set to {@code true} then <a href="http://en.wikipedia.org/wiki/Nagle's_algorithm">Nagle's algorithm</a>
   * will turned <b>off</b> for the TCP connections created by this instance.
   * @return a reference to this so multiple method calls can be chained together
   */
  NetClient setTCPNoDelay(boolean tcpNoDelay) {
    jClient.setTCPNoDelay(tcpNoDelay)
    this
  }

  /**
   * Set the TCP send buffer size for connections created by this instance to {@code size} in bytes.
   * @return a reference to this so multiple method calls can be chained together
   */
  NetClient setSendBufferSize(int size) {
    jClient.setSendBufferSize(size)
    this
  }

  /**
   * Set the TCP receive buffer size for connections created by this instance to {@code size} in bytes.
   * @return a reference to this so multiple method calls can be chained together
   */
  NetClient setReceiveBufferSize(int size) {
    jClient.setReceiveBufferSize(size)
    this
  }

  /**
   * Set the TCP keepAlive setting for connections created by this instance to {@code keepAlive}.
   * @return a reference to this so multiple method calls can be chained together
   */
  NetClient setTCPKeepAlive(boolean keepAlive) {
    jClient.setTCPKeepAlive(keepAlive)
  }

  /**
   * Set the TCP reuseAddress setting for connections created by this instance to {@code reuse}.
   * @return a reference to this so multiple method calls can be chained together
   */
  NetClient setReuseAddress(boolean reuse) {
    jClient.setReuseAddress(reuse)
    this
  }

  /**
   * Set the TCP soLinger setting for connections created by this instance to {@code linger}.
   * @return a reference to this so multiple method calls can be chained together
   */
  NetClient setSoLinger(boolean linger) {
    jClient.setSoLinger(linger)
    this
  }

  /**
   * Set the TCP trafficClass setting for connections created by this instance to {@code trafficClass}.
   * @return a reference to this so multiple method calls can be chained together
   */
  NetClient setTrafficClass(int trafficClass) {
    jClient.setTrafficClass(trafficClass)
    this
  }

  /**
   * Set the connect timeout in milliseconds
   * @return a reference to this so multiple method calls can be chained together
   */
  NetClient setConnectTimeout(long timeout) {
    jClient.setConnectTimeout(timeout)
    this
  }

  /**
   * Set the number of boss threads to use. Boss threads are used to make connections.
   * @return a reference to this so multiple method calls can be chained together
   */
  NetClient setBossThreads(long threads) {
    jClient.setBossThreads(threads)
    this
  }

  /**
   * @return true if Nagle's algorithm is disabled.
   */
  Boolean isTCPNoDelay() {
    jClient.isTCPNoDelay()
  }

  /**
   * @return The TCP send buffer size
   */
  Integer getSendBufferSize() {
    jClient.getSendBufferSize()
  }

  /**
   * @return The TCP receive buffer size
   */
  Integer getReceiveBufferSize() {
    jClient.getReceiveBufferSize()
  }

  /**
   *
   * @return true if TCP keep alive is enabled
   */
  Boolean isTCPKeepAlive() {
    return jClient.isTCPKeepAlive()
  }

  /**
   *
   * @return The value of TCP reuse address
   */
  Boolean isReuseAddress() {
    jClient.isReuseAddress()
  }

  /**
   *
   * @return the value of TCP so linger
   */
  Boolean isSoLinger() {
    jClient.isSoLinger()
  }

  /**
   *
   * @return the value of TCP traffic class
   */
  Integer getTrafficClass() {
    jClient.getTrafficClass()
  }

  /**
   *
   * @return The connect timeout
   */
  Long getConnectTimeout() {
    jClient.getConnectTimeout()
  }

  /**
   *
   * @return The number of boss threads
   */
  Integer getBossThreads() {
    jClient.getBossThreads();
  }

  /**
   *
   * @return true if this client will make SSL connections
   */
  boolean isSSL() {
    jClient.isSSL()
  }

  /**
   *
   * @return The path to the key store
   */
  String getKeyStorePath() {
    jClient.getKeyStorePath()
  }

  /**
   *
   * @return The keystore password
   */
  String getKeyStorePassword() {
    jClient.getKeyStorePassword()
  }

  /**
   *
   * @return The trust store path
   */
  String getTrustStorePath() {
     jClient.getTrustStorePath()
  }

  /**
   *
   * @return The trust store password
   */
  String getTrustStorePassword() {
    jClient.getTrustStorePassword()
  }

  private Handler wrapHandler(Closure hndlr) {
    return {hndlr(new NetSocket(it))} as Handler
  }


}
