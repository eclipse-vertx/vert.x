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

package org.vertx.java.core.http;

import org.vertx.java.core.Handler;
import org.vertx.java.core.http.impl.HttpServerImpl;
import org.vertx.java.core.logging.Logger;
import org.vertx.java.core.logging.impl.LoggerFactory;

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
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class HttpServer {

  private static final Logger log = LoggerFactory.getLogger(HttpServer.class);

  private final HttpServerImpl server = new HttpServerImpl();
  
  /**
   * Create an {@code HttpServer}
   */
  public HttpServer() {   
  }

  /**
   * Set the request handler for the server to {@code requestHandler}. As HTTP requests are received by the server,
   * instances of {@link HttpServerRequest} will be created and passed to this handler.
   *
   * @return a reference to this, so methods can be chained.
   */
  public synchronized HttpServer requestHandler(Handler<HttpServerRequest> requestHandler) {
    server.requestHandler(requestHandler);
    return this;
  }

  /**
   * Get the request handler
   * @return The request handler
   */
  public synchronized Handler<HttpServerRequest> requestHandler() {
    return server.requestHandler();
  }

  /**
   * Set the websocket handler for the server to {@code wsHandler}. If a websocket connect handshake is successful a
   * new {@link WebSocket} instance will be created and passed to the handler.
   *
   * @return a reference to this, so methods can be chained.
   */
  public synchronized HttpServer websocketHandler(Handler<ServerWebSocket> wsHandler) {
    server.websocketHandler(wsHandler);
    return this;
  }

  /**
   * Get the websocket handler
   * @return The websocket handler
   */
  public synchronized Handler<ServerWebSocket> websocketHandler() {
    return server.websocketHandler();
  }

  /**
   * Tell the server to start listening on all interfaces and port {@code port}
   *
   * @return a reference to this, so methods can be chained.
   */
  public synchronized HttpServer listen(int port) {
    server.listen(port);
    return this;
  }

  /**
   * Tell the server to start listening on port {@code port} and host / ip address given by {@code host}.
   *
   * @return a reference to this, so methods can be chained.
   */
  public synchronized HttpServer listen(int port, String host) {
    server.listen(port, host); 
    return this;
  }
  
  /**
   * Close the server. Any open HTTP connections will be closed.
   */
  public synchronized void close() {
    server.close();
  }

  /**
   * Close the server. Any open HTTP connections will be closed. {@code doneHandler} will be called when the close
   * is complete.
   */
  public synchronized void close(final Handler<Void> done) {
    server.close(done);
  }
  
  /**
   * If {@code ssl} is {@code true}, this signifies that any connections will be SSL connections.
   * @return A reference to this, so multiple invocations can be chained together.
   */
  public synchronized HttpServer setSSL(boolean ssl) {
    server.setSSL(ssl);
    return this;
  }

  /**
   * Set the path to the SSL key store. This method should only be used in SSL mode, i.e. after {@link #setSSL(boolean)}
   * has been set to {@code true}.<p>
   * The SSL key store is a standard Java Key Store, and, if on the tcpHelper side will contain the tcpHelper certificate.
   * @return A reference to this, so multiple invocations can be chained together.
   */
  public synchronized HttpServer setKeyStorePath(String path) {
    server.setKeyStorePath(path);
    return this;
  }

  /**
   * Set the password for the SSL key store. This method should only be used in SSL mode, i.e. after {@link #setSSL(boolean)}
   * has been set to {@code true}.<p>
   * @return A reference to this, so multiple invocations can be chained together.
   */
  public synchronized HttpServer setKeyStorePassword(String pwd) {
    server.setKeyStorePassword(pwd);
    return this;
  }

  /**
   * Set the path to the SSL trust store. This method should only be used in SSL mode, i.e. after {@link #setSSL(boolean)}
   * has been set to {@code true}.<p>
   * The trust store is a standard Java Key Store, and should contain the certificates of
   * any clients that the server trusts - this is only necessary if client authentication is enabled.
   * @return A reference to this, so multiple invocations can be chained together.
   */
  public synchronized HttpServer setTrustStorePath(String path) {
    server.setTrustStorePath(path);
    return this;
  }

  /**
   * Set the password for the SSL trust store. This method should only be used in SSL mode, i.e. after {@link #setSSL(boolean)}
   * has been set to {@code true}.<p>
   * @return A reference to this, so multiple invocations can be chained together.
   */
  public synchronized HttpServer setTrustStorePassword(String pwd) {
    server.setTrustStorePassword(pwd);
    return this;
  }

  /**
   * Set {@code required} to true if you want the server to request client authentication from any connecting clients. This
   * is an extra level of security in SSL, and requires clients to provide client certificates. Those certificates must be added
   * to the server trust store.
   * @return A reference to this, so multiple invocations can be chained together.
   */
  public synchronized HttpServer setClientAuthRequired(boolean required) {
    server.setClientAuthRequired(required);
    return this;
  }

  /**
   * If {@code tcpNoDelay} is set to {@code true} then <a href="http://en.wikipedia.org/wiki/Nagle's_algorithm">Nagle's algorithm</a>
   * will turned <b>off</b> for the TCP connections created by this instance.
   * @return a reference to this so multiple method calls can be chained together
   */
  public synchronized HttpServer setTCPNoDelay(boolean tcpNoDelay) {
    server.setTCPNoDelay(tcpNoDelay);
    return this;
  }

  /**
   * Set the TCP send buffer size for connections created by this instance to {@code size} in bytes.
   * @return a reference to this so multiple method calls can be chained together
   */
  public synchronized HttpServer setSendBufferSize(int size) {
    server.setSendBufferSize(size);
    return this;
  }

  /**
   * Set the TCP receive buffer size for connections created by this instance to {@code size} in bytes.
   * @return a reference to this so multiple method calls can be chained together
   */
  public synchronized HttpServer setReceiveBufferSize(int size) {
    server.setReceiveBufferSize(size);
    return this;
  }

  /**
   * Set the TCP keepAlive setting for connections created by this instance to {@code keepAlive}.
   * @return a reference to this so multiple method calls can be chained together
   */
  public synchronized HttpServer setTCPKeepAlive(boolean keepAlive) {
    server.setTCPKeepAlive(keepAlive);
    return this;
  }

  /**
   * Set the TCP reuseAddress setting for connections created by this instance to {@code reuse}.
   * @return a reference to this so multiple method calls can be chained together
   */
  public synchronized HttpServer setReuseAddress(boolean reuse) {
    server.setReuseAddress(reuse);
    return this;
  }

  /**
   * Set the TCP soLinger setting for connections created by this instance to {@code reuse}.
   * @return a reference to this so multiple method calls can be chained together
   */
  public synchronized HttpServer setSoLinger(boolean linger) {
    server.setSoLinger(linger);
    return this;
  }

  /**
   * Set the TCP trafficClass setting for connections created by this instance to {@code reuse}.
   * @return a reference to this so multiple method calls can be chained together
   */
  public synchronized HttpServer setTrafficClass(int trafficClass) {
    server.setTrafficClass(trafficClass);
    return this;
  }

  /**
   * @return true if Nagle's algorithm is disabled.
   */
  public synchronized Boolean isTCPNoDelay() {
    return server.isTCPNoDelay();
  }

  /**
   * @return The TCP send buffer size
   */
  public synchronized Integer getSendBufferSize() {
    return server.getSendBufferSize();
  }

  /**
   * @return The TCP receive buffer size
   */
  public synchronized Integer getReceiveBufferSize() {
    return server.getReceiveBufferSize();
  }

  /**
   *
   * @return true if TCP keep alive is enabled
   */
  public synchronized Boolean isTCPKeepAlive() {
    return server.isTCPKeepAlive();
  }

  /**
   *
   * @return The value of TCP reuse address
   */
  public synchronized Boolean isReuseAddress() {
    return server.isReuseAddress();
  }

  /**
   *
   * @return the value of TCP so linger
   */
  public synchronized Boolean isSoLinger() {
    return server.isSoLinger();
  }

  /**
   *
   * @return the value of TCP traffic class
   */
  public synchronized Integer getTrafficClass() {
    return server.getTrafficClass();
  }

  /**
   *
   * @return true if this server will make SSL connections
   */
  public synchronized boolean isSSL() {
    return server.isSSL();
  }

  /**
   *
   * @return The path to the key store
   */
  public synchronized String getKeyStorePath() {
    return server.getKeyStorePath();
  }

  /**
   *
   * @return The keystore password
   */
  public synchronized String getKeyStorePassword() {
    return server.getKeyStorePassword();
  }

  /**
   *
   * @return The trust store path
   */
  public synchronized String getTrustStorePath() {
    return server.getTrustStorePath();
  }

  /**
   *
   * @return The trust store password
   */
  public synchronized String getTrustStorePassword() {
    return server.getTrustStorePassword();
  }
}
