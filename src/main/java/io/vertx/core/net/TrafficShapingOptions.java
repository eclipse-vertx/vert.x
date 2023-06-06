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

import java.util.concurrent.TimeUnit;

import io.netty.util.internal.ObjectUtil;
import io.vertx.codegen.annotations.DataObject;
import io.vertx.codegen.annotations.Unstable;
import io.vertx.core.json.JsonObject;

/**
 * Options describing how {@link io.netty.handler.traffic.GlobalTrafficShapingHandler} will handle traffic shaping.
 */
@Unstable
@DataObject(generateConverter = true, publicConverter = false)
public class TrafficShapingOptions {
  /*
   * Default inbound bandwidth limit in bytes/sec = 0 (0 implies unthrottled)
   */
  public static final long DEFAULT_INBOUND_GLOBAL_BANDWIDTH_LIMIT = 0;

  /**
   * Default outbound bandwidth limit in bytes/sec = 0 (0 implies unthrottled)
   */
  public static final long DEFAULT_OUTBOUND_GLOBAL_BANDWIDTH_LIMIT = 0;

  private long inboundGlobalBandwidth;
  private long outboundGlobalBandwidth;
  private long peakOutboundGlobalBandwidth;
  private long maxDelayToWait;
  private TimeUnit maxDelayToWaitTimeUnit;
  private long checkIntervalForStats;
  private TimeUnit checkIntervalForStatsTimeUnit;

  public TrafficShapingOptions() {
  }

  public TrafficShapingOptions(TrafficShapingOptions other) {
    this.inboundGlobalBandwidth = other.getInboundGlobalBandwidth();
    this.outboundGlobalBandwidth = other.getOutboundGlobalBandwidth();
    this.peakOutboundGlobalBandwidth = other.getPeakOutboundGlobalBandwidth();
    this.maxDelayToWait = other.getMaxDelayToWait();
    this.checkIntervalForStats = other.getCheckIntervalForStats();
  }

  public TrafficShapingOptions(JsonObject json) {
    TrafficShapingOptionsConverter.fromJson(json, this);
  }

  public JsonObject toJson() {
    JsonObject json = new JsonObject();
    TrafficShapingOptionsConverter.toJson(this, json);
    return json;
  }

  /**
   * Set bandwidth limit in bytes per second for inbound connections
   *
   * @param inboundGlobalBandwidth bandwidth limit
   * @return a reference to this, so the API can be used fluently
   */
  public TrafficShapingOptions setInboundGlobalBandwidth(long inboundGlobalBandwidth) {
    this.inboundGlobalBandwidth = inboundGlobalBandwidth;
    return this;
  }

  /**
   * Set bandwidth limit in bytes per second for outbound connections
   *
   * @param outboundGlobalBandwidth bandwidth limit
   * @return a reference to this, so the API can be used fluently
   */
  public TrafficShapingOptions setOutboundGlobalBandwidth(long outboundGlobalBandwidth) {
    this.outboundGlobalBandwidth = outboundGlobalBandwidth;
    return this;
  }

  /**
   * Set the maximum delay to wait in case of traffic excess
   *
   * @param maxDelayToWaitTime maximum delay time for waiting
   * @return a reference to this, so the API can be used fluently
   */
  public TrafficShapingOptions setMaxDelayToWait(long maxDelayToWaitTime) {
    this.maxDelayToWait = maxDelayToWaitTime;
    ObjectUtil.checkPositive(this.maxDelayToWait, "maxDelayToWaitTime");
    return this;
  }

  /**
   * Set the maximum delay to wait time unit
   *
   * @param maxDelayToWaitTimeUnit maximum delay time's unit
   * @return a reference to this, so the API can be used fluently
   */
  public TrafficShapingOptions setMaxDelayToWaitUnit(TimeUnit maxDelayToWaitTimeUnit) {
    this.maxDelayToWaitTimeUnit = maxDelayToWaitTimeUnit;
    return this;
  }

  /**
   * Set the delay between two computations of performances for channels or 0 if no stats are to be computed
   *
   * @param checkIntervalForStats delay between two computations of performances
   * @return a reference to this, so the API can be used fluently
   */
  public TrafficShapingOptions setCheckIntervalForStats(long checkIntervalForStats) {
    this.checkIntervalForStats = checkIntervalForStats;
    ObjectUtil.checkPositive(this.checkIntervalForStats, "checkIntervalForStats");
    return this;
  }

  /**
   * Set time unit for check interval for stats.
   *
   * @param checkIntervalForStatsTimeUnit check interval for stats time unit
   * @return a reference to this, so the API can be used fluently
   */
  public TrafficShapingOptions setCheckIntervalForStatsTimeUnit(TimeUnit checkIntervalForStatsTimeUnit) {
    this.maxDelayToWaitTimeUnit = maxDelayToWaitTimeUnit;
    return this;
  }

  /**
   * Set the maximum global write size in bytes per second allowed in the buffer globally for all channels before write
   * suspended is set. Default value is 400 MB
   *
   * @param peakOutboundGlobalBandwidth peak outbound bandwidth
   * @return a reference to this, so the API can be used fluently
   */
  public TrafficShapingOptions setPeakOutboundGlobalBandwidth(long peakOutboundGlobalBandwidth) {
    this.peakOutboundGlobalBandwidth = peakOutboundGlobalBandwidth;
    ObjectUtil.checkPositive(this.peakOutboundGlobalBandwidth , "peakOutboundGlobalBandwidth");
    return this;
  }

  /**
   * @return inbound bandwidth limit in bytes
   */
  public long getInboundGlobalBandwidth() {
    return inboundGlobalBandwidth;
  }

  /**
   * @return outbound bandwidth limit in byte
   */
  public long getOutboundGlobalBandwidth() {
    return outboundGlobalBandwidth;
  }

  /**
   * @return max outbound bandwdith limit in bytes
   */
  public long getPeakOutboundGlobalBandwidth() {
    return peakOutboundGlobalBandwidth;
  }

  /**
   * @return maximum delay to wait in case of traffic excess
   */
  public long getMaxDelayToWait() {
    return maxDelayToWait;
  }

  /**
   * @return maximum delay time unit
   */
  public TimeUnit getMaxDelayToWaitTimeUnit() {
    return maxDelayToWaitTimeUnit;
  }

  /**
   * @return delay between two computations of performances
   */
  public long getCheckIntervalForStats() {
    return checkIntervalForStats;
  }

  /**
   * @return check interval for stats time unit
   */
  public TimeUnit getCheckIntervalForStatsTimeUnit() {
    return checkIntervalForStatsTimeUnit;
  }
}
