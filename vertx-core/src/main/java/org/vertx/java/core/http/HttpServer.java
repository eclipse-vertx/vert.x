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

/**
 * An HTTP and WebSockets server<p>
 * If an instance is instantiated from an event loop then the handlers
 * of the instance will always be called on that same event loop.
 * If an instance is instantiated from some other arbitrary Java thread then
 * an event loop will be assigned to the instance and used when any of its handlers
 * are called.<p>
 * Instances cannot be used from worker verticles
 *
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public interface HttpServer {

  /**
   * Set the request handler for the server to {@code requestHandler}. As HTTP requests are received by the server,
   * instances of {@link HttpServerRequest} will be created and passed to this handler.
   *
   * @return a reference to this, so methods can be chained.
   */
  HttpServer requestHandler(Handler<HttpServerRequest> requestHandler);

  /**
   * Get the request handler
   * @return The request handler
   */
  Handler<HttpServerRequest> requestHandler();

  /**
   * Set the websocket handler for the server to {@code wsHandler}. If a websocket connect handshake is successful a
   * new {@link WebSocket} instance will be created and passed to the handler.
   *
   * @return a reference to this, so methods can be chained.
   */
  HttpServer websocketHandler(Handler<ServerWebSocket> wsHandler);

  /**
   * Get the websocket handler
   * @return The websocket handler
   */
  Handler<ServerWebSocket> websocketHandler();

  /**
   * Tell the server to start listening on all available interfaces and port {@code port}
   *
   * @return a reference to this, so methods can be chained.
   */
  HttpServer listen(int port);

  /**
   * Tell the server to start listening on port {@code port} and hostname or ip address given by {@code host}.
   *
   * @return a reference to this, so methods can be chained.
   */
  HttpServer listen(int port, String host);
  
  /**
   * Close the server. Any open HTTP connections will be closed.
   */
  void close();

  /**
   * Close the server. Any open HTTP connections will be closed. The {@code doneHandler} will be called when the close
   * is complete.
   */
  void close(final Handler<Void> doneHandler);
  
  /**
   * If {@code ssl} is {@code true}, this signifies that any connections will be SSL connections.
   * @return A reference to this, so multiple invocations can be chained together.
   */
  HttpServer setSSL(boolean ssl);

  /**
   * Set the path to the SSL key store. This method should only be used in SSL mode, i.e. after {@link #setSSL(boolean)}
   * has been set to {@code true}.<p>
   * The SSL key store is a standard Java Key Store, and should contain the server certificate.
   * @return A reference to this, so multiple invocations can be chained together.
   */
  HttpServer setKeyStorePath(String path);

  /**
   * Set the password for the SSL key store. This method should only be used in SSL mode, i.e. after {@link #setSSL(boolean)}
   * has been set to {@code true}.<p>
   * @return A reference to this, so multiple invocations can be chained together.
   */
  HttpServer setKeyStorePassword(String pwd);

  /**
   * Set the path to the SSL trust store. This method should only be used in SSL mode, i.e. after {@link #setSSL(boolean)}
   * has been set to {@code true}.<p>
   * The trust store is a standard Java Key Store, and should contain the certificates of
   * any clients that the server trusts - this is only necessary if client authentication is enabled.
   * @return A reference to this, so multiple invocations can be chained together.
   */
  HttpServer setTrustStorePath(String path);

  /**
   * Set the password for the SSL trust store. This method should only be used in SSL mode, i.e. after {@link #setSSL(boolean)}
   * has been set to {@code true}.<p>
   * @return A reference to this, so multiple invocations can be chained together.
   */
  HttpServer setTrustStorePassword(String pwd);

  /**
   * Set {@code required} to true if you want the server to request client authentication from any connecting clients. This
   * is an extra level of security in SSL, and requires clients to provide client certificates. Those certificates must be added
   * to the server trust store.
   * @return A reference to this, so multiple invocations can be chained together.
   */
  HttpServer setClientAuthRequired(boolean required);

  /**
   * If {@code tcpNoDelay} is set to {@code true} then <a href="http://en.wikipedia.org/wiki/Nagle's_algorithm">Nagle's algorithm</a>
   * will turned <b>off</b> for the TCP connections created by this instance.
   * @return a reference to this so multiple method calls can be chained together
   */
  HttpServer setTCPNoDelay(boolean tcpNoDelay);

  /**
   * Set the TCP send buffer size for connections created by this instance to {@code size} in bytes.
   * @return a reference to this so multiple method calls can be chained together
   */
  HttpServer setSendBufferSize(int size);

  /**
   * Set the TCP receive buffer size for connections created by this instance to {@code size} in bytes.
   * @return a reference to this so multiple method calls can be chained together
   */
  HttpServer setReceiveBufferSize(int size);

  /**
   * Set the TCP keepAlive setting for connections created by this instance to {@code keepAlive}.
   * @return a reference to this so multiple method calls can be chained together
   */
  HttpServer setTCPKeepAlive(boolean keepAlive);

  /**
   * Set the TCP reuseAddress setting for connections created by this instance to {@code reuse}.
   * @return a reference to this so multiple method calls can be chained together
   */
  HttpServer setReuseAddress(boolean reuse);

  /**
   * Set the TCP soLinger setting for connections created by this instance to {@code linger}.
   * @return a reference to this so multiple method calls can be chained together
   */
  HttpServer setSoLinger(boolean linger);

  /**
   * Set the TCP trafficClass setting for connections created by this instance to {@code trafficClass}.
   * @return a reference to this so multiple method calls can be chained together
   */
  HttpServer setTrafficClass(int trafficClass);

  /**
   * Set the accept backlog
   * @return a reference to this so multiple method calls can be chained together
   */
  HttpServer setAcceptBacklog(int backlog);

  /**
   * If {@code automaticCompression} is set to {@code true} then the server will compress the outgoing
   * payloads using GZIP.
   * @return a reference to this so multiple method calls can be chained together
   */
  HttpServer setAutomaticCompression(boolean automaticCompression);

  /**
   * @return true if Nagle's algorithm is disabled.
   */
  Boolean isTCPNoDelay();

  /**
   * @return The TCP send buffer size
   */
  Integer getSendBufferSize();

  /**
   * @return The TCP receive buffer size
   */
  Integer getReceiveBufferSize();

  /**
   *
   * @return true if TCP keep alive is enabled
   */
  Boolean isTCPKeepAlive();

  /**
   *
   * @return The value of TCP reuse address
   */
  Boolean isReuseAddress();

  /**
   *
   * @return the value of TCP so linger
   */
  Boolean isSoLinger();

  /**
   *
   * @return the value of TCP traffic class
   */
  Integer getTrafficClass();

  /**
   *
   * @return the accept backlog
   */
  Integer getAcceptBacklog();

  /**
   *
   * @return true if this server will make SSL connections
   */
  boolean isSSL();

  /**
   *
   * @return The path to the key store
   */
  String getKeyStorePath();

  /**
   *
   * @return The keystore password
   */
  String getKeyStorePassword();

  /**
   *
   * @return The trust store path
   */
  String getTrustStorePath();

  /**
   *
   * @return The trust store password
   */
  String getTrustStorePassword();

  /**
   *
   * @return true if this server will automatically compress the payloads.
   */
  boolean isAutomaticCompression();
}
