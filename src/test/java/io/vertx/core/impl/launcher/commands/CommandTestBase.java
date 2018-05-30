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

package io.vertx.core.impl.launcher.commands;

import io.vertx.core.Vertx;
import io.vertx.core.impl.launcher.VertxCommandLauncher;
import io.vertx.core.logging.JULLogDelegateFactory;
import io.vertx.test.core.AsyncTestBase;
import org.junit.After;
import org.junit.Before;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import java.util.logging.LogManager;

import static org.assertj.core.api.Assertions.assertThat;

public class CommandTestBase {

  protected static final PrintStream originalOutputPrintStream = System.out;
  protected static final PrintStream originalErrorPrintStream = System.err;

  protected ByteArrayOutputStream output;
  protected ByteArrayOutputStream error;

  protected PrintStream os;
  protected PrintStream err;
  protected VertxCommandLauncher cli;

  @Before
  public void setUp() throws IOException {
    cli = new VertxCommandLauncher();
    output = new ByteArrayOutputStream();
    error = new ByteArrayOutputStream();

    // We need to reset the log configuration to recreate the logger
    // Indeed print stream may have been cached.
    LogManager.getLogManager().reset();
    JULLogDelegateFactory.loadConfig();
  }

  @After
  public void tearDown() throws InterruptedException {
    stop();

    if (os != null) {
      os.close();
    }
    if (err != null) {
      err.close();
    }

    try {
      output.close();
    } catch (IOException e) {
      // Ignore it.
    }

    try {
      error.close();
    } catch (IOException e) {
      // Ignore it.
    }
  }

  public void record() {
    os = new PrintStream(output);
    err = new PrintStream(error);

    System.setOut(os);
    System.setErr(err);
  }

  public void stop() {
    if (System.out != originalOutputPrintStream) {
      System.setOut(originalOutputPrintStream);
    }
    if (System.err != originalErrorPrintStream) {
      System.setErr(originalErrorPrintStream);
    }
  }

  protected void assertWaitUntil(BooleanSupplier supplier) {
    AsyncTestBase.assertWaitUntil(supplier, 20000);
  }

  protected void assertWaitUntil(BooleanSupplier supplier, String reason) {
    AsyncTestBase.assertWaitUntil(supplier, 20000, reason);
  }

  protected void assertWaitUntil(BooleanSupplier supplier, long timeout) {
    AsyncTestBase.assertWaitUntil(supplier, timeout);
  }

  protected void awaitLatch(CountDownLatch latch) throws InterruptedException {
    assertThat(latch.await(10, TimeUnit.SECONDS)).isTrue();
  }

  protected void close(Vertx vertx) throws InterruptedException {
    if (vertx == null) {
      return;
    }
    CountDownLatch latch = new CountDownLatch(1);
    vertx.close(ar -> {
      latch.countDown();
    });
    awaitLatch(latch);
  }

}
