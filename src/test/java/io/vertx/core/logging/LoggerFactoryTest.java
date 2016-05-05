package io.vertx.core.logging;

import org.junit.Test;

/**
 *
 */
public class LoggerFactoryTest {

  @Test
  public void testProperlyLogFromAnonymousClass() {
    new Runnable() {

      @Override
      public void run() {
        LoggerFactory.getLogger(getClass()).info("I'm inside anonymous class");
      }

    }.run();
  }
}
