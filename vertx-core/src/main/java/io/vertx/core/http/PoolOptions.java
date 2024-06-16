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
package io.vertx.core.http;

import io.vertx.codegen.annotations.DataObject;
import io.vertx.codegen.json.annotations.JsonGen;
import io.vertx.core.impl.Arguments;
import io.vertx.core.json.JsonObject;

/**
 * Options configuring a {@link HttpClient} pool.
 *
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
@DataObject
@JsonGen(publicConverter = false)
public class PoolOptions {

  /**
   * The default maximum number of HTTP/1 connections a client will pool = 5
   */
  public static final int DEFAULT_MAX_POOL_SIZE = 5;

  /**
   * The default maximum number of connections an HTTP/2 client will pool = 1
   */
  public static final int DEFAULT_HTTP2_MAX_POOL_SIZE = 1;

  /**
   * Default max wait queue size = -1 (unbounded)
   */
  public static final int DEFAULT_MAX_WAIT_QUEUE_SIZE = -1;

  /**
   * Default pool cleaner period = 1000 ms (1 second)
   */
  public static final int DEFAULT_POOL_CLEANER_PERIOD = 1000;

  /**
   * Default pool event loop size = 0 (reuse current event-loop)
   */
  public static final int DEFAULT_POOL_EVENT_LOOP_SIZE = 0;

  private int http1MaxSize;
  private int http2MaxSize;
  private int cleanerPeriod;
  private int eventLoopSize;
  private int maxWaitQueueSize;

  /**
   * Default constructor
   */
  public PoolOptions() {
    http1MaxSize = DEFAULT_MAX_POOL_SIZE;
    http2MaxSize = DEFAULT_HTTP2_MAX_POOL_SIZE;
    cleanerPeriod = DEFAULT_POOL_CLEANER_PERIOD;
    eventLoopSize = DEFAULT_POOL_EVENT_LOOP_SIZE;
    maxWaitQueueSize = DEFAULT_MAX_WAIT_QUEUE_SIZE;
  }

  /**
   * Copy constructor
   *
   * @param other  the options to copy
   */
  public PoolOptions(PoolOptions other) {
    this.http1MaxSize = other.http1MaxSize;
    this.http2MaxSize = other.http2MaxSize;
    this.cleanerPeriod = other.cleanerPeriod;
    this.eventLoopSize = other.eventLoopSize;
    this.maxWaitQueueSize = other.maxWaitQueueSize;
  }

  /**
   * Constructor to create an options from JSON
   *
   * @param json  the JSON
   */
  public PoolOptions(JsonObject json) {
    PoolOptionsConverter.fromJson(json, this);
  }

  /**
   * Get the maximum pool size for HTTP/1.x connections
   *
   * @return  the maximum pool size
   */
  public int getHttp1MaxSize() {
    return http1MaxSize;
  }

  /**
   * Set the maximum pool size for HTTP/1.x connections
   *
   * @param http1MaxSize  the maximum pool size
   * @return a reference to this, so the API can be used fluently
   */
  public PoolOptions setHttp1MaxSize(int http1MaxSize) {
    if (http1MaxSize < 1) {
      throw new IllegalArgumentException("maxPoolSize must be > 0");
    }
    this.http1MaxSize = http1MaxSize;
    return this;
  }

  /**
   * Get the maximum pool size for HTTP/2 connections
   *
   * @return  the maximum pool size
   */
  public int getHttp2MaxSize() {
    return http2MaxSize;
  }

  /**
   * Set the maximum pool size for HTTP/2 connections
   *
   * @param max  the maximum pool size
   * @return a reference to this, so the API can be used fluently
   */
  public PoolOptions setHttp2MaxSize(int max) {
    if (max < 1) {
      throw new IllegalArgumentException("http2MaxPoolSize must be > 0");
    }
    this.http2MaxSize = max;
    return this;
  }

  /**
   * @return the connection pool cleaner period in ms.
   */
  public int getCleanerPeriod() {
    return cleanerPeriod;
  }

  /**
   * Set the connection pool cleaner period in milli seconds, a non positive value disables expiration checks and connections
   * will remain in the pool until they are closed.
   *
   * @param cleanerPeriod the pool cleaner period
   * @return a reference to this, so the API can be used fluently
   */
  public PoolOptions setCleanerPeriod(int cleanerPeriod) {
    this.cleanerPeriod = cleanerPeriod;
    return this;
  }

  /**
   * @return the max number of event-loop a pool will use, the default value is {@code 0} which implies
   * to reuse the current event-loop
   */
  public int getEventLoopSize() {
    return eventLoopSize;
  }

  /**
   * Set the number of event-loop the pool use.
   *
   * <ul>
   *   <li>when the size is {@code 0}, the client pool will use the current event-loop</li>
   *   <li>otherwise the client will create and use its own event loop</li>
   * </ul>
   *
   * The default size is {@code 0}.
   *
   * @param eventLoopSize  the new size
   * @return a reference to this, so the API can be used fluently
   */
  public PoolOptions setEventLoopSize(int eventLoopSize) {
    Arguments.require(eventLoopSize >= 0, "poolEventLoopSize must be >= 0");
    this.eventLoopSize = eventLoopSize;
    return this;
  }

  /**
   * Set the maximum requests allowed in the wait queue, any requests beyond the max size will result in
   * a ConnectionPoolTooBusyException.  If the value is set to a negative number then the queue will be unbounded.
   * @param maxWaitQueueSize the maximum number of waiting requests
   * @return a reference to this, so the API can be used fluently
   */
  public PoolOptions setMaxWaitQueueSize(int maxWaitQueueSize) {
    this.maxWaitQueueSize = maxWaitQueueSize;
    return this;
  }

  /**
   * Returns the maximum wait queue size
   * @return the maximum wait queue size
   */
  public int getMaxWaitQueueSize() {
    return maxWaitQueueSize;
  }

  public JsonObject toJson() {
    JsonObject json = new JsonObject();
    PoolOptionsConverter.toJson(this, json);
    return json;
  }
}
