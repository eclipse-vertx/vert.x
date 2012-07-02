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

/**
 * An HTTP and WebSockets server<p>
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
abstract class HttpServer {

  private reqHandler
  private wsHandler
  
  protected org.vertx.java.core.http.HttpServer jServer

  /**
   * Set the request handler for the server to {code requestHandler}. As HTTP requests are received by the server,
   * instances of {link HttpServerRequest} will be created and passed to this handler.
   *
   * return a reference to this, so methods can be chained.
   */
  HttpServer requestHandler(Closure hndlr) {
    jServer.requestHandler(wrapRequestHandler(hndlr))
    this.reqHandler = hndlr
    this
  }

  /**
   * Get the request handler
   * return The request handler
   */
  Closure getRequestHandler() {
    return reqHandler
  }

  /**
   * Set the websocket handler for the server to {code wsHandler}. If a websocket connect handshake is successful a
   * new {link WebSocket} instance will be created and passed to the handler.
   *
   * return a reference to this, so methods can be chained.
   */
  HttpServer websocketHandler(Closure hndlr) {
    jServer.websocketHandler(wrapWebsocketHandler(hndlr))
    this.wsHandler = hndlr
    this
  }

  /**
   * Get the websocket handler
   * return The websocket handler
   */
  Closure getWebsocketHandler() {
    wsHandler
  }

  /**
   * Tell the server to start listening on all available interfaces and port {@code port}
   *
   * @return a reference to this, so methods can be chained.
   */
  HttpServer listen(int port) {
    jServer.listen(port)
    this
  }

  /**
   * Tell the server to start listening on port {@code port} and hostname or ip address given by {@code host}.
   *
   * @return a reference to this, so methods can be chained.
   */
  HttpServer listen(int port, String host) {
    jServer.listen(port, host)
    this
  }


  /**
   * Close the server. Any open HTTP connections will be closed. The {code hndlr} will be called when the close
   * is complete.
   */
  void close(Closure hndlr) {
    jServer.close(hndlr as Handler)
  }

  /**
   * Close the server. Any open HTTP connections will be closed.
   */
  void close() {
    jServer.close()
  }

  /**
   * If {@code ssl} is {@code true}, this signifies that any connections will be SSL connections.
   * @return A reference to this, so multiple invocations can be chained together.
   */
  HttpServer setSSL(boolean ssl) {
    jServer.setSSL(ssl)
    this
  }

  /**
   * Set the path to the SSL key store. This method should only be used in SSL mode, i.e. after {@link #setSSL(boolean)}
   * has been set to {@code true}.<p>
   * The SSL key store is a standard Java Key Store, and should contain the server certificate.
   * @return A reference to this, so multiple invocations can be chained together.
   */
  HttpServer setKeyStorePath(String path) {
    jServer.setKeyStorePath(path)
    this
  }

  /**
   * Set the password for the SSL key store. This method should only be used in SSL mode, i.e. after {@link #setSSL(boolean)}
   * has been set to {@code true}.<p>
   * @return A reference to this, so multiple invocations can be chained together.
   */
  HttpServer setKeyStorePassword(String pwd) {
    jServer.setKeyStorePassword(pwd)
    this
  }

  /**
   * Set the path to the SSL trust store. This method should only be used in SSL mode, i.e. after {@link #setSSL(boolean)}
   * has been set to {@code true}.<p>
   * The trust store is a standard Java Key Store, and should contain the certificates of
   * any clients that the server trusts - this is only necessary if client authentication is enabled.
   * @return A reference to this, so multiple invocations can be chained together.
   */
  HttpServer setTrustStorePath(String path) {
    jServer.setTrustStorePath(path)
    this
  }

  /**
   * Set the password for the SSL trust store. This method should only be used in SSL mode, i.e. after {@link #setSSL(boolean)}
   * has been set to {@code true}.<p>
   * @return A reference to this, so multiple invocations can be chained together.
   */
  HttpServer setTrustStorePassword(String pwd) {
    jServer.setTrustStorePassword(pwd)
    this
  }

  /**
   * Set {@code required} to true if you want the server to request client authentication from any connecting clients. This
   * is an extra level of security in SSL, and requires clients to provide client certificates. Those certificates must be added
   * to the server trust store.
   * @return A reference to this, so multiple invocations can be chained together.
   */
  HttpServer setClientAuthRequired(boolean required) {
    jServer.setClientAuthRequired(required)
    this
  }

  /**
   * If {@code tcpNoDelay} is set to {@code true} then <a href="http://en.wikipedia.org/wiki/Nagle's_algorithm">Nagle's algorithm</a>
   * will turned <b>off</b> for the TCP connections created by this instance.
   * @return a reference to this so multiple method calls can be chained together
   */
  HttpServer setTCPNoDelay(boolean tcpNoDelay) {
    jServer.setTCPNoDelay(tcpNoDelay)
    this
  }

  /**
   * Set the TCP send buffer size for connections created by this instance to {@code size} in bytes.
   * @return a reference to this so multiple method calls can be chained together
   */
  HttpServer setSendBufferSize(int size) {
    jServer.setSendBufferSize(size)
    this
  }

  /**
   * Set the TCP receive buffer size for connections created by this instance to {@code size} in bytes.
   * @return a reference to this so multiple method calls can be chained together
   */
  HttpServer setReceiveBufferSize(int size) {
    jServer.setReceiveBufferSize(size)
    this
  }

  /**
   * Set the TCP keepAlive setting for connections created by this instance to {@code keepAlive}.
   * @return a reference to this so multiple method calls can be chained together
   */
  HttpServer setTCPKeepAlive(boolean keepAlive) {
    jServer.setTCPKeepAlive(keepAlive)
  }

  /**
   * Set the TCP reuseAddress setting for connections created by this instance to {@code reuse}.
   * @return a reference to this so multiple method calls can be chained together
   */
  HttpServer setReuseAddress(boolean reuse) {
    jServer.setReuseAddress(reuse)
    this
  }

  /**
   * Set the TCP soLinger setting for connections created by this instance to {@code linger}.
   * @return a reference to this so multiple method calls can be chained together
   */
  HttpServer setSoLinger(boolean linger) {
    jServer.setSoLinger(linger)
    this
  }

  /**
   * Set the TCP trafficClass setting for connections created by this instance to {@code trafficClass}.
   * @return a reference to this so multiple method calls can be chained together
   */
  HttpServer setTrafficClass(int trafficClass) {
    jServer.setTrafficClass(trafficClass)
    this
  }

  /**
   * Set the accept backlog
   * @return a reference to this so multiple method calls can be chained together
   */
  HttpServer setAcceptBacklog(int backlog) {
    jServer.setAcceptBacklog(backlog)
    this
  }

  /**
   * @return true if Nagle's algorithm is disabled.
   */
  Boolean isTCPNoDelay() {
    jServer.isTCPNoDelay()
  }

  /**
   * @return The TCP send buffer size
   */
  Integer getSendBufferSize() {
    jServer.getSendBufferSize()
  }

  /**
   * @return The TCP receive buffer size
   */
  Integer getReceiveBufferSize() {
    jServer.getReceiveBufferSize()
  }

  /**
   *
   * @return true if TCP keep alive is enabled
   */
  Boolean isTCPKeepAlive() {
    return jServer.isTCPKeepAlive()
  }

  /**
   *
   * @return The value of TCP reuse address
   */
  Boolean isReuseAddress() {
    jServer.isReuseAddress()
  }

  /**
   *
   * @return the value of TCP so linger
   */
  Boolean isSoLinger() {
    jServer.isSoLinger()
  }

  /**
   *
   * @return the value of TCP traffic class
   */
  Integer getTrafficClass() {
    jServer.getTrafficClass()
  }

  /**
   *
   * @return The accept backlog
   */
  Integer getAcceptBacklog() {
    jServer.getAcceptBacklog()
  }

  /**
   *
   * @return true if this server will make SSL connections
   */
  boolean isSSL() {
    jServer.isSSL()
  }

  /**
   *
   * @return The path to the key store
   */
  String getKeyStorePath() {
    jServer.getKeyStorePath()
  }

  /**
   *
   * @return The keystore password
   */
  String getKeyStorePassword() {
    jServer.getKeyStorePassword()
  }

  /**
   *
   * @return The trust store path
   */
  String getTrustStorePath() {
     jServer.getTrustStorePath()
  }

  /**
   *
   * @return The trust store password
   */
  String getTrustStorePassword() {
    jServer.getTrustStorePassword()
  }

  org.vertx.java.core.http.HttpServer toJavaServer() {
    jServer
  }

  private wrapRequestHandler(Closure hndlr) {
    return {hndlr(new HttpServerRequest(it))} as Handler
  }

  private wrapWebsocketHandler(Closure hndlr) {
    return {hndlr(new ServerWebSocket(it))} as Handler
  }


}
