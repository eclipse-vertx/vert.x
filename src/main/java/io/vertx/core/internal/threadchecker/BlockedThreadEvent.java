/*
 * Copyright (c) 2011-2022 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */

package io.vertx.core.internal.threadchecker;

/**
 * A class containing status details about how long a particular thread has been blocked,
 * and how long it is allowed to be blocked before warnings start getting logged. All times
 * and durations are in nanoseconds.
 */
public class BlockedThreadEvent {

  private final Thread thread;
  private final long maxExecTime;
  private final long duration;
  private final long warningExceptionTime;

  /**
   * Create an instance of BlockedThreadEvent
   *
   * @param thread The thread being checked
   * @param duration The duration the thread has been blocked, in nanoseconds
   * @param maxExecTime The max execution time the thread is allowed, in nanoseconds
   * @param warningExceptionTime The max time a thread can be blocked before stack traces get logged, in nanoseconds
   */
  public BlockedThreadEvent(Thread thread, long duration, long maxExecTime, long warningExceptionTime) {
    this.thread = thread;
    this.duration = duration;
    this.maxExecTime = maxExecTime;
    this.warningExceptionTime = warningExceptionTime;
  }

  public Thread thread() {
    return thread;
  }

  public long maxExecTime() {
    return maxExecTime;
  }

  public long duration() {
    return duration;
  }

  public long warningExceptionTime() {
    return warningExceptionTime;
  }

  @Override
  public String toString() {
    return "BlockedThreadEvent(thread=" + thread.getName() + ",duration=" + duration + ",maxExecTime=" + maxExecTime + ")";
  }
}
