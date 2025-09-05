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

package io.vertx.core.datagram;

import io.vertx.codegen.annotations.DataObject;
import io.vertx.codegen.json.annotations.JsonGen;
import io.vertx.core.impl.Arguments;
import io.vertx.core.json.JsonObject;
import io.vertx.core.net.NetworkOptions;
import io.netty.handler.logging.ByteBufFormat;

/**
 * Options used to configure a datagram socket.
 *
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
@DataObject
@JsonGen(publicConverter = false, inheritConverter = true)
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

  private boolean reusePort;
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
    init();
    setReuseAddress(DEFAULT_REUSE_ADDRESS); // default is different for DatagramSocket
  }

  /**
   * Copy constructor
   *
   * @param other  the options to copy
   */
  public DatagramSocketOptions(DatagramSocketOptions other) {
    super(other);
    this.reusePort = other.reusePort;
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
    init();
    DatagramSocketOptionsConverter.fromJson(json, this);
  }

  private void init() {
    reusePort = DEFAULT_REUSE_PORT;
    broadcast = DEFAULT_BROADCAST;
    loopbackModeDisabled = DEFAULT_LOOPBACK_MODE_DISABLED;
    multicastTimeToLive = DEFAULT_MULTICAST_TIME_TO_LIVE;
    multicastNetworkInterface = DEFAULT_MULTICAST_NETWORK_INTERFACE;
    ipV6 = DEFAULT_IPV6;
  }

  @Override
  public boolean isReusePort() {
    return reusePort;
  }

  @Override
  public DatagramSocketOptions setReusePort(boolean reusePort) {
    this.reusePort = reusePort;
    return this;
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
   * @return true if the socket can send or receive broadcast packets?
   */
  public boolean isBroadcast() {
    return broadcast;
  }

  /**
   * Set if the socket can send or receive broadcast packets
   *
   * @param broadcast  true if the socket can send or receive broadcast packets
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
  public DatagramSocketOptions setLogActivity(boolean logEnabled) {
    return (DatagramSocketOptions) super.setLogActivity(logEnabled);
  }

  @Override
  public DatagramSocketOptions setActivityLogDataFormat(ByteBufFormat activityLogDataFormat) {
    return (DatagramSocketOptions) super.setActivityLogDataFormat(activityLogDataFormat);
  }

  @Override
  public JsonObject toJson() {
    JsonObject json = new JsonObject();
    DatagramSocketOptionsConverter.toJson(this, json);
    return json;
  }
}
