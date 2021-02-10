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
import java.util.Arrays;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import org.hamcrest.Matcher;
import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;

/**
 * A generic log scanner used by testcases to check that messages have appeared
 * in the JUnit test logs.
 */
public class LogContainsRule implements TestRule {

  protected Logger logger; // which logger to scan
  protected Level level; // the log level required to find what we are looking for
  protected String[] msgsRequired; // what we are looking for
  protected boolean doTest; // toggle testing on and off if needed
  protected Matcher<String>[] matchers = null; // set null to refresh the matchers

  private List<String> logStrings = new ArrayList<>(); // guarded by this

  /**
   * Allow default construction in subclasses. If using the default constructor,
   * set the {@code logger} and {@code msgsRequired} fields and also, if not OK at
   * the default level of logging detail, the {@code level} field to ensure your
   * required log messages are generated. To make checking occur, you must also
   * set {@code doTest} which defaults to false.
   */
  public LogContainsRule() {
  }

  /**
   * @param loggingClass   what logger to look in
   * @param level          logging log level will be set to this or null to
   *                       default
   * @param logMustContain an array of Strings that will be searched for
   */
  public LogContainsRule(Class loggingClass, Level level, String... logMustContain) {
    logger = Logger.getLogger(loggingClass.getName());
    this.level = level;
    this.msgsRequired = logMustContain;
    doTest = true;
  }

  @Override
  public Statement apply(Statement base, Description description) {
    return new Statement() {
      @Override
      public void evaluate() throws Throwable {
        synchronized (LogContainsRule.this) {
          logStrings.clear();
          if (level != null)
            logger.setLevel(level);
        }
        Handler handler = new Handler() {
          public void publish(LogRecord record) {
            synchronized (LogContainsRule.this) {
              logStrings.add(record.getMessage());
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
    if (!doTest) {
      return;
    } else {
      if (matchers == null) {
        matchers = constructMatchers();
      }
      assertThat(logStrings, hasItem(allOf(matchers)));
    }
  }

  /**
   * This method is run once, and only if the log checking is going to occur for
   * this test fixture. The intention is that it can be overridden by subclasses
   * to allow for lazy construction of the matchers when they are actually needed.
   * The default implementation will just return a set of matchers that checks
   * each of the strings in the msgs field are found in the log.
   * 
   * @return an array of matchers, all of which must be found
   */
  protected Matcher[] constructMatchers() {
    return Arrays.stream(msgsRequired).map(s -> containsString(s)).toArray(Matcher[]::new);
  }

}
