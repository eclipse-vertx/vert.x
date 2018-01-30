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
import io.vertx.core.logging.SLF4JLogDelegate;
import io.vertx.core.logging.SLF4JLogDelegateFactory;
import io.vertx.core.spi.logging.LogDelegate;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.slf4j.Marker;
import org.slf4j.impl.SimpleLogger;
import org.slf4j.spi.LocationAwareLogger;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.lang.reflect.Field;
import java.util.concurrent.Callable;

import static org.junit.Assert.*;

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
  public void testDelegateUnwrap() {
    Logger logger = LoggerFactory.getLogger("my-slf4j-logger");
    LogDelegate delegate = logger.getDelegate();
    assertNotNull("Delegate is null", delegate);
    try {
      org.slf4j.Logger unwrapped = (org.slf4j.Logger) delegate.unwrap();
      assertNotNull("Unwrapped is null", unwrapped);
    } catch (ClassCastException e) {
      fail("Unexpected unwrapped type: " + e.getMessage());
    }
  }

  @Test
  public void testInfo() {
    testInfo(LoggerFactory.getLogger("my-slf4j-logger"));
  }

  @Test
  public void testInfoLocationAware() {
    testInfo(new Logger(new SLF4JLogDelegate(new TestLocationAwareLogger("my-slf4j-logger"))));
  }

  private void testInfo(Logger logger) {
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
    testError(LoggerFactory.getLogger("my-slf4j-logger"));
  }

  @Test
  public void testErrorLocationAware() {
    testError(LoggerFactory.getLogger("my-slf4j-logger"));
  }

  private void testError(Logger logger) {
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
    testWarning(LoggerFactory.getLogger("my-slf4j-logger"));
  }

  @Test
  public void testWarningLocationAware() {
    testWarning(LoggerFactory.getLogger("my-slf4j-logger"));
  }

  private void testWarning(Logger logger) {
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

  static class TestLocationAwareLogger implements LocationAwareLogger {

    private final org.slf4j.Logger actual;

    TestLocationAwareLogger(String name) {
      Logger logger = LoggerFactory.getLogger(name);
      SLF4JLogDelegate delegate = (SLF4JLogDelegate) logger.getDelegate();
      this.actual = (org.slf4j.Logger) delegate.unwrap();
    }

    @Override
    public void log(Marker marker, String fqcn, int level, String message, Object[] argArray, Throwable t) {
      switch (level) {
        case ERROR_INT:
          error(marker, message, t);
          break;
        case WARN_INT:
          warn(marker, message, t);
          break;
        case INFO_INT:
          info(marker, message, t);
          break;
        case DEBUG_INT:
          debug(marker, message, t);
          break;
        case TRACE_INT:
          trace(marker, message, t);
          break;
        default:
          throw new AssertionError();
      }
    }
    @Override public String getName() { return actual.getName(); }
    @Override public boolean isTraceEnabled() { return actual.isTraceEnabled(); }
    @Override public void trace(String msg) { throw new AssertionError(); }
    @Override public void trace(String format, Object arg) { throw new AssertionError(); }
    @Override public void trace(String format, Object arg1, Object arg2) { throw new AssertionError(); }
    @Override public void trace(String format, Object... arguments) { throw new AssertionError(); }
    @Override public void trace(String msg, Throwable t) { actual.trace(msg, t); }
    @Override public boolean isTraceEnabled(Marker marker) { return actual.isTraceEnabled(); }
    @Override public void trace(Marker marker, String msg) { throw new AssertionError(); }
    @Override public void trace(Marker marker, String format, Object arg) { throw new AssertionError(); }
    @Override public void trace(Marker marker, String format, Object arg1, Object arg2) { actual.trace(marker, format, arg1, arg2); }
    @Override public void trace(Marker marker, String format, Object... argArray) { throw new AssertionError(); }
    @Override public void trace(Marker marker, String msg, Throwable t) { actual.trace(marker, msg, t); }
    @Override public boolean isDebugEnabled() { return actual.isDebugEnabled(); }
    @Override public void debug(String msg) { throw new AssertionError(); }
    @Override public void debug(String format, Object arg) { throw new AssertionError(); }
    @Override public void debug(String format, Object arg1, Object arg2) { throw new AssertionError(); }
    @Override public void debug(String format, Object... arguments) { throw new AssertionError(); }
    @Override public void debug(String msg, Throwable t) { throw new AssertionError(); }
    @Override public boolean isDebugEnabled(Marker marker) { return actual.isDebugEnabled(marker); }
    @Override public void debug(Marker marker, String msg) { throw new AssertionError(); }
    @Override public void debug(Marker marker, String format, Object arg) { throw new AssertionError(); }
    @Override public void debug(Marker marker, String format, Object arg1, Object arg2) { throw new AssertionError(); }
    @Override public void debug(Marker marker, String format, Object... arguments) { throw new AssertionError(); }
    @Override public void debug(Marker marker, String msg, Throwable t) { actual.debug(marker, msg, t); }
    @Override public boolean isInfoEnabled() { return actual.isInfoEnabled(); }
    @Override public void info(String msg) { throw new AssertionError(); }
    @Override public void info(String format, Object arg) { throw new AssertionError(); }
    @Override public void info(String format, Object arg1, Object arg2) { throw new AssertionError(); }
    @Override public void info(String format, Object... arguments) { throw new AssertionError(); }
    @Override public void info(String msg, Throwable t) { throw new AssertionError(); }
    @Override public boolean isInfoEnabled(Marker marker) { return actual.isInfoEnabled(marker); }
    @Override public void info(Marker marker, String msg) { throw new AssertionError(); }
    @Override public void info(Marker marker, String format, Object arg) { throw new AssertionError(); }
    @Override public void info(Marker marker, String format, Object arg1, Object arg2) { throw new AssertionError(); }
    @Override public void info(Marker marker, String format, Object... arguments) { throw new AssertionError(); }
    @Override public void info(Marker marker, String msg, Throwable t) { actual.info(marker, msg, t); }
    @Override public boolean isWarnEnabled() { return actual.isWarnEnabled(); }
    @Override public void warn(String msg) { throw new AssertionError(); }
    @Override public void warn(String format, Object arg) { throw new AssertionError(); }
    @Override public void warn(String format, Object... arguments) { throw new AssertionError(); }
    @Override public void warn(String format, Object arg1, Object arg2) { throw new AssertionError(); }
    @Override public void warn(String msg, Throwable t) { throw new AssertionError(); }
    @Override public boolean isWarnEnabled(Marker marker) { return actual.isWarnEnabled(); }
    @Override public void warn(Marker marker, String msg) { throw new AssertionError(); }
    @Override public void warn(Marker marker, String format, Object arg) { throw new AssertionError(); }
    @Override public void warn(Marker marker, String format, Object arg1, Object arg2) { throw new AssertionError(); }
    @Override public void warn(Marker marker, String format, Object... arguments) { throw new AssertionError(); }
    @Override public void warn(Marker marker, String msg, Throwable t) { actual.warn(marker, msg, t); }
    @Override public boolean isErrorEnabled() { return actual.isErrorEnabled(); }
    @Override public void error(String msg) { throw new AssertionError(); }
    @Override public void error(String format, Object arg) { throw new AssertionError(); }
    @Override public void error(String format, Object arg1, Object arg2) { throw new AssertionError(); }
    @Override public void error(String format, Object... arguments) { throw new AssertionError(); }
    @Override public void error(String msg, Throwable t) { throw new AssertionError(); }
    @Override public boolean isErrorEnabled(Marker marker) { return actual.isErrorEnabled(marker); }
    @Override public void error(Marker marker, String msg) { throw new AssertionError(); }
    @Override public void error(Marker marker, String format, Object arg) { actual.error(marker, format, arg); }
    @Override public void error(Marker marker, String format, Object arg1, Object arg2) { throw new AssertionError(); }
    @Override public void error(Marker marker, String format, Object... arguments) { throw new AssertionError(); }
    @Override public void error(Marker marker, String msg, Throwable t) { actual.error(marker, msg, t); }
  }
}
