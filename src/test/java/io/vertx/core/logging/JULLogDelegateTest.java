/*
 *  Copyright (c) 2011-2015 The original author or authors
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

package io.vertx.core.logging;

import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;

import java.io.IOException;

import static junit.framework.Assert.assertTrue;

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
    recording = new Recording();
  }

  @Test
  @Ignore
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
  @Ignore
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
  @Ignore
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