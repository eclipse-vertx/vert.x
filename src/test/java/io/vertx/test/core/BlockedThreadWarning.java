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

import io.vertx.core.impl.BlockedThreadChecker;
import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static org.hamcrest.CoreMatchers.*;
import static org.junit.Assert.assertThat;

/**
 * @author Thomas Segismont
 */
public class BlockedThreadWarning implements TestRule {

  private boolean doTest;
  private String poolName;
  private long maxExecuteTime;
  private TimeUnit maxExecuteTimeUnit;
  private List<String> logs = new ArrayList<>(); // guarded by this

  public synchronized void expectMessage(String poolName, long maxExecuteTime, TimeUnit maxExecuteTimeUnit) {
    doTest = true;
    this.poolName = poolName;
    this.maxExecuteTime = maxExecuteTime;
    this.maxExecuteTimeUnit = maxExecuteTimeUnit;
  }

  @Override
  public Statement apply(Statement base, Description description) {
    return new Statement() {
      @Override
      public void evaluate() throws Throwable {
        synchronized (BlockedThreadWarning.this) {
          logs.clear();
        }
        Logger logger = Logger.getLogger(BlockedThreadChecker.class.getName());
        Handler handler = new Handler() {
          public void publish(LogRecord record) {
            synchronized (BlockedThreadWarning.this) {
              logs.add(record.getMessage());
            }
          }
          public void flush() { }
          public void close() throws SecurityException { }
        };
        logger.addHandler(handler);
        try {
          base.evaluate();
          doTest();
        } finally {
          logger.removeHandler(handler);
        }
      }
    };
  }

  private synchronized void doTest() {
    if (!doTest) {
      return;
    }
    assertThat(logs, hasItem(allOf(
      containsString(" has been blocked for "),
      containsString(" time limit is " + maxExecuteTimeUnit.toMillis(maxExecuteTime) + " ms"),
      containsString("Thread[" + poolName + "-"))
    ));
  }
}
