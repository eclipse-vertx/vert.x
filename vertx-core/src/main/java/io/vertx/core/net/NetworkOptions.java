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
import io.vertx.core.json.JsonObject;
import io.netty.handler.logging.ByteBufFormat;


/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
@DataObject
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

  private boolean logActivity;
  private ByteBufFormat activityLogDataFormat;

  /**
   * Default constructor
   */
  public NetworkOptions() {
    logActivity = DEFAULT_LOG_ENABLED;
    activityLogDataFormat = DEFAULT_LOG_ACTIVITY_FORMAT;
  }

  /**
   * Copy constructor
   *
   * @param other  the options to copy
   */
  public NetworkOptions(NetworkOptions other) {
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
  }

  /**
   * Convert to JSON
   *
   * @return the JSON
   */
  public abstract JsonObject toJson();

  /**
   * Return the TCP send buffer size, in bytes.
   *
   * @return the send buffer size
   */
  public abstract int getSendBufferSize();

  /**
   * Set the TCP send buffer size
   *
   * @param sendBufferSize  the buffers size, in bytes
   * @return a reference to this, so the API can be used fluently
   */
  public abstract NetworkOptions setSendBufferSize(int sendBufferSize);

  /**
   * Return the TCP receive buffer size, in bytes
   *
   * @return the receive buffer size
   */
  public abstract int getReceiveBufferSize();

  /**
   * Set the TCP receive buffer size
   *
   * @param receiveBufferSize  the buffers size, in bytes
   * @return a reference to this, so the API can be used fluently
   */
  public abstract NetworkOptions setReceiveBufferSize(int receiveBufferSize);

  /**
   * @return  the value of reuse address
   */
  public abstract boolean isReuseAddress();

  /**
   * Set the value of reuse address
   * @param reuseAddress  the value of reuse address
   * @return a reference to this, so the API can be used fluently
   */
  public abstract NetworkOptions setReuseAddress(boolean reuseAddress);

  /**
   * @return  the value of traffic class
   */
  public abstract int getTrafficClass();

  /**
   * Set the value of traffic class
   *
   * @param trafficClass  the value of traffic class
   * @return a reference to this, so the API can be used fluently
   */
  public abstract NetworkOptions setTrafficClass(int trafficClass);

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
  public abstract boolean isReusePort();

  /**
   * Set the value of reuse port.
   * <p/>
   * This is only supported by native transports.
   *
   * @param reusePort  the value of reuse port
   * @return a reference to this, so the API can be used fluently
   */
  public abstract NetworkOptions setReusePort(boolean reusePort);

}
