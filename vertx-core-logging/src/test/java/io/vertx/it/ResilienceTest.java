package io.vertx.it;

import io.vertx.core.internal.logging.Logger;
import io.vertx.core.internal.logging.LoggerAdapter;
import io.vertx.core.spi.logging.LogDelegate;
import org.assertj.core.api.Assertions;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class ResilienceTest {

  private static final RuntimeException ERR = new RuntimeException();

  final Map<String, AtomicInteger> loggerUsage = new HashMap<>();
  LogDelegate logDelegate = new LogDelegate() {
    private <T> T fail() {
      StackTraceElement[] trace = Thread.currentThread().getStackTrace();
      StackTraceElement elt = trace[2];
      AtomicInteger cnt = loggerUsage.computeIfAbsent(elt.getMethodName(), curr -> new AtomicInteger());
      cnt.incrementAndGet();
      throw ERR;
    }
    @Override
    public String implementation() {
      return fail();
    }
    @Override
    public boolean isWarnEnabled() {
      return fail();
    }
    @Override
    public boolean isInfoEnabled() {
      return fail();
    }
    @Override
    public boolean isDebugEnabled() {
      return fail();
    }
    @Override
    public boolean isTraceEnabled() {
      return fail();
    }
    @Override
    public void error(Object message) {
      fail();
    }
    @Override
    public void error(Object message, Throwable t) {
      fail();
    }
    @Override
    public void warn(Object message) {
      fail();
    }
    @Override
    public void warn(Object message, Throwable t) {
      fail();
    }
    @Override
    public void info(Object message) {
      fail();
    }
    @Override
    public void info(Object message, Throwable t) {
      fail();
    }
    @Override
    public void debug(Object message) {
      fail();
    }
    @Override
    public void debug(Object message, Throwable t) {
      fail();
    }
    @Override
    public void trace(Object message) {
      fail();
    }
    @Override
    public void trace(Object message, Throwable t) {
      fail();
    }
  };

  @Before
  public void before() {
    loggerUsage.clear();
  }

  @Test
  public void testCatchAndReportFailures() {
    Logger logger = new LoggerAdapter(logDelegate);
    List<Throwable> uncaughtReports = new ArrayList<>();
    LoggerAdapter.setLoggerFailureHandler(uncaughtReports::add);
    try {
      logger.isDebugEnabled();
      assertEquals(1, loggerUsage.size());
      AtomicInteger c = loggerUsage.get("isDebugEnabled");
      assertNotNull(c);
      assertEquals(1, c.get());
      assertEquals(List.of(ERR), uncaughtReports);
    } finally {
      LoggerAdapter.setLoggerFailureHandler(null);
    }
  }

  @Test
  public void testUncaughtHandlerFailure() {
    AtomicInteger uncaughtCount = new AtomicInteger();
    Logger logger = new LoggerAdapter(logDelegate);
    LoggerAdapter.setLoggerFailureHandler(err -> {
      uncaughtCount.incrementAndGet();
      throw new AssertionError();
    });
    try {
      logger.isDebugEnabled();
      assertEquals(1, loggerUsage.size());
      AtomicInteger c = loggerUsage.get("isDebugEnabled");
      assertNotNull(c);
      assertEquals(1, c.get());
      assertEquals(1, uncaughtCount.get());
    } finally {
      LoggerAdapter.setLoggerFailureHandler(null);
    }
  }
}
