/*
 * Copyright (c) 2011-2017 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package io.vertx.core.queue;

import io.vertx.core.impl.Arguments;

/**
 * Queue creation options.
 */
public class QueueOptions {

  /**
   * The default maximum size = 16
   */
  public static final int DEFAULT_MAX_SIZE = 16;

  /**
   * The default maximum number of elements delivered per event loop tick = 16
   */
  public static final int DEFAULT_HIGH_WATER_MARK = 16;

  private int highWaterMark = DEFAULT_MAX_SIZE;
  private int maxElementsPerTick = DEFAULT_HIGH_WATER_MARK;

  public QueueOptions() {
  }

  /**
   * @return the queue high water mark
   */
  public int getHighWaterMark() {
    return highWaterMark;
  }

  /**
   * Set the queue high water mark.
   * <p/>
   * It must be a positive number.
   *
   * @param value the queue high water mark
   * @return a reference to this, so the API can be used fluently
   */
  public QueueOptions setHighWaterMark(int value) {
    Arguments.require(value >= 0, "highWaterMark " + value + " >= 0");
    highWaterMark = value;
    return this;
  }

  /**
   * @return the max number of elements delivered per event loop tick
   */
  public int getMaxElementsPerTick() {
    return maxElementsPerTick;
  }

  /**
   * Set the max number of elements delivered per event loop tick when the queue operates on a {@link io.vertx.core.Context}.
   * <p/>
   * It must be a strictly positive number.
   *
   * @param value the max number of elements delivered per event loop tick
   * @return a reference to this, so the API can be used fluently
   */
  public QueueOptions setMaxElementsPerTick(int value) {
    Arguments.require(value > 0, "maxElementsPerTick " + highWaterMark + " > 0");
    maxElementsPerTick = value;
    return this;
  }
}
