package io.vertx.it.context;

import io.vertx.core.Context;
import io.vertx.test.core.VertxTestBase;
import org.junit.Test;

public class CustomContextLocalTest extends VertxTestBase {

  @Test
  public void testResolver() {
    assertTrue(CustomContextLocal.initialized);
    Context context = vertx.getOrCreateContext();
    Object o = new Object();
    CustomContextLocal.CUSTOM_LOCAL.put(context, o);
    assertSame(o, CustomContextLocal.CUSTOM_LOCAL.get(context));
  }
}
