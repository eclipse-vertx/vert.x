/*
 *  Copyright (c) 2011-2013 The original author or authors
 *  ------------------------------------------------------
 *  All rights reserved. This program and the accompanying materials
 *  are made available under the terms of the Eclipse Public License v1.0
 *  and Apache License v2.0 which accompanies this distribution.
 *
 *       The Eclipse Public License is available at
 *       http://www.eclipse.org/legal/epl-v10.html
 *
 *       The Apache License v2.0 is available at
 *       http://www.opensource.org/licenses/apache2.0.php
 *
 *  You may elect to redistribute this code under either of these licenses.
 */
package io.vertx.core.impl.launcher.commands;

import io.vertx.core.Vertx;
import io.vertx.core.impl.launcher.VertxCommandLauncher;
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
    LogManager.getLogManager().readConfiguration();
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

  protected void waitUntil(BooleanSupplier supplier) {
    waitUntil(supplier, 10000);
  }

  protected void waitUntil(BooleanSupplier supplier, long timeout) {
    long start = System.currentTimeMillis();
    while (true) {
      if (supplier.getAsBoolean()) {
        break;
      }
      try {
        Thread.sleep(10);
      } catch (InterruptedException ignore) {
        Thread.currentThread().interrupt();
      }
      long now = System.currentTimeMillis();
      if (now - start > timeout) {
        throw new IllegalStateException("Timed out");
      }
    }
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
