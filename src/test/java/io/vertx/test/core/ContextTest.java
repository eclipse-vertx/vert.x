/*
 * Copyright 2014 Red Hat, Inc.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * and Apache License v2.0 which accompanies this distribution.
 *
 * The Eclipse Public License is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * The Apache License v2.0 is available at
 * http://www.opensource.org/licenses/apache2.0.php
 *
 * You may elect to redistribute this code under either of these licenses.
 */

package io.vertx.test.core;

import io.vertx.core.Context;
import io.vertx.core.Vertx;
import org.junit.Test;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class ContextTest extends VertxTestBase {

  @Test
  public void testRunOnContext() throws Exception {
    vertx.runOnContext(v -> {
      Thread th = Thread.currentThread();
      Context ctx = Vertx.currentContext();
      ctx.runOnContext(v2 -> {
        assertEquals(th, Thread.currentThread());
        // Execute it a few times to make sure it returns same context
        for (int i = 0; i < 10; i++) {
          Context c = Vertx.currentContext();
          assertEquals(ctx, c);
        }
        // And simulate a third party thread - e.g. a 3rd party async library wishing to return a result on the
        // correct context
        new Thread() {
          public void run() {
            ctx.runOnContext(v3 -> {
              assertEquals(th, Thread.currentThread());
              assertEquals(ctx, Vertx.currentContext());
              testComplete();
            });
          }
        }.start();
      });
    });
    await();
  }

  @Test
  public void testNoContext() throws Exception {
    assertNull(Vertx.currentContext());
  }

  class SomeObject {
  }

  @Test
  public void testPutGetRemoveData() throws Exception {
    SomeObject obj = new SomeObject();
    vertx.runOnContext(v -> {
      Context ctx = Vertx.currentContext();
      ctx.put("foo", obj);
      ctx.runOnContext(v2 -> {
        assertEquals(obj, ctx.get("foo"));
        assertTrue(ctx.remove("foo"));
        ctx.runOnContext(v3 -> {
          assertNull(ctx.get("foo"));
          testComplete();
        });
      });
    });
    await();
  }

  @Test
  public void testGettingContextContextUnderContextAnotherInstanceShouldReturnDifferentContext() throws Exception {
    Vertx other = Vertx.vertx();
    Context context = vertx.getOrCreateContext();
    context.runOnContext(v -> {
      Context otherContext = other.getOrCreateContext();
      assertNotSame(otherContext, context);
      testComplete();
    });
    await();
  }
}
