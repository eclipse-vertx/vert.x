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

package io.vertx.test.it;

import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.core.spi.logging.LogDelegate;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.IOException;

import static org.junit.Assert.*;

/**
 * These tests check the JUL log delegate. It analyses the output, so any change in the configuration may break the
 * tests.
 *
 * TODO Ignore these tests for now, they break the CI, because the logging has already been initialized.
 *
 * @author <a href="http://escoffier.me">Clement Escoffier</a>
 */
public class JULLogDelegateTest {

  private static Recording recording;

  @BeforeClass
  public static void initialize() throws IOException {
    // Clear value.
    System.clearProperty("vertx.logger-delegate-factory-class-name");
    LoggerFactory.initialise();
    recording = new Recording();
  }

  @Test
  public void testDelegateUnwrap() {
    Logger logger = LoggerFactory.getLogger("my-jul-logger");
    LogDelegate delegate = logger.getDelegate();
    assertNotNull("Delegate is null", delegate);
    try {
      java.util.logging.Logger unwrapped = (java.util.logging.Logger) delegate.unwrap();
      assertNotNull("Unwrapped is null", unwrapped);
    } catch (ClassCastException e) {
      fail("Unexpected unwrapped type: " + e.getMessage());
    }
  }

  @Test
  public void testInfo() {
    String result = recording.execute(() -> {
      Logger logger = LoggerFactory.getLogger("my-jul-logger");
      logger.info("hello");
    });
    assertTrue(result.contains("hello"));
    result = recording.execute(() -> {
      Logger logger = LoggerFactory.getLogger("my-jul-logger");
      logger.info("exception", new NullPointerException());
    });

    assertTrue(result.contains("exception"));
    assertTrue(result.contains("java.lang.NullPointerException"));

    result = recording.execute(() -> {
      Logger logger = LoggerFactory.getLogger("my-jul-logger");
      logger.info("hello {0} and {1}", "Paulo", "Julien");
    });
    assertTrue(result.contains("hello Paulo and Julien"));

    result = recording.execute(() -> {
      Logger logger = LoggerFactory.getLogger("my-jul-logger");
      logger.info("hello {0}", "vert.x");
    });
    assertTrue(result.contains("hello vert.x"));

    result = recording.execute(() -> {
      Logger logger = LoggerFactory.getLogger("my-jul-logger");
      logger.info("hello {0} - {1}", "vert.x");
    });
    assertTrue(result.contains("hello vert.x - {1}"));

    result = recording.execute(() -> {
      Logger logger = LoggerFactory.getLogger("my-jul-logger");
      logger.info("hello {0}", "vert.x", "foo");
    });
    assertTrue(result.contains("hello vert.x"));

    result = recording.execute(() -> {
      Logger logger = LoggerFactory.getLogger("my-jul-logger");
      logger.info("{0}, an exception has been thrown", new IllegalStateException(), "Luke");
    });
    assertTrue(result.contains("Luke, an exception has been thrown"));
    assertTrue(result.contains("java.lang.IllegalStateException"));

    result = recording.execute(() -> {
      Logger logger = LoggerFactory.getLogger("my-jul-logger");
      logger.info("{0}, an exception has been thrown", "Luke", new IllegalStateException());
    });
    assertTrue(result.contains("Luke, an exception has been thrown"));
    assertTrue(result.contains("java.lang.IllegalStateException"));
  }

  @Test
  public void testError() {
    String result = recording.execute(() -> {
      Logger logger = LoggerFactory.getLogger("my-jul-logger");
      logger.error("hello");
    });
    assertTrue(result.contains("hello"));
    result = recording.execute(() -> {
      Logger logger = LoggerFactory.getLogger("my-jul-logger");
      logger.error("exception", new NullPointerException());
    });
    assertTrue(result.contains("exception"));
    assertTrue(result.contains("java.lang.NullPointerException"));

    result = recording.execute(() -> {
      Logger logger = LoggerFactory.getLogger("my-jul-logger");
      logger.error("hello {0} and {1}", "Paulo", "Julien");
    });
    assertTrue(result.contains("hello Paulo and Julien"));

    result = recording.execute(() -> {
      Logger logger = LoggerFactory.getLogger("my-jul-logger");
      logger.error("hello {0}", "vert.x");
    });
    assertTrue(result.contains("hello vert.x"));

    result = recording.execute(() -> {
      Logger logger = LoggerFactory.getLogger("my-jul-logger");
      logger.error("hello {0} - {1}", "vert.x");
    });
    assertTrue(result.contains("hello vert.x - {1}"));

    result = recording.execute(() -> {
      Logger logger = LoggerFactory.getLogger("my-jul-logger");
      logger.error("hello {0}", "vert.x", "foo");
    });
    assertTrue(result.contains("hello vert.x"));

    result = recording.execute(() -> {
      Logger logger = LoggerFactory.getLogger("my-jul-logger");
      logger.error("{0}, an exception has been thrown", new IllegalStateException(), "Luke");
    });
    assertTrue(result.contains("Luke, an exception has been thrown"));
    assertTrue(result.contains("java.lang.IllegalStateException"));

    result = recording.execute(() -> {
      Logger logger = LoggerFactory.getLogger("my-jul-logger");
      logger.error("{0}, an exception has been thrown", "Luke", new IllegalStateException());
    });
    assertTrue(result.contains("Luke, an exception has been thrown"));
    assertTrue(result.contains("java.lang.IllegalStateException"));
  }

  @Test
  public void testWarning() {
    String result = recording.execute(() -> {
      Logger logger = LoggerFactory.getLogger("my-jul-logger");
      logger.warn("hello");
    });
    assertTrue(result.contains("hello"));
    result = recording.execute(() -> {
      Logger logger = LoggerFactory.getLogger("my-jul-logger");
      logger.warn("exception", new NullPointerException());
    });
    assertTrue(result.contains("exception"));
    assertTrue(result.contains("java.lang.NullPointerException"));

    result = recording.execute(() -> {
      Logger logger = LoggerFactory.getLogger("my-jul-logger");
      logger.warn("hello {0} and {1}", "Paulo", "Julien");
    });
    assertTrue(result.contains("hello Paulo and Julien"));

    result = recording.execute(() -> {
      Logger logger = LoggerFactory.getLogger("my-jul-logger");
      logger.warn("hello {0}", "vert.x");
    });
    assertTrue(result.contains("hello vert.x"));

    result = recording.execute(() -> {
      Logger logger = LoggerFactory.getLogger("my-jul-logger");
      logger.warn("hello {0} - {1}", "vert.x");
    });
    assertTrue(result.contains("hello vert.x - {1}"));

    result = recording.execute(() -> {
      Logger logger = LoggerFactory.getLogger("my-jul-logger");
      logger.warn("hello {0}", "vert.x", "foo");
    });
    assertTrue(result.contains("hello vert.x"));

    result = recording.execute(() -> {
      Logger logger = LoggerFactory.getLogger("my-jul-logger");
      logger.warn("{0}, an exception has been thrown", new IllegalStateException(), "Luke");
    });
    assertTrue(result.contains("Luke, an exception has been thrown"));
    assertTrue(result.contains("java.lang.IllegalStateException"));

    result = recording.execute(() -> {
      Logger logger = LoggerFactory.getLogger("my-jul-logger");
      logger.warn("{0}, an exception has been thrown", "Luke", new IllegalStateException());
    });
    assertTrue(result.contains("Luke, an exception has been thrown"));
    assertTrue(result.contains("java.lang.IllegalStateException"));
  }
}
