/*
 * Copyright (c) 2011-2018 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */

package io.vertx.test.core;

import static org.hamcrest.CoreMatchers.containsString;

import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import org.hamcrest.Matcher;

import io.vertx.core.impl.BlockedThreadChecker;

/**
 * A log checker that specializes in task blocked messages.
 * 
 * @author Thomas Segismont (first version)
 */
public class BlockedThreadWarning extends LogContainsRule {

  private String poolName;
  private long maxExecuteTime;
  private TimeUnit maxExecuteTimeUnit;

  public BlockedThreadWarning() {
    logger = Logger.getLogger(BlockedThreadChecker.class.getName());
  }

  /**
   * Setup the search, the instance is recyclable via this method
   * 
   * @param poolName           which pool is expected to have a blocked task
   * @param maxExecuteTime     the expected time limit
   * @param maxExecuteTimeUnit the expected time limit units
   */
  public synchronized void expectMessage(String poolName, long maxExecuteTime, TimeUnit maxExecuteTimeUnit) {
    this.poolName = poolName;
    this.maxExecuteTime = maxExecuteTime;
    this.maxExecuteTimeUnit = maxExecuteTimeUnit;
    doTest = true; // switch testing on
    matchers = null; // reset what is looked
  }

  @Override
  public Matcher[] constructMatchers() {
    Matcher<String> blocked = containsString(" has been blocked for ");
    Matcher<String> timeLimit = containsString(" time limit is " + maxExecuteTimeUnit.toMillis(maxExecuteTime) + " ms");
    Matcher<String> pool = containsString("Thread[" + poolName + "-");
    return new Matcher[] { blocked, timeLimit, pool };
  }
}
