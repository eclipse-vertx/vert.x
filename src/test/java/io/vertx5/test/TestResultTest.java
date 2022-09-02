package io.vertx5.test;

import junit.framework.AssertionFailedError;
import org.junit.Assert;
import org.junit.Test;

public class TestResultTest {

  @Test
  public void testComplete() {
    TestResult res = new TestResult();
    res.complete();
    res.await();
  }

  @Test
  public void testFailure() {
    TestResult res = new TestResult();
    RuntimeException failure = new RuntimeException();
    res.run(() -> {
      throw failure;
    });
    try {
      res.await();
    } catch (AssertionFailedError afe) {
      Assert.assertSame(failure, afe.getCause());
    }
  }
}
