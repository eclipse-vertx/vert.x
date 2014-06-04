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
import org.vertx.java.core.*;
import org.vertx.java.core.impl.WorkerContext;
import org.vertx.java.core.json.JsonObject;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class DeploymentTest extends VertxTestBase {

  @Test
  public void testDeployFromTestThread() throws Exception {
    MyVerticle verticle = new MyVerticle();
    vertx.deployVerticle(verticle, ar -> {
      assertDeployment(1, verticle, null, ar);
      testComplete();
    });
    await();
  }

  @Test
  public void testDeployFromTestThreadNoHandler() throws Exception {
    MyVerticle verticle = new MyVerticle();
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
    MyVerticle verticle = new MyVerticle();
    JsonObject config = generateJSONObject();
    vertx.deployVerticle(verticle, config, ar -> {
      assertDeployment(1, verticle, config, ar);
      testComplete();
    });
    await();
  }

  @Test
  public void testDeployFromContext() throws Exception {
    MyVerticle verticle = new MyVerticle();
    vertx.deployVerticle(verticle, ar -> {
      assertTrue(ar.succeeded());
      Context ctx = vertx.currentContext();
      MyVerticle verticle2 = new MyVerticle();
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
    MyVerticle verticle = new MyVerticle();
    vertx.deployVerticle(verticle, true, ar -> {
      assertDeployment(1, verticle, null, ar);
      assertTrue(verticle.startContext instanceof WorkerContext);
      vertx.undeployVerticle(ar.result(), ar2 -> {
        assertTrue(ar2.succeeded());
        assertEquals(verticle.startContext, verticle.stopContext);
        testComplete();
      });
    });
    await();
  }

  @Test
  public void testDeployWorkerWithConfig() throws Exception {
    MyVerticle verticle = new MyVerticle();
    JsonObject conf = generateJSONObject();
    vertx.deployVerticle(verticle, conf, true, ar -> {
      assertDeployment(1, verticle, conf, ar);
      assertTrue(verticle.startContext instanceof WorkerContext);
      vertx.undeployVerticle(ar.result(), ar2 -> {
        assertTrue(ar2.succeeded());
        assertEquals(verticle.startContext, verticle.stopContext);
        testComplete();
      });
    });
    await();
  }

  @Test
  public void testDeployFromContextExceptionInStart() throws Exception {
    MyVerticle verticle = new MyVerticle();
    vertx.deployVerticle(verticle, ar -> {
      assertTrue(ar.succeeded());
      Context ctx = vertx.currentContext();
      MyVerticle verticle2 = new MyVerticle(true, false);
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
    MyVerticle verticle = new MyVerticle();
    vertx.deployVerticle(verticle, ar -> {
      assertTrue(ar.succeeded());
      Context ctx = vertx.currentContext();
      MyVerticle verticle2 = new MyVerticle(false, true);
      vertx.deployVerticle(verticle2, ar2 -> {
        assertTrue(ar2.succeeded());
        vertx.undeployVerticle(ar2.result(), ar3 -> {
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
    MyVerticle verticle = new MyVerticle();
    vertx.deployVerticle(verticle, ar -> {
      assertTrue(ar.succeeded());
      vertx.undeployVerticle(ar.result(), ar2 -> {
        assertTrue(ar2.succeeded());
        assertNull(ar2.result());
        assertFalse(vertx.deployments().contains(ar.result()));
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
    MyVerticle verticle = new MyVerticle();
    vertx.deployVerticle(verticle, ar -> {
      assertTrue(ar.succeeded());
      vertx.undeployVerticle(ar.result(), ar2 -> {
        assertTrue(ar2.succeeded());
        vertx.undeployVerticle(ar.result(), ar3 -> {
          assertFalse(ar3.succeeded());
          assertTrue(ar3.cause() instanceof IllegalStateException);
          testComplete();
        });
      });
    });
    await();
  }

  @Test
  public void testUndeployInvalidID() throws Exception {
    vertx.undeployVerticle("uqhwdiuhqwd", ar -> {
      assertFalse(ar.succeeded());
      assertTrue(ar.cause() instanceof IllegalStateException);
      testComplete();
    });
    await();
  }

  //undeploy with wrong id

  @Test
  public void testDeployExceptionInStart() throws Exception {
    MyVerticle verticle = new MyVerticle(true, false);
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
    MyVerticle verticle = new MyVerticle(false, true);
    vertx.deployVerticle(verticle, ar -> {
      assertTrue(ar.succeeded());
      vertx.undeployVerticle(ar.result(), ar2 -> {
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
      MyVerticle verticle = new MyVerticle();
      vertx.deployVerticle(verticle, ar -> {
        assertTrue(ar.succeeded());
        assertTrue(vertx.deployments().contains(ar.result()));
        deployLatch.countDown();
      });
    }
    assertTrue(deployLatch.await(10, TimeUnit.SECONDS));
    assertEquals(num, vertx.deployments().size());
    CountDownLatch undeployLatch = new CountDownLatch(num);
    for (String deploymentID: vertx.deployments()) {
      vertx.undeployVerticle(deploymentID, ar -> {
        assertTrue(ar.succeeded());
        assertFalse(vertx.deployments().contains(deploymentID));
        undeployLatch.countDown();
      });
    }
    assertTrue(undeployLatch.await(10, TimeUnit.SECONDS));
    assertTrue(vertx.deployments().isEmpty());
  }

  @Test
  public void testDeployUsingClassName() throws Exception {
    vertx.deployVerticle(TestVerticle.class.getCanonicalName(), ar -> {
      assertTrue(ar.succeeded());
      testComplete();
    });
    await();
  }

  @Test
  public void testDeployUsingClassAndConfig() throws Exception {
    JsonObject config = generateJSONObject();
    vertx.deployVerticle(TestVerticle.class.getCanonicalName(), config, ar -> {
      assertTrue(ar.succeeded());
      testComplete();
    });
    await();
  }

  @Test
  public void testDeployUsingClassFails() throws Exception {
    vertx.deployVerticle("uhqwuhiqwduhwd", ar -> {
      assertFalse(ar.succeeded());
      assertTrue(ar.cause() instanceof ClassNotFoundException);
      testComplete();
    });
    await();
  }

  @Test
  public void testSimpleChildDeployment() throws Exception {
    Verticle verticle = new TestVerticle2(() -> {
      Context parentContext = vertx.currentContext();
      Verticle child1 = new TestVerticle2(() -> {
        Context childContext = vertx.currentContext();
        assertNotSame(parentContext, childContext);
        testComplete();
      }, null);
      vertx.deployVerticle(child1, ar -> {
        assertTrue(ar.succeeded());
      });
    }, null);
    vertx.deployVerticle(verticle, ar -> {
      assertTrue(ar.succeeded());
    });
    await();
  }

  @Test
  public void testSimpleChildUndeploymentOrder() throws Exception {
    AtomicBoolean childStopCalled = new AtomicBoolean();
    AtomicBoolean parentStopCalled = new AtomicBoolean();
    CountDownLatch deployLatch = new CountDownLatch(1);
    Verticle verticle = new TestVerticle2(() -> {
      Verticle child1 = new TestVerticle2(null, () -> {
        // Child stop is called
        assertFalse(parentStopCalled.get());
        assertFalse(childStopCalled.get());
        childStopCalled.set(true);
      });
      vertx.deployVerticle(child1, ar -> {
        assertTrue(ar.succeeded());
      });
    }, () -> {
      // Parent stop is called
      assertFalse(parentStopCalled.get());
      assertTrue(childStopCalled.get());
      parentStopCalled.set(true);
      testComplete();
    });
    AtomicReference<String> depID = new AtomicReference<>();
    vertx.deployVerticle(verticle, ar -> {
      depID.set(ar.result());
      assertTrue(ar.succeeded());
      deployLatch.countDown();
    });
    assertTrue(deployLatch.await(10, TimeUnit.SECONDS));
    // Now they're deployed, undeploy them
    vertx.undeployVerticle(depID.get(), ar -> {
      assertTrue(ar.succeeded());
    });
    await();
  }

  class DeploymentBuilder {
    Set<DeploymentBuilder> deployments = new HashSet<>();
    DeploymentBuilder addChildDeployment() {
      DeploymentBuilder builder = new DeploymentBuilder();
      deployments.add(builder);
      return builder;
    }

    void assemble() {
      TestVerticle2 parent = new TestVerticle2(() -> {
        for (DeploymentBuilder builder: deployments) {
          // blah
        }
      }, () -> {

      });
      vertx.deployVerticle(parent, ar -> {

      });
    }
  }

  /*
  TODO - child undeploy tests
  more child-parent types (multiple children)
  failure in undeploy with child (make sure children are removed)
  failure in deploy with child (make sure child is not added)
  concurrent undeploy on same deployment
  concurrent undeploy of children
  order of undeployment
   */

  @Test
  public void testAsyncDeployCalledSynchronously() throws Exception {
    MyAsyncVerticle verticle = new MyAsyncVerticle(f -> f.setResult(null), null);
    vertx.deployVerticle(verticle, ar -> {
      assertTrue(ar.succeeded());
      testComplete();
    });
    await();
  }

  @Test
  public void testAsyncDeployFailureCalledSynchronously() throws Exception {
    MyAsyncVerticle verticle = new MyAsyncVerticle(f -> f.setFailure(new Exception("foobar")), null);
    vertx.deployVerticle(verticle, ar -> {
      assertFalse(ar.succeeded());
      assertEquals("foobar", ar.cause().getMessage());
      testComplete();
    });
    await();
  }

  @Test
  public void testAsyncDeploy() throws Exception {
    long start = System.currentTimeMillis();
    long delay = 1000;
    MyAsyncVerticle verticle = new MyAsyncVerticle(f -> vertx.setTimer(delay, id -> f.setResult(null)), null);
    vertx.deployVerticle(verticle, ar -> {
      assertTrue(ar.succeeded());
      long now = System.currentTimeMillis();
      assertTrue(now - start >= delay);
      assertTrue(vertx.deployments().contains(ar.result()));
      testComplete();
    });
    Thread.sleep(delay / 2);
    assertTrue(vertx.deployments().isEmpty());
    await();
  }

  @Test
  public void testAsyncDeployFailure() throws Exception {
    long start = System.currentTimeMillis();
    long delay = 1000;
    MyAsyncVerticle verticle = new MyAsyncVerticle(f -> vertx.setTimer(delay, id -> f.setFailure(new Exception("foobar"))), null);
    vertx.deployVerticle(verticle, ar -> {
      assertFalse(ar.succeeded());
      assertEquals("foobar", ar.cause().getMessage());
      long now = System.currentTimeMillis();
      assertTrue(now - start >= delay);
      assertTrue(vertx.deployments().isEmpty());
      testComplete();
    });
    await();
  }

  @Test
  public void testAsyncUndeployCalledSynchronously() throws Exception {
    MyAsyncVerticle verticle = new MyAsyncVerticle(f -> f.setResult(null), f ->  f.setResult(null));
    vertx.deployVerticle(verticle, ar -> {
      assertTrue(ar.succeeded());
      vertx.undeployVerticle(ar.result(), ar2 -> {
        assertTrue(ar2.succeeded());
        assertFalse(vertx.deployments().contains(ar.result()));
        testComplete();
      });
    });
    await();
  }

  @Test
  public void testAsyncUndeployFailureCalledSynchronously() throws Exception {
    MyAsyncVerticle verticle = new MyAsyncVerticle(f -> f.setResult(null), f -> f.setFailure(new Exception("foobar")));
    vertx.deployVerticle(verticle, ar -> {
      assertTrue(ar.succeeded());
      vertx.undeployVerticle(ar.result(), ar2 -> {
        assertFalse(ar2.succeeded());
        assertEquals("foobar", ar2.cause().getMessage());
        assertFalse(vertx.deployments().contains(ar.result()));
        testComplete();
      });
    });
    await();
  }

  @Test
  public void testAsyncUndeploy() throws Exception {
    long delay = 1000;
    MyAsyncVerticle verticle = new MyAsyncVerticle(f-> f.setResult(null), f -> vertx.setTimer(delay, id -> f.setResult(null)));
    vertx.deployVerticle(verticle, ar -> {
      assertTrue(ar.succeeded());
      long start = System.currentTimeMillis();
      vertx.undeployVerticle(ar.result(), ar2 -> {
        assertTrue(ar2.succeeded());
        long now = System.currentTimeMillis();
        assertTrue(now - start >= delay);
        assertFalse(vertx.deployments().contains(ar.result()));
        testComplete();
      });
      vertx.setTimer(delay / 2, id -> assertFalse(vertx.deployments().isEmpty()));
    });
    await();
  }

  @Test
  public void testAsyncUndeployFailure() throws Exception {
    long delay = 1000;
    MyAsyncVerticle verticle = new MyAsyncVerticle(f-> f.setResult(null), f -> vertx.setTimer(delay, id -> f.setFailure(new Exception("foobar"))));
    vertx.deployVerticle(verticle, ar -> {
      assertTrue(ar.succeeded());
      long start = System.currentTimeMillis();
      vertx.undeployVerticle(ar.result(), ar2 -> {
        assertFalse(ar2.succeeded());
        long now = System.currentTimeMillis();
        assertTrue(now - start >= delay);
        assertFalse(vertx.deployments().contains(ar.result()));
        testComplete();
      });
    });
    await();
  }


  class TestVerticle2 extends AbstractVerticle {

    private Runnable startRunnable;
    private Runnable stopRunnable;

    TestVerticle2(Runnable startRunnable, Runnable stopRunnable) {
      this.startRunnable = startRunnable;
      this.stopRunnable = stopRunnable;
    }

    @Override
    public void start() throws Exception {
      if (startRunnable != null) {
        startRunnable.run();
      }
    }

    @Override
    public void stop() throws Exception {
      if (stopRunnable != null) {
        stopRunnable.run();
      }
    }
  }

  private void assertDeployment(int instances, MyVerticle verticle, JsonObject config, AsyncResult<String> ar) {
    assertTrue(ar.succeeded());
    assertEquals(vertx, verticle.getVertx());
    String deploymentID = ar.result();
    assertNotNull(ar.result());
    if (config == null) {
      assertNull(verticle.getConfig());
    } else {
      assertEquals(config, verticle.getConfig());
    }
    assertTrue(verticle.startCalled);
    assertFalse(verticle.stopCalled);
    assertTrue(vertx.deployments().contains(deploymentID));
    assertEquals(instances, vertx.deployments().size());
    Context currentContext = vertx.currentContext();
    assertNotSame(currentContext, verticle.startContext);
  }

  private JsonObject generateJSONObject() {
    return new JsonObject().putString("foo", "bar").putNumber("blah", 123)
      .putObject("obj", new JsonObject().putString("quux", "flip"));
  }


  public class MyVerticle extends AbstractVerticle {

    boolean startCalled;
    boolean stopCalled;
    Context startContext;
    Context stopContext;
    boolean exceptionInStart;
    boolean exceptionInStop;

    MyVerticle() {
      this(false, false);
    }

    MyVerticle(boolean exceptionInStart, boolean exceptionInStop) {
      this.exceptionInStart = exceptionInStart;
      this.exceptionInStop = exceptionInStop;
    }

    @Override
    public void start() throws Exception {
      if (exceptionInStart) {
        throw new Exception("FooBar!");
      }
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

  public class MyAsyncVerticle extends AbstractVerticle {

    private final Consumer<Future<Void>> startConsumer;
    private final Consumer<Future<Void>> stopConsumer;

    public MyAsyncVerticle(Consumer<Future<Void>> startConsumer, Consumer<Future<Void>> stopConsumer) {
      this.startConsumer = startConsumer;
      this.stopConsumer = stopConsumer;
    }

    @Override
    public void start(Future<Void> startFuture) throws Exception {
      if (startConsumer != null) {
        startConsumer.accept(startFuture);
      }
    }

    @Override
    public void stop(Future<Void> stopFuture) throws Exception {
      if (stopConsumer != null) {
        stopConsumer.accept(stopFuture);
      }
    }
  }


}
