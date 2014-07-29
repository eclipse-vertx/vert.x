/*
 * Copyright 2014 Red Hat, Inc.
 *
 *   Red Hat licenses this file to you under the Apache License, version 2.0
 *   (the "License"); you may not use this file except in compliance with the
 *   License.  You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 *   WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 *   License for the specific language governing permissions and limitations
 *   under the License.
 */

package io.vertx.core.datagram.impl;

import io.vertx.core.datagram.DatagramSocketOptions;
import io.vertx.core.json.JsonObject;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class DatagramSocketOptionsImpl implements DatagramSocketOptions {

  private static final int DEFAULT_SENDBUFFERSIZE = -1;
  private static final int DEFAULT_RECEIVEBUFFERSIZE = -1;
  private static final int DEFAULT_TRAFFICCLASS = -1;

  private int sendBufferSize = DEFAULT_SENDBUFFERSIZE;
  private int receiveBufferSize = DEFAULT_RECEIVEBUFFERSIZE;
  private int trafficClass = DEFAULT_TRAFFICCLASS;

  private static final boolean DEFAULT_BROADCAST = false;
  private static final boolean DEFAULT_LOOPBACK_MODE_DISABLED = true;
  private static final int DEFAULT_MULTICASTTIMETOLIVE = -1;
  private static final String DEFAULT_MULTICASTNETWORKINTERFACE = null;
  private static final boolean DEFAULT_REUSEADDRESS = false;
  private static final boolean DEFAULT_IPV6 = false;

  private boolean broadcast;
  private boolean loopbackModeDisabled;
  private int multicastTimeToLive;
  private String multicastNetworkInterface;
  private boolean reuseAddress;
  private boolean ipV6;

  DatagramSocketOptionsImpl(DatagramSocketOptions other) {
    this.sendBufferSize = other.getSendBufferSize();
    this.receiveBufferSize = other.getReceiveBufferSize();
    this.reuseAddress = other.isReuseAddress();
    this.trafficClass = other.getTrafficClass();
    this.broadcast = other.isBroadcast();
    this.loopbackModeDisabled = other.isLoopbackModeDisabled();
    this.multicastTimeToLive = other.getMulticastTimeToLive();
    this.multicastNetworkInterface = other.getMulticastNetworkInterface();
    this.ipV6 = other.isIpV6();
  }

  DatagramSocketOptionsImpl(JsonObject json) {
    this.sendBufferSize = json.getInteger("sendBufferSize", DEFAULT_SENDBUFFERSIZE);
    this.receiveBufferSize = json.getInteger("receiveBufferSize", DEFAULT_RECEIVEBUFFERSIZE);
    this.reuseAddress = json.getBoolean("reuseAddress", DEFAULT_REUSEADDRESS);
    this.trafficClass = json.getInteger("trafficClass", DEFAULT_TRAFFICCLASS);
    this.broadcast = json.getBoolean("broadcast", DEFAULT_BROADCAST);
    this.loopbackModeDisabled = json.getBoolean("loopbackModeDisabled", DEFAULT_LOOPBACK_MODE_DISABLED);
    this.multicastTimeToLive = json.getInteger("multicastTimeToLive", DEFAULT_MULTICASTTIMETOLIVE);
    this.multicastNetworkInterface = json.getString("multicastNetworkInterface", DEFAULT_MULTICASTNETWORKINTERFACE);
    this.reuseAddress = json.getBoolean("reuseAddress", DEFAULT_REUSEADDRESS);
    this.ipV6 = json.getBoolean("ipV6", DEFAULT_IPV6);
  }

  DatagramSocketOptionsImpl() {
    sendBufferSize = DEFAULT_SENDBUFFERSIZE;
    receiveBufferSize = DEFAULT_RECEIVEBUFFERSIZE;
    reuseAddress = DEFAULT_REUSEADDRESS;
    trafficClass = DEFAULT_TRAFFICCLASS;

    broadcast = DEFAULT_BROADCAST;
    loopbackModeDisabled = DEFAULT_LOOPBACK_MODE_DISABLED;
    multicastTimeToLive = DEFAULT_MULTICASTTIMETOLIVE;
    multicastNetworkInterface = DEFAULT_MULTICASTNETWORKINTERFACE;
    reuseAddress = DEFAULT_REUSEADDRESS; // We override this as default is different for DatagramSocket
    ipV6 = DEFAULT_IPV6;
  }

  public int getSendBufferSize() {
    return sendBufferSize;
  }

  public DatagramSocketOptions setSendBufferSize(int sendBufferSize) {
    if (sendBufferSize < 1) {
      throw new IllegalArgumentException("sendBufferSize must be > 0");
    }
    this.sendBufferSize = sendBufferSize;
    return this;
  }

  public int getReceiveBufferSize() {
    return receiveBufferSize;
  }

  public DatagramSocketOptions setReceiveBufferSize(int receiveBufferSize) {
    if (receiveBufferSize < 1) {
      throw new IllegalArgumentException("receiveBufferSize must be > 0");
    }
    this.receiveBufferSize = receiveBufferSize;
    return this;
  }

  public boolean isReuseAddress() {
    return reuseAddress;
  }

  public DatagramSocketOptions setReuseAddress(boolean reuseAddress) {
    this.reuseAddress = reuseAddress;
    return this;
  }

  public int getTrafficClass() {
    return trafficClass;
  }

  public DatagramSocketOptions setTrafficClass(int trafficClass) {
    if (trafficClass < 0 || trafficClass > 255) {
      throw new IllegalArgumentException("trafficClass tc must be 0 <= tc <= 255");
    }
    this.trafficClass = trafficClass;
    return this;
  }

  public boolean isBroadcast() {
    return broadcast;
  }

  public DatagramSocketOptions setBroadcast(boolean broadcast) {
    this.broadcast = broadcast;
    return this;
  }

  public boolean isLoopbackModeDisabled() {
    return loopbackModeDisabled;
  }

  public DatagramSocketOptions setLoopbackModeDisabled(boolean loopbackModeDisabled) {
    this.loopbackModeDisabled = loopbackModeDisabled;
    return this;
  }

  public int getMulticastTimeToLive() {
    return multicastTimeToLive;
  }

  public DatagramSocketOptions setMulticastTimeToLive(int multicastTimeToLive) {
    if (multicastTimeToLive < 0) {
      throw new IllegalArgumentException("multicastTimeToLive must be >= 0");
    }
    this.multicastTimeToLive = multicastTimeToLive;
    return this;
  }

  public String getMulticastNetworkInterface() {
    return multicastNetworkInterface;
  }

  public DatagramSocketOptions setMulticastNetworkInterface(String multicastNetworkInterface) {
    this.multicastNetworkInterface = multicastNetworkInterface;
    return this;
  }

  public boolean isIpV6() {
    return ipV6;
  }

  public DatagramSocketOptions setIpV6(boolean ipV6) {
    this.ipV6 = ipV6;
    return this;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof DatagramSocketOptionsImpl)) return false;

    DatagramSocketOptionsImpl that = (DatagramSocketOptionsImpl) o;

    if (broadcast != that.broadcast) return false;
    if (ipV6 != that.ipV6) return false;
    if (loopbackModeDisabled != that.loopbackModeDisabled) return false;
    if (multicastTimeToLive != that.multicastTimeToLive) return false;
    if (receiveBufferSize != that.receiveBufferSize) return false;
    if (reuseAddress != that.reuseAddress) return false;
    if (sendBufferSize != that.sendBufferSize) return false;
    if (trafficClass != that.trafficClass) return false;
    if (multicastNetworkInterface != null ? !multicastNetworkInterface.equals(that.multicastNetworkInterface) : that.multicastNetworkInterface != null)
      return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = sendBufferSize;
    result = 31 * result + receiveBufferSize;
    result = 31 * result + trafficClass;
    result = 31 * result + (broadcast ? 1 : 0);
    result = 31 * result + (loopbackModeDisabled ? 1 : 0);
    result = 31 * result + multicastTimeToLive;
    result = 31 * result + (multicastNetworkInterface != null ? multicastNetworkInterface.hashCode() : 0);
    result = 31 * result + (reuseAddress ? 1 : 0);
    result = 31 * result + (ipV6 ? 1 : 0);
    return result;
  }
}
