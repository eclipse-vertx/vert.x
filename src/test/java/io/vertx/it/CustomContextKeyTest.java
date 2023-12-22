package io.vertx.it;

import io.vertx.core.Context;
import io.vertx.test.core.VertxTestBase;
import org.junit.Test;

public class CustomContextKeyTest extends VertxTestBase {

  @Test
  public void testResolver() {
    assertTrue(CustomContextKey.initialized);
    Context context = vertx.getOrCreateContext();
    Object o = new Object();
    context.putLocal(CustomContextKey.CUSTOM_KEY, o);
    assertSame(o, context.getLocal(CustomContextKey.CUSTOM_KEY));
  }
}
