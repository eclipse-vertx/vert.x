/*
 * Copyright 2014 Red Hat, Inc.
 *
 * Red Hat licenses this file to you under the Apache License, version 2.0
 * (the "License"); you may not use this file except in compliance with the
 * License.  You may obtain a copy of the License at:
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

package org.vertx.java.tests.core;

import org.junit.Test;
import org.vertx.java.core.AsyncResult;
import org.vertx.java.core.Context;
import org.vertx.java.core.Verticle;
import org.vertx.java.core.VerticleDeployment;
import org.vertx.java.core.json.JsonObject;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class DeploymentTest extends VertxTestBase {

  @Test
  public void testDeployFromTestThread() throws Exception {
    TestVerticle verticle = new TestVerticle();
    vertx.deployVerticle(verticle, ar -> {
      assertDeployment(1, verticle, null, ar);
      testComplete();
    });
    await();
  }

  @Test
  public void testDeployFromTestThreadNoHandler() throws Exception {
    TestVerticle verticle = new TestVerticle();
    vertx.deployVerticle(verticle);
    long start = System.currentTimeMillis();
    long timeout = 2000;
    while (true) {
      if (vertx.deployments().size() == 1) {
        break;
      }
      Thread.sleep(10);
      long now = System.currentTimeMillis();
      if (now - start > timeout) {
        throw new IllegalStateException("Timed out");
      }
    }
  }

  @Test
  public void testDeployWithConfig() throws Exception {
    TestVerticle verticle = new TestVerticle();
    JsonObject config = new JsonObject().putString("foo", "bar").putNumber("blah", 123)
                                        .putObject("obj", new JsonObject().putString("quux", "flip"));
    vertx.deployVerticle(verticle, config, ar -> {
      assertDeployment(1, verticle, config, ar);
      testComplete();
    });
    await();
  }

  @Test
  public void testDeployFromContext() throws Exception {
    TestVerticle verticle = new TestVerticle();
    vertx.deployVerticle(verticle, ar -> {
      assertTrue(ar.succeeded());
      Context ctx = vertx.currentContext();
      TestVerticle verticle2 = new TestVerticle();
      vertx.deployVerticle(verticle2, ar2 -> {
        assertDeployment(2, verticle2, null, ar2);
        Context ctx2 = vertx.currentContext();
        assertEquals(ctx, ctx2);
        testComplete();
      });
    });
    await();
  }

  @Test
  public void testDeployWorkerFromTestThread() throws Exception {
    TestVerticle verticle = new TestVerticle();
    vertx.deployVerticle(verticle, ar -> {
      assertDeployment(1, verticle, null, ar);
      testComplete();
    });
    await();
  }

  @Test
  public void testDeployFromContextExceptonInStart() throws Exception {
    TestVerticle verticle = new TestVerticle();
    vertx.deployVerticle(verticle, ar -> {
      assertTrue(ar.succeeded());
      Context ctx = vertx.currentContext();
      TestVerticle verticle2 = new TestVerticle(true, false);
      vertx.deployVerticle(verticle2, ar2 -> {
        assertFalse(ar2.succeeded());
        assertEquals("FooBar!", ar2.cause().getMessage());
        assertEquals(1, vertx.deployments().size());
        Context ctx2 = vertx.currentContext();
        assertEquals(ctx, ctx2);
        testComplete();
      });
    });
    await();
  }

  @Test
  public void testDeployFromContextExceptonInStop() throws Exception {
    TestVerticle verticle = new TestVerticle();
    vertx.deployVerticle(verticle, ar -> {
      assertTrue(ar.succeeded());
      Context ctx = vertx.currentContext();
      TestVerticle verticle2 = new TestVerticle(false, true);
      vertx.deployVerticle(verticle2, ar2 -> {
        assertTrue(ar2.succeeded());
        ar2.result().undeploy(ar3 -> {
          assertFalse(ar3.succeeded());
          assertEquals("BooFar!", ar3.cause().getMessage());
          assertEquals(1, vertx.deployments().size());
          assertEquals(ctx, vertx.currentContext());
          testComplete();
        });
      });
    });
    await();
  }

  @Test
  public void testUndeploy() throws Exception {
    TestVerticle verticle = new TestVerticle();
    vertx.deployVerticle(verticle, ar -> {
      assertTrue(ar.succeeded());
      VerticleDeployment theDeployment = ar.result();
      theDeployment.undeploy(ar2 -> {
        assertTrue(ar2.succeeded());
        assertNull(ar2.result());
        assertFalse(vertx.deployments().contains(theDeployment));
        assertEquals(verticle.startContext, verticle.stopContext);
        Context currentContext = vertx.currentContext();
        assertNotSame(currentContext, verticle.startContext);
        testComplete();
      });
    });
    await();
  }

  @Test
  public void testUndeployTwice() throws Exception {
    TestVerticle verticle = new TestVerticle();
    vertx.deployVerticle(verticle, ar -> {
      assertTrue(ar.succeeded());
      VerticleDeployment theDeployment = ar.result();
      theDeployment.undeploy(ar2 -> {
        assertTrue(ar2.succeeded());
        theDeployment.undeploy(ar3 -> {
          assertFalse(ar3.succeeded());
          assertTrue(ar3.cause() instanceof IllegalStateException);
          testComplete();
        });
      });
    });
    await();
  }

  @Test
  public void testDeployExceptionInStart() throws Exception {
    TestVerticle verticle = new TestVerticle(true, false);
    vertx.deployVerticle(verticle, ar -> {
      assertFalse(ar.succeeded());
      assertEquals("FooBar!", ar.cause().getMessage());
      assertTrue(vertx.deployments().isEmpty());
      testComplete();
    });
    await();
  }

  @Test
  public void testUndeployExceptionInStop() throws Exception {
    TestVerticle verticle = new TestVerticle(false, true);
    vertx.deployVerticle(verticle, ar -> {
      assertTrue(ar.succeeded());
      VerticleDeployment theDeployment = ar.result();
      theDeployment.undeploy(ar2 -> {
        assertFalse(ar2.succeeded());
        assertEquals("BooFar!", ar2.cause().getMessage());
        assertTrue(vertx.deployments().isEmpty());
        testComplete();
      });
    });
    await();
  }

  @Test
  public void testDeployUndeployMultiple() throws Exception {
    int num = 10;
    CountDownLatch deployLatch = new CountDownLatch(num);
    for (int i = 0; i < num; i++) {
      TestVerticle verticle = new TestVerticle();
      vertx.deployVerticle(verticle, ar -> {
        assertTrue(ar.succeeded());
        assertTrue(vertx.deployments().contains(ar.result()));
        deployLatch.countDown();
      });
    }
    assertTrue(deployLatch.await(10, TimeUnit.SECONDS));
    assertEquals(num, vertx.deployments().size());
    CountDownLatch undeployLatch = new CountDownLatch(num);
    for (VerticleDeployment deployment: vertx.deployments()) {
      deployment.undeploy(ar -> {
        assertTrue(ar.succeeded());
        assertFalse(vertx.deployments().contains(deployment));
        undeployLatch.countDown();
      });
    }
    assertTrue(undeployLatch.await(10, TimeUnit.SECONDS));
    assertTrue(vertx.deployments().isEmpty());
  }

  private void assertDeployment(int instances, TestVerticle verticle, JsonObject config, AsyncResult<VerticleDeployment> ar) {
    assertTrue(ar.succeeded());
    VerticleDeployment theDeployment = ar.result();
    assertNotNull(theDeployment);
    if (config == null) {
      assertNull(theDeployment.config());
    } else {
      assertEquals(config, theDeployment.config());
    }
    assertEquals(verticle.deployment, theDeployment);
    assertTrue(verticle.startCalled);
    assertFalse(verticle.stopCalled);
    assertTrue(vertx.deployments().contains(verticle.deployment));
    assertEquals(instances, vertx.deployments().size());
    Context currentContext = vertx.currentContext();
    assertNotSame(currentContext, verticle.startContext);
  }

  class TestVerticle implements Verticle {

    VerticleDeployment deployment;
    boolean startCalled;
    boolean stopCalled;
    Context startContext;
    Context stopContext;
    boolean exceptionInStart;
    boolean exceptionInStop;

    TestVerticle() {
      this(false, false);
    }

    TestVerticle(boolean exceptionInStart, boolean exceptionInStop) {
      this.exceptionInStart = exceptionInStart;
      this.exceptionInStop = exceptionInStop;
    }

    @Override
    public void start(VerticleDeployment theDeployment) throws Exception {
      if (exceptionInStart) {
        throw new Exception("FooBar!");
      }
      deployment = theDeployment;
      startCalled = true;
      startContext = vertx.currentContext();
    }

    @Override
    public void stop() throws Exception {
      if (exceptionInStop) {
        throw new Exception("BooFar!");
      }
      stopCalled = true;
      stopContext = vertx.currentContext();
    }
  }
}
