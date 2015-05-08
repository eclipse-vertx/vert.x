/*
 * Copyright (c) 2011-2014 The original author or authors
 * ------------------------------------------------------
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * and Apache License v2.0 which accompanies this distribution.
 *
 *     The Eclipse Public License is available at
 *     http://www.eclipse.org/legal/epl-v10.html
 *
 *     The Apache License v2.0 is available at
 *     http://www.opensource.org/licenses/apache2.0.php
 *
 * You may elect to redistribute this code under either of these licenses.
 */

package io.vertx.test.core;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Future;
import io.vertx.core.Verticle;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.Message;
import io.vertx.core.impl.Closeable;
import io.vertx.core.impl.ContextImpl;
import io.vertx.core.impl.Deployment;
import io.vertx.core.impl.VertxInternal;
import io.vertx.core.impl.WorkerContext;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.test.core.sourceverticle.SourceVerticle;
import org.junit.Test;

import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
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

  public void setUp() throws Exception {
    super.setUp();
    TestVerticle.instanceCount.set(0);
  }

  @Test
  public void testOptions() {
    DeploymentOptions options = new DeploymentOptions();
    assertNull(options.getConfig());
    JsonObject config = new JsonObject().put("foo", "bar").put("obj", new JsonObject().put("quux", 123));
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
    assertFalse(options.isHa());
    assertEquals(options, options.setHa(true));
    assertTrue(options.isHa());
    assertNull(options.getExtraClasspath());
    List<String> cp = Arrays.asList("foo", "bar");
    assertEquals(options, options.setExtraClasspath(cp));
    assertSame(cp, options.getExtraClasspath());
    assertFalse(options.isRedeploy());
    assertSame(options, options.setRedeploy(true));
    assertTrue(options.isRedeploy());
    assertEquals(DeploymentOptions.DEFAULT_REDEPLOY_GRACE_PERIOD, options.getRedeployGracePeriod());
    int randInt = TestUtils.randomPositiveInt();
    assertEquals(options, options.setRedeployGracePeriod(randInt));
    assertEquals(randInt, options.getRedeployGracePeriod());
    randInt = TestUtils.randomPositiveInt();
    assertEquals(options, options.setRedeployScanPeriod(randInt));
    assertEquals(randInt, options.getRedeployScanPeriod());
    try {
      options.setRedeployGracePeriod(-1);
      fail();
    } catch (IllegalArgumentException e) {
      // OK
    }
    try {
      options.setRedeployScanPeriod(-1);
      fail();
    } catch (IllegalArgumentException e) {
      // OK
    }
    try {
      options.setRedeployGracePeriod(0);
      fail();
    } catch (IllegalArgumentException e) {
      // OK
    }
    try {
      options.setRedeployScanPeriod(0);
      fail();
    } catch (IllegalArgumentException e) {
      // OK
    }
  }

  @Test
  public void testCopyOptions() {
    DeploymentOptions options = new DeploymentOptions();
    JsonObject config = new JsonObject().put("foo", "bar");
    Random rand = new Random();
    boolean worker = rand.nextBoolean();
    boolean multiThreaded = rand.nextBoolean();
    String isolationGroup = TestUtils.randomAlphaString(100);
    boolean ha = rand.nextBoolean();
    long gracePeriod = 7236;
    long scanPeriod = 7812673;
    List<String> cp = Arrays.asList("foo", "bar");
    options.setConfig(config);
    options.setWorker(worker);
    options.setMultiThreaded(multiThreaded);
    options.setIsolationGroup(isolationGroup);
    options.setHa(ha);
    options.setExtraClasspath(cp);
    options.setRedeploy(true);
    options.setRedeployGracePeriod(gracePeriod);
    options.setRedeployScanPeriod(scanPeriod);
    DeploymentOptions copy = new DeploymentOptions(options);
    assertEquals(worker, copy.isWorker());
    assertEquals(multiThreaded, copy.isMultiThreaded());
    assertEquals(isolationGroup, copy.getIsolationGroup());
    assertNotSame(config, copy.getConfig());
    assertEquals("bar", copy.getConfig().getString("foo"));
    assertEquals(ha, copy.isHa());
    assertEquals(cp, copy.getExtraClasspath());
    assertNotSame(cp, copy.getExtraClasspath());
    assertTrue(options.isRedeploy());
    assertEquals(gracePeriod, options.getRedeployGracePeriod());
    assertEquals(scanPeriod, options.getRedeployScanPeriod());
  }

  @Test
  public void testDefaultJsonOptions() {
    DeploymentOptions def = new DeploymentOptions();
    DeploymentOptions json = new DeploymentOptions(new JsonObject());
    assertEquals(def.getConfig(), json.getConfig());
    assertEquals(def.isWorker(), json.isWorker());
    assertEquals(def.isMultiThreaded(), json.isMultiThreaded());
    assertEquals(def.getIsolationGroup(), json.getIsolationGroup());
    assertEquals(def.isHa(), json.isHa());
    assertEquals(def.getExtraClasspath(), json.getExtraClasspath());
    assertEquals(def.isRedeploy(), json.isRedeploy());
  }

  @Test
  public void testJsonOptions() {
    JsonObject config = new JsonObject().put("foo", "bar");
    Random rand = new Random();
    boolean worker = rand.nextBoolean();
    boolean multiThreaded = rand.nextBoolean();
    String isolationGroup = TestUtils.randomAlphaString(100);
    boolean ha = rand.nextBoolean();
    List<String> cp = Arrays.asList("foo", "bar");
    JsonObject json = new JsonObject();
    json.put("config", config);
    json.put("worker", worker);
    json.put("multiThreaded", multiThreaded);
    json.put("isolationGroup", isolationGroup);
    json.put("ha", ha);
    json.put("extraClasspath", new JsonArray(cp));
    json.put("redeploy", true);
    json.put("redeployGracePeriod", 1234);
    json.put("redeployScanPeriod", 4567);
    DeploymentOptions options = new DeploymentOptions(json);
    assertEquals(worker, options.isWorker());
    assertEquals(multiThreaded, options.isMultiThreaded());
    assertEquals(isolationGroup, options.getIsolationGroup());
    assertEquals("bar", options.getConfig().getString("foo"));
    assertEquals(ha, options.isHa());
    assertEquals(cp, options.getExtraClasspath());
    assertTrue(options.isRedeploy());
    assertEquals(1234, options.getRedeployGracePeriod());
    assertEquals(4567, options.getRedeployScanPeriod());
  }

  @Test
  public void testToJson() {
    DeploymentOptions options = new DeploymentOptions();
    JsonObject config = new JsonObject().put("foo", "bar");
    Random rand = new Random();
    boolean worker = rand.nextBoolean();
    boolean multiThreaded = rand.nextBoolean();
    String isolationGroup = TestUtils.randomAlphaString(100);
    boolean ha = rand.nextBoolean();
    long gracePeriod = 521445;
    long scanPeriod = 234234;
    List<String> cp = Arrays.asList("foo", "bar");
    options.setConfig(config);
    options.setWorker(worker);
    options.setMultiThreaded(multiThreaded);
    options.setIsolationGroup(isolationGroup);
    options.setHa(ha);
    options.setExtraClasspath(cp);
    options.setRedeploy(true);
    options.setRedeployGracePeriod(gracePeriod);
    options.setRedeployScanPeriod(scanPeriod);
    JsonObject json = options.toJson();
    DeploymentOptions copy = new DeploymentOptions(json);
    assertEquals(worker, copy.isWorker());
    assertEquals(multiThreaded, copy.isMultiThreaded());
    assertEquals(isolationGroup, copy.getIsolationGroup());
    assertEquals("bar", copy.getConfig().getString("foo"));
    assertEquals(ha, copy.isHa());
    assertEquals(cp, copy.getExtraClasspath());
    assertEquals(gracePeriod, copy.getRedeployGracePeriod());
    assertEquals(scanPeriod, copy.getRedeployScanPeriod());
  }

  @Test
  public void testDeployFromTestThread() throws Exception {
    MyVerticle verticle = new MyVerticle();
    vertx.deployVerticle(verticle, ar -> {
      assertDeployment(1, verticle, null, ar);
      assertFalse(verticle.startContext.isMultiThreadedWorkerContext());
      assertFalse(verticle.startContext.isWorkerContext());
      assertTrue(verticle.startContext.isEventLoopContext());
      testComplete();
    });
    await();
  }

  @Test
  public void testDeployFromTestThreadNoHandler() throws Exception {
    MyVerticle verticle = new MyVerticle();
    vertx.deployVerticle(verticle);
    waitUntil(() -> vertx.deploymentIDs().size() == 1);
  }

  @Test
  public void testDeployWithConfig() throws Exception {
    MyVerticle verticle = new MyVerticle();
    JsonObject config = generateJSONObject();
    vertx.deployVerticle(verticle, new DeploymentOptions().setConfig(config), ar -> {
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
      Context ctx = Vertx.currentContext();
      MyVerticle verticle2 = new MyVerticle();
      vertx.deployVerticle(verticle2, ar2 -> {
        assertDeployment(2, verticle2, null, ar2);
        Context ctx2 = Vertx.currentContext();
        assertEquals(ctx, ctx2);
        testComplete();
      });
    });
    await();
  }

  @Test
  public void testDeployWorkerFromTestThread() throws Exception {
    MyVerticle verticle = new MyVerticle();
    vertx.deployVerticle(verticle, new DeploymentOptions().setWorker(true), ar -> {
      assertDeployment(1, verticle, null, ar);
      assertTrue(verticle.startContext instanceof WorkerContext);
      vertx.undeploy(ar.result(), ar2 -> {
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
    vertx.deployVerticle(verticle, new DeploymentOptions().setConfig(conf).setWorker(true), ar -> {
      assertDeployment(1, verticle, conf, ar);
      assertFalse(verticle.startContext.isMultiThreadedWorkerContext());
      assertTrue(verticle.startContext.isWorkerContext());
      assertFalse(verticle.startContext.isEventLoopContext());
      vertx.undeploy(ar.result(), ar2 -> {
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
    vertx.deployVerticle(verticle, new DeploymentOptions().setConfig(conf).setWorker(true).setMultiThreaded(true), ar -> {
      assertDeployment(1, verticle, conf, ar);
      assertTrue(verticle.startContext.isMultiThreadedWorkerContext());
      assertTrue(verticle.startContext.isWorkerContext());
      assertFalse(verticle.startContext.isEventLoopContext());
      vertx.undeploy(ar.result(), ar2 -> {
        assertTrue(ar2.succeeded());
        assertEquals(verticle.startContext, verticle.stopContext);
        testComplete();
      });
    });
    await();
  }

  @Test
  public void testWorkerRightThread() throws Exception {
    assertFalse(Context.isOnVertxThread());
    Verticle verticle = new AbstractVerticle() {
      @Override
      public void start() throws Exception {
        assertTrue(Context.isOnVertxThread());
        assertTrue(Context.isOnWorkerThread());
        assertFalse(Context.isOnEventLoopThread());
      }

      @Override
      public void stop() throws Exception {
        assertTrue(Context.isOnVertxThread());
        assertTrue(Context.isOnWorkerThread());
        assertFalse(Context.isOnEventLoopThread());
      }
    };
    vertx.deployVerticle(verticle, new DeploymentOptions().setWorker(true), onSuccess(res -> {
      assertTrue(Context.isOnVertxThread());
      assertFalse(Context.isOnWorkerThread());
      assertTrue(Context.isOnEventLoopThread());
      vertx.undeploy(res, onSuccess(res2 -> {
        assertTrue(Context.isOnVertxThread());
        assertFalse(Context.isOnWorkerThread());
        assertTrue(Context.isOnEventLoopThread());
        testComplete();
      }));
    }));

    await();
  }

  @Test
  public void testMTWorkerRightThread() throws Exception {
    assertFalse(Context.isOnVertxThread());
    Verticle verticle = new AbstractVerticle() {
      @Override
      public void start() throws Exception {
        assertTrue(Context.isOnVertxThread());
        assertTrue(Context.isOnWorkerThread());
        assertFalse(Context.isOnEventLoopThread());
      }

      @Override
      public void stop() throws Exception {
        assertTrue(Context.isOnVertxThread());
        assertTrue(Context.isOnWorkerThread());
        assertFalse(Context.isOnEventLoopThread());
      }
    };
    vertx.deployVerticle(verticle, new DeploymentOptions().setWorker(true).setMultiThreaded(true), onSuccess(res -> {
      assertTrue(Context.isOnVertxThread());
      assertFalse(Context.isOnWorkerThread());
      assertTrue(Context.isOnEventLoopThread());
      vertx.undeploy(res, onSuccess(res2 -> {
        assertTrue(Context.isOnVertxThread());
        assertFalse(Context.isOnWorkerThread());
        assertTrue(Context.isOnEventLoopThread());
        testComplete();
      }));
    }));

    await();
  }

  @Test
  public void testStandardRightThread() throws Exception {
    assertFalse(Context.isOnVertxThread());
    Verticle verticle = new AbstractVerticle() {
      @Override
      public void start() throws Exception {
        assertTrue(Context.isOnVertxThread());
        assertFalse(Context.isOnWorkerThread());
        assertTrue(Context.isOnEventLoopThread());
      }

      @Override
      public void stop() throws Exception {
        assertTrue(Context.isOnVertxThread());
        assertFalse(Context.isOnWorkerThread());
        assertTrue(Context.isOnEventLoopThread());
      }
    };
    vertx.deployVerticle(verticle, onSuccess(res -> {
      assertTrue(Context.isOnVertxThread());
      assertFalse(Context.isOnWorkerThread());
      assertTrue(Context.isOnEventLoopThread());
      vertx.undeploy(res, onSuccess(res2 -> {
        assertTrue(Context.isOnVertxThread());
        assertFalse(Context.isOnWorkerThread());
        assertTrue(Context.isOnEventLoopThread());
        testComplete();
      }));
    }));

    await();
  }

  @Test
  public void testDeployMultithreadedNotWorker() throws Exception {
    MyVerticle verticle = new MyVerticle();
    try {
      vertx.deployVerticle(verticle, new DeploymentOptions().setWorker(false).setMultiThreaded(true), ar -> {
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
    vertx.deployVerticle(verticle, ar -> {
      assertTrue(ar.succeeded());
      Context ctx = Vertx.currentContext();
      MyVerticle verticle2 = new MyVerticle(startAction, MyVerticle.NOOP);
      vertx.deployVerticle(verticle2, ar2 -> {
        assertFalse(ar2.succeeded());
        assertEquals(expectedThrowable, ar2.cause().getClass());
        assertEquals("FooBar!", ar2.cause().getMessage());
        assertEquals(1, vertx.deploymentIDs().size());
        Context ctx2 = Vertx.currentContext();
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
    vertx.deployVerticle(verticle, ar -> {
      assertTrue(ar.succeeded());
      Context ctx = Vertx.currentContext();
      MyVerticle verticle2 = new MyVerticle(MyVerticle.NOOP, stopAction);
      vertx.deployVerticle(verticle2, ar2 -> {
        assertTrue(ar2.succeeded());
        vertx.undeploy(ar2.result(), ar3 -> {
          assertFalse(ar3.succeeded());
          assertEquals(expectedThrowable, ar3.cause().getClass());
          assertEquals("BooFar!", ar3.cause().getMessage());
          assertEquals(1, vertx.deploymentIDs().size());
          assertEquals(ctx, Vertx.currentContext());
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
      vertx.undeploy(ar.result(), ar2 -> {
        assertTrue(ar2.succeeded());
        assertNull(ar2.result());
        assertFalse(vertx.deploymentIDs().contains(ar.result()));
        assertEquals(verticle.startContext, verticle.stopContext);
        Context currentContext = Vertx.currentContext();
        assertNotSame(currentContext, verticle.startContext);
        testComplete();
      });
    });
    await();
  }

  @Test
  public void testUndeployNoHandler() throws Exception {
    MyVerticle verticle = new MyVerticle();
    vertx.deployVerticle(verticle, ar -> {
      assertTrue(ar.succeeded());
      vertx.undeploy(ar.result());
    });
    waitUntil(() -> vertx.deploymentIDs().isEmpty());
  }

  @Test
  public void testUndeployTwice() throws Exception {
    MyVerticle verticle = new MyVerticle();
    vertx.deployVerticle(verticle, ar -> {
      assertTrue(ar.succeeded());
      vertx.undeploy(ar.result(), ar2 -> {
        assertTrue(ar2.succeeded());
        vertx.undeploy(ar.result(), ar3 -> {
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
    vertx.undeploy("uqhwdiuhqwd", ar -> {
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
    vertx.deployVerticle(verticle, ar -> {
      assertFalse(ar.succeeded());
      assertEquals(expectedThrowable, ar.cause().getClass());
      assertEquals("FooBar!", ar.cause().getMessage());
      assertTrue(vertx.deploymentIDs().isEmpty());
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
    vertx.deployVerticle(verticle, ar -> {
      assertTrue(ar.succeeded());
      vertx.undeploy(ar.result(), ar2 -> {
        assertFalse(ar2.succeeded());
        assertEquals(expectedThrowable, ar2.cause().getClass());
        assertEquals("BooFar!", ar2.cause().getMessage());
        assertTrue(vertx.deploymentIDs().isEmpty());
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
        assertTrue(vertx.deploymentIDs().contains(ar.result()));
        deployLatch.countDown();
      });
    }
    assertTrue(deployLatch.await(10, TimeUnit.SECONDS));
    assertEquals(num, vertx.deploymentIDs().size());
    CountDownLatch undeployLatch = new CountDownLatch(num);
    for (String deploymentID: vertx.deploymentIDs()) {
      vertx.undeploy(deploymentID, ar -> {
        assertTrue(ar.succeeded());
        assertFalse(vertx.deploymentIDs().contains(deploymentID));
        undeployLatch.countDown();
      });
    }
    assertTrue(undeployLatch.await(10, TimeUnit.SECONDS));
    assertTrue(vertx.deploymentIDs().isEmpty());
  }

  @Test(expected = IllegalArgumentException.class)
  public void testDeployInstanceSetInstances() throws Exception {
    vertx.deployVerticle(new MyVerticle(), new DeploymentOptions().setInstances(2));
  }

  @Test(expected = IllegalArgumentException.class)
  public void testDeployInstanceSetExtraClasspath() throws Exception {
    vertx.deployVerticle(new MyVerticle(), new DeploymentOptions().setExtraClasspath(Arrays.asList("foo")));
  }

  @Test(expected = IllegalArgumentException.class)
  public void testDeployInstanceSetIsolationGroup() throws Exception {
    vertx.deployVerticle(new MyVerticle(), new DeploymentOptions().setIsolationGroup("foo"));
  }

  @Test
  public void testDeployUsingClassName() throws Exception {
    vertx.deployVerticle("java:" + TestVerticle.class.getCanonicalName(), ar -> {
      assertTrue(ar.succeeded());
      testComplete();
    });
    await();
  }

  @Test
  public void testDeployUsingClassAndConfig() throws Exception {
    JsonObject config = generateJSONObject();
    vertx.deployVerticle("java:" + TestVerticle.class.getCanonicalName(), new DeploymentOptions().setConfig(config), ar -> {
      assertTrue(ar.succeeded());
      testComplete();
    });
    await();
  }

  @Test
  public void testDeployUsingClassFails() throws Exception {
    vertx.deployVerticle("java:uhqwuhiqwduhwd", ar -> {
      assertFalse(ar.succeeded());
      assertTrue(ar.cause() instanceof ClassNotFoundException);
      testComplete();
    });
    await();
  }

  @Test
  public void testDeployUndeployMultipleInstancesUsingClassName() throws Exception {
    int numInstances = 10;
    DeploymentOptions options = new DeploymentOptions().setInstances(numInstances);
    AtomicInteger deployCount = new AtomicInteger();
    AtomicInteger undeployCount = new AtomicInteger();
    AtomicInteger deployHandlerCount = new AtomicInteger();
    AtomicInteger undeployHandlerCount = new AtomicInteger();
    vertx.eventBus().<String>consumer("tvstarted").handler(msg -> {
      deployCount.incrementAndGet();
    });
    vertx.eventBus().<String>consumer("tvstopped").handler(msg -> {
      undeployCount.incrementAndGet();
      msg.reply("whatever");
    });
    CountDownLatch deployLatch = new CountDownLatch(1);
    vertx.deployVerticle(TestVerticle2.class.getCanonicalName(), options, onSuccess(depID -> {
      assertEquals(1, deployHandlerCount.incrementAndGet());
      deployLatch.countDown();
    }));
    awaitLatch(deployLatch);
    waitUntil(() -> deployCount.get() == numInstances);
    assertEquals(1, vertx.deploymentIDs().size());
    Deployment deployment = ((VertxInternal) vertx).getDeployment(vertx.deploymentIDs().iterator().next());
    Set<Verticle> verticles = deployment.getVerticles();
    assertEquals(numInstances, verticles.size());
    CountDownLatch undeployLatch = new CountDownLatch(1);
    assertEquals(numInstances, deployCount.get());
    vertx.undeploy(deployment.deploymentID(), onSuccess(v -> {
      assertEquals(1, undeployHandlerCount.incrementAndGet());
      undeployLatch.countDown();
    }));
    awaitLatch(undeployLatch);
    waitUntil(() -> deployCount.get() == numInstances);
    assertTrue(vertx.deploymentIDs().isEmpty());
  }

  @Test
  public void testDeployClassNotFound1() throws Exception {
    testDeployClassNotFound("iqwjdiqwjdoiqwjdqwij");
  }

  @Test
  public void testDeployClassNotFound2() throws Exception {
    testDeployClassNotFound("foo.bar.wibble.CiejdioqjdoiqwjdoiqjwdClass");
  }

  private void testDeployClassNotFound(String className) throws Exception {
    vertx.deployVerticle(className, ar -> {
      assertTrue(ar.failed());
      // No prefix or suffix so should be interpreted as a java class
      assertTrue(ar.cause() instanceof ClassNotFoundException);
      testComplete();
    });
    await();
  }

  @Test
  public void testDeployAsSource() throws Exception {
    String sourceFile = SourceVerticle.class.getName().replace('.', '/');
    sourceFile += ".java";
    vertx.deployVerticle("java:" + sourceFile, onSuccess(res -> {
      testComplete();
    }));
    await();
  }

  @Test
  public void testSimpleChildDeployment() throws Exception {
    Verticle verticle = new MyAsyncVerticle(f -> {
      Context parentContext = Vertx.currentContext();
      Verticle child1 = new MyAsyncVerticle(f2 -> {
        Context childContext = Vertx.currentContext();
        assertNotSame(parentContext, childContext);
        f2.complete(null);
        testComplete();
      }, f2 -> f2.complete(null));
      vertx.deployVerticle(child1, ar -> {
        assertTrue(ar.succeeded());
      });
      f.complete(null);
    }, f -> f.complete(null));
    vertx.deployVerticle(verticle, ar -> {
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
      Verticle child1 = new MyAsyncVerticle(f2 -> f2.complete(null), f2 -> {
        // Child stop is called
        assertFalse(parentStopCalled.get());
        assertFalse(childStopCalled.get());
        childStopCalled.set(true);
        f2.complete(null);
      });
      vertx.deployVerticle(child1, ar -> {
        assertTrue(ar.succeeded());
        childDepID.set(ar.result());
        f.complete(null);
      });
    }, f2 -> {
      // Parent stop is called
      assertFalse(parentStopCalled.get());
      assertTrue(childStopCalled.get());
      assertTrue(vertx.deploymentIDs().contains(parentDepID.get()));
      assertFalse(vertx.deploymentIDs().contains(childDepID.get()));
      parentStopCalled.set(true);
      testComplete();
      f2.complete(null);
    });
    vertx.deployVerticle(verticle, ar -> {
      parentDepID.set(ar.result());
      assertTrue(ar.succeeded());
      deployLatch.countDown();
    });
    assertTrue(deployLatch.await(10, TimeUnit.SECONDS));
    assertTrue(vertx.deploymentIDs().contains(parentDepID.get()));
    assertTrue(vertx.deploymentIDs().contains(childDepID.get()));

    // Now they're deployed, undeploy them
    vertx.undeploy(parentDepID.get(), ar -> {
      assertTrue(ar.succeeded());
    });
    await();
  }

  @Test
  public void testAsyncDeployCalledSynchronously() throws Exception {
    MyAsyncVerticle verticle = new MyAsyncVerticle(f -> f.complete(null), f -> f.complete(null));
    vertx.deployVerticle(verticle, ar -> {
      assertTrue(ar.succeeded());
      testComplete();
    });
    await();
  }

  @Test
  public void testAsyncDeployFailureCalledSynchronously() throws Exception {
    MyAsyncVerticle verticle = new MyAsyncVerticle(f -> f.fail(new Exception("foobar")), null);
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
    MyAsyncVerticle verticle = new MyAsyncVerticle(f -> {
      vertx.setTimer(delay, id -> {
        f.complete(null);
      });
    }, f -> f.complete(null));
    vertx.deployVerticle(verticle, ar -> {
      assertTrue(ar.succeeded());
      long now = System.currentTimeMillis();
      assertTrue(now - start >= delay);
      assertTrue(vertx.deploymentIDs().contains(ar.result()));
      testComplete();
    });
    Thread.sleep(delay / 2);
    assertTrue(vertx.deploymentIDs().isEmpty());
    await();
  }

  @Test
  public void testAsyncDeployFailure() throws Exception {
    long start = System.currentTimeMillis();
    long delay = 1000;
    MyAsyncVerticle verticle = new MyAsyncVerticle(f -> vertx.setTimer(delay, id -> f.fail(new Exception("foobar"))), null);
    vertx.deployVerticle(verticle, ar -> {
      assertFalse(ar.succeeded());
      assertEquals("foobar", ar.cause().getMessage());
      long now = System.currentTimeMillis();
      assertTrue(now - start >= delay);
      assertTrue(vertx.deploymentIDs().isEmpty());
      testComplete();
    });
    await();
  }

  @Test
  public void testAsyncUndeployCalledSynchronously() throws Exception {
    MyAsyncVerticle verticle = new MyAsyncVerticle(f -> f.complete(null), f ->  f.complete(null));
    vertx.deployVerticle(verticle, ar -> {
      assertTrue(ar.succeeded());
      vertx.undeploy(ar.result(), ar2 -> {
        assertTrue(ar2.succeeded());
        assertFalse(vertx.deploymentIDs().contains(ar.result()));
        testComplete();
      });
    });
    await();
  }

  @Test
  public void testAsyncUndeployFailureCalledSynchronously() throws Exception {
    MyAsyncVerticle verticle = new MyAsyncVerticle(f -> f.complete(null), f -> f.fail(new Exception("foobar")));
    vertx.deployVerticle(verticle, ar -> {
      assertTrue(ar.succeeded());
      vertx.undeploy(ar.result(), ar2 -> {
        assertFalse(ar2.succeeded());
        assertEquals("foobar", ar2.cause().getMessage());
        assertFalse(vertx.deploymentIDs().contains(ar.result()));
        testComplete();
      });
    });
    await();
  }

  @Test
  public void testAsyncUndeploy() throws Exception {
    long delay = 1000;
    MyAsyncVerticle verticle = new MyAsyncVerticle(f-> f.complete(null), f -> vertx.setTimer(delay, id -> f.complete(null)));
    vertx.deployVerticle(verticle, ar -> {
      assertTrue(ar.succeeded());
      long start = System.currentTimeMillis();
      vertx.undeploy(ar.result(), ar2 -> {
        assertTrue(ar2.succeeded());
        long now = System.currentTimeMillis();
        assertTrue(now - start >= delay);
        assertFalse(vertx.deploymentIDs().contains(ar.result()));
        testComplete();
      });
      vertx.setTimer(delay / 2, id -> assertFalse(vertx.deploymentIDs().isEmpty()));
    });
    await();
  }

  @Test
  public void testAsyncUndeployFailure() throws Exception {
    long delay = 1000;
    MyAsyncVerticle verticle = new MyAsyncVerticle(f-> f.complete(null), f -> vertx.setTimer(delay, id -> f.fail(new Exception("foobar"))));
    vertx.deployVerticle(verticle, ar -> {
      assertTrue(ar.succeeded());
      long start = System.currentTimeMillis();
      vertx.undeploy(ar.result(), ar2 -> {
        assertFalse(ar2.succeeded());
        long now = System.currentTimeMillis();
        assertTrue(now - start >= delay);
        assertFalse(vertx.deploymentIDs().contains(ar.result()));
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
      completionHandler.handle(Future.succeededFuture());
    };
    Closeable myCloseable2 = completionHandler -> {
      closedCount.incrementAndGet();
      completionHandler.handle(Future.succeededFuture());
    };
    MyAsyncVerticle verticle = new MyAsyncVerticle(f-> {
      ContextImpl ctx = (ContextImpl)Vertx.currentContext();
      ctx.addCloseHook(myCloseable1);
      ctx.addCloseHook(myCloseable2);
      f.complete(null);
    }, f -> f.complete(null));
    vertx.deployVerticle(verticle, ar -> {
      assertTrue(ar.succeeded());
      assertEquals(0, closedCount.get());
      // Now undeploy
      vertx.undeploy(ar.result(), ar2 -> {
        assertTrue(ar2.succeeded());
        assertEquals(2, closedCount.get());
        testComplete();
      });
    });
    await();
  }

  @Test
  public void testIsolationGroup1() throws Exception {
    vertx.deployVerticle("java:" + TestVerticle.class.getCanonicalName(), new DeploymentOptions().setIsolationGroup("somegroup"), ar -> {
      assertTrue(ar.succeeded());
      assertEquals(0, TestVerticle.instanceCount.get());
      testComplete();
    });
    await();
  }

  @Test
  public void testNullIsolationGroup() throws Exception {
    vertx.deployVerticle("java:" + TestVerticle.class.getCanonicalName(), new DeploymentOptions().setIsolationGroup(null), ar -> {
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
  public void testExtraClasspath() throws Exception {
    String cp1 = "foo/bar/wibble";
    String cp2 = "blah/socks/mice";
    List<String> extraClasspath = Arrays.asList(cp1, cp2);
    vertx.deployVerticle("java:" + TestVerticle.class.getCanonicalName(), new DeploymentOptions().setIsolationGroup("somegroup").
      setExtraClasspath(extraClasspath), ar -> {
      assertTrue(ar.succeeded());
      Deployment deployment = ((VertxInternal) vertx).getDeployment(ar.result());
      Verticle verticle = deployment.getVerticles().iterator().next();
      ClassLoader cl = verticle.getClass().getClassLoader();
      URLClassLoader urlc = (URLClassLoader) cl;
      assertTrue(urlc.getURLs()[0].toString().endsWith(cp1));
      assertTrue(urlc.getURLs()[1].toString().endsWith(cp2));
      testComplete();
    });
    await();
  }

  public static class ParentVerticle extends AbstractVerticle {

    @Override
    public void start(Future<Void> startFuture) throws Exception {
      vertx.deployVerticle("java:" + ChildVerticle.class.getName(), ar -> {
        if (ar.succeeded()) {
          startFuture.complete(null);
        } else {
          ar.cause().printStackTrace();
        }
      });
    }
  }

  public static class ChildVerticle extends AbstractVerticle {
  }

  @Test
  public void testUndeployAll() throws Exception {
    int numVerticles = 10;
    List<MyVerticle> verticles = new ArrayList<>();
    CountDownLatch latch = new CountDownLatch(numVerticles);
    for (int i = 0; i < numVerticles; i++) {
      MyVerticle verticle = new MyVerticle();
      verticles.add(verticle);
      vertx.deployVerticle("java:" + ParentVerticle.class.getName(), onSuccess(res -> {
        latch.countDown();
      }));
    }
    awaitLatch(latch);
    assertEquals(2 * numVerticles, vertx.deploymentIDs().size());
    vertx.close(ar -> {
      assertTrue(ar.succeeded());
      assertEquals(0, vertx.deploymentIDs().size());
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
      vertx.deployVerticle(verticle, ar2 -> {
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
    vertx.eventBus().<JsonObject>consumer("testcounts").handler((Message<JsonObject> msg) -> {
      countMap.put(msg.body().getString("deploymentID"), msg.body().getInteger("count"));
    });
    CountDownLatch latch = new CountDownLatch(1);
    AtomicReference<String> deploymentID1 = new AtomicReference<>();
    AtomicReference<String> deploymentID2 = new AtomicReference<>();
    vertx.deployVerticle("java:" + TestVerticle.class.getCanonicalName(), new DeploymentOptions().setIsolationGroup(group1), ar -> {
      assertTrue(ar.succeeded());
      deploymentID1.set(ar.result());
      assertEquals(0, TestVerticle.instanceCount.get());
      vertx.deployVerticle("java:" + TestVerticle.class.getCanonicalName(), new DeploymentOptions().setIsolationGroup(group2), ar2 -> {
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
    assertEquals(deploymentID, verticle.deploymentID);
    if (config == null) {
      assertEquals(0, verticle.config.size());
    } else {
      assertEquals(config, verticle.config);
    }
    assertTrue(verticle.startCalled);
    assertFalse(verticle.stopCalled);
    assertTrue(vertx.deploymentIDs().contains(deploymentID));
    assertEquals(instances, vertx.deploymentIDs().size());
    Context currentContext = Vertx.currentContext();
    assertNotSame(currentContext, verticle.startContext);
  }

  private JsonObject generateJSONObject() {
    return new JsonObject().put("foo", "bar").put("blah", 123)
      .put("obj", new JsonObject().put("quux", "flip"));
  }

  public class MyVerticle extends AbstractVerticle {

    static final int NOOP = 0, THROW_EXCEPTION = 1, THROW_ERROR = 2;

    boolean startCalled;
    boolean stopCalled;
    Context startContext;
    Context stopContext;
    int startAction;
    int stopAction;
    String deploymentID;
    JsonObject config;

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
          startContext = Vertx.currentContext();
      }
      deploymentID = Vertx.currentContext().deploymentID();
      config = context.config();
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
          stopContext = Vertx.currentContext();
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

