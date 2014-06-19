/*
 * Copyright 2014 Red Hat, Inc.
 *
 * Red Hat licenses this file to you under the Apache License, version 2.0
 * (the "License"); you may not use this file except in compliance with the
 * License.  You may obtain a copy of the License at:
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

package org.vertx.java.core.datagram;

import org.vertx.java.core.net.NetworkOptions;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class DatagramSocketOptions extends NetworkOptions {

  private boolean broadcast;
  private boolean loopbackModeDisabled = true;
  private int multicastTimeToLive = -1;
  private String multicastNetworkInterface;
  private boolean reuseAddress = false; // We override this as default is different for DatagramSocket

  public DatagramSocketOptions(NetworkOptions other) {
    super(other);
  }

  public DatagramSocketOptions() {
    super();
  }

  public DatagramSocketOptions(DatagramSocketOptions other) {
    super(other);
    this.broadcast = other.broadcast;
    this.loopbackModeDisabled = other.loopbackModeDisabled;
    this.multicastTimeToLive = other.multicastTimeToLive;
    this.multicastNetworkInterface = other.multicastNetworkInterface;
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

  @Override
  public DatagramSocketOptions setSendBufferSize(int sendBufferSize) {
    super.setSendBufferSize(sendBufferSize);
    return this;
  }

  @Override
  public DatagramSocketOptions setReceiveBufferSize(int receiveBufferSize) {
    super.setReceiveBufferSize(receiveBufferSize);
    return this;
  }

  @Override
  public boolean isReuseAddress() {
    return reuseAddress;
  }

  @Override
  public DatagramSocketOptions setReuseAddress(boolean reuseAddress) {
    this.reuseAddress = reuseAddress;
    return this;
  }

  @Override
  public DatagramSocketOptions setTrafficClass(int trafficClass) {
    super.setTrafficClass(trafficClass);
    return this;
  }


}
