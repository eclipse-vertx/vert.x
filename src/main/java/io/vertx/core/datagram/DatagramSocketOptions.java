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

package io.vertx.core.datagram;

import io.vertx.codegen.annotations.DataObject;
import io.vertx.core.impl.Arguments;
import io.vertx.core.json.JsonObject;
import io.vertx.core.net.NetworkOptions;

/**
 * Options used to configure a datagram socket.
 *
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
@DataObject
public class DatagramSocketOptions extends NetworkOptions {

  /**
   * The default value of broadcast for the socket = false
   */
  public static final boolean DEFAULT_BROADCAST = false;

  /**
   * The default value of loopback disabled = true
   */
  public static final boolean DEFAULT_LOOPBACK_MODE_DISABLED = true;

  /**
   * The default value of multicast disabled = -1
   */
  public static final int DEFAULT_MULTICAST_TIME_TO_LIVE = -1;

  /**
   * The default value of multicast network interface = null
   */
  public static final String DEFAULT_MULTICAST_NETWORK_INTERFACE = null;

  /**
   * The default value of reuse address = false
   */
  public static final boolean DEFAULT_REUSE_ADDRESS = false; // Override this

  /**
   * The default value of use IP v6 = false
   */
  public static final boolean DEFAULT_IPV6 = false;

  private boolean broadcast;
  private boolean loopbackModeDisabled;
  private int multicastTimeToLive;
  private String multicastNetworkInterface;
  private boolean ipV6;

  /**
   * Default constructor
   */
  public DatagramSocketOptions() {
    super();
    setReuseAddress(DEFAULT_REUSE_ADDRESS); // default is different for DatagramSocket
    broadcast = DEFAULT_BROADCAST;
    loopbackModeDisabled = DEFAULT_LOOPBACK_MODE_DISABLED;
    multicastTimeToLive = DEFAULT_MULTICAST_TIME_TO_LIVE;
    multicastNetworkInterface = DEFAULT_MULTICAST_NETWORK_INTERFACE;
    ipV6 = DEFAULT_IPV6;
  }

  /**
   * Copy constructor
   *
   * @param other  the options to copy
   */
  public DatagramSocketOptions(DatagramSocketOptions other) {
    super(other);
    this.broadcast = other.isBroadcast();
    this.loopbackModeDisabled = other.isLoopbackModeDisabled();
    this.multicastTimeToLive = other.getMulticastTimeToLive();
    this.multicastNetworkInterface = other.getMulticastNetworkInterface();
    this.ipV6 = other.isIpV6();
  }

  /**
   * Constructor to create options from JSON
   *
   * @param json  the JSON
   */
  public DatagramSocketOptions(JsonObject json) {
    super(json);
    this.broadcast = json.getBoolean("broadcast", DEFAULT_BROADCAST);
    this.loopbackModeDisabled = json.getBoolean("loopbackModeDisabled", DEFAULT_LOOPBACK_MODE_DISABLED);
    this.multicastTimeToLive = json.getInteger("multicastTimeToLive", DEFAULT_MULTICAST_TIME_TO_LIVE);
    this.multicastNetworkInterface = json.getString("multicastNetworkInterface", DEFAULT_MULTICAST_NETWORK_INTERFACE);
    this.ipV6 = json.getBoolean("ipV6", DEFAULT_IPV6);
    setReuseAddress(json.getBoolean("reuseAddress", DEFAULT_REUSE_ADDRESS));
  }

  @Override
  public int getSendBufferSize() {
    return super.getSendBufferSize();
  }

  @Override
  public DatagramSocketOptions setSendBufferSize(int sendBufferSize) {
    super.setSendBufferSize(sendBufferSize);
    return this;
  }

  @Override
  public int getReceiveBufferSize() {
    return super.getReceiveBufferSize();
  }

  @Override
  public DatagramSocketOptions setReceiveBufferSize(int receiveBufferSize) {
    super.setReceiveBufferSize(receiveBufferSize);
    return this;
  }

  @Override
  public DatagramSocketOptions setReuseAddress(boolean reuseAddress) {
    super.setReuseAddress(reuseAddress);
    return this;
  }

  @Override
  public int getTrafficClass() {
    return super.getTrafficClass();
  }

  @Override
  public DatagramSocketOptions setTrafficClass(int trafficClass) {
    super.setTrafficClass(trafficClass);
    return this;
  }

  /**
   * @return true if the socket receive broadcast packets?
   */
  public boolean isBroadcast() {
    return broadcast;
  }

  /**
   * Set if the socket can receive broadcast packets
   *
   * @param broadcast  true if the socket can receive broadcast packets
   * @return a reference to this, so the API can be used fluently
   */
  public DatagramSocketOptions setBroadcast(boolean broadcast) {
    this.broadcast = broadcast;
    return this;
  }

  /**
   * @return true if loopback mode is disabled
   *
   */
  public boolean isLoopbackModeDisabled() {
    return loopbackModeDisabled;
  }

  /**
   * Set if loopback mode is disabled
   *
   * @param loopbackModeDisabled  true if loopback mode is disabled
   * @return a reference to this, so the API can be used fluently
   */
  public DatagramSocketOptions setLoopbackModeDisabled(boolean loopbackModeDisabled) {
    this.loopbackModeDisabled = loopbackModeDisabled;
    return this;
  }

  /**
   * @return the multicast ttl value
   */
  public int getMulticastTimeToLive() {
    return multicastTimeToLive;
  }

  /**
   * Set the multicast ttl value
   *
   * @param multicastTimeToLive  the multicast ttl value
   * @return a reference to this, so the API can be used fluently
   */
  public DatagramSocketOptions setMulticastTimeToLive(int multicastTimeToLive) {
    Arguments.require(multicastTimeToLive >= 0, "multicastTimeToLive must be >= 0");
    this.multicastTimeToLive = multicastTimeToLive;
    return this;
  }

  /**
   * Get the multicast network interface address
   *
   * @return  the interface address
   */
  public String getMulticastNetworkInterface() {
    return multicastNetworkInterface;
  }

  /**
   * Set the multicast network interface address
   *
   * @param multicastNetworkInterface  the address
   * @return a reference to this, so the API can be used fluently
   */
  public DatagramSocketOptions setMulticastNetworkInterface(String multicastNetworkInterface) {
    this.multicastNetworkInterface = multicastNetworkInterface;
    return this;
  }

  /**
   * @return  true if IP v6 be used?
   */
  public boolean isIpV6() {
    return ipV6;
  }

  /**
   * Set if IP v6 should be used
   *
   * @param ipV6  true if IP v6 should be used
   * @return a reference to this, so the API can be used fluently
   */
  public DatagramSocketOptions setIpV6(boolean ipV6) {
    this.ipV6 = ipV6;
    return this;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof DatagramSocketOptions)) return false;
    if (!super.equals(o)) return false;

    DatagramSocketOptions that = (DatagramSocketOptions) o;

    if (broadcast != that.broadcast) return false;
    if (ipV6 != that.ipV6) return false;
    if (loopbackModeDisabled != that.loopbackModeDisabled) return false;
    if (multicastTimeToLive != that.multicastTimeToLive) return false;
    if (multicastNetworkInterface != null ? !multicastNetworkInterface.equals(that.multicastNetworkInterface) : that.multicastNetworkInterface != null)
      return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = super.hashCode();
    result = 31 * result + (broadcast ? 1 : 0);
    result = 31 * result + (loopbackModeDisabled ? 1 : 0);
    result = 31 * result + multicastTimeToLive;
    result = 31 * result + (multicastNetworkInterface != null ? multicastNetworkInterface.hashCode() : 0);
    result = 31 * result + (ipV6 ? 1 : 0);
    return result;
  }
}
