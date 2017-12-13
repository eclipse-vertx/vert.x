/*
 * Copyright (c) 2011-2017 The original author or authors
 * ------------------------------------------------------
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * and Apache License v2.0 which accompanies this distribution.
 *
 *     The Eclipse Public License is available at
 *     http://www.eclipse.org/legal/epl-v10.html
 *
 *     The Apache License v2.0 is available at
 *     http://www.opensource.org/licenses/apache2.0.php
 *
 * You may elect to redistribute this code under either of these licenses.
 */

package io.vertx.test.core;

import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static java.util.concurrent.TimeUnit.*;
import static org.hamcrest.CoreMatchers.*;
import static org.junit.Assert.*;

/**
 * @author Thomas Segismont
 */
public class BlockedThreadWarning implements TestRule {

  private boolean doTest;
  private String poolName;
  private long maxExecuteTime;

  public synchronized void expectMessage(String poolName, long maxExecuteTime) {
    doTest = true;
    this.poolName = poolName;
    this.maxExecuteTime = maxExecuteTime;
  }

  @Override
  public Statement apply(Statement base, Description description) {
    return new Statement() {
      @Override
      public void evaluate() throws Throwable {
        base.evaluate();
        doTest(description);
      }
    };
  }

  private synchronized void doTest(Description description) throws IOException {
    if (!doTest) {
      return;
    }
    List<String> logs = getLogs(description.getTestClass().getSimpleName(), description.getMethodName());
    assertThat(logs, hasItem(allOf(
      containsString(" has been blocked for "),
      containsString(" time limit is " + MILLISECONDS.convert(maxExecuteTime, NANOSECONDS)),
      containsString("Thread[" + poolName + "-"))
    ));
  }

  private List<String> getLogs(String testClass, String methodName) throws IOException {
    String startingTestMessage = "Starting test: " + testClass + "#" + methodName;
    List<String> logs = new ArrayList<>();
    AtomicBoolean reachedTest = new AtomicBoolean();
    Files.lines(Paths.get(System.getProperty("java.io.tmpdir"), "vertx.log"))
      .forEach(line -> {
        if (!reachedTest.get()) {
          if (line.contains(startingTestMessage)) {
            reachedTest.set(true);
          }
        } else {
          logs.add(line);
        }
      });
    return logs;
  }
}
