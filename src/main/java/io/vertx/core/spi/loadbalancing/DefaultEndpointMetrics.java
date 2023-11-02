/*
 * Copyright (c) 2011-2023 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package io.vertx.core.spi.loadbalancing;

import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

public class DefaultEndpointMetrics implements EndpointMetrics<RequestMetric> {

  private final LongAdder numberOfInflightRequests = new LongAdder();
  private final LongAdder numberOfRequests = new LongAdder();
  private final LongAdder numberOfFailures = new LongAdder();
  private final AtomicLong minResponseTime = new AtomicLong(Long.MAX_VALUE);
  private final AtomicLong maxResponseTime = new AtomicLong(0);

  @Override
  public RequestMetric initiateRequest() {
    numberOfInflightRequests.increment();
    numberOfRequests.increment();
    return new RequestMetric();
  }

  @Override
  public void reportFailure(RequestMetric metric, Throwable failure) {
    if (metric.failure == null) {
      metric.failure = failure;
      numberOfInflightRequests.decrement();
      numberOfFailures.increment();
    }
  }

  @Override
  public void reportRequestBegin(RequestMetric metric) {
    metric.requestBegin = System.currentTimeMillis();
  }

  @Override
  public void reportRequestEnd(RequestMetric metric) {
    metric.requestEnd = System.currentTimeMillis();
  }

  @Override
  public void reportResponseBegin(RequestMetric metric) {
    metric.responseBegin = System.currentTimeMillis();
  }

  @Override
  public void reportResponseEnd(RequestMetric metric) {
    metric.responseEnd = System.currentTimeMillis();
    if (metric.failure == null) {
      reportRequestMetric(metric);
      numberOfInflightRequests.decrement();
    }
  }

  void reportRequestMetric(RequestMetric metric) {
    long responseTime = metric.responseEnd - metric.requestBegin;
    while (true) {
      long val = minResponseTime.get();
      if (responseTime >= val || minResponseTime.compareAndSet(val, responseTime)) {
        break;
      }
    }
    while (true) {
      long val = maxResponseTime.get();
      if (responseTime <= val || maxResponseTime.compareAndSet(val, responseTime)) {
        break;
      }
    }
  }

  /**
   * @return the number of inflight requests
   */
  public int numberOfInflightRequests() {
    return numberOfInflightRequests.intValue();
  }

  /**
   * @return the total number of requests
   */
  public int numberOfRequests() {
    return numberOfRequests.intValue();
  }

  /**
   * @return the total number of failures
   */
  public int numberOfFailures() {
    return numberOfFailures.intValue();
  }

  public int minResponseTime() {
    return minResponseTime.intValue();
  }

  public int maxResponseTime() {
    return maxResponseTime.intValue();
  }

}
