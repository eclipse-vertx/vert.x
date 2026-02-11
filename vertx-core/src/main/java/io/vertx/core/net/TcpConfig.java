/*
 * Copyright (c) 2011-2019 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package io.vertx.core.net;

import io.vertx.core.impl.Arguments;

import static io.vertx.core.net.TCPSSLOptions.DEFAULT_SO_LINGER;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class TcpConfig extends TransportConfig {

  private int sendBufferSize;
  private int receiveBufferSize;
  private int trafficClass;
  private boolean reuseAddress;
  private boolean reusePort;
  private boolean tcpNoDelay;
  private boolean tcpKeepAlive;
  private int soLinger;
  private boolean tcpFastOpen;
  private boolean tcpCork;
  private boolean tcpQuickAck;
  private int tcpUserTimeout;

  public TcpConfig() {
    init();
  }

  public TcpConfig(TcpConfig other) {
    init();
    this.sendBufferSize = other.getSendBufferSize();
    this.receiveBufferSize = other.getReceiveBufferSize();
    this.reuseAddress = other.isReuseAddress();
    this.trafficClass = other.getTrafficClass();
    this.reusePort = other.isReusePort();
    this.tcpNoDelay = other.isTcpNoDelay();
    this.tcpKeepAlive = other.isTcpKeepAlive();
    this.soLinger = other.getSoLinger();
    this.tcpFastOpen = other.isTcpFastOpen();
    this.tcpCork = other.isTcpCork();
    this.tcpQuickAck = other.isTcpQuickAck();
    this.tcpUserTimeout = other.getTcpUserTimeout();
  }

  void init() {
    sendBufferSize = NetworkOptions.DEFAULT_SEND_BUFFER_SIZE;
    receiveBufferSize = NetworkOptions.DEFAULT_RECEIVE_BUFFER_SIZE;
    reuseAddress = NetworkOptions.DEFAULT_REUSE_ADDRESS;
    trafficClass = NetworkOptions.DEFAULT_TRAFFIC_CLASS;
    reusePort = NetworkOptions.DEFAULT_REUSE_PORT;
    tcpNoDelay = TCPSSLOptions.DEFAULT_TCP_NO_DELAY;
    tcpKeepAlive = TCPSSLOptions.DEFAULT_TCP_KEEP_ALIVE;
    soLinger = DEFAULT_SO_LINGER;
    tcpFastOpen = TCPSSLOptions.DEFAULT_TCP_FAST_OPEN;
    tcpCork = TCPSSLOptions.DEFAULT_TCP_CORK;
    tcpQuickAck = TCPSSLOptions.DEFAULT_TCP_QUICKACK;
    tcpUserTimeout = TCPSSLOptions.DEFAULT_TCP_USER_TIMEOUT;
  }

  @Override
  protected TcpConfig copy() {
    return new TcpConfig(this);
  }

  /**
   * Return the TCP send buffer size, in bytes.
   *
   * @return the send buffer size
   */
  public int getSendBufferSize() {
    return sendBufferSize;
  }

  /**
   * Set the TCP send buffer size
   *
   * @param sendBufferSize  the buffers size, in bytes
   * @return a reference to this, so the API can be used fluently
   */
  public TcpConfig setSendBufferSize(int sendBufferSize) {
    Arguments.require(sendBufferSize > 0  || sendBufferSize == NetworkOptions.DEFAULT_SEND_BUFFER_SIZE, "sendBufferSize must be > 0");
    this.sendBufferSize = sendBufferSize;
    return this;
  }

  /**
   * Return the TCP receive buffer size, in bytes
   *
   * @return the receive buffer size
   */
  public int getReceiveBufferSize() {
    return receiveBufferSize;
  }

  /**
   * Set the TCP receive buffer size
   *
   * @param receiveBufferSize  the buffers size, in bytes
   * @return a reference to this, so the API can be used fluently
   */
  public TcpConfig setReceiveBufferSize(int receiveBufferSize) {
    Arguments.require(receiveBufferSize > 0 || receiveBufferSize == NetworkOptions.DEFAULT_RECEIVE_BUFFER_SIZE, "receiveBufferSize must be > 0");
    this.receiveBufferSize = receiveBufferSize;
    return this;
  }

  /**
   * @return  the value of reuse address
   */
  public boolean isReuseAddress() {
    return reuseAddress;
  }

  /**
   * Set the value of reuse address
   * @param reuseAddress  the value of reuse address
   * @return a reference to this, so the API can be used fluently
   */
  public TcpConfig setReuseAddress(boolean reuseAddress) {
    this.reuseAddress = reuseAddress;
    return this;
  }

  /**
   * @return  the value of traffic class
   */
  public int getTrafficClass() {
    return trafficClass;
  }

  /**
   * Set the value of traffic class
   *
   * @param trafficClass  the value of traffic class
   * @return a reference to this, so the API can be used fluently
   */
  public TcpConfig setTrafficClass(int trafficClass) {
    Arguments.requireInRange(trafficClass, NetworkOptions.DEFAULT_TRAFFIC_CLASS, 255, "trafficClass tc must be 0 <= tc <= 255");
    this.trafficClass = trafficClass;
    return this;
  }

  /**
   * @return  the value of reuse address - only supported by native transports
   */
  public boolean isReusePort() {
    return reusePort;
  }

  /**
   * Set the value of reuse port.
   * <p/>
   * This is only supported by native transports.
   *
   * @param reusePort  the value of reuse port
   * @return a reference to this, so the API can be used fluently
   */
  public TcpConfig setReusePort(boolean reusePort) {
    this.reusePort = reusePort;
    return this;
  }

  /**
   * @return TCP no delay enabled ?
   */
  public boolean isTcpNoDelay() {
    return tcpNoDelay;
  }

  /**
   * Set whether TCP no delay is enabled
   *
   * @param tcpNoDelay true if TCP no delay is enabled (Nagle disabled)
   * @return a reference to this, so the API can be used fluently
   */
  public TcpConfig setTcpNoDelay(boolean tcpNoDelay) {
    this.tcpNoDelay = tcpNoDelay;
    return this;
  }

  /**
   * @return is TCP keep alive enabled?
   */
  public boolean isTcpKeepAlive() {
    return tcpKeepAlive;
  }

  /**
   * Set whether TCP keep alive is enabled
   *
   * @param tcpKeepAlive true if TCP keep alive is enabled
   * @return a reference to this, so the API can be used fluently
   */
  public TcpConfig setTcpKeepAlive(boolean tcpKeepAlive) {
    this.tcpKeepAlive = tcpKeepAlive;
    return this;
  }

  /**
   *
   * @return is SO_linger enabled
   */
  public int getSoLinger() {
    return soLinger;
  }

  /**
   * Set whether SO_linger keep alive is enabled
   *
   * @param soLinger true if SO_linger is enabled
   * @return a reference to this, so the API can be used fluently
   */
  public TcpConfig setSoLinger(int soLinger) {
    if (soLinger < 0 && soLinger != DEFAULT_SO_LINGER) {
      throw new IllegalArgumentException("soLinger must be >= 0");
    }
    this.soLinger = soLinger;
    return this;
  }

  /**
   * @return wether {@code TCP_FASTOPEN} option is enabled
   */
  public boolean isTcpFastOpen() {
    return tcpFastOpen;
  }

  /**
   * Enable the {@code TCP_FASTOPEN} option - only with linux native transport.
   *
   * @param tcpFastOpen the fast open value
   */
  public TcpConfig setTcpFastOpen(boolean tcpFastOpen) {
    this.tcpFastOpen = tcpFastOpen;
    return this;
  }

  /**
   * @return wether {@code TCP_CORK} option is enabled
   */
  public boolean isTcpCork() {
    return tcpCork;
  }

  /**
   * Enable the {@code TCP_CORK} option - only with linux native transport.
   *
   * @param tcpCork the cork value
   */
  public TcpConfig setTcpCork(boolean tcpCork) {
    this.tcpCork = tcpCork;
    return this;
  }

  /**
   * @return wether {@code TCP_QUICKACK} option is enabled
   */
  public boolean isTcpQuickAck() {
    return tcpQuickAck;
  }

  /**
   * Enable the {@code TCP_QUICKACK} option - only with linux native transport.
   *
   * @param tcpQuickAck the quick ack value
   */
  public TcpConfig setTcpQuickAck(boolean tcpQuickAck) {
    this.tcpQuickAck = tcpQuickAck;
    return this;
  }

  /**
   *
   * @return the {@code TCP_USER_TIMEOUT} value
   */
  public int getTcpUserTimeout() {
    return tcpUserTimeout;
  }

  /**
   * Sets the {@code TCP_USER_TIMEOUT} option - only with linux native transport.
   *
   * @param tcpUserTimeout the tcp user timeout value
   */
  public TcpConfig setTcpUserTimeout(int tcpUserTimeout) {
    if (tcpUserTimeout < 0) {
      throw new IllegalArgumentException("tcpUserTimeout must be >= 0");
    }
    this.tcpUserTimeout = tcpUserTimeout;
    return this;
  }
}
