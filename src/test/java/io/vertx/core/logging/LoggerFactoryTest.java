package io.vertx.core.logging;

import org.junit.Test;

/**
 * @author <a href="https://twitter.com/bartekzdanowski">Bartek Zdanowski</a>
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
