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

package io.vertx.test.core;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Future;
import io.vertx.core.Verticle;
import io.vertx.core.eventbus.Message;
import io.vertx.core.impl.Closeable;
import io.vertx.core.impl.ContextImpl;
import io.vertx.core.impl.FutureResultImpl;
import io.vertx.core.impl.MultiThreadedWorkerContext;
import io.vertx.core.impl.WorkerContext;
import io.vertx.core.json.JsonObject;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 *
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class DeploymentTest extends VertxTestBase {

  @Before
  public void before() {
    TestVerticle.instanceCount.set(0);
  }

  @Test
  public void testOptions() {
    DeploymentOptions options = DeploymentOptions.options();
    assertNull(options.getConfig());
    JsonObject config = new JsonObject().putString("foo", "bar").putObject("obj", new JsonObject().putNumber("quux", 123));
    assertEquals(options, options.setConfig(config));
    assertEquals(config, options.getConfig());
    assertFalse(options.isWorker());
    assertEquals(options, options.setWorker(true));
    assertTrue(options.isWorker());
    assertFalse(options.isMultiThreaded());
    assertEquals(options, options.setMultiThreaded(true));
    assertTrue(options.isMultiThreaded());
    assertNull(options.getIsolationGroup());
    String rand = TestUtils.randomUnicodeString(1000);
    assertEquals(options, options.setIsolationGroup(rand));
    assertEquals(rand, options.getIsolationGroup());
  }

  @Test
  public void testCopyOptions() {
    DeploymentOptions options = DeploymentOptions.options();
    JsonObject config = new JsonObject().putString("foo", "bar");
    Random rand = new Random();
    boolean worker = rand.nextBoolean();
    boolean multiThreaded = rand.nextBoolean();
    String isolationGroup = TestUtils.randomAlphaString(100);
    options.setConfig(config);
    options.setWorker(worker);
    options.setMultiThreaded(multiThreaded);
    options.setIsolationGroup(isolationGroup);
    DeploymentOptions copy = DeploymentOptions.copiedOptions(options);
    assertEquals(worker, copy.isWorker());
    assertEquals(multiThreaded, copy.isMultiThreaded());
    assertEquals(isolationGroup, copy.getIsolationGroup());
    assertNotSame(config, copy.getConfig());
    assertEquals("bar", copy.getConfig().getString("foo"));
  }

  @Test
  public void testJsonOptions() {
    JsonObject config = new JsonObject().putString("foo", "bar");
    Random rand = new Random();
    boolean worker = rand.nextBoolean();
    boolean multiThreaded = rand.nextBoolean();
    String isolationGroup = TestUtils.randomAlphaString(100);
    JsonObject json = new JsonObject();
    json.putObject("config", config);
    json.putBoolean("worker", worker);
    json.putBoolean("multiThreaded", multiThreaded);
    json.putString("isolationGroup", isolationGroup);
    DeploymentOptions copy = DeploymentOptions.optionsFromJson(json);
    assertEquals(worker, copy.isWorker());
    assertEquals(multiThreaded, copy.isMultiThreaded());
    assertEquals(isolationGroup, copy.getIsolationGroup());
    assertNotSame(config, copy.getConfig());
    assertEquals("bar", copy.getConfig().getString("foo"));
  }

  @Test
  public void testDeployFromTestThread() throws Exception {
    MyVerticle verticle = new MyVerticle();
    vertx.deployVerticleInstance(verticle, DeploymentOptions.options(), ar -> {
      assertDeployment(1, verticle, null, ar);
      testComplete();
    });
    await();
  }

  @Test
  public void testDeployFromTestThreadNoHandler() throws Exception {
    MyVerticle verticle = new MyVerticle();
    vertx.deployVerticleInstance(verticle);
    waitUntil(() -> vertx.deployments().size() == 1);
  }

  @Test
  public void testDeployWithConfig() throws Exception {
    MyVerticle verticle = new MyVerticle();
    JsonObject config = generateJSONObject();
    vertx.deployVerticleInstance(verticle, DeploymentOptions.options().setConfig(config), ar -> {
      assertDeployment(1, verticle, config, ar);
      testComplete();
    });
    await();
  }

  @Test
  public void testDeployFromContext() throws Exception {
    MyVerticle verticle = new MyVerticle();
    vertx.deployVerticleInstance(verticle, DeploymentOptions.options(), ar -> {
      assertTrue(ar.succeeded());
      Context ctx = vertx.currentContext();
      MyVerticle verticle2 = new MyVerticle();
      vertx.deployVerticleInstance(verticle2, DeploymentOptions.options(), ar2 -> {
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
    vertx.deployVerticleInstance(verticle, DeploymentOptions.options().setWorker(true), ar -> {
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
    vertx.deployVerticleInstance(verticle, DeploymentOptions.options().setConfig(conf).setWorker(true), ar -> {
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
  public void testDeployMultithreadedWorkerWithConfig() throws Exception {
    MyVerticle verticle = new MyVerticle();
    JsonObject conf = generateJSONObject();
    vertx.deployVerticleInstance(verticle, DeploymentOptions.options().setConfig(conf).setWorker(true).setMultiThreaded(true), ar -> {
      assertDeployment(1, verticle, conf, ar);
      assertTrue(verticle.startContext instanceof MultiThreadedWorkerContext);
      vertx.undeployVerticle(ar.result(), ar2 -> {
        assertTrue(ar2.succeeded());
        assertEquals(verticle.startContext, verticle.stopContext);
        testComplete();
      });
    });
    await();
  }

  @Test
  public void testDeployMultithreadedNotWorker() throws Exception {
    MyVerticle verticle = new MyVerticle();
    try {
      vertx.deployVerticleInstance(verticle, DeploymentOptions.options().setWorker(false).setMultiThreaded(true), ar -> {
      });
      fail("Should throw exception");
    } catch (IllegalArgumentException e) {
      // OK
    }
  }

  @Test
  public void testDeployFromContextExceptionInStart() throws Exception {
    testDeployFromThrowableInStart(MyVerticle.THROW_EXCEPTION, Exception.class);
  }

  @Test
  public void testDeployFromContextErrorInStart() throws Exception {
    testDeployFromThrowableInStart(MyVerticle.THROW_ERROR, Error.class);
  }

  private void testDeployFromThrowableInStart(int startAction, Class<? extends Throwable> expectedThrowable) throws Exception {
    MyVerticle verticle = new MyVerticle();
    vertx.deployVerticleInstance(verticle, DeploymentOptions.options(), ar -> {
      assertTrue(ar.succeeded());
      Context ctx = vertx.currentContext();
      MyVerticle verticle2 = new MyVerticle(startAction, MyVerticle.NOOP);
      vertx.deployVerticleInstance(verticle2, DeploymentOptions.options(), ar2 -> {
        assertFalse(ar2.succeeded());
        assertEquals(expectedThrowable, ar2.cause().getClass());
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
    testDeployFromContextThrowableInStop(MyVerticle.THROW_EXCEPTION, Exception.class);
  }

  @Test
  public void testDeployFromContextErrorInStop() throws Exception {
    testDeployFromContextThrowableInStop(MyVerticle.THROW_ERROR, Error.class);
  }

  private void testDeployFromContextThrowableInStop(int stopAction, Class<? extends Throwable> expectedThrowable) throws Exception {
    MyVerticle verticle = new MyVerticle();
    vertx.deployVerticleInstance(verticle, DeploymentOptions.options(), ar -> {
      assertTrue(ar.succeeded());
      Context ctx = vertx.currentContext();
      MyVerticle verticle2 = new MyVerticle(MyVerticle.NOOP, stopAction);
      vertx.deployVerticleInstance(verticle2, DeploymentOptions.options(), ar2 -> {
        assertTrue(ar2.succeeded());
        vertx.undeployVerticle(ar2.result(), ar3 -> {
          assertFalse(ar3.succeeded());
          assertEquals(expectedThrowable, ar3.cause().getClass());
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
    vertx.deployVerticleInstance(verticle, DeploymentOptions.options(), ar -> {
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
    vertx.deployVerticleInstance(verticle, DeploymentOptions.options(), ar -> {
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

  @Test
  public void testDeployExceptionInStart() throws Exception {
    testDeployThrowableInStart(MyVerticle.THROW_EXCEPTION, Exception.class);
  }

  @Test
  public void testDeployErrorInStart() throws Exception {
    testDeployThrowableInStart(MyVerticle.THROW_ERROR, Error.class);
  }

  private void testDeployThrowableInStart(int startAction, Class<? extends Throwable> expectedThrowable) throws Exception {
    MyVerticle verticle = new MyVerticle(startAction, MyVerticle.NOOP);
    vertx.deployVerticleInstance(verticle, DeploymentOptions.options(), ar -> {
      assertFalse(ar.succeeded());
      assertEquals(expectedThrowable, ar.cause().getClass());
      assertEquals("FooBar!", ar.cause().getMessage());
      assertTrue(vertx.deployments().isEmpty());
      testComplete();
    });
    await();
  }

  @Test
  public void testUndeployExceptionInStop() throws Exception {
    testUndeployThrowableInStop(MyVerticle.THROW_EXCEPTION, Exception.class);
  }

  @Test
  public void testUndeployErrorInStop() throws Exception {
    testUndeployThrowableInStop(MyVerticle.THROW_ERROR, Error.class);
  }

  private void testUndeployThrowableInStop(int stopAction, Class<? extends Throwable> expectedThrowable) throws Exception {
    MyVerticle verticle = new MyVerticle(MyVerticle.NOOP, stopAction);
    vertx.deployVerticleInstance(verticle, DeploymentOptions.options(), ar -> {
      assertTrue(ar.succeeded());
      vertx.undeployVerticle(ar.result(), ar2 -> {
        assertFalse(ar2.succeeded());
        assertEquals(expectedThrowable, ar2.cause().getClass());
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
      vertx.deployVerticleInstance(verticle, DeploymentOptions.options(), ar -> {
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
    vertx.deployVerticle("java:" + TestVerticle.class.getCanonicalName(), DeploymentOptions.options(), ar -> {
      assertTrue(ar.succeeded());
      testComplete();
    });
    await();
  }

  @Test
  public void testDeployUsingClassAndConfig() throws Exception {
    JsonObject config = generateJSONObject();
    vertx.deployVerticle("java:" + TestVerticle.class.getCanonicalName(), DeploymentOptions.options().setConfig(config), ar -> {
      assertTrue(ar.succeeded());
      testComplete();
    });
    await();
  }

  @Test
  public void testDeployUsingClassFails() throws Exception {
    vertx.deployVerticle("java:uhqwuhiqwduhwd", DeploymentOptions.options(), ar -> {
      assertFalse(ar.succeeded());
      assertTrue(ar.cause() instanceof ClassNotFoundException);
      testComplete();
    });
    await();
  }

  @Test
  public void testDeployWithNoPrefix() throws Exception {
    try {
      vertx.deployVerticle("uhqwuhiqwduhwd", DeploymentOptions.options(), ar -> {
      });
      fail("Should throw exception");
    } catch (IllegalArgumentException e) {
      // OK
    }
  }

  @Test
  public void testSimpleChildDeployment() throws Exception {
    Verticle verticle = new MyAsyncVerticle(f -> {
      Context parentContext = vertx.currentContext();
      Verticle child1 = new MyAsyncVerticle(f2 -> {
        Context childContext = vertx.currentContext();
        assertNotSame(parentContext, childContext);
        f2.setResult(null);
        testComplete();
      }, f2 -> f2.setResult(null));
      vertx.deployVerticleInstance(child1, DeploymentOptions.options(), ar -> {
        assertTrue(ar.succeeded());
      });
      f.setResult(null);
    }, f -> f.setResult(null));
    vertx.deployVerticleInstance(verticle, DeploymentOptions.options(), ar -> {
      assertTrue(ar.succeeded());
    });
    await();
  }

  @Test
  public void testSimpleChildUndeploymentOrder() throws Exception {
    AtomicBoolean childStopCalled = new AtomicBoolean();
    AtomicBoolean parentStopCalled = new AtomicBoolean();
    AtomicReference<String> parentDepID = new AtomicReference<>();
    AtomicReference<String> childDepID = new AtomicReference<>();
    CountDownLatch deployLatch = new CountDownLatch(1);
    Verticle verticle = new MyAsyncVerticle(f -> {
      Verticle child1 = new MyAsyncVerticle(f2 -> f2.setResult(null), f2 -> {
        // Child stop is called
        assertFalse(parentStopCalled.get());
        assertFalse(childStopCalled.get());
        childStopCalled.set(true);
        f2.setResult(null);
      });
      vertx.deployVerticleInstance(child1, DeploymentOptions.options(), ar -> {
        assertTrue(ar.succeeded());
        childDepID.set(ar.result());
        f.setResult(null);
      });
    }, f2 -> {
      // Parent stop is called
      assertFalse(parentStopCalled.get());
      assertTrue(childStopCalled.get());
      assertTrue(vertx.deployments().contains(parentDepID.get()));
      assertFalse(vertx.deployments().contains(childDepID.get()));
      parentStopCalled.set(true);
      testComplete();
      f2.setResult(null);
    });
    vertx.deployVerticleInstance(verticle, DeploymentOptions.options(), ar -> {
      parentDepID.set(ar.result());
      assertTrue(ar.succeeded());
      deployLatch.countDown();
    });
    assertTrue(deployLatch.await(10, TimeUnit.SECONDS));
    assertTrue(vertx.deployments().contains(parentDepID.get()));
    assertTrue(vertx.deployments().contains(childDepID.get()));

    // Now they're deployed, undeploy them
    vertx.undeployVerticle(parentDepID.get(), ar -> {
      assertTrue(ar.succeeded());
    });
    await();
  }

  @Test
  public void testAsyncDeployCalledSynchronously() throws Exception {
    MyAsyncVerticle verticle = new MyAsyncVerticle(f -> f.setResult(null), f -> f.setResult(null));
    vertx.deployVerticleInstance(verticle, DeploymentOptions.options(), ar -> {
      assertTrue(ar.succeeded());
      testComplete();
    });
    await();
  }

  @Test
  public void testAsyncDeployFailureCalledSynchronously() throws Exception {
    MyAsyncVerticle verticle = new MyAsyncVerticle(f -> f.setFailure(new Exception("foobar")), null);
    vertx.deployVerticleInstance(verticle, DeploymentOptions.options(), ar -> {
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
    MyAsyncVerticle verticle = new MyAsyncVerticle(f -> {
      vertx.setTimer(delay, id -> {
        f.setResult(null);
      });
    }, f -> f.setResult(null));
    vertx.deployVerticleInstance(verticle, DeploymentOptions.options(), ar -> {
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
    vertx.deployVerticleInstance(verticle, DeploymentOptions.options(), ar -> {
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
    vertx.deployVerticleInstance(verticle, DeploymentOptions.options(), ar -> {
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
    vertx.deployVerticleInstance(verticle, DeploymentOptions.options(), ar -> {
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
    vertx.deployVerticleInstance(verticle, DeploymentOptions.options(), ar -> {
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
    vertx.deployVerticleInstance(verticle, DeploymentOptions.options(), ar -> {
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

  @Test
  public void testCloseHooksCalled() throws Exception {
    AtomicInteger closedCount = new AtomicInteger();
    Closeable myCloseable1 = completionHandler -> {
      closedCount.incrementAndGet();
      completionHandler.handle(new FutureResultImpl<>((Void)null));
    };
    Closeable myCloseable2 = completionHandler -> {
      closedCount.incrementAndGet();
      completionHandler.handle(new FutureResultImpl<>((Void)null));
    };
    MyAsyncVerticle verticle = new MyAsyncVerticle(f-> {
      ContextImpl ctx = (ContextImpl)vertx.currentContext();
      ctx.addCloseHook(myCloseable1);
      ctx.addCloseHook(myCloseable2);
      f.setResult(null);
    }, f -> f.setResult(null));
    vertx.deployVerticleInstance(verticle, DeploymentOptions.options(), ar -> {
      assertTrue(ar.succeeded());
      assertEquals(0, closedCount.get());
      // Now undeploy
      vertx.undeployVerticle(ar.result(), ar2 -> {
        assertTrue(ar2.succeeded());
        assertEquals(2, closedCount.get());
        testComplete();
      });
    });
    await();
  }

  @Test
  public void testIsolationGroup1() throws Exception {
    vertx.deployVerticle("java:" + TestVerticle.class.getCanonicalName(), DeploymentOptions.options().setIsolationGroup("somegroup"), ar -> {
      assertTrue(ar.succeeded());
      assertEquals(0, TestVerticle.instanceCount.get());
      testComplete();
    });
    await();
  }

  @Test
  public void testNullIsolationGroup() throws Exception {
    vertx.deployVerticle("java:" + TestVerticle.class.getCanonicalName(), DeploymentOptions.options().setIsolationGroup(null), ar -> {
      assertTrue(ar.succeeded());
      assertEquals(1, TestVerticle.instanceCount.get());
      testComplete();
    });
    await();
  }

  @Test
  public void testIsolationGroupSameGroup() throws Exception {
    testIsolationGroup("somegroup", "somegroup", 1, 2);
  }

  @Test
  public void testIsolationGroupDifferentGroup() throws Exception {
    testIsolationGroup("somegroup", "someothergroup", 1, 1);
  }

  @Test
  public void testUndeployAll() throws Exception {
    int numVerticles = 10;
    List<MyVerticle> verticles = new ArrayList<>();
    CountDownLatch latch = new CountDownLatch(numVerticles);
    for (int i = 0; i < numVerticles; i++) {
      MyVerticle verticle = new MyVerticle();
      verticles.add(verticle);
      vertx.deployVerticleInstance(verticle, DeploymentOptions.options(), ar2 -> {
        assertTrue(ar2.succeeded());
        latch.countDown();
      });
    }
    awaitLatch(latch);
    vertx.close(ar -> {
      assertTrue(ar.succeeded());
      for (MyVerticle verticle: verticles) {
        assertTrue(verticle.stopCalled);
      }
      testComplete();
    });
    await();
    vertx = null;
  }

  @Test
  public void testUndeployAllFailureInUndeploy() throws Exception {
    int numVerticles = 10;
    List<MyVerticle> verticles = new ArrayList<>();
    CountDownLatch latch = new CountDownLatch(numVerticles);
    for (int i = 0; i < numVerticles; i++) {
      MyVerticle verticle = new MyVerticle(MyVerticle.NOOP, MyVerticle.THROW_EXCEPTION);
      verticles.add(verticle);
      vertx.deployVerticleInstance(verticle, DeploymentOptions.options(), ar2 -> {
        assertTrue(ar2.succeeded());
        latch.countDown();
      });
    }
    awaitLatch(latch);
    vertx.close(ar -> {
      assertTrue(ar.succeeded());
      for (MyVerticle verticle: verticles) {
        assertFalse(verticle.stopCalled);
      }
      testComplete();
    });
    await();
    vertx = null;
  }

  @Test
  public void testUndeployAllNoDeployments() throws Exception {
    vertx.close(ar -> {
      assertTrue(ar.succeeded());
      testComplete();
    });
    await();
    vertx = null;
  }

  // TODO

  // Multi-threaded workers


  private void testIsolationGroup(String group1, String group2, int count1, int count2) throws Exception {
    Map<String, Integer> countMap = new ConcurrentHashMap<>();
    vertx.eventBus().registerHandler("testcounts", (Message<JsonObject> msg) -> {
      countMap.put(msg.body().getString("deploymentID"), msg.body().getInteger("count"));
    });
    CountDownLatch latch = new CountDownLatch(1);
    AtomicReference<String> deploymentID1 = new AtomicReference<>();
    AtomicReference<String> deploymentID2 = new AtomicReference<>();
    vertx.deployVerticle("java:" + TestVerticle.class.getCanonicalName(), DeploymentOptions.options().setIsolationGroup(group1), ar -> {
      assertTrue(ar.succeeded());
      deploymentID1.set(ar.result());
      assertEquals(0, TestVerticle.instanceCount.get());
      vertx.deployVerticle("java:" + TestVerticle.class.getCanonicalName(), DeploymentOptions.options().setIsolationGroup(group2), ar2 -> {
        assertTrue(ar2.succeeded());
        deploymentID2.set(ar2.result());
        assertEquals(0, TestVerticle.instanceCount.get());
        latch.countDown();
      });
    });
    awaitLatch(latch);
    // Wait until two entries in the map
    waitUntil(() -> countMap.size() == 2);
    assertEquals(count1, countMap.get(deploymentID1.get()).intValue());
    assertEquals(count2, countMap.get(deploymentID2.get()).intValue());
  }

  private void assertDeployment(int instances, MyVerticle verticle, JsonObject config, AsyncResult<String> ar) {
    assertTrue(ar.succeeded());
    assertEquals(vertx, verticle.getVertx());
    String deploymentID = ar.result();
    assertNotNull(ar.result());
    assertEquals(deploymentID, verticle.getDeploymentID());
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

    static final int NOOP = 0, THROW_EXCEPTION = 1, THROW_ERROR = 2;

    boolean startCalled;
    boolean stopCalled;
    Context startContext;
    Context stopContext;
    int startAction;
    int stopAction;

    MyVerticle() {
      this(NOOP, NOOP);
    }

    MyVerticle(int startAction, int stopAction) {
      this.startAction = startAction;
      this.stopAction = stopAction;
    }

    @Override
    public void start() throws Exception {
      switch (startAction) {
        case THROW_EXCEPTION:
          throw new Exception("FooBar!");
        case THROW_ERROR:
          throw new Error("FooBar!");
        default:
          startCalled = true;
          startContext = vertx.currentContext();
      }
    }

    @Override
    public void stop() throws Exception {
      switch (stopAction) {
        case THROW_EXCEPTION:
          throw new Exception("BooFar!");
        case THROW_ERROR:
          throw new Error("BooFar!");
        default:
          stopCalled = true;
          stopContext = vertx.currentContext();
      }
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
