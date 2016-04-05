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

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.slf4j.impl.SimpleLogger;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.lang.reflect.Field;
import java.util.concurrent.Callable;

import static junit.framework.Assert.assertTrue;
import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertNotNull;

/**
 * Theses test checks the SLF4J log delegate. It assumes the binding used by SLF4J is slf4j-simple with the default
 * configuration. It injects a print stream to read the logged message. This is definitely a hack, but it's the only
 * way to test the output.
 *
 * @author <a href="http://escoffier.me">Clement Escoffier</a>
 */
public class SLF4JLogDelegateTest {


  @BeforeClass
  public static void initialize() throws IOException {
    System.setProperty("vertx.logger-delegate-factory-class-name", SLF4JLogDelegateFactory.class.getName());
    LoggerFactory.initialise();
  }

  @AfterClass
  public static void terminate() {
    System.clearProperty("vertx.logger-delegate-factory-class-name");
  }

  @Test
  public void testInfo() {
    Logger logger = LoggerFactory.getLogger("my-slf4j-logger");

    String result = record(() -> logger.info("hello"));
    assertContains("[main] INFO my-slf4j-logger - hello", result);

    result = record(() -> logger.info("exception", new NullPointerException()));
    assertTrue(result.contains("[main] INFO my-slf4j-logger - exception"));
    assertTrue(result.contains("java.lang.NullPointerException"));

    result = record(() -> logger.info("hello {} and {}", "Paulo", "Julien"));
    assertContains("[main] INFO my-slf4j-logger - hello Paulo and Julien", result);

    result = record(() -> logger.info("hello {}", "vert.x"));
    assertContains("[main] INFO my-slf4j-logger - hello vert.x", result);

    result = record(() -> logger.info("hello {} - {}", "vert.x"));
    assertContains("[main] INFO my-slf4j-logger - hello vert.x - {}", result);

    result = record(() -> logger.info("hello {}", "vert.x", "foo"));
    assertContains("[main] INFO my-slf4j-logger - hello vert.x", result);

    result = record(() -> logger.info("{}, an exception has been thrown", new IllegalStateException(), "Luke"));
    assertTrue(result.contains("[main] INFO my-slf4j-logger - Luke, an exception has been thrown"));
    assertTrue(result.contains("java.lang.IllegalStateException"));

    result = record(() -> logger.info("{}, an exception has been thrown", "Luke", new IllegalStateException()));
    assertTrue(result.contains("[main] INFO my-slf4j-logger - Luke, an exception has been thrown"));
    assertTrue(result.contains("java.lang.IllegalStateException"));
  }

  @Test
  public void testError() {
    Logger logger = LoggerFactory.getLogger("my-slf4j-logger");
    String result = record(() -> logger.error("hello"));
    assertContains("[main] ERROR my-slf4j-logger - hello", result);

    result = record(() -> logger.error("exception", new NullPointerException()));
    assertTrue(result.contains("[main] ERROR my-slf4j-logger - exception"));
    assertTrue(result.contains("java.lang.NullPointerException"));

    result = record(() -> logger.error("hello {} and {}", "Paulo", "Julien"));
    assertContains("[main] ERROR my-slf4j-logger - hello Paulo and Julien", result);

    result = record(() -> logger.error("hello {}", "vert.x"));
    assertContains("[main] ERROR my-slf4j-logger - hello vert.x", result);

    result = record(() -> logger.error("hello {} - {}", "vert.x"));
    assertContains("[main] ERROR my-slf4j-logger - hello vert.x - {}", result);

    result = record(() -> logger.error("hello {}", "vert.x", "foo"));
    assertContains("[main] ERROR my-slf4j-logger - hello vert.x", result);

    result = record(() -> logger.error("{}, an exception has been thrown", new IllegalStateException(), "Luke"));
    assertTrue(result.contains("[main] ERROR my-slf4j-logger - Luke, an exception has been thrown"));
    assertTrue(result.contains("java.lang.IllegalStateException"));

    result = record(() -> logger.error("{}, an exception has been thrown", "Luke", new IllegalStateException()));
    assertTrue(result.contains("[main] ERROR my-slf4j-logger - Luke, an exception has been thrown"));
    assertTrue(result.contains("java.lang.IllegalStateException"));
  }

  private void assertContains(String expectedExcerpt, String object) {
    assertNotNull(object);
    assertTrue(object.contains(expectedExcerpt));
  }

  @Test
  public void testWarning() {
    Logger logger = LoggerFactory.getLogger("my-slf4j-logger");

    String result = record(() -> logger.warn("hello"));
    assertContains("[main] WARN my-slf4j-logger - hello", result);

    result = record(() -> logger.warn("exception", new NullPointerException()));
    assertTrue(result.contains("[main] WARN my-slf4j-logger - exception"));
    assertTrue(result.contains("java.lang.NullPointerException"));

    result = record(() -> logger.warn("hello {} and {}", "Paulo", "Julien"));
    assertContains("[main] WARN my-slf4j-logger - hello Paulo and Julien", result);

    result = record(() -> logger.warn("hello {}", "vert.x"));
    assertContains("[main] WARN my-slf4j-logger - hello vert.x", result);

    result = record(() -> logger.warn("hello {} - {}", "vert.x"));
    assertContains("[main] WARN my-slf4j-logger - hello vert.x - {}", result);

    result = record(() -> logger.warn("hello {}", "vert.x", "foo"));
    assertContains("[main] WARN my-slf4j-logger - hello vert.x", result);

    result = record(() -> logger.warn("{}, an exception has been thrown", new IllegalStateException(), "Luke"));
    assertTrue(result.contains("[main] WARN my-slf4j-logger - Luke, an exception has been thrown"));
    assertTrue(result.contains("java.lang.IllegalStateException"));

    result = record(() -> logger.warn("{}, an exception has been thrown", "Luke", new IllegalStateException()));
    assertTrue(result.contains("[main] WARN my-slf4j-logger - Luke, an exception has been thrown"));
    assertTrue(result.contains("java.lang.IllegalStateException"));
  }

  private void setStream(PrintStream stream) {
    try {
      Field field = SimpleLogger.class.getDeclaredField("TARGET_STREAM");
      field.setAccessible(true);
      field.set(null, stream);
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  private String record(Runnable runnable) {
    ByteArrayOutputStream stream = new ByteArrayOutputStream();
    PrintStream written = new PrintStream(stream);
    setStream(written);
    runnable.run();
    written.flush();
    String result = stream.toString();
    quiet(() -> {
      written.close();
      return null;
    });
    return result;
  }

  private void quiet(Callable<Void> action) {
    try {
      action.call();
    } catch (Exception ignored) {
      // Ignored.
    }
  }

}