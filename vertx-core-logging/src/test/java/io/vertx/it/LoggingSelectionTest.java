package io.vertx.it;

import org.junit.Test;
import org.junit.Assert;
import io.vertx.core.internal.logging.LoggerFactory;
import io.vertx.core.internal.logging.Logger;

public class LoggingSelectionTest {

  @Test
  public void test() {
    String expected = System.getProperty("test.expected");
    Logger logger = LoggerFactory.getLogger("com.example");
    Assert.assertEquals(expected, logger.implementation());
  }
}
