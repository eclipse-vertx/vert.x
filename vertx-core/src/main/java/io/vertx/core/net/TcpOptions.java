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

import static io.vertx.core.net.TCPSSLOptions.DEFAULT_SO_LINGER;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class TcpOptions extends TransportOptions {

  private boolean reusePort;
  private boolean tcpNoDelay;
  private boolean tcpKeepAlive;
  private int soLinger;
  private boolean tcpFastOpen;
  private boolean tcpCork;
  private boolean tcpQuickAck;
  private int tcpUserTimeout;

  public TcpOptions() {
    init();
  }

  public TcpOptions(TcpOptions other) {
    init();
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
  protected TcpOptions copy() {
    return new TcpOptions(this);
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
  public TcpOptions setReusePort(boolean reusePort) {
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
  public TcpOptions setTcpNoDelay(boolean tcpNoDelay) {
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
  public TcpOptions setTcpKeepAlive(boolean tcpKeepAlive) {
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
  public TcpOptions setSoLinger(int soLinger) {
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
  public TcpOptions setTcpFastOpen(boolean tcpFastOpen) {
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
  public TcpOptions setTcpCork(boolean tcpCork) {
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
  public TcpOptions setTcpQuickAck(boolean tcpQuickAck) {
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
  public TcpOptions setTcpUserTimeout(int tcpUserTimeout) {
    this.tcpUserTimeout = tcpUserTimeout;
    return this;
  }
}
