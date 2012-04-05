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
 * An HTTP and WebSockets server
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
 * author Peter Ledbrook
 * author <a href="http://tfox.org">Tim Fox</a>
 */
abstract class HttpServer {

  private reqHandler;
  private wsHandler;
  
  protected org.vertx.java.core.http.HttpServer jServer;

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
    return reqHandler;
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
    wsHandler;
  }

  /**
   * Close the server. Any open HTTP connections will be closed. {code hndlr} will be called when the close
   * is complete.
   */
  void close(Closure hndlr) {
    jServer.close(hndlr as Handler)
  }

  private wrapRequestHandler(Closure hndlr) {
    return {hndlr(new HttpServerRequest(it))} as Handler
  }

  private wrapWebsocketHandler(Closure hndlr) {
    return {hndlr(new ServerWebSocket(it))} as Handler
  }
  
  HttpServer listen(int port) {
    jServer.listen(port)
    this
  }
  
  HttpServer listen(int port, String host) {
    jServer.listen(port, host)
    this
  }

  void close() {
    jServer.close()
  }

  HttpServer setSSL(boolean ssl) {
    jServer.setSSL(ssl)
    this
  }

  HttpServer setKeyStorePath(String path) {
    jServer.setKeyStorePath(path)
    this
  }

  HttpServer setKeyStorePassword(String pwd) {
    jServer.setKeyStorePassword(pwd)
    this
  }

  HttpServer setTrustStorePath(String path) {
    jServer.setTrustStorePath(path)
    this
  }

  HttpServer setTrustStorePassword(String pwd) {
    jServer.setTrustStorePassword(pwd)
    this
  }

  HttpServer setClientAuthRequired(boolean required) {
    jServer.setClientAuthRequired(required)
    this
  }

  HttpServer setTCPNoDelay(boolean tcpNoDelay) {
    jServer.setTCPNoDelay(tcpNoDelay)
    this
  }

  HttpServer setSendBufferSize(int size) {
    jServer.setSendBufferSize(size)
    this
  }

  HttpServer setReceiveBufferSize(int size) {
    jServer.setReceiveBufferSize(size)
    this
  }

  HttpServer setTCPKeepAlive(boolean keepAlive) {
    jServer.setTCPKeepAlive(keepAlive)
  }

  HttpServer setReuseAddress(boolean reuse) {
    jServer.setReuseAddress(reuse)
    this
  }

  HttpServer setSoLinger(boolean linger) {
    jServer.setSoLinger(linger)
    this
  }

  HttpServer setTrafficClass(int trafficClass) {
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

  org.vertx.java.core.http.HttpServer toJavaServer() {
    jServer
  }

}
