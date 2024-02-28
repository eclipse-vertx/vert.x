package io.vertx.it;

import io.vertx.core.Context;
import io.vertx.test.core.VertxTestBase;
import org.junit.Test;

public class CustomContextLocalTest extends VertxTestBase {

  @Test
  public void testResolver() {
    assertTrue(CustomContextLocal.initialized);
    Context context = vertx.getOrCreateContext();
    Object o = new Object();
    context.putLocal(CustomContextLocal.CUSTOM_LOCAL, o);
    assertSame(o, context.getLocal(CustomContextLocal.CUSTOM_LOCAL));
  }
}
