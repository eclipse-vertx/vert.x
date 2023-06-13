/*
 * Copyright (c) 2011-2019 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */

package io.vertx.it;

import io.vertx.core.impl.logging.Logger;
import io.vertx.core.impl.logging.LoggerAdapter;
import io.vertx.core.impl.logging.LoggerFactory;
import io.vertx.core.spi.logging.LogDelegate;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.IOException;

import static org.junit.Assert.*;

/**
 * These test checks the Log4J 2 log delegate.
 *
 * @author <a href="http://escoffier.me">Clement Escoffier</a>
 */
public class Log4J2LogDelegateTest {

  private static StreamRecording recording;

  @BeforeClass
  public static void initialize() throws IOException {
    recording = new StreamRecording();
  }

  @AfterClass
  public static void terminate() {
    recording.terminate();
  }

  @Test
  public void testDelegateUnwrap() {
    Logger logger = LoggerFactory.getLogger("my-log4j2-logger");
    LogDelegate delegate = ((LoggerAdapter) logger).unwrap();
    assertNotNull("Delegate is null", delegate);
    try {
      org.apache.logging.log4j.Logger unwrapped = (org.apache.logging.log4j.Logger) delegate.unwrap();
      assertNotNull("Unwrapped is null", unwrapped);
    } catch (ClassCastException e) {
      fail("Unexpected unwrapped type: " + e.getMessage());
    }
  }

  @Test
  public void testInfo() {
    Logger logger = LoggerFactory.getLogger("my-log4j2-logger");
    String result = recording.execute(() -> {
      logger.info("hello");
    });
    assertTrue(result.contains("hello"));
    result = recording.execute(() -> {
      logger.info("exception", new NullPointerException());
    });
    assertTrue(result.contains("exception"));
    assertTrue(result.contains("java.lang.NullPointerException"));
  }

  @Test
  public void testError() {
    Logger logger = LoggerFactory.getLogger("my-log4j2-logger");
    String result = recording.execute(() -> {
      logger.error("hello");
    });
    assertTrue(result.contains("hello"));
    result = recording.execute(() -> {
      logger.error("exception", new NullPointerException());
    });
    assertTrue(result.contains("exception"));
    assertTrue(result.contains("java.lang.NullPointerException"));
  }

  @Test
  public void testWarning() {
    Logger logger = LoggerFactory.getLogger("my-log4j2-logger");
    String result = recording.execute(() -> {
      logger.warn("hello");
    });
    assertTrue(result.contains("hello"));
    result = recording.execute(() -> {
      logger.warn("exception", new NullPointerException());
    });
    assertTrue(result.contains("exception"));
    assertTrue(result.contains("java.lang.NullPointerException"));
  }

  // test that line numbers and method name are logged correctly
  // we use anonymous class instead of a lambda since we have to know the calling method name
  @Test
  public void testMethodName() {
    Logger logger = LoggerFactory.getLogger("my-log4j2-logger");
    String result = recording.execute(new Runnable() {
      @Override
      public void run() {
        logger.warn("hello");
      }
    });
    assertTrue(result.contains(".run:"));
  }

}
