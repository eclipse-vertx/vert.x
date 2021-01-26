/*
 * Copyright (c) 2011-2020 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */

package io.vertx.test.core;

import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.hasItem;
import static org.junit.Assert.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;

/**
 * A generic log scanner used by testcases to check that messages have appeared
 * in the JUnit test logs.
 *
 */
public class LogContainsRule implements TestRule {

  private Logger logger;
  private List<String> logs = new ArrayList<>(); // guarded by this
  private String logMustContainString;

  public LogContainsRule(Class loggingClass, String logMustContain) {
    this.logger = Logger.getLogger(loggingClass.getName());
    this.logMustContainString = logMustContain;
  }

  @Override
  public Statement apply(Statement base, Description description) {
    return new Statement() {
      @Override
      public void evaluate() throws Throwable {
        synchronized (LogContainsRule.this) {
          logs.clear();
          logger.setLevel(Level.ALL);
        }
        Handler handler = new Handler() {
          public void publish(LogRecord record) {
            synchronized (LogContainsRule.this) {
              logs.add(record.getMessage());
            }
          }

          public void flush() {
          }

          public void close() throws SecurityException {
          }
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
    assertThat(logs, hasItem(allOf(containsString(logMustContainString))));
  }
}
