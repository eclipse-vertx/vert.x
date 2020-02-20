/*
 * Copyright (c) 2011-2019 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */

package io.vertx.core;

import io.vertx.core.eventbus.Message;
import io.vertx.core.impl.ContextInternal;
import io.vertx.core.impl.Deployment;
import io.vertx.core.impl.VertxInternal;
import io.vertx.core.impl.verticle.CompilingClassLoader;
import io.vertx.core.json.JsonObject;
import io.vertx.test.core.TestUtils;
import io.vertx.test.core.VertxTestBase;
import io.vertx.test.verticles.*;
import io.vertx.test.verticles.sourceverticle.SourceVerticle;
import org.junit.Test;

import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Supplier;

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
    assertNull(options.getIsolatedClasses());
    List<String> isol = Arrays.asList("com.foo.MyClass", "org.foo.*");
    assertEquals(options, options.setIsolatedClasses(isol));
    assertSame(isol, options.getIsolatedClasses());
    String workerPoolName = TestUtils.randomAlphaString(10);
    assertEquals(options, options.setWorkerPoolName(workerPoolName));
    assertEquals(workerPoolName, options.getWorkerPoolName());
    int workerPoolSize = TestUtils.randomPositiveInt();
    assertEquals(options, options.setWorkerPoolSize(workerPoolSize));
    assertEquals(workerPoolSize, options.getWorkerPoolSize());
    long maxWorkerExecuteTime = TestUtils.randomPositiveLong();
    assertEquals(options, options.setMaxWorkerExecuteTime(maxWorkerExecuteTime));
    assertEquals(maxWorkerExecuteTime, options.getMaxWorkerExecuteTime());
    assertEquals(options, options.setMaxWorkerExecuteTimeUnit(TimeUnit.MILLISECONDS));
    assertEquals(TimeUnit.MILLISECONDS, options.getMaxWorkerExecuteTimeUnit());
  }

  @Test
  public void testCopyOptions() {
    DeploymentOptions options = new DeploymentOptions();
    JsonObject config = new JsonObject().put("foo", "bar");
    Random rand = new Random();
    boolean worker = rand.nextBoolean();
    String isolationGroup = TestUtils.randomAlphaString(100);
    boolean ha = rand.nextBoolean();
    List<String> cp = Arrays.asList("foo", "bar");
    List<String> isol = Arrays.asList("com.foo.MyClass", "org.foo.*");
    String poolName = TestUtils.randomAlphaString(10);
    int poolSize = TestUtils.randomPositiveInt();
    long maxWorkerExecuteTime = TestUtils.randomPositiveLong();
    TimeUnit maxWorkerExecuteTimeUnit = TimeUnit.MILLISECONDS;
    options.setConfig(config);
    options.setWorker(worker);
    options.setIsolationGroup(isolationGroup);
    options.setHa(ha);
    options.setExtraClasspath(cp);
    options.setIsolatedClasses(isol);
    options.setWorkerPoolName(poolName);
    options.setWorkerPoolSize(poolSize);
    options.setMaxWorkerExecuteTime(maxWorkerExecuteTime);
    options.setMaxWorkerExecuteTimeUnit(maxWorkerExecuteTimeUnit);
    DeploymentOptions copy = new DeploymentOptions(options);
    assertEquals(worker, copy.isWorker());
    assertEquals(isolationGroup, copy.getIsolationGroup());
    assertNotSame(config, copy.getConfig());
    assertEquals("bar", copy.getConfig().getString("foo"));
    assertEquals(ha, copy.isHa());
    assertEquals(cp, copy.getExtraClasspath());
    assertNotSame(cp, copy.getExtraClasspath());
    assertEquals(isol, copy.getIsolatedClasses());
    assertNotSame(isol, copy.getIsolatedClasses());
    assertEquals(poolName, copy.getWorkerPoolName());
    assertEquals(poolSize, copy.getWorkerPoolSize());
    assertEquals(maxWorkerExecuteTime, copy.getMaxWorkerExecuteTime());
    assertEquals(maxWorkerExecuteTimeUnit, copy.getMaxWorkerExecuteTimeUnit());
  }

  @Test
  public void testDefaultJsonOptions() {
    DeploymentOptions def = new DeploymentOptions();
    DeploymentOptions json = new DeploymentOptions(new JsonObject());
    assertEquals(def.getConfig(), json.getConfig());
    assertEquals(def.isWorker(), json.isWorker());
    assertEquals(def.getIsolationGroup(), json.getIsolationGroup());
    assertEquals(def.isHa(), json.isHa());
    assertEquals(def.getExtraClasspath(), json.getExtraClasspath());
    assertEquals(def.getIsolatedClasses(), json.getIsolatedClasses());
    assertEquals(def.getWorkerPoolName(), json.getWorkerPoolName());
    assertEquals(def.getWorkerPoolSize(), json.getWorkerPoolSize());
    assertEquals(def.getMaxWorkerExecuteTime(), json.getMaxWorkerExecuteTime());
    assertEquals(def.getMaxWorkerExecuteTimeUnit(), json.getMaxWorkerExecuteTimeUnit());
  }

  @Test
  public void testJsonOptions() {
    JsonObject config = new JsonObject().put("foo", "bar");
    Random rand = new Random();
    boolean worker = rand.nextBoolean();
    boolean ha = rand.nextBoolean();
    List<String> cp = Arrays.asList("foo", "bar");
    List<String> isol = Arrays.asList("com.foo.MyClass", "org.foo.*");
    String poolName = TestUtils.randomAlphaString(10);
    int poolSize = TestUtils.randomPositiveInt();
    long maxWorkerExecuteTime = TestUtils.randomPositiveLong();
    TimeUnit maxWorkerExecuteTimeUnit = TimeUnit.MILLISECONDS;
    JsonObject json = new JsonObject();
    json.put("config", config);
    json.put("worker", worker);
    json.put("ha", ha);
    json.put("workerPoolName", poolName);
    json.put("workerPoolSize", poolSize);
    json.put("maxWorkerExecuteTime", maxWorkerExecuteTime);
    json.put("maxWorkerExecuteTimeUnit", maxWorkerExecuteTimeUnit);
    DeploymentOptions options = new DeploymentOptions(json);
    assertEquals(worker, options.isWorker());
    assertEquals("bar", options.getConfig().getString("foo"));
    assertEquals(ha, options.isHa());
    assertEquals(poolName, options.getWorkerPoolName());
    assertEquals(poolSize, options.getWorkerPoolSize());
    assertEquals(maxWorkerExecuteTime, options.getMaxWorkerExecuteTime());
    assertEquals(maxWorkerExecuteTimeUnit, options.getMaxWorkerExecuteTimeUnit());
  }

  @Test
  public void testToJson() {
    DeploymentOptions options = new DeploymentOptions();
    JsonObject config = new JsonObject().put("foo", "bar");
    Random rand = new Random();
    boolean worker = rand.nextBoolean();
    boolean ha = rand.nextBoolean();
    List<String> cp = Arrays.asList("foo", "bar");
    List<String> isol = Arrays.asList("com.foo.MyClass", "org.foo.*");
    String poolName = TestUtils.randomAlphaString(10);
    int poolSize = TestUtils.randomPositiveInt();
    long maxWorkerExecuteTime = TestUtils.randomPositiveLong();
    TimeUnit maxWorkerExecuteTimeUnit = TimeUnit.MILLISECONDS;
    options.setConfig(config);
    options.setWorker(worker);
    options.setHa(ha);
    options.setWorkerPoolName(poolName);
    options.setWorkerPoolSize(poolSize);
    options.setMaxWorkerExecuteTime(maxWorkerExecuteTime);
    options.setMaxWorkerExecuteTimeUnit(maxWorkerExecuteTimeUnit);
    JsonObject json = options.toJson();
    DeploymentOptions copy = new DeploymentOptions(json);
    assertEquals(worker, copy.isWorker());
    assertEquals("bar", copy.getConfig().getString("foo"));
    assertEquals(ha, copy.isHa());
    assertEquals(poolName, copy.getWorkerPoolName());
    assertEquals(poolSize, copy.getWorkerPoolSize());
    assertEquals(maxWorkerExecuteTime, copy.getMaxWorkerExecuteTime());
    assertEquals(maxWorkerExecuteTimeUnit, copy.getMaxWorkerExecuteTimeUnit());
  }

  @Test
  public void testDeployFromTestThread() throws Exception {
    MyVerticle verticle = new MyVerticle();
    vertx.deployVerticle(verticle, ar -> {
      assertDeployment(1, verticle, null, ar);
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
    assertWaitUntil(() -> vertx.deploymentIDs().size() == 1);
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
      assertTrue(verticle.startContext.isWorkerContext());
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
    assertWaitUntil(() -> vertx.deploymentIDs().isEmpty());
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

  @Test(expected = IllegalArgumentException.class)
  public void testDeployInstanceSetIsolatedClasses() throws Exception {
    vertx.deployVerticle(new MyVerticle(), new DeploymentOptions().setIsolatedClasses(Arrays.asList("foo")));
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
    assertWaitUntil(() -> deployCount.get() == numInstances);
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
    assertWaitUntil(() -> deployCount.get() == numInstances);
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
  public void testSimpleChildDeployment() {
    waitFor(3);
    Consumer<Promise<Void>> start = f -> {
      Context parentContext = Vertx.currentContext();
      Verticle child1 = new MyAsyncVerticle(f2 -> {
        Context childContext = Vertx.currentContext();
        assertNotSame(parentContext, childContext);
        f2.complete();
        complete();
      }, Promise::complete);
      vertx.deployVerticle(child1, onSuccess(id -> complete()));
      f.complete();
    };
    Verticle verticle = new MyAsyncVerticle(start, Promise::complete);
    vertx.deployVerticle(verticle, onSuccess(id -> complete()));
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
  public void testSimpleChildUndeploymentOnParentAsyncFailure() throws Exception {
    AtomicInteger childDeployed = new AtomicInteger();
    AtomicInteger childUndeployed = new AtomicInteger();
    vertx.deployVerticle(new AbstractVerticle() {
      @Override
      public void start(Promise<Void> startPromise) throws Exception {
        vertx.deployVerticle(new AbstractVerticle() {
          @Override
          public void start() throws Exception {
            childDeployed.incrementAndGet();
          }
          @Override
          public void stop() throws Exception {
            childUndeployed.incrementAndGet();
          }
        }, onSuccess(child -> {
          startPromise.fail("Undeployed");
        }));
      }
    }, onFailure(expected -> {
      assertEquals(1, childDeployed.get());
      assertEquals(1, childUndeployed.get());
      testComplete();
    }));
    await();
  }

  @Test
  public void testSimpleChildUndeploymentOnParentSyncFailure() throws Exception {
    AtomicInteger childDeployed = new AtomicInteger();
    AtomicInteger childUndeployed = new AtomicInteger();
    vertx.deployVerticle(new AbstractVerticle() {
      @Override
      public void start(Promise<Void> startPromise) throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        vertx.deployVerticle(new AbstractVerticle() {
          @Override
          public void start() throws Exception {
            childDeployed.incrementAndGet();
            latch.countDown();
          }
          @Override
          public void stop() throws Exception {
            childUndeployed.incrementAndGet();
          }
        });
        awaitLatch(latch);
        throw new RuntimeException();
      }
    }, onFailure(expected -> {
      waitUntil(() -> childDeployed.get() == 1);
      waitUntil(() -> childUndeployed.get() == 1);
      testComplete();
    }));
    await();
  }

  @Test
  public void testSimpleChildUndeploymentDeployedAfterParentFailure() throws Exception {
    AtomicInteger parentFailed = new AtomicInteger();
    vertx.deployVerticle(new AbstractVerticle() {
      @Override
      public void start(Promise<Void> startPromise) throws Exception {
        vertx.deployVerticle(new AbstractVerticle() {
          @Override
          public void start(Promise<Void> startPromise) throws Exception {
            vertx.setTimer(100, id -> {
              startPromise.complete();
            });
          }
          @Override
          public void stop() throws Exception {
            waitUntil(() -> parentFailed.get() == 1);
            testComplete();
          }
        });
        parentFailed.incrementAndGet();
        throw new RuntimeException();
      }
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
  public void testAsyncUndeployFailsAfterSuccess() {
    waitFor(2);
    Verticle verticle = new AbstractVerticle() {
      @Override
      public void stop(Promise<Void> stopPromise) throws Exception {
        stopPromise.complete();
        throw new Exception();
      }
    };
    Context ctx = vertx.getOrCreateContext();
    ctx.runOnContext(v1 -> {
      vertx.deployVerticle(verticle, onSuccess(id -> {
        ctx.exceptionHandler(err -> {
          complete();
        });
        vertx.undeploy(id, onSuccess(v2 -> {
          complete();
        }));
      }));
    });
    await();
  }
  @Test
  public void testChildUndeployedDirectly() throws Exception {
    Verticle parent = new AbstractVerticle() {
      @Override
      public void start(Promise<Void> startPromise) throws Exception {

        Verticle child = new AbstractVerticle() {
          @Override
          public void start(Promise<Void> startPromise) throws Exception {
            startPromise.complete();

            // Undeploy it directly
            vertx.runOnContext(v -> vertx.undeploy(context.deploymentID()));
          }
        };

        vertx.deployVerticle(child, onSuccess(depID -> {
          startPromise.complete();
        }));

      }
    };

    vertx.deployVerticle(parent, onSuccess(depID -> {
      vertx.setTimer(10, tid -> vertx.undeploy(depID, onSuccess(v -> {
        testComplete();
      })));
    }));


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
      ContextInternal ctx = (ContextInternal)Vertx.currentContext();
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
  public void testDeployWhenClosedShouldFail() throws Exception {
    CountDownLatch closed = new CountDownLatch(1);
    vertx.close(ar -> {
      assertTrue(ar.succeeded());
      closed.countDown();
    });
    awaitLatch(closed);
    vertx.deployVerticle(new AbstractVerticle() {
    }, ar -> {
      assertFalse(ar.succeeded());
      assertEquals("Vert.x closed", ar.cause().getMessage());
      testComplete();
    });
    await();
  }

  @Test
  public void testIsolationGroup1() throws Exception {
    boolean expectedSuccess = Thread.currentThread().getContextClassLoader() instanceof URLClassLoader;
    List<String> isolatedClasses = Arrays.asList(TestVerticle.class.getCanonicalName());
    try {
      vertx.deployVerticle("java:" + TestVerticle.class.getCanonicalName(),
        new DeploymentOptions().setIsolationGroup("somegroup").setIsolatedClasses(isolatedClasses), ar -> {
          assertTrue(ar.succeeded());
          assertEquals(0, TestVerticle.instanceCount.get());
          testComplete();
      });
      assertTrue(expectedSuccess);
      await();
    } catch (IllegalStateException e) {
      assertFalse(expectedSuccess);
    }
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
    List<String> isolatedClasses = Arrays.asList(TestVerticle.class.getCanonicalName());
    testIsolationGroup("somegroup", "somegroup", 1, 2, isolatedClasses, "java:" + TestVerticle.class.getCanonicalName());
  }

  @Test
  public void testIsolationGroupSameGroupWildcard() throws Exception {
    List<String> isolatedClasses = Arrays.asList("io.vertx.test.verticles.*");
    testIsolationGroup("somegroup", "somegroup", 1, 2, isolatedClasses, "java:" + TestVerticle.class.getCanonicalName());
  }

  @Test
  public void testIsolationGroupDifferentGroup() throws Exception {
    List<String> isolatedClasses = Arrays.asList(TestVerticle.class.getCanonicalName());
    testIsolationGroup("somegroup", "someothergroup", 1, 1, isolatedClasses, "java:" + TestVerticle.class.getCanonicalName());
  }

  private String createClassOutsideClasspath(String className) throws Exception {
    File dir = Files.createTempDirectory("vertx").toFile();
    dir.deleteOnExit();
    File source = new File(dir, className + ".java");
    Files.write(source.toPath(), ("public class " + className + " extends io.vertx.core.AbstractVerticle {} ").getBytes());

    URLClassLoader loader = new URLClassLoader(new URL[]{dir.toURI().toURL()}, Thread.currentThread().getContextClassLoader());

    CompilingClassLoader compilingClassLoader = new CompilingClassLoader(loader, className + ".java");
    compilingClassLoader.loadClass(className);

    byte[] bytes = compilingClassLoader.getClassBytes(className);
    assertNotNull(bytes);

    File classFile = new File(dir, className + ".class");
    Files.write(classFile.toPath(), bytes);

    return dir.getAbsolutePath();
  }

  @Test
  public void testExtraClasspathLoaderNotInParentLoader() throws Exception {
    boolean expectedSuccess = Thread.currentThread().getContextClassLoader() instanceof URLClassLoader;
    String dir = createClassOutsideClasspath("MyVerticle");
    List<String> extraClasspath = Arrays.asList(dir);
    try {
      DeploymentOptions options = new DeploymentOptions().setIsolationGroup("somegroup").setExtraClasspath(extraClasspath);
      vertx.deployVerticle("java:" + ExtraCPVerticleNotInParentLoader.class.getCanonicalName(), options, onSuccess(id1 -> {
        vertx.deployVerticle("java:" + ExtraCPVerticleNotInParentLoader.class.getCanonicalName(), options, onSuccess(id2 -> {
          vertx.undeploy(id1, onSuccess(v1 -> {
            assertFalse(ExtraCPVerticleNotInParentLoader.cl.isClosed());
            vertx.undeploy(id2, onSuccess(v2 -> {
              assertTrue(ExtraCPVerticleNotInParentLoader.cl.isClosed());
              testComplete();
            }));
          }));
        }));
      }));
      assertTrue(expectedSuccess);
      await();
    } catch (IllegalStateException e) {
      assertFalse(expectedSuccess);
    } finally {
      ExtraCPVerticleNotInParentLoader.cl = null;
    }
  }

  @Test
  public void testExtraClasspathLoaderAlreadyInParentLoader() throws Exception {
    String dir = createClassOutsideClasspath("MyVerticle");
    URLClassLoader loader = new URLClassLoader(new URL[]{new File(dir).toURI().toURL()}, Thread.currentThread().getContextClassLoader());
    List<String> extraClasspath = Arrays.asList(dir);
    ClassLoader currentCL = Thread.currentThread().getContextClassLoader();
    Thread.currentThread().setContextClassLoader(loader);
    try {
      vertx.deployVerticle("java:" + ExtraCPVerticleAlreadyInParentLoader.class.getCanonicalName(), new DeploymentOptions().setIsolationGroup("somegroup").
          setExtraClasspath(extraClasspath), ar -> {
        assertTrue(ar.succeeded());
        testComplete();
      });
    } finally {
      Thread.currentThread().setContextClassLoader(currentCL);
    }
    await();
  }

  @Test
  public void testCloseIsolationGroup() throws Exception {
    testCloseIsolationGroup(1);
  }

  @Test
  public void testCloseIsolationGroupMultiInstances() throws Exception {
    testCloseIsolationGroup(3);
  }

  private void testCloseIsolationGroup(int instances) throws Exception {
    boolean expectedSuccess = Thread.currentThread().getContextClassLoader() instanceof URLClassLoader;
    String dir = createClassOutsideClasspath("MyVerticle");
    List<String> extraClasspath = Collections.singletonList(dir);
    Vertx vertx = Vertx.vertx();
    try {
      DeploymentOptions options = new DeploymentOptions()
        .setInstances(instances)
        .setIsolationGroup("somegroup")
        .setExtraClasspath(extraClasspath);
      vertx.deployVerticle("java:" + ExtraCPVerticleNotInParentLoader.class.getCanonicalName(), options, onSuccess(id -> {
        vertx.close(onSuccess(v -> {
          assertTrue(ExtraCPVerticleNotInParentLoader.cl.isClosed());
          testComplete();
        }));
      }));
      assertTrue(expectedSuccess);
      await();
    } catch (IllegalStateException e) {
      assertFalse(expectedSuccess);
    } finally {
      ExtraCPVerticleNotInParentLoader.cl = null;
    }
  }

  public static class ParentVerticle extends AbstractVerticle {

    @Override
    public void start(Promise<Void> startPromise) throws Exception {
      vertx.deployVerticle("java:" + ChildVerticle.class.getName(), ar -> {
        if (ar.succeeded()) {
          startPromise.complete(null);
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
  public void testDeployChildOnParentUndeploy() {
    class ParentVerticle extends AbstractVerticle {
      @Override
      public void stop(Promise<Void> stopPromise) {
        vertx.deployVerticle(ChildVerticle.class.getName())
          .<Void>mapEmpty()
          .setHandler(stopPromise);
      }
    }

    vertx.deployVerticle(new ParentVerticle(), onSuccess(id ->
      vertx.undeploy(id, onFailure(u -> testComplete()))));
    await();
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

  @Test
  public void testGetInstanceCount() {
    class MultiInstanceVerticle extends AbstractVerticle {
      @Override
      public void start() {
        assertEquals(vertx.getOrCreateContext().getInstanceCount(), 1);
      }
    }

    vertx.deployVerticle(new MultiInstanceVerticle(), ar -> {
      assertTrue(ar.succeeded());
      testComplete();
    });
    await();
  }

  @Test
  public void testGetInstanceCountMultipleVerticles() throws Exception {
    AtomicInteger messageCount = new AtomicInteger(0);
    AtomicInteger totalReportedInstances = new AtomicInteger(0);

    vertx.eventBus().consumer("instanceCount", event -> {
      messageCount.incrementAndGet();
      totalReportedInstances.addAndGet((int)event.body());
      if(messageCount.intValue() == 3) {
        assertEquals(9, totalReportedInstances.get());
        testComplete();
      }
    });

    vertx.deployVerticle(TestVerticle3.class.getCanonicalName(), new DeploymentOptions().setInstances(3), ar -> {
      assertTrue(ar.succeeded());
    });
    await();
    Deployment deployment = ((VertxInternal) vertx).getDeployment(vertx.deploymentIDs().iterator().next());
    CountDownLatch latch = new CountDownLatch(1);
    vertx.undeploy(deployment.deploymentID(), ar -> latch.countDown());
    awaitLatch(latch);
  }

  @Test
  public void testFailedVerticleStopNotCalled() {
    Verticle verticleChild = new AbstractVerticle() {
      @Override
      public void start(Promise<Void> startPromise) throws Exception {
        startPromise.fail("wibble");
      }
      @Override
      public void stop() {
        fail("stop should not be called");
      }

    };
    Verticle verticleParent = new AbstractVerticle() {
      @Override
      public void start(Promise<Void> startPromise) throws Exception {
        vertx.deployVerticle(verticleChild, onFailure(v -> {
          startPromise.complete();
        }));
      }
    };
    vertx.deployVerticle(verticleParent, onSuccess(depID -> {
      vertx.undeploy(depID, onSuccess(v -> {
        testComplete();
      }));
    }));
    await();
  }

  @Test
  public void testUndeployWhenUndeployIsInProgress() throws Exception {
    int numIts = 10;
    CountDownLatch latch = new CountDownLatch(numIts);
    for (int i = 0; i < numIts; i++) {
      Verticle parent = new AbstractVerticle() {
        @Override
        public void start() throws Exception {
          vertx.deployVerticle(new AbstractVerticle() {
          }, id -> vertx.undeploy(id.result()));
        }
      };
      vertx.deployVerticle(parent, id -> {
        vertx.undeploy(id.result(), res -> {
          latch.countDown();
        });
      });
    }
    awaitLatch(latch);
  }

  @Test
  public void testDeploySupplier() throws Exception {
    JsonObject config = generateJSONObject();
    Set<MyVerticle> myVerticles = Collections.synchronizedSet(new HashSet<>());
    Supplier<Verticle> supplier = () -> {
      MyVerticle myVerticle = new MyVerticle();
      myVerticles.add(myVerticle);
      return myVerticle;
    };
    DeploymentOptions options = new DeploymentOptions().setInstances(4).setConfig(config);
    Consumer<String> check = deploymentId -> {
      myVerticles.forEach(myVerticle -> {
        assertEquals(deploymentId, myVerticle.deploymentID);
        assertEquals(config, myVerticle.config);
        assertTrue(myVerticle.startCalled);
      });
    };

    // Without completion handler
    CompletableFuture<String> deployedLatch = new CompletableFuture<>();
    vertx.deployVerticle(supplier, options, onSuccess(id -> deployedLatch.complete(id)));
    String id = deployedLatch.get(10, TimeUnit.SECONDS);
    check.accept(id);
    myVerticles.clear();
    vertx.undeploy(id);
    assertWaitUntil(() -> vertx.deploymentIDs().size() == 0);

    // With completion handler
    vertx.deployVerticle(supplier, options, onSuccess(deploymentId -> {
      check.accept(deploymentId);
      testComplete();
    }));
    await();
  }

  @Test
  public void testDeploySupplierNull() {
    Supplier<Verticle> supplier = () -> null;
    DeploymentOptions options = new DeploymentOptions();
    // Without completion handler
    vertx.deployVerticle(supplier, options);
    assertEquals(Collections.emptySet(), vertx.deploymentIDs());
    // With completion handler
    vertx.deployVerticle(supplier, options, onFailure(t -> {
      assertEquals(Collections.emptySet(), vertx.deploymentIDs());
      testComplete();
    }));
    await();
  }

  @Test
  public void testDeploySupplierDuplicate() {
    MyVerticle myVerticle = new MyVerticle();
    Supplier<Verticle> supplier = () -> myVerticle;
    DeploymentOptions options = new DeploymentOptions().setInstances(2);
    // Without completion handler
    vertx.deployVerticle(supplier, options);
    assertEquals(Collections.emptySet(), vertx.deploymentIDs());
    // With completion handler
    vertx.deployVerticle(supplier, options, onFailure(t -> {
      assertEquals(Collections.emptySet(), vertx.deploymentIDs());
      assertFalse(myVerticle.startCalled);
      testComplete();
    }));
    await();
  }

  @Test
  public void testDeploySupplierThrowsException() {
    Supplier<Verticle> supplier = () -> {
      throw new RuntimeException("boum");
    };
    // Without completion handler
    vertx.deployVerticle(supplier, new DeploymentOptions());
    assertEquals(Collections.emptySet(), vertx.deploymentIDs());
    // With completion handler
    vertx.deployVerticle(supplier, new DeploymentOptions().setInstances(2), onFailure(t -> {
      assertEquals(Collections.emptySet(), vertx.deploymentIDs());
      testComplete();
    }));
    await();
  }

  @Test
  public void testDeployClass() {
    JsonObject config = generateJSONObject();
    vertx.deployVerticle(ReferenceSavingMyVerticle.class, new DeploymentOptions().setInstances(4).setConfig(config), onSuccess(deploymentId -> {
      ReferenceSavingMyVerticle.myVerticles.forEach(myVerticle -> {
        assertEquals(deploymentId, myVerticle.deploymentID);
        assertEquals(config, myVerticle.config);
        assertTrue(myVerticle.startCalled);
      });
      testComplete();
    }));
    await();
  }

  @Test
  public void testDeployClassNoDefaultPublicConstructor() throws Exception {
    class NoDefaultPublicConstructorVerticle extends AbstractVerticle {
    }
    vertx.deployVerticle(NoDefaultPublicConstructorVerticle.class, new DeploymentOptions(), onFailure(t -> {
      testComplete();
    }));
    await();
  }

  @Test
  public void testFailedDeployRunsContextShutdownHook() throws Exception {
    AtomicBoolean closeHookCalledBeforeDeployFailure = new AtomicBoolean(false);
    Closeable closeable = completionHandler -> {
      closeHookCalledBeforeDeployFailure.set(true);
      completionHandler.handle(Future.succeededFuture());
    };
    Verticle v = new AbstractVerticle() {
      @Override
      public void start(Promise<Void> startPromise) throws Exception {
        this.context.addCloseHook(closeable);
        startPromise.fail("Fail to deploy.");
      }
    };
    vertx.deployVerticle(v, asyncResult -> {
      assertTrue(closeHookCalledBeforeDeployFailure.get());
      assertTrue(asyncResult.failed());
      assertNull(asyncResult.result());
      testComplete();
    });
    await();
  }

  @Test
  public void testMultipleFailedDeploys() throws InterruptedException {
    int instances = 10;
    DeploymentOptions options = new DeploymentOptions();
    options.setInstances(instances);

    AtomicBoolean called = new AtomicBoolean(false);

    vertx.deployVerticle(() -> {
      Verticle v = new AbstractVerticle() {
        @Override
        public void start(final Promise<Void> startPromise) throws Exception {
          startPromise.fail("Fail to deploy.");
        }
      };
      return v;
    }, options, asyncResult -> {
      assertTrue(asyncResult.failed());
      assertNull(asyncResult.result());
      if (!called.compareAndSet(false, true)) {
        fail("Completion handler called more than once");
      }
      vertx.setTimer(30, id -> {
        testComplete();
      });

    });
    await();
  }

  private void testIsolationGroup(String group1, String group2, int count1, int count2, List<String> isolatedClasses,
                                  String verticleID) throws Exception {
    Map<String, Integer> countMap = new ConcurrentHashMap<>();
    vertx.eventBus().<JsonObject>consumer("testcounts").handler((Message<JsonObject> msg) -> {
      countMap.put(msg.body().getString("deploymentID"), msg.body().getInteger("count"));
    });
    CountDownLatch latch = new CountDownLatch(1);
    AtomicReference<String> deploymentID1 = new AtomicReference<>();
    AtomicReference<String> deploymentID2 = new AtomicReference<>();
    boolean expectedSuccess = Thread.currentThread().getContextClassLoader() instanceof URLClassLoader;
    try {
      vertx.deployVerticle(verticleID, new DeploymentOptions().
        setIsolationGroup(group1).setIsolatedClasses(isolatedClasses), ar -> {
        assertTrue(ar.succeeded());
        deploymentID1.set(ar.result());
        assertEquals(0, TestVerticle.instanceCount.get());
        vertx.deployVerticle(verticleID,
          new DeploymentOptions().setIsolationGroup(group2).setIsolatedClasses(isolatedClasses), ar2 -> {
          assertTrue(ar2.succeeded());
          deploymentID2.set(ar2.result());
          assertEquals(0, TestVerticle.instanceCount.get());
          latch.countDown();
        });
      });
      awaitLatch(latch);
      // Wait until two entries in the map
      assertWaitUntil(() -> countMap.size() == 2);
      assertEquals(count1, countMap.get(deploymentID1.get()).intValue());
      assertEquals(count2, countMap.get(deploymentID2.get()).intValue());
      assertTrue(expectedSuccess);
    } catch (IllegalStateException e) {
      assertFalse(expectedSuccess);
    }
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

  public static class MyVerticle extends AbstractVerticle {

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

  public static class ReferenceSavingMyVerticle extends MyVerticle {
    static Set<MyVerticle> myVerticles = new HashSet<>();

    public ReferenceSavingMyVerticle() {
      super();
      myVerticles.add(this);
    }
  }

  public class MyAsyncVerticle extends AbstractVerticle {

    private final Consumer<Promise<Void>> startConsumer;
    private final Consumer<Promise<Void>> stopConsumer;

    public MyAsyncVerticle(Consumer<Promise<Void>> startConsumer, Consumer<Promise<Void>> stopConsumer) {
      this.startConsumer = startConsumer;
      this.stopConsumer = stopConsumer;
    }

    @Override
    public void start(Promise<Void> startPromise) throws Exception {
      if (startConsumer != null) {
        startConsumer.accept(startPromise);
      }
    }

    @Override
    public void stop(Promise<Void> stopPromise) throws Exception {
      if (stopConsumer != null) {
        stopConsumer.accept(stopPromise);
      }
    }
  }
}

