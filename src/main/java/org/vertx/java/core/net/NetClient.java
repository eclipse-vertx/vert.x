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
import org.vertx.java.core.net.impl.NetClientImpl;

/**
 * NetClient is an asynchronous factory for TCP or SSL connections
 * <p>
 * Multiple connections to different servers can be made using the same instance.
 * <p>
 * This client supports a configurable number of connection attempts and a configurable
 * delay between attempts.
 * <p>
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class NetClient {

  private NetClientImpl client = new NetClientImpl();

  /**
   * Create a new {@code NetClient}
   */
  public NetClient() {
    super();
  }

  /**
   * Attempt to open a connection to a server at the specific {@code port} and {@code host}.
   * {@code host} can be a valid host name or IP addresss. The connect is done asynchronously and on success, a
   * {@link NetSocket} instance is supplied via the {@code connectHandler} instance
   * @return a reference to this so multiple method calls can be chained together
   */
  public NetClient connect(int port, String host, final Handler<NetSocket> connectHandler) {
    client.connect(port, host, connectHandler);
    return this;
  }

  /**
   * Attempt to open a connection to a server at the specific {@code port} and host localhost
   * The connect is done asynchronously and on success, a
   * {@link NetSocket} instance is supplied via the {@code connectHandler} instance
   * @return a reference to this so multiple method calls can be chained together
   */
  public NetClient connect(int port, Handler<NetSocket> connectCallback) {
    return connect(port, "localhost", connectCallback);
  }

  /**
   * Close the client. Any sockets which have not been closed manually will be closed here.
   */
  public void close() {
    client.close();
  }

  /**
   * Set the number of reconnection attempts. In the event a connection attempt fails, the client will attempt
   * to connect a further number of times, before it fails. Default value is zero.
   */
  public NetClient setReconnectAttempts(int attempts) {
    client.setReconnectAttempts(attempts);
    return this;
  }

  /**
   * Get the number of reconnect attempts
   */
  public int getReconnectAttempts() {
    return client.getReconnectAttempts();
  }

  /**
   * Set the reconnect interval, in milliseconds
   */
  public NetClient setReconnectInterval(long interval) {
    client.setReconnectInterval(interval);
    return this;
  }

  /**
   * Get the reconnect interval, in milliseconds.
   */
  public long getReconnectInterval() {
    return client.getReconnectInterval();
  }

  /**
   * Set the exception handler. Any exceptions that occur during connect or later on will be notified via the {@code handler}.
   * If no handler is supplied any exceptions will be printed to {@link System#err}
   */
  public void exceptionHandler(Handler<Exception> handler) {
    client.exceptionHandler(handler);
  }

  // SSL and TCP attributes

  /**
   * If {@code ssl} is {@code true}, this signifies that any connections will be SSL connections.
   * @return A reference to this, so multiple invocations can be chained together.
   */
  public NetClient setSSL(boolean ssl) {
    client.setSSL(ssl);
    return this;
  }

  /**
   * Set the path to the SSL key store. This method should only be used in SSL mode, i.e. after {@link #setSSL(boolean)}
   * has been set to {@code true}.<p>
   * The SSL key store is a standard Java Key Store, and will contain the client certificate. Client certificates are only required if the server
   * requests client authentication.<p>
   * @return A reference to this, so multiple invocations can be chained together.
   */
  public NetClient setKeyStorePath(String path) {
    client.setKeyStorePath(path);
    return this;
  }

  /**
   * Set the password for the SSL key store. This method should only be used in SSL mode, i.e. after {@link #setSSL(boolean)}
   * has been set to {@code true}.<p>
   * @return A reference to this, so multiple invocations can be chained together.
   */
  public NetClient setKeyStorePassword(String pwd) {
    client.setKeyStorePassword(pwd);
    return this;
  }

  /**
   * Set the path to the SSL trust store. This method should only be used in SSL mode, i.e. after {@link #setSSL(boolean)}
   * has been set to {@code true}.<p>
   * The trust store is a standard Java Key Store, and should contain the certificates of
   * any servers that the client trusts.
   * If you wish the client to trust all server certificates you can use the {@link #setTrustAll(boolean)} method.<p>
   * @return A reference to this, so multiple invocations can be chained together.
   */
  public NetClient setTrustStorePath(String path) {
    client.setTrustStorePath(path);
    return this;
  }

  /**
   * Set the password for the SSL trust store. This method should only be used in SSL mode, i.e. after {@link #setSSL(boolean)}
   * has been set to {@code true}.<p>
   * @return A reference to this, so multiple invocations can be chained together.
   */
  public NetClient setTrustStorePassword(String pwd) {
    client.setTrustStorePassword(pwd);
    return this;
  }

  /**
   * If you want an SSL client to trust *all* server certificates rather than match them
   * against those in its trust store. Set this to true.
   * Use this with caution as you may be exposed to "main in the middle" attacks
   * @param trustAll Set to true if you want to trust all server certificates
   */
  public NetClient setTrustAll(boolean trustAll) {
    client.setTrustAll(trustAll);
    return this;
  }

  /**
   * If {@code tcpNoDelay} is set to {@code true} then <a href="http://en.wikipedia.org/wiki/Nagle's_algorithm">Nagle's algorithm</a>
   * will turned <b>off</b> for the TCP connections created by this instance.
   * @return a reference to this so multiple method calls can be chained together
   */
  public NetClient setTCPNoDelay(boolean tcpNoDelay) {
    client.setTCPNoDelay(tcpNoDelay);
    return this;
  }

  /**
   * Set the TCP send buffer size for connections created by this instance to {@code size} in bytes.
   * @return a reference to this so multiple method calls can be chained together
   */
  public NetClient setSendBufferSize(int size) {
    client.setSendBufferSize(size);
    return this;
  }

  /**
   * Set the TCP receive buffer size for connections created by this instance to {@code size} in bytes.
   * @return a reference to this so multiple method calls can be chained together
   */
  public NetClient setReceiveBufferSize(int size) {
    client.setReceiveBufferSize(size);
    return this;
  }

  /**
   * Set the TCP keepAlive setting for connections created by this instance to {@code keepAlive}.
   * @return a reference to this so multiple method calls can be chained together
   */
  public NetClient setTCPKeepAlive(boolean keepAlive) {
    client.setTCPKeepAlive(keepAlive);
    return this;
  }

  /**
   * Set the TCP reuseAddress setting for connections created by this instance to {@code reuse}.
   * @return a reference to this so multiple method calls can be chained together
   */
  public NetClient setReuseAddress(boolean reuse) {
    client.setReuseAddress(reuse);
    return this;
  }

  /**
   * Set the TCP soLinger setting for connections created by this instance to {@code reuse}.
   * @return a reference to this so multiple method calls can be chained together
   */
  public NetClient setSoLinger(boolean linger) {
    client.setSoLinger(linger);
    return this;
  }

  /**
   * Set the TCP trafficClass setting for connections created by this instance to {@code reuse}.
   * @return a reference to this so multiple method calls can be chained together
   */
  public NetClient setTrafficClass(int trafficClass) {
    client.setTrafficClass(trafficClass);
    return this;
  }

  /**
   * @return true if Nagle's algorithm is disabled.
   */
  public Boolean isTCPNoDelay() {
    return client.isTCPNoDelay();
  }

  /**
   * @return The TCP send buffer size
   */
  public Integer getSendBufferSize() {
    return client.getSendBufferSize();
  }

  /**
   * @return The TCP receive buffer size
   */
  public Integer getReceiveBufferSize() {
    return client.getReceiveBufferSize();
  }

  /**
   *
   * @return true if TCP keep alive is enabled
   */
  public Boolean isTCPKeepAlive() {
    return client.isTCPKeepAlive();
  }

  /**
   *
   * @return The value of TCP reuse address
   */
  public Boolean isReuseAddress() {
    return client.isReuseAddress();
  }

  /**
   *
   * @return the value of TCP so linger
   */
  public Boolean isSoLinger() {
    return client.isSoLinger();
  }

  /**
   *
   * @return the value of TCP traffic class
   */
  public Integer getTrafficClass() {
    return client.getTrafficClass();
  }

  /**
   *
   * @return true if this client will make SSL connections
   */
  public boolean isSSL() {
    return client.isSSL();
  }

  /**
   *
   * @return true if this client will trust all server certificates.
   */
  public boolean isTrustAll() {
    return client.isTrustAll();
  }

  /**
   *
   * @return The path to the key store
   */
  public String getKeyStorePath() {
    return client.getKeyStorePath();
  }

  /**
   *
   * @return The keystore password
   */
  public String getKeyStorePassword() {
    return client.getKeyStorePassword();
  }

  /**
   *
   * @return The trust store path
   */
  public String getTrustStorePath() {
    return client.getTrustStorePath();
  }

  /**
   *
   * @return The trust store password
   */
  public String getTrustStorePassword() {
    return client.getTrustStorePassword();
  }
}
