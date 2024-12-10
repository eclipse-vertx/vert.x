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

import io.vertx.codegen.annotations.DataObject;
import io.vertx.codegen.json.annotations.JsonGen;
import io.vertx.core.impl.Arguments;
import io.vertx.core.json.JsonObject;
import io.netty.handler.logging.ByteBufFormat;


/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
@DataObject
@JsonGen(publicConverter = false)
public abstract class NetworkOptions {

  /**
   * The default value of TCP send buffer size
   */
  public static final int DEFAULT_SEND_BUFFER_SIZE = -1;

  /**
   * The default value of TCP receive buffer size
   */
  public static final int DEFAULT_RECEIVE_BUFFER_SIZE = -1;

  /**
   * The default value of traffic class
   */
  public static final int DEFAULT_TRAFFIC_CLASS = -1;

  /**
   * The default value of reuse address
   */
  public static final boolean DEFAULT_REUSE_ADDRESS = true;

  /**
   * The default value of reuse port
   */
  public static final boolean DEFAULT_REUSE_PORT = false;

  /**
   * The default log enabled = false
   */
  public static final boolean DEFAULT_LOG_ENABLED = false;

  /**
   * The default logActivity is ByteBufFormat.SIMPLE
   */
  public static final ByteBufFormat DEFAULT_LOG_ACTIVITY_FORMAT = ByteBufFormat.HEX_DUMP;

  private int sendBufferSize;
  private int receiveBufferSize;
  private int trafficClass;
  private boolean reuseAddress;
  private boolean logActivity;
  private ByteBufFormat activityLogDataFormat;
  private boolean reusePort;

  /**
   * Default constructor
   */
  public NetworkOptions() {
    sendBufferSize = DEFAULT_SEND_BUFFER_SIZE;
    receiveBufferSize = DEFAULT_RECEIVE_BUFFER_SIZE;
    reuseAddress = DEFAULT_REUSE_ADDRESS;
    trafficClass = DEFAULT_TRAFFIC_CLASS;
    logActivity = DEFAULT_LOG_ENABLED;
    activityLogDataFormat = DEFAULT_LOG_ACTIVITY_FORMAT;
    reusePort = DEFAULT_REUSE_PORT;
  }

  /**
   * Copy constructor
   *
   * @param other  the options to copy
   */
  public NetworkOptions(NetworkOptions other) {
    this.sendBufferSize = other.getSendBufferSize();
    this.receiveBufferSize = other.getReceiveBufferSize();
    this.reuseAddress = other.isReuseAddress();
    this.reusePort = other.isReusePort();
    this.trafficClass = other.getTrafficClass();
    this.logActivity = other.logActivity;
    this.activityLogDataFormat = other.activityLogDataFormat;
  }

  /**
   * Constructor from JSON
   *
   * @param json  the JSON
   */
  public NetworkOptions(JsonObject json) {
    this();
    NetworkOptionsConverter.fromJson(json, this);
  }

  /**
   * Convert to JSON
   *
   * @return the JSON
   */
  public JsonObject toJson() {
    JsonObject json = new JsonObject();
    NetworkOptionsConverter.toJson(this, json);
    return json;
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
  public NetworkOptions setSendBufferSize(int sendBufferSize) {
    Arguments.require(sendBufferSize > 0  || sendBufferSize == DEFAULT_SEND_BUFFER_SIZE, "sendBufferSize must be > 0");
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
  public NetworkOptions setReceiveBufferSize(int receiveBufferSize) {
    Arguments.require(receiveBufferSize > 0 || receiveBufferSize == DEFAULT_RECEIVE_BUFFER_SIZE, "receiveBufferSize must be > 0");
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
  public NetworkOptions setReuseAddress(boolean reuseAddress) {
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
  public NetworkOptions setTrafficClass(int trafficClass) {
    Arguments.requireInRange(trafficClass, DEFAULT_TRAFFIC_CLASS, 255, "trafficClass tc must be 0 <= tc <= 255");
    this.trafficClass = trafficClass;
    return this;
  }

  /**
   * @return true when network activity logging is enabled
   */
  public boolean getLogActivity() {
    return logActivity;
  }

  /**
   * @return Netty's logging handler's data format.
   */
  public ByteBufFormat getActivityLogDataFormat() {
    return activityLogDataFormat;
  }

  /**
   * Set to true to enabled network activity logging: Netty's pipeline is configured for logging on Netty's logger.
   *
   * @param logActivity true for logging the network activity
   * @return a reference to this, so the API can be used fluently
   */
  public NetworkOptions setLogActivity(boolean logActivity) {
    this.logActivity = logActivity;
    return this;
  }

  /**
   * Set the value of Netty's logging handler's data format: Netty's pipeline is configured for logging on Netty's logger.
   *
   * @param activityLogDataFormat the format to use
   * @return a reference to this, so the API can be used fluently
   */
  public NetworkOptions setActivityLogDataFormat(ByteBufFormat activityLogDataFormat) {
    this.activityLogDataFormat = activityLogDataFormat;
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
  public NetworkOptions setReusePort(boolean reusePort) {
    this.reusePort = reusePort;
    return this;
  }
}
