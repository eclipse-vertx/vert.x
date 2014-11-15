/*
 * Copyright (c) 2011-2014 The original author or authors
 * ------------------------------------------------------
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * and Apache License v2.0 which accompanies this distribution.
 *
 *     The Eclipse Public License is available at
 *     http://www.eclipse.org/legal/epl-v10.html
 *
 *     The Apache License v2.0 is available at
 *     http://www.opensource.org/licenses/apache2.0.php
 *
 * You may elect to redistribute this code under either of these licenses.
 */

package io.vertx.core.net;

import io.vertx.core.impl.Arguments;
import io.vertx.core.json.JsonObject;
import io.vertx.core.net.impl.SocketDefaults;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public abstract class NetworkOptions {

  public static final int DEFAULT_SEND_BUFFER_SIZE = SocketDefaults.instance.getTcpSendBufferSize();
  public static final int DEFAULT_RECEIVE_BUFFER_SIZE = SocketDefaults.instance.getTcpReceiveBufferSize();
  public static final int DEFAULT_TRAFFIC_CLASS = SocketDefaults.instance.getTrafficClass();
  public static final boolean DEFAULT_REUSE_ADDRESS = true;

  private int sendBufferSize;
  private int receiveBufferSize;
  private int trafficClass;
  private boolean reuseAddress;

  public NetworkOptions(NetworkOptions other) {
    this.sendBufferSize = other.getSendBufferSize();
    this.receiveBufferSize = other.getReceiveBufferSize();
    this.reuseAddress = other.isReuseAddress();
    this.trafficClass = other.getTrafficClass();
  }

  public NetworkOptions(JsonObject json) {
    this.sendBufferSize = json.getInteger("sendBufferSize", DEFAULT_SEND_BUFFER_SIZE);
    this.receiveBufferSize = json.getInteger("receiveBufferSize", DEFAULT_RECEIVE_BUFFER_SIZE);
    this.reuseAddress = json.getBoolean("reuseAddress", DEFAULT_REUSE_ADDRESS);
    this.trafficClass = json.getInteger("trafficClass", DEFAULT_TRAFFIC_CLASS);
  }

  public NetworkOptions() {
    sendBufferSize = DEFAULT_SEND_BUFFER_SIZE;
    receiveBufferSize = DEFAULT_RECEIVE_BUFFER_SIZE;
    reuseAddress = DEFAULT_REUSE_ADDRESS;
    trafficClass = DEFAULT_TRAFFIC_CLASS;
  }

  public int getSendBufferSize() {
    return sendBufferSize;
  }

  public NetworkOptions setSendBufferSize(int sendBufferSize) {
    Arguments.require(sendBufferSize > 0, "sendBufferSize must be > 0");
    this.sendBufferSize = sendBufferSize;
    return this;
  }

  public int getReceiveBufferSize() {
    return receiveBufferSize;
  }

  public NetworkOptions setReceiveBufferSize(int receiveBufferSize) {
    Arguments.require(receiveBufferSize > 0, "receiveBufferSize must be > 0");
    this.receiveBufferSize = receiveBufferSize;
    return this;
  }

  public boolean isReuseAddress() {
    return reuseAddress;
  }

  public NetworkOptions setReuseAddress(boolean reuseAddress) {
    this.reuseAddress = reuseAddress;
    return this;
  }

  public int getTrafficClass() {
    return trafficClass;
  }

  public NetworkOptions setTrafficClass(int trafficClass) {
    Arguments.requireInRange(trafficClass, 0, 255, "trafficClass tc must be 0 <= tc <= 255");
    this.trafficClass = trafficClass;
    return this;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof NetworkOptions)) return false;

    NetworkOptions that = (NetworkOptions) o;

    if (receiveBufferSize != that.receiveBufferSize) return false;
    if (reuseAddress != that.reuseAddress) return false;
    if (sendBufferSize != that.sendBufferSize) return false;
    if (trafficClass != that.trafficClass) return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = sendBufferSize;
    result = 31 * result + receiveBufferSize;
    result = 31 * result + trafficClass;
    result = 31 * result + (reuseAddress ? 1 : 0);
    return result;
  }
}
