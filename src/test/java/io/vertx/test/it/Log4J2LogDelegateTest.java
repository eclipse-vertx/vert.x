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

package io.vertx.test.it;

import io.vertx.core.logging.Log4j2LogDelegateFactory;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.core.spi.logging.LogDelegate;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.IOException;

import static org.junit.Assert.*;

/**
 * Theses test checks the Log4J 2 log delegate.
 *
 * @author <a href="http://escoffier.me">Clement Escoffier</a>
 */
public class Log4J2LogDelegateTest {

  private static StreamRecording recording;

  @BeforeClass
  public static void initialize() throws IOException {
    // Clear value.
    System.setProperty("vertx.logger-delegate-factory-class-name", Log4j2LogDelegateFactory.class.getName());
    LoggerFactory.initialise();
    recording = new StreamRecording();
  }

  @AfterClass
  public static void terminate() {
    System.clearProperty("vertx.logger-delegate-factory-class-name");
    recording.terminate();
  }

  @Test
  public void testDelegateUnwrap() {
    Logger logger = LoggerFactory.getLogger("my-log4j2-logger");
    LogDelegate delegate = logger.getDelegate();
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

    result = recording.execute(() -> {
      logger.info("hello {} and {}", "Paulo", "Julien");
    });
    assertTrue(result.contains("hello Paulo and Julien"));

    result = recording.execute(() -> {
      logger.info("hello {}", "vert.x");
    });
    String expected = "hello vert.x";
    assertTrue("Was expected <" + result + "> to contain <" + expected + ">" , result.contains(expected));

    result = recording.execute(() -> {
      logger.info("hello {} - {}", "vert.x");
    });
    assertTrue(result.contains("hello vert.x - {}"));

    result = recording.execute(() -> {
      logger.info("hello {} {}", "vert.x", "foo");
    });
    assertTrue(result.contains("hello vert.x foo"));

    result = recording.execute(() -> {
      logger.info("{}, an exception has been thrown", new IllegalStateException(), "Luke");
    });
    assertTrue(result.contains("Luke, an exception has been thrown"));
    assertTrue(result.contains("java.lang.IllegalStateException"));

    result = recording.execute(() -> {
      logger.info("{}, an exception has been thrown", "Luke", new IllegalStateException());
    });
    assertTrue(result.contains("Luke, an exception has been thrown"));
    assertTrue(result.contains("java.lang.IllegalStateException"));
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

    result = recording.execute(() -> {
      logger.error("hello {} and {}", "Paulo", "Julien");
    });
    assertTrue(result.contains("hello Paulo and Julien"));

    result = recording.execute(() -> {
      logger.error("hello {}", "vert.x");
    });
    assertTrue(result.contains("hello vert.x"));

    result = recording.execute(() -> {
      logger.error("hello {} - {}", "vert.x");
    });
    assertTrue(result.contains("hello vert.x - {}"));

    result = recording.execute(() -> {
      logger.error("hello {} {}", "vert.x", "foo");
    });
    String expected = "hello vert.x foo";
    assertTrue("Was expected <" + result + "> to contain <" + expected + ">" , result.contains(expected));

    result = recording.execute(() -> {
      logger.error("{}, an exception has been thrown", new IllegalStateException(), "Luke");
    });
    assertTrue(result.contains("Luke, an exception has been thrown"));
    assertTrue(result.contains("java.lang.IllegalStateException"));

    result = recording.execute(() -> {
      logger.error("{}, an exception has been thrown", "Luke", new IllegalStateException());
    });
    assertTrue(result.contains("Luke, an exception has been thrown"));
    assertTrue(result.contains("java.lang.IllegalStateException"));
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

    result = recording.execute(() -> {
      logger.warn("hello {} and {}", "Paulo", "Julien");
    });
    assertTrue(result.contains("hello Paulo and Julien"));

    result = recording.execute(() -> {
      logger.warn("hello {}", "vert.x");
    });
    assertTrue(result.contains("hello vert.x"));

    result = recording.execute(() -> {
      logger.warn("hello {} - {}", "vert.x");
    });
    assertTrue(result.contains("hello vert.x - {}"));

    result = recording.execute(() -> {
      logger.warn("hello {} {}", "vert.x", "foo");
    });
    String expected = "hello vert.x foo";
    assertTrue("Was expected <" + result + "> to contain <" + expected + ">" , result.contains(expected));

    result = recording.execute(() -> {
      logger.warn("{}, an exception has been thrown", new IllegalStateException(), "Luke");
    });
    assertTrue(result.contains("Luke, an exception has been thrown"));
    assertTrue(result.contains("java.lang.IllegalStateException"));

    result = recording.execute(() -> {
      logger.warn("{}, an exception has been thrown", "Luke", new IllegalStateException());
    });
    assertTrue(result.contains("Luke, an exception has been thrown"));
    assertTrue(result.contains("java.lang.IllegalStateException"));
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
