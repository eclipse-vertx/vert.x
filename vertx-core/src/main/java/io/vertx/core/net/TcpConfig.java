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

import java.util.HashMap;
import java.util.Map;

import static io.vertx.core.net.TCPSSLOptions.DEFAULT_SO_LINGER;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class TcpConfig extends TransportConfig {

  private int sendBufferSize;
  private int receiveBufferSize;
  private int trafficClass;
  private boolean reuseAddress;
  private boolean soReusePort;
  private boolean soKeepAlive;
  private int soLinger;
  private Map<TcpOption<?>, Object> options;

  public TcpConfig() {
    init();
  }

  public TcpConfig(TcpConfig other) {
    init();
    this.sendBufferSize = other.getSendBufferSize();
    this.receiveBufferSize = other.getReceiveBufferSize();
    this.reuseAddress = other.isReuseAddress();
    this.trafficClass = other.getTrafficClass();
    this.soReusePort = other.isSoReusePort();
    this.soKeepAlive = other.isSoKeepAlive();
    this.soLinger = other.getSoLinger();
    this.options = other.options != null ? new HashMap<>(other.options) : null;
  }

  void init() {
    sendBufferSize = NetworkOptions.DEFAULT_SEND_BUFFER_SIZE;
    receiveBufferSize = NetworkOptions.DEFAULT_RECEIVE_BUFFER_SIZE;
    reuseAddress = NetworkOptions.DEFAULT_REUSE_ADDRESS;
    trafficClass = NetworkOptions.DEFAULT_TRAFFIC_CLASS;
    soReusePort = NetworkOptions.DEFAULT_REUSE_PORT;
    soKeepAlive = TCPSSLOptions.DEFAULT_TCP_KEEP_ALIVE;
    soLinger = DEFAULT_SO_LINGER;
    options = null;
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
  public boolean isSoReusePort() {
    return soReusePort;
  }

  /**
   * Set the value of reuse port.
   * <p/>
   * This is only supported by native transports.
   *
   * @param soReusePort  the value of reuse port
   * @return a reference to this, so the API can be used fluently
   */
  public TcpConfig setSoReusePort(boolean soReusePort) {
    this.soReusePort = soReusePort;
    return this;
  }

  /**
   * @return is keep alive enabled?
   */
  public boolean isSoKeepAlive() {
    return soKeepAlive;
  }

  /**
   * Set whether keep alive is enabled
   *
   * @param keepAlive true if keep alive is enabled
   * @return a reference to this, so the API can be used fluently
   */
  public TcpConfig setSoKeepAlive(boolean keepAlive) {
    this.soKeepAlive = keepAlive;
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
   * Returns a configurable TCP option.
   *
   * @param option the option to probe
   * @return the option value or {@code null} when not set
   */
  public <T> T getOption(TcpOption<T> option) {
    return options != null ? option.type.cast(options.get(option)) : null;
  }

  /**
   * Configure a TCP option.
   *
   * @param option the option to configure
   * @param value the value to set or {@code null} to unset
   * @return a reference to this, so the API can be used fluently
   */
  public <T> TcpConfig setOption(TcpOption<T> option, T value) {
    if (value == null) {
      if (options != null) {
        options.remove(option);
      }
    } else {
      option.validate(value);
      if (options == null) {
        options = new HashMap<>();
      }
      options.put(option, value);
    }
    return this;
  }
}
