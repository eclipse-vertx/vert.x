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

package org.vertx.java.core.net;

import org.vertx.java.core.Handler;

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
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public interface NetClient {

  /**
   * Attempt to open a connection to a server at the specific {@code port} and host {@code localhost}
   * The connect is done asynchronously and on success, a
   * {@link NetSocket} instance is supplied via the {@code connectHandler} instance
   * @return a reference to this so multiple method calls can be chained together
   */
  NetClient connect(int port, Handler<NetSocket> connectCallback);

  /**
   * Attempt to open a connection to a server at the specific {@code port} and {@code host}.
   * {@code host} can be a valid host name or IP addresss. The connect is done asynchronously and on success, a
   * {@link NetSocket} instance is supplied via the {@code connectHandler} instance
   * @return a reference to this so multiple method calls can be chained together
   */
  NetClient connect(int port, String host, final Handler<NetSocket> connectHandler);

  /**
   * Close the client. Any sockets which have not been closed manually will be closed here.
   */
  void close();

  /**
   * Set the number of reconnection attempts. In the event a connection attempt fails, the client will attempt
   * to connect a further number of times, before it fails. Default value is zero.
   */
  NetClient setReconnectAttempts(int attempts);

  /**
   * Get the number of reconnect attempts
   */
  int getReconnectAttempts();

  /**
   * Set the reconnect interval, in milliseconds
   */
  NetClient setReconnectInterval(long interval);

  /**
   * Get the reconnect interval, in milliseconds.
   */
  long getReconnectInterval();

  /**
   * Set the exception handler. Any exceptions that occur during connect or later on will be notified via the {@code handler}.
   * If no handler is supplied any exceptions will be printed to {@link System#err}
   */
  void exceptionHandler(Handler<Exception> handler);

  /**
   * If {@code ssl} is {@code true}, this signifies that any connections will be SSL connections.
   * @return A reference to this, so multiple invocations can be chained together.
   */
  NetClient setSSL(boolean ssl);

  /**
   * Set the path to the SSL key store. This method should only be used in SSL mode, i.e. after {@link #setSSL(boolean)}
   * has been set to {@code true}.<p>
   * The SSL key store is a standard Java Key Store, and will contain the client certificate. Client certificates are
   * only required if the server requests client authentication.<p>
   * @return A reference to this, so multiple invocations can be chained together.
   */
  NetClient setKeyStorePath(String path);

  /**
   * Set the password for the SSL key store. This method should only be used in SSL mode, i.e. after {@link #setSSL(boolean)}
   * has been set to {@code true}.<p>
   * @return A reference to this, so multiple invocations can be chained together.
   */
  NetClient setKeyStorePassword(String pwd);

  /**
   * Set the path to the SSL trust store. This method should only be used in SSL mode, i.e. after {@link #setSSL(boolean)}
   * has been set to {@code true}.<p>
   * The trust store is a standard Java Key Store, and should contain the certificates of any servers that the client trusts.
   * If you wish the client to trust all server certificates you can use the {@link #setTrustAll(boolean)} method.<p>
   * @return A reference to this, so multiple invocations can be chained together.
   */
  NetClient setTrustStorePath(String path);

  /**
   * Set the password for the SSL trust store. This method should only be used in SSL mode, i.e. after {@link #setSSL(boolean)}
   * has been set to {@code true}.<p>
   * @return A reference to this, so multiple invocations can be chained together.
   */
  NetClient setTrustStorePassword(String pwd);

  /**
   * If you want an SSL client to trust *all* server certificates rather than match them
   * against those in its trust store, you can set this to true.<p>
   * Use this with caution as you may be exposed to "main in the middle" attacks
   * @param trustAll Set to true if you want to trust all server certificates
   */
  NetClient setTrustAll(boolean trustAll);

  /**
   * If {@code tcpNoDelay} is set to {@code true} then <a href="http://en.wikipedia.org/wiki/Nagle's_algorithm">Nagle's algorithm</a>
   * will turned <b>off</b> for the TCP connections created by this instance.
   * @return a reference to this so multiple method calls can be chained together
   */
  NetClient setTCPNoDelay(boolean tcpNoDelay);

  /**
   * Set the TCP send buffer size for connections created by this instance to {@code size} in bytes.
   * @return a reference to this so multiple method calls can be chained together
   */
  NetClient setSendBufferSize(int size);

  /**
   * Set the TCP receive buffer size for connections created by this instance to {@code size} in bytes.
   * @return a reference to this so multiple method calls can be chained together
   */
  NetClient setReceiveBufferSize(int size) ;

  /**
   * Set the TCP keepAlive setting for connections created by this instance to {@code keepAlive}.
   * @return a reference to this so multiple method calls can be chained together
   */
  NetClient setTCPKeepAlive(boolean keepAlive);

  /**
   * Set the TCP reuseAddress setting for connections created by this instance to {@code reuse}.
   * @return a reference to this so multiple method calls can be chained together
   */
  NetClient setReuseAddress(boolean reuse);

  /**
   * Set the TCP soLinger setting for connections created by this instance to {@code linger}.
   * @return a reference to this so multiple method calls can be chained together
   */
  NetClient setSoLinger(boolean linger);

  /**
   * Set the TCP trafficClass setting for connections created by this instance to {@code trafficClass}.
   * @return a reference to this so multiple method calls can be chained together
   */
  NetClient setTrafficClass(int trafficClass);

  /**
   * Set the connect timeout in milliseconds.
   * @return a reference to this so multiple method calls can be chained together
   */
  NetClient setConnectTimeout(long timeout);

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
   * @return The connect timeout in milliseconds
   */
  Long getConnectTimeout();

  /**
   *
   * @return true if this client will make SSL connections
   */
  boolean isSSL();

  /**
   *
   * @return true if this client will trust all server certificates.
   */
  boolean isTrustAll();

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
}
