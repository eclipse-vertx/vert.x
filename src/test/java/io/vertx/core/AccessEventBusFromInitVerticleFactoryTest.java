package io.vertx.core;

import io.vertx.core.spi.VerticleFactory;
import io.vertx.test.core.VertxTestBase;
import org.junit.Test;

public class AccessEventBusFromInitVerticleFactoryTest extends VertxTestBase {

  @Test
  public void testLoadFactoryThatCheckEventBusAndSharedDataForNull() {
    assertEquals(2, vertx.verticleFactories().size());
    boolean found = false;
    for (VerticleFactory fact : vertx.verticleFactories()) {
      if (fact instanceof AccessEventBusFromInitVerticleFactory) {
        found = true;
      }
    }
    assertTrue(found);
  }
}
