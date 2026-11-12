/*
 * Copyright (c) 2011-2026 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */

package io.vertx.tests.deployment;

import io.netty.channel.EventLoop;
import io.vertx.core.*;
import io.vertx.core.impl.JdkDependent;
import io.vertx.core.internal.ContextInternal;
import io.vertx.core.internal.VertxInternal;
import io.vertx.core.internal.deployment.DeploymentContext;
import io.vertx.core.json.JsonObject;
import io.vertx.test.core.Checkpoint;
import io.vertx.test.core.TestUtils;
import io.vertx.test.core.VertxTestBase2;
import io.vertx.tests.vertx.VertxTest;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import java.io.File;
import java.lang.ref.WeakReference;
import java.net.URL;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
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
public class DeploymentTest extends VertxTestBase2 {

  @Before
  public void setUp(Vertx vertx) throws Exception {
    super.setUp(vertx);
    TestVerticle.instanceCount.set(0);
  }

  @Test
  public void testOptions() {
    DeploymentOptions options = new DeploymentOptions();
    Assert.assertNull(options.getConfig());
    JsonObject config = new JsonObject().put("foo", "bar").put("obj", new JsonObject().put("quux", 123));
    Assert.assertEquals(options, options.setConfig(config));
    Assert.assertEquals(config, options.getConfig());
    String rand = TestUtils.randomUnicodeString(1000);
    Assert.assertFalse(options.isHa());
    Assert.assertEquals(options, options.setHa(true));
    Assert.assertTrue(options.isHa());
    String workerPoolName = TestUtils.randomAlphaString(10);
    Assert.assertEquals(options, options.setWorkerPoolName(workerPoolName));
    Assert.assertEquals(workerPoolName, options.getWorkerPoolName());
    int workerPoolSize = TestUtils.randomPositiveInt();
    Assert.assertEquals(options, options.setWorkerPoolSize(workerPoolSize));
    Assert.assertEquals(workerPoolSize, options.getWorkerPoolSize());
    long maxWorkerExecuteTime = TestUtils.randomPositiveLong();
    Assert.assertEquals(options, options.setMaxWorkerExecuteTime(maxWorkerExecuteTime));
    Assert.assertEquals(maxWorkerExecuteTime, options.getMaxWorkerExecuteTime());
    Assert.assertEquals(options, options.setMaxWorkerExecuteTimeUnit(TimeUnit.MILLISECONDS));
    Assert.assertEquals(TimeUnit.MILLISECONDS, options.getMaxWorkerExecuteTimeUnit());
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
    options.setHa(ha);
    options.setWorkerPoolName(poolName);
    options.setWorkerPoolSize(poolSize);
    options.setMaxWorkerExecuteTime(maxWorkerExecuteTime);
    options.setMaxWorkerExecuteTimeUnit(maxWorkerExecuteTimeUnit);
    DeploymentOptions copy = new DeploymentOptions(options);
    Assert.assertNotSame(config, copy.getConfig());
    Assert.assertEquals("bar", copy.getConfig().getString("foo"));
    Assert.assertEquals(ha, copy.isHa());
    Assert.assertEquals(poolName, copy.getWorkerPoolName());
    Assert.assertEquals(poolSize, copy.getWorkerPoolSize());
    Assert.assertEquals(maxWorkerExecuteTime, copy.getMaxWorkerExecuteTime());
    Assert.assertEquals(maxWorkerExecuteTimeUnit, copy.getMaxWorkerExecuteTimeUnit());
  }

  @Test
  public void testDefaultJsonOptions() {
    DeploymentOptions def = new DeploymentOptions();
    DeploymentOptions json = new DeploymentOptions(new JsonObject());
    Assert.assertEquals(def.getConfig(), json.getConfig());
    Assert.assertEquals(def.isHa(), json.isHa());
    Assert.assertEquals(def.getWorkerPoolName(), json.getWorkerPoolName());
    Assert.assertEquals(def.getWorkerPoolSize(), json.getWorkerPoolSize());
    Assert.assertEquals(def.getMaxWorkerExecuteTime(), json.getMaxWorkerExecuteTime());
    Assert.assertEquals(def.getMaxWorkerExecuteTimeUnit(), json.getMaxWorkerExecuteTimeUnit());
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
    Assert.assertEquals("bar", options.getConfig().getString("foo"));
    Assert.assertEquals(ha, options.isHa());
    Assert.assertEquals(poolName, options.getWorkerPoolName());
    Assert.assertEquals(poolSize, options.getWorkerPoolSize());
    Assert.assertEquals(maxWorkerExecuteTime, options.getMaxWorkerExecuteTime());
    Assert.assertEquals(maxWorkerExecuteTimeUnit, options.getMaxWorkerExecuteTimeUnit());
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
    options.setHa(ha);
    options.setWorkerPoolName(poolName);
    options.setWorkerPoolSize(poolSize);
    options.setMaxWorkerExecuteTime(maxWorkerExecuteTime);
    options.setMaxWorkerExecuteTimeUnit(maxWorkerExecuteTimeUnit);
    JsonObject json = options.toJson();
    DeploymentOptions copy = new DeploymentOptions(json);
    Assert.assertEquals("bar", copy.getConfig().getString("foo"));
    Assert.assertEquals(ha, copy.isHa());
    Assert.assertEquals(poolName, copy.getWorkerPoolName());
    Assert.assertEquals(poolSize, copy.getWorkerPoolSize());
    Assert.assertEquals(maxWorkerExecuteTime, copy.getMaxWorkerExecuteTime());
    Assert.assertEquals(maxWorkerExecuteTimeUnit, copy.getMaxWorkerExecuteTimeUnit());
  }

  @Test
  public void testDeployFromTestThread(Checkpoint checkpoint) throws Exception {
    MyVerticle verticle = new MyVerticle();
    vertx.deployVerticle(verticle).onComplete(ar -> {
      assertDeployment(1, verticle, null, ar);
      Assert.assertFalse(verticle.startContext.isWorkerContext());
      Assert.assertTrue(verticle.startContext.isEventLoopContext());
      checkpoint.succeed();
    });
  }

  @Test
  public void testDeployFromTestThreadNoHandler() throws Exception {
    MyVerticle verticle = new MyVerticle();
    vertx.deployVerticle(verticle);
    TestUtils.assertWaitUntil(() -> vertx.deploymentIDs().size() == 1);
  }

  @Test
  public void testDeployWithConfig(Checkpoint checkpoint) throws Exception {
    MyVerticle verticle = new MyVerticle();
    JsonObject config = generateJSONObject();
    vertx.deployVerticle(verticle, new DeploymentOptions().setConfig(config)).onComplete(ar -> {
      assertDeployment(1, verticle, config, ar);
      checkpoint.succeed();
    });
  }

  @Test
  public void testDeployFromContext(Checkpoint checkpoint) throws Exception {
    MyVerticle verticle = new MyVerticle();
    vertx.deployVerticle(verticle).onComplete(ar -> {
      Assert.assertTrue(ar.succeeded());
      Context ctx = Vertx.currentContext();
      MyVerticle verticle2 = new MyVerticle();
      vertx.deployVerticle(verticle2).onComplete(ar2 -> {
        assertDeployment(2, verticle2, null, ar2);
        Context ctx2 = Vertx.currentContext();
        Assert.assertEquals(ctx, ctx2);
        checkpoint.succeed();
      });
    });
  }

  @Test
  public void testDeployWorkerFromTestThread(Checkpoint checkpoint) throws Exception {
    MyVerticle verticle = new MyVerticle();
    vertx.deployVerticle(verticle, new DeploymentOptions().setThreadingModel(ThreadingModel.WORKER)).onComplete(ar -> {
      assertDeployment(1, verticle, null, ar);
      Assert.assertTrue(verticle.startContext.isWorkerContext());
      vertx.undeploy(ar.result()).onComplete(ar2 -> {
        Assert.assertTrue(ar2.succeeded());
        Assert.assertEquals(verticle.startContext, verticle.stopContext);
        checkpoint.succeed();
      });
    });
  }

  @Test
  public void testDeployWorkerWithConfig(Checkpoint checkpoint) throws Exception {
    MyVerticle verticle = new MyVerticle();
    JsonObject conf = generateJSONObject();
    vertx.deployVerticle(verticle, new DeploymentOptions().setConfig(conf).setThreadingModel(ThreadingModel.WORKER)).onComplete(ar -> {
      assertDeployment(1, verticle, conf, ar);
      Assert.assertTrue(verticle.startContext.isWorkerContext());
      Assert.assertFalse(verticle.startContext.isEventLoopContext());
      vertx.undeploy(ar.result()).onComplete(ar2 -> {
        Assert.assertTrue(ar2.succeeded());
        Assert.assertEquals(verticle.startContext, verticle.stopContext);
        checkpoint.succeed();
      });
    });
  }

  @Test
  public void testWorkerRightThread(Checkpoint checkpoint) throws Exception {
    Assert.assertFalse(Context.isOnVertxThread());
    Verticle verticle = new AbstractVerticle() {
      @Override
      public void start() {
        Assert.assertTrue(Context.isOnVertxThread());
        Assert.assertTrue(Context.isOnWorkerThread());
        Assert.assertFalse(Context.isOnEventLoopThread());
      }

      @Override
      public void stop() {
        Assert.assertTrue(Context.isOnVertxThread());
        Assert.assertTrue(Context.isOnWorkerThread());
        Assert.assertFalse(Context.isOnEventLoopThread());
      }
    };
    vertx.deployVerticle(verticle, new DeploymentOptions().setThreadingModel(ThreadingModel.WORKER))
      .onComplete(TestUtils.onSuccess(res -> {
        Assert.assertTrue(Context.isOnVertxThread());
        Assert.assertFalse(Context.isOnWorkerThread());
        Assert.assertTrue(Context.isOnEventLoopThread());
        vertx.undeploy(res)
          .onComplete(TestUtils.onSuccess(res2 -> {
            Assert.assertTrue(Context.isOnVertxThread());
            Assert.assertFalse(Context.isOnWorkerThread());
            Assert.assertTrue(Context.isOnEventLoopThread());
            checkpoint.succeed();
          }));
      }));

  }

  @Test
  public void testStandardRightThread(Checkpoint checkpoint) throws Exception {
    Assert.assertFalse(Context.isOnVertxThread());
    Verticle verticle = new AbstractVerticle() {
      @Override
      public void start() throws Exception {
        Assert.assertTrue(Context.isOnVertxThread());
        Assert.assertFalse(Context.isOnWorkerThread());
        Assert.assertTrue(Context.isOnEventLoopThread());
      }

      @Override
      public void stop() throws Exception {
        Assert.assertTrue(Context.isOnVertxThread());
        Assert.assertFalse(Context.isOnWorkerThread());
        Assert.assertTrue(Context.isOnEventLoopThread());
      }
    };
    vertx.deployVerticle(verticle)
      .onComplete(TestUtils.onSuccess(res -> {
        Assert.assertTrue(Context.isOnVertxThread());
        Assert.assertFalse(Context.isOnWorkerThread());
        Assert.assertTrue(Context.isOnEventLoopThread());
        vertx.undeploy(res)
          .onComplete(TestUtils.onSuccess(res2 -> {
            Assert.assertTrue(Context.isOnVertxThread());
            Assert.assertFalse(Context.isOnWorkerThread());
            Assert.assertTrue(Context.isOnEventLoopThread());
            checkpoint.succeed();
          }));
      }));

  }

  @Test
  public void testDeployFromContextExceptionInStart(Checkpoint checkpoint) throws Exception {
    testDeployFromThrowableInStart(checkpoint, MyVerticle.THROW_EXCEPTION, Exception.class);
  }

  @Test
  public void testDeployFromContextErrorInStart(Checkpoint checkpoint) throws Exception {
    testDeployFromThrowableInStart(checkpoint, MyVerticle.THROW_ERROR, Error.class);
  }

  private void testDeployFromThrowableInStart(Checkpoint checkpoint, int startAction, Class<? extends Throwable> expectedThrowable) throws Exception {
    MyVerticle verticle = new MyVerticle();
    MyVerticle verticle2 = new MyVerticle(startAction, MyVerticle.NOOP);
    vertx.deployVerticle(verticle).onComplete(TestUtils.onSuccess(v -> {
      Context ctx = Vertx.currentContext();
      vertx.deployVerticle(verticle2).onComplete(TestUtils.onFailure(err -> {
        Assert.assertEquals(expectedThrowable, err.getClass());
        Assert.assertEquals("FooBar!", err.getMessage());
        Assert.assertEquals(1, vertx.deploymentIDs().size());
        Context ctx2 = Vertx.currentContext();
        Assert.assertEquals(ctx, ctx2);
        checkpoint.succeed();
      }));
    }));
    checkpoint.awaitSuccess();
    Assert.assertTrue(((Promise<?>)verticle2.completion).future().isComplete());
  }

  @Test
  public void testDeployFromContextExceptionInStop(Checkpoint checkpoint) throws Exception {
    testDeployFromContextThrowableInStop(checkpoint, MyVerticle.THROW_EXCEPTION, Exception.class);
  }

  @Test
  public void testDeployFromContextErrorInStop(Checkpoint checkpoint) throws Exception {
    testDeployFromContextThrowableInStop(checkpoint, MyVerticle.THROW_ERROR, Error.class);
  }

  private void testDeployFromContextThrowableInStop(Checkpoint checkpoint, int stopAction, Class<? extends Throwable> expectedThrowable) throws Exception {
    MyVerticle verticle = new MyVerticle();
    vertx.deployVerticle(verticle).onComplete(TestUtils.onSuccess(id1 -> {
      Context ctx = Vertx.currentContext();
      MyVerticle verticle2 = new MyVerticle(MyVerticle.NOOP, stopAction);
      vertx.deployVerticle(verticle2).onComplete(TestUtils.onSuccess(id2 -> {
        vertx.undeploy(id2).onComplete(TestUtils.onFailure(err -> {
          Assert.assertEquals(expectedThrowable, err.getClass());
          Assert.assertEquals("BooFar!", err.getMessage());
          Assert.assertEquals(1, vertx.deploymentIDs().size());
          Assert.assertEquals(ctx, Vertx.currentContext());
          checkpoint.succeed();
        }));
      }));
    }));
  }

  @Test
  public void testUndeploy(Checkpoint checkpoint) throws Exception {
    MyVerticle verticle = new MyVerticle();
    vertx.deployVerticle(verticle).onComplete(TestUtils.onSuccess(id -> {
      vertx.undeploy(id).onComplete(TestUtils.onSuccess(v -> {
        Assert.assertFalse(vertx.deploymentIDs().contains(id));
        Assert.assertEquals(verticle.startContext, verticle.stopContext);
        Context currentContext = Vertx.currentContext();
        Assert.assertNotSame(currentContext, verticle.startContext);
        checkpoint.succeed();
      }));
    }));
  }

  @Test
  public void testUndeployTwice() {
    MyVerticle verticle = new MyVerticle();
    String id = vertx.deployVerticle(verticle).await();
    vertx.undeploy(id).await();
    Assert.assertThrows(IllegalStateException.class, vertx.undeploy(id)::await);
  }

  @Test
  public void testUndeployInvalidID() {
    Assert.assertThrows(IllegalStateException.class, vertx.undeploy("uqhwdiuhqwd")::await);
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
    Throwable err = Assert.assertThrows(Throwable.class, vertx.deployVerticle(verticle)::await);
    Assert.assertEquals(expectedThrowable, err.getClass());
    Assert.assertEquals("FooBar!", err.getMessage());
    Assert.assertTrue(vertx.deploymentIDs().isEmpty());
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
    String id = vertx.deployVerticle(verticle).await();
    Throwable err = Assert.assertThrows(Throwable.class, vertx.undeploy(id)::await);
    Assert.assertEquals(expectedThrowable, err.getClass());
    Assert.assertEquals("BooFar!", err.getMessage());
    Assert.assertTrue(vertx.deploymentIDs().isEmpty());
  }

  @Test
  public void testDeployUndeployMultiple() throws Exception {
    int num = 10;
    List<Future<String>> deployments = new ArrayList<>();
    for (int i = 0; i < num; i++) {
      deployments.add(vertx.deployVerticle(new MyVerticle()));
    }
    Future.all(deployments).await();
    Assert.assertEquals(num, vertx.deploymentIDs().size());
    for (Future<String> deployment : deployments) {
      Assert.assertTrue(vertx.deploymentIDs().contains(deployment.result()));
    }
    List<Future<Void>> undeployments = new ArrayList<>();
    for (String deploymentID: vertx.deploymentIDs()) {
      undeployments.add(vertx.undeploy(deploymentID));
    }
    Future.all(undeployments).await();
    Assert.assertTrue(vertx.deploymentIDs().isEmpty());
  }

  @Test(expected = VertxException.class)
  public void testDeployInstanceSetInstances() throws Exception {
    vertx.deployVerticle(new MyVerticle(), new DeploymentOptions().setInstances(2)).await();
  }

  @Test
  public void testDeployUsingClassName() throws Exception {
    vertx.deployVerticle("java:" + TestVerticle.class.getCanonicalName()).await();
  }

  @Test
  public void testDeployUsingClassAndConfig() throws Exception {
    JsonObject config = generateJSONObject();
    vertx.deployVerticle("java:" + TestVerticle.class.getCanonicalName(), new DeploymentOptions().setConfig(config)).await();
  }

  @Test
  public void testDeployUsingClassFails() throws Exception {
    Assert.assertThrows(ClassNotFoundException.class, vertx.deployVerticle("java:uhqwuhiqwduhwd")::await);
  }

  @Test
  public void testDeployUndeployMultipleInstancesUsingClassName() throws Exception {
    try {
      int numInstances = 10;
      DeploymentOptions options = new DeploymentOptions().setInstances(numInstances);
      AtomicInteger deployCount = new AtomicInteger();
      AtomicInteger undeployCount = new AtomicInteger();
      vertx.eventBus().<String>consumer("tvstarted").handler(msg -> {
        deployCount.incrementAndGet();
      });
      vertx.eventBus().<String>consumer("tvstopped").handler(msg -> {
        undeployCount.incrementAndGet();
        msg.reply("whatever");
      });
      vertx.deployVerticle(TestVerticle2.class.getCanonicalName(), options).await();
      TestUtils.assertWaitUntil(() -> deployCount.get() == numInstances);
      Assert.assertEquals(1, vertx.deploymentIDs().size());
      DeploymentContext deployment = ((VertxInternal) vertx).deploymentManager().deployment(vertx.deploymentIDs().iterator().next());
      Set<Deployable> verticles = deployment.deployment().instances();
      Assert.assertEquals(numInstances, verticles.size());
      Assert.assertEquals(numInstances, deployCount.get());
      vertx.undeploy(deployment.id()).await();
      TestUtils.assertWaitUntil(() -> deployCount.get() == numInstances);
      Assert.assertTrue(vertx.deploymentIDs().isEmpty());
    } finally {
      TestVerticle2.contexts.clear();
    }
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
    // No prefix or suffix so should be interpreted as a java class
    Assert.assertThrows(ClassNotFoundException.class, vertx.deployVerticle(className)::await);
  }

  @Ignore("does not seem to work in module mode but works in IDE")
  @Test
  public void testDeployAsSource() throws Exception {
    String sourceFile = "io/vertx/test/sourceverticle/SourceVerticle.java";
    vertx.deployVerticle("java:" + sourceFile).await();
  }

  @Test
  public void testSimpleChildDeployment(Checkpoint checkpoint1, Checkpoint checkpoint2) {
    Consumer<Promise<Void>> start = f -> {
      Context parentContext = Vertx.currentContext();
      Verticle child1 = new MyAsyncVerticle(f2 -> {
        Context childContext = Vertx.currentContext();
        Assert.assertNotSame(parentContext, childContext);
        f2.complete();
        checkpoint1.succeed();
      }, Promise::complete);
      vertx.deployVerticle(child1).onComplete(checkpoint2);
      f.complete();
    };
    Verticle verticle = new MyAsyncVerticle(start, Promise::complete);
    vertx.deployVerticle(verticle).await();
  }

  @Test
  public void testSimpleChildUndeploymentOrder() throws Exception {
    AtomicBoolean childStopCalled = new AtomicBoolean();
    AtomicBoolean parentStopCalled = new AtomicBoolean();
    AtomicReference<String> parentDepID = new AtomicReference<>();
    AtomicReference<String> childDepID = new AtomicReference<>();
    Verticle verticle = new MyAsyncVerticle(f -> {
      Verticle child1 = new MyAsyncVerticle(f2 -> f2.complete(null), f2 -> {
        // Child stop is called
        Assert.assertFalse(parentStopCalled.get());
        Assert.assertFalse(childStopCalled.get());
        childStopCalled.set(true);
        f2.complete(null);
      });
      vertx.deployVerticle(child1).onComplete(TestUtils.onSuccess(id -> {
        childDepID.set(id);
        f.complete(null);
      }));
    }, f2 -> {
      // Parent stop is called
      Assert.assertFalse(parentStopCalled.get());
      Assert.assertTrue(childStopCalled.get());
      Assert.assertTrue(vertx.deploymentIDs().contains(parentDepID.get()));
      Assert.assertFalse(vertx.deploymentIDs().contains(childDepID.get()));
      parentStopCalled.set(true);
      f2.complete(null);
    });
    parentDepID.set(vertx.deployVerticle(verticle).await());
    Assert.assertTrue(vertx.deploymentIDs().contains(parentDepID.get()));
    Assert.assertTrue(vertx.deploymentIDs().contains(childDepID.get()));

    // Now they're deployed, undeploy them
    vertx.undeploy(parentDepID.get()).await();
    Assert.assertTrue(parentStopCalled.get());
  }

  @Test
  public void testSimpleChildUndeploymentOnParentAsyncFailure() throws Exception {
    AtomicInteger childDeployed = new AtomicInteger();
    AtomicInteger childUndeployed = new AtomicInteger();
    Future<String> fut = vertx.deployVerticle(new AbstractVerticle() {
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
        }).onComplete(TestUtils.onSuccess(child -> {
          startPromise.fail("Undeployed");
        }));
      }
    });
    Assert.assertThrows(Throwable.class, fut::await);
    Assert.assertEquals(1, childDeployed.get());
    Assert.assertEquals(1, childUndeployed.get());
  }

  @Test
  public void testSimpleChildUndeploymentOnParentSyncFailure() throws Exception {
    AtomicInteger childDeployed = new AtomicInteger();
    AtomicInteger childUndeployed = new AtomicInteger();
    Future<String> fut = vertx.deployVerticle(new AbstractVerticle() {
      @Override
      public void start(Promise<Void> startPromise) throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        vertx.deployVerticle(new AbstractVerticle() {
          @Override
          public void start() {
            childDeployed.incrementAndGet();
            latch.countDown();
          }
          @Override
          public void stop() {
            childUndeployed.incrementAndGet();
          }
        });
        TestUtils.awaitLatch(latch);
        throw new RuntimeException();
      }
    });
    Assert.assertThrows(RuntimeException.class, fut::await);
    TestUtils.waitUntil(() -> childDeployed.get() == 1);
    TestUtils.waitUntil(() -> childUndeployed.get() == 1);
  }

  @Test
  public void testSimpleChildUndeploymentDeployedAfterParentFailure(Checkpoint checkpoint) throws Exception {
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
            TestUtils.waitUntil(() -> parentFailed.get() == 1);
            checkpoint.succeed();
          }
        });
        parentFailed.incrementAndGet();
        throw new RuntimeException();
      }
    });
  }

  @Test
  public void testAsyncDeployCalledSynchronously() throws Exception {
    MyAsyncVerticle verticle = new MyAsyncVerticle(f -> f.complete(null), f -> f.complete(null));
    vertx.deployVerticle(verticle).await();
  }

  @Test
  public void testAsyncDeployFailureCalledSynchronously() throws Exception {
    MyAsyncVerticle verticle = new MyAsyncVerticle(f -> f.fail(new Exception("foobar")), null);
    Throwable err = Assert.assertThrows(Exception.class, vertx.deployVerticle(verticle)::await);
    Assert.assertEquals("foobar", err.getMessage());
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
    Future<String> fut = vertx.deployVerticle(verticle);
    Thread.sleep(delay / 2);
    Assert.assertTrue(vertx.deploymentIDs().isEmpty());
    String id = fut.await();
    long now = System.currentTimeMillis();
    Assert.assertTrue(now - start >= delay);
    Assert.assertTrue(vertx.deploymentIDs().contains(id));
  }

  @Test
  public void testAsyncDeployFailure() throws Exception {
    long start = System.currentTimeMillis();
    long delay = 1000;
    MyAsyncVerticle verticle = new MyAsyncVerticle(f -> vertx.setTimer(delay, id -> f.fail(new Exception("foobar"))), null);
    Throwable err = Assert.assertThrows(Exception.class, vertx.deployVerticle(verticle)::await);
    Assert.assertEquals("foobar", err.getMessage());
    long now = System.currentTimeMillis();
    Assert.assertTrue(now - start >= delay);
    Assert.assertTrue(vertx.deploymentIDs().isEmpty());
  }


  @Test
  public void testAsyncUndeployCalledSynchronously() throws Exception {
    MyAsyncVerticle verticle = new MyAsyncVerticle(f -> f.complete(null), f ->  f.complete(null));
    String id = vertx.deployVerticle(verticle).await();
    vertx.undeploy(id).await();
    Assert.assertFalse(vertx.deploymentIDs().contains(id));
  }

  @Test
  public void testAsyncUndeployFailureCalledSynchronously() throws Exception {
    MyAsyncVerticle verticle = new MyAsyncVerticle(f -> f.complete(null), f -> f.fail(new Exception("foobar")));
    String id = vertx.deployVerticle(verticle).await();
    Throwable err = Assert.assertThrows(Exception.class, vertx.undeploy(id)::await);
    Assert.assertEquals("foobar", err.getMessage());
    Assert.assertFalse(vertx.deploymentIDs().contains(id));
  }

  @Test
  public void testAsyncUndeploy() throws Exception {
    long delay = 1000;
    MyAsyncVerticle verticle = new MyAsyncVerticle(f-> f.complete(null), f -> vertx.setTimer(delay, id -> f.complete(null)));
    String id = vertx.deployVerticle(verticle).await();
    long start = System.currentTimeMillis();
    Future<Void> fut = vertx.undeploy(id);
    Thread.sleep(delay / 2);
    Assert.assertFalse(vertx.deploymentIDs().isEmpty());
    fut.await();
    long now = System.currentTimeMillis();
    Assert.assertTrue(now - start >= delay);
    Assert.assertFalse(vertx.deploymentIDs().contains(id));
  }

  @Test
  public void testAsyncUndeployFailure() throws Exception {
    long delay = 1000;
    MyAsyncVerticle verticle = new MyAsyncVerticle(f-> f.complete(null), f -> vertx.setTimer(delay, id -> f.fail(new Exception("foobar"))));
    String id = vertx.deployVerticle(verticle).await();
    long start = System.currentTimeMillis();
    Assert.assertThrows(Exception.class, vertx.undeploy(id)::await);
    long now = System.currentTimeMillis();
    Assert.assertTrue(now - start >= delay);
    Assert.assertFalse(vertx.deploymentIDs().contains(id));
  }

  @Test
  public void testAsyncUndeployFailsAfterSuccess(Checkpoint checkpoint) {
    Exception expected = new Exception();
    AtomicReference<Throwable> logged = new AtomicReference<>();
    Verticle verticle = new AbstractVerticle() {
      @Override
      public void stop(Promise<Void> stopPromise) throws Exception {
        stopPromise.complete();
        // Avoid this exception to fail the test
        context.exceptionHandler(logged::set);
        throw expected;
      }
    };
    Context ctx = vertx.getOrCreateContext();
    ctx.runOnContext(v1 -> {
      vertx.deployVerticle(verticle).onComplete(TestUtils.onSuccess(id -> {
        vertx.undeploy(id).onComplete(checkpoint);
      }));
    });
    checkpoint.awaitSuccess();
    Assert.assertSame(expected, logged.get());
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

        vertx.deployVerticle(child).onComplete(TestUtils.onSuccess(depID -> {
          startPromise.complete();
        }));

      }
    };

    String depID = vertx.deployVerticle(parent).await();
    vertx.timer(10).await();
    vertx.undeploy(depID).await();
  }

  @Test
  public void testCloseHooksCalled() throws Exception {
    AtomicInteger closedCount = new AtomicInteger();
    Closeable myCloseable1 = completionHandler -> {
      closedCount.incrementAndGet();
      completionHandler.succeed();
    };
    Closeable myCloseable2 = completionHandler -> {
      closedCount.incrementAndGet();
      completionHandler.succeed();
    };
    MyAsyncVerticle verticle = new MyAsyncVerticle(f-> {
      ContextInternal ctx = (ContextInternal)Vertx.currentContext();
      ctx.addCloseHook(myCloseable1);
      ctx.addCloseHook(myCloseable2);
      f.complete(null);
    }, f -> f.complete(null));
    String id = vertx.deployVerticle(verticle).await();
    Assert.assertEquals(0, closedCount.get());
    // Now undeploy
    vertx.undeploy(id).await();
    Assert.assertEquals(2, closedCount.get());
  }

  @Test
  public void testDeployWhenClosedShouldFail() throws Exception {
    vertx.close().await();
    Throwable err = Assert.assertThrows(Exception.class, vertx.deployVerticle(new AbstractVerticle() {
    })::await);
    Assert.assertEquals("Vert.x closed", err.getMessage());
  }

/*
  private String createClassOutsideClasspath(String className) throws Exception {
    File dir = Files.createTempDirectory("vertx").toFile();
    dir.deleteOnExit();
    File source = new File(dir, className + ".java");
    Files.write(source.toPath(), ("public class " + className + " extends io.vertx.core.AbstractVerticle {} ").getBytes());

    URLClassLoader loader = new URLClassLoader(new URL[]{dir.toURI().toURL()}, Thread.currentThread().getContextClassLoader());

    CompilingClassLoader compilingClassLoader = new CompilingClassLoader(loader, className + ".java");
    compilingClassLoader.loadClass(className);

    byte[] bytes = compilingClassLoader.getClassBytes(className);
    Assert.assertNotNull(bytes);

    File classFile = new File(dir, className + ".class");
    Files.write(classFile.toPath(), bytes);

    return dir.getAbsolutePath();
  }
*/

  public static class ParentVerticle extends AbstractVerticle {

    @Override
    public void start(Promise<Void> startPromise) throws Exception {
      vertx.deployVerticle("java:" + ChildVerticle.class.getName()).onComplete(ar -> {
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
    List<Future<String>> deployments = new ArrayList<>();
    for (int i = 0; i < numVerticles; i++) {
      MyVerticle verticle = new MyVerticle();
      verticles.add(verticle);
      deployments.add(vertx.deployVerticle("java:" + ParentVerticle.class.getName()));
    }
    Future.all(deployments).await();
    Assert.assertEquals(2 * numVerticles, vertx.deploymentIDs().size());
    vertx.close().await();
    Assert.assertEquals(0, vertx.deploymentIDs().size());
  }

  @Test
  public void testDeployChildOnParentUndeploy() {
    class ParentVerticle extends AbstractVerticle {
      @Override
      public void stop(Promise<Void> stopPromise) {
        vertx.deployVerticle(ChildVerticle.class.getName())
          .<Void>mapEmpty()
          .onComplete(stopPromise);
      }
    }

    String id = vertx.deployVerticle(new ParentVerticle()).await();
    Assert.assertThrows(Throwable.class, vertx.undeploy(id)::await);
  }

  @Test
  public void testUndeployAllFailureInUndeploy() throws Exception {
    int numVerticles = 10;
    List<MyVerticle> verticles = new ArrayList<>();
    List<Future<String>> deployments = new ArrayList<>();
    for (int i = 0; i < numVerticles; i++) {
      MyVerticle verticle = new MyVerticle(MyVerticle.NOOP, MyVerticle.THROW_EXCEPTION);
      verticles.add(verticle);
      deployments.add(vertx.deployVerticle(verticle));
    }
    Future.all(deployments).await();
    vertx.close().await();
    for (MyVerticle verticle: verticles) {
      Assert.assertFalse(verticle.stopCalled);
    }
  }

  @Test
  public void testUndeployAllNoDeployments() throws Exception {
    vertx.close().await();
  }

  @Test
  public void testGetInstanceCount() {
    class MultiInstanceVerticle extends AbstractVerticle {
      @Override
      public void start() {
        Assert.assertEquals(vertx.getOrCreateContext().getInstanceCount(), 1);
      }
    }

    vertx.deployVerticle(new MultiInstanceVerticle()).await();
  }

  @Test
  public void testGetInstanceCountMultipleVerticles() throws Exception {
    AtomicInteger messageCount = new AtomicInteger(0);
    AtomicInteger totalReportedInstances = new AtomicInteger(0);
    vertx.eventBus().consumer("instanceCount", event -> {
      totalReportedInstances.addAndGet((int)event.body());
      messageCount.incrementAndGet();
    });
    vertx.deployVerticle(TestVerticle3.class.getCanonicalName(), new DeploymentOptions().setInstances(3)).await();
    TestUtils.assertWaitUntil(() -> messageCount.get() == 3);
    Assert.assertEquals(9, totalReportedInstances.get());
    TestUtils.assertWaitUntil(() -> vertx.deploymentIDs().size() == 1);
    DeploymentContext deployment = ((VertxInternal) vertx).deploymentManager().deployment(vertx.deploymentIDs().iterator().next());
    vertx.undeploy(deployment.id()).await();
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
        Assert.fail("stop should not be called");
      }

    };
    Verticle verticleParent = new AbstractVerticle() {
      @Override
      public void start(Promise<Void> startPromise) {
        vertx.deployVerticle(verticleChild).onComplete(TestUtils.onFailure(v -> {
          startPromise.complete();
        }));
      }
    };
    String depID = vertx.deployVerticle(verticleParent).await();
    vertx.undeploy(depID).await();
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
    String deploymentId = vertx.deployVerticle(supplier, options).await();
    myVerticles.forEach(myVerticle -> {
      Assert.assertEquals(deploymentId, myVerticle.deploymentID);
      Assert.assertEquals(config, myVerticle.config);
      Assert.assertTrue(myVerticle.startCalled);
    });
  }

  @Test
  public void testDeploySupplierNull() {
    Supplier<Verticle> supplier = () -> null;
    DeploymentOptions options = new DeploymentOptions();
    Assert.assertThrows(Throwable.class, vertx.deployVerticle(supplier, options)::await);
    Assert.assertEquals(Collections.emptySet(), vertx.deploymentIDs());
  }

  @Test
  public void testDeploySupplierDuplicate() {
    MyVerticle myVerticle = new MyVerticle();
    Supplier<Verticle> supplier = () -> myVerticle;
    DeploymentOptions options = new DeploymentOptions().setInstances(2);
    Assert.assertThrows(Throwable.class, vertx.deployVerticle(supplier, options)::await);
    Assert.assertEquals(Collections.emptySet(), vertx.deploymentIDs());
    Assert.assertFalse(myVerticle.startCalled);
  }

  @Test
  public void testDeploySupplierThrowsException() {
    Supplier<Verticle> supplier = () -> {
      throw new RuntimeException("boum");
    };
    Assert.assertThrows(Throwable.class, vertx.deployVerticle(supplier, new DeploymentOptions().setInstances(1))::await);
    Assert.assertEquals(Collections.emptySet(), vertx.deploymentIDs());
  }

  @Test
  public void testDeployClass() {
    try {
      JsonObject config = generateJSONObject();
      String deploymentID = vertx.deployVerticle(ReferenceSavingMyVerticle.class, new DeploymentOptions().setInstances(4).setConfig(config)).await();
      ReferenceSavingMyVerticle.myVerticles.forEach(myVerticle -> {
        Assert.assertEquals(deploymentID, myVerticle.deploymentID);
        Assert.assertEquals(config, myVerticle.config);
        Assert.assertTrue(myVerticle.startCalled);
      });
    } finally {
      ReferenceSavingMyVerticle.myVerticles.clear();;
    }
  }

  @Test
  public void testDeployClassNoDefaultPublicConstructor() throws Exception {
    class NoDefaultPublicConstructorVerticle extends AbstractVerticle {
    }
    Assert.assertThrows(Throwable.class, vertx.deployVerticle(NoDefaultPublicConstructorVerticle.class, new DeploymentOptions())::await);
  }

  @Test
  public void testFailedDeployRunsContextShutdownHook() throws Exception {
    AtomicBoolean closeHookCalledBeforeDeployFailure = new AtomicBoolean(false);
    Closeable closeable = completionHandler -> {
      closeHookCalledBeforeDeployFailure.set(true);
      completionHandler.succeed();
    };
    Verticle v = new AbstractVerticle() {
      @Override
      public void start(Promise<Void> startPromise) {
        ((ContextInternal)context).addCloseHook(closeable);
        startPromise.fail("Fail to deploy.");
      }
    };
    Assert.assertThrows(Throwable.class, vertx.deployVerticle(v)::await);
    Assert.assertTrue(closeHookCalledBeforeDeployFailure.get());
  }

  @Test
  public void testWorkerInstancesUseSameEventLoopThread() throws Exception {
    Set<EventLoop> eventLoops = Collections.synchronizedSet(new HashSet<>());
    Future<String> fut = vertx.deployVerticle(() -> {
      return new AbstractVerticle() {
        @Override
        public void start() throws Exception {
          EventLoop eventLoop = ((ContextInternal) context).nettyEventLoop();
          eventLoops.add(eventLoop);
          super.start();
        }
      };
    }, new DeploymentOptions().setInstances(5).setThreadingModel(ThreadingModel.WORKER));
    fut.await();
    Assert.assertEquals(1, eventLoops.size());
  }

  @Test
  public void testMultipleFailedDeploys() throws InterruptedException {
    int instances = 10;
    DeploymentOptions options = new DeploymentOptions();
    options.setInstances(instances);

    AtomicInteger called = new AtomicInteger();

    Future<String> fut = vertx.deployVerticle(() -> {
      Verticle v = new AbstractVerticle() {
        @Override
        public void start(final Promise<Void> startPromise) throws Exception {
          startPromise.fail("Fail to deploy.");
        }
      };
      return v;
    }, options).onComplete(ar -> called.incrementAndGet());
    Assert.assertThrows(Throwable.class, fut::await);
    vertx.timer(30).await();
    Assert.assertEquals("Completion handler called more than once", 1, called.get());
  }

  @Test
  public void testUndeployParentDuringChildDeployment(Checkpoint checkpoint) throws Exception {
    CountDownLatch deployLatch = new CountDownLatch(2);
    CountDownLatch undeployLatch = new CountDownLatch(1);

    MyAsyncVerticle childVerticle = new MyAsyncVerticle(startPromise -> {
      deployLatch.countDown();
      Vertx.currentContext().<Void>executeBlocking(() -> {
        try {
          undeployLatch.await();
          return null;
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
          throw e;
        }
      }).onComplete(startPromise);
    }, Promise::complete);

    MyAsyncVerticle verticle = new MyAsyncVerticle(startPromise -> {
      Context parentVerticleContext = Vertx.currentContext();
      parentVerticleContext.owner().deployVerticle(childVerticle).onComplete(TestUtils.onFailure(t -> {
        Assert.assertSame(parentVerticleContext, Vertx.currentContext());
        checkpoint.succeed();
      }));
      startPromise.complete();
    }, stopPromise -> {
      undeployLatch.countDown();
    });
    AtomicReference<String> deploymentID = new AtomicReference<>();
    vertx.deployVerticle(verticle).onComplete(TestUtils.onSuccess(id -> {
      deploymentID.set(id);
      deployLatch.countDown();
    }));
    TestUtils.awaitLatch(deployLatch);
    vertx.undeploy(deploymentID.get());
  }

  @Test
  public void testDeployWithPartialFailure() {
    testDeployWithPartialFailure(3, 2);
  }

  private void testDeployWithPartialFailure(int numberOfInstances, int instanceToFail) {
    AtomicInteger count = new AtomicInteger();
    Map<Integer, Promise<Void>> startPromises = Collections.synchronizedMap(new HashMap<>());
    Map<Integer, Boolean> stopped = Collections.synchronizedMap(new HashMap<>());
    Set<Integer> closeHooks = Collections.synchronizedSet(new HashSet<>());
    Future<String> fut = vertx.deployVerticle(() -> {
      int idx = count.getAndIncrement();
      return new AbstractVerticle() {
        @Override
        public void start(Promise<Void> startPromise) {
          ContextInternal ctx = (ContextInternal) context;
          ctx.addCloseHook(completion -> {
            closeHooks.add(idx);
            completion.succeed();
          });
          startPromises.put(idx, startPromise);
          if (startPromises.size() == numberOfInstances) {
            startPromises
              .forEach((idx, p) -> {
                if (idx != instanceToFail) {
                  p.tryComplete();
                } else {
                  p.tryFail("it-failed");
                }
              });
          }
        }
        @Override
        public void stop() {
          stopped.put(idx, true);
        }
      };
    }, new DeploymentOptions().setInstances(numberOfInstances));
    Assert.assertThrows(Throwable.class, fut::await);
    for (int j = 0;j < numberOfInstances;j++) {
      if (instanceToFail != j) {
        Assert.assertTrue(stopped.containsKey(j));
      }
      Assert.assertTrue(closeHooks.contains(j));
    }
  }

  @Test
  public void testCloseDeploymentInProgress(Vertx vertx) {
    AtomicReference<Completable<Void>> completionRef = new AtomicReference<>();
    AtomicBoolean deploying = new AtomicBoolean();
    Future<String> deployment = vertx.deployVerticle(new AbstractVerticle() {
      @Override
      public void start(Promise<Void> startPromise) {
        ((ContextInternal) context).addCloseHook(completionRef::set);
        deploying.set(true);
      }
      @Override
      public void stop(Promise<Void> stopPromise) {
        Assert.fail();
      }
    });
    TestUtils.waitUntil(deploying::get);
    Future<Void> fut = vertx.close();
    TestUtils.waitUntil(() -> completionRef.get() != null);
    Assert.assertFalse(fut.isComplete());
    completionRef.get().succeed();
    fut.await();
    Assert.assertTrue(deployment.failed());
  }

  @Test
  public void testContextClassLoader(Checkpoint checkpoint) throws Exception {
    File tmp = File.createTempFile("vertx-", ".txt");
    tmp.deleteOnExit();
    Files.write(tmp.toPath(), "hello".getBytes());
    URL url = tmp.toURI().toURL();
    AtomicBoolean used = new AtomicBoolean();
    ClassLoader cl = new ClassLoader(Thread.currentThread().getContextClassLoader()) {
      @Override
      public URL getResource(String name) {
        if (name.equals("foo.txt")) {
          used.set(true);
          return url;
        }
        return super.getResource(name);
      }
    };
    vertx.deployVerticle(new AbstractVerticle() {
      @Override
      public void start() {
        Assert.assertSame(cl, Thread.currentThread().getContextClassLoader());
        Assert.assertSame(cl, ((ContextInternal)context).classLoader());
        vertx.fileSystem().props("foo.txt").onComplete(TestUtils.onSuccess(props -> {
          Assert.assertEquals(5, props.size());
          Assert.assertTrue(used.get());
          checkpoint.succeed();
        }));
      }
    }, new DeploymentOptions().setClassLoader(cl)).await();
  }

  private void assertDeployment(int instances, MyVerticle verticle, JsonObject config, AsyncResult<String> ar) {
    Assert.assertTrue(ar.succeeded());
    Assert.assertEquals(vertx, verticle.getVertx());
    String deploymentID = ar.result();
    Assert.assertNotNull(ar.result());
    Assert.assertEquals(deploymentID, verticle.deploymentID);
    if (config == null) {
      Assert.assertEquals(0, verticle.config.size());
    } else {
      Assert.assertEquals(config, verticle.config);
    }
    Assert.assertTrue(verticle.startCalled);
    Assert.assertFalse(verticle.stopCalled);
    Assert.assertTrue(vertx.deploymentIDs().contains(deploymentID));
    Assert.assertEquals(instances, vertx.deploymentIDs().size());
    Context currentContext = Vertx.currentContext();
    Assert.assertNotSame(currentContext, verticle.startContext);
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
    Completable<Void> completion;

    MyVerticle() {
      this(NOOP, NOOP);
    }

    MyVerticle(int startAction, int stopAction) {
      this.startAction = startAction;
      this.stopAction = stopAction;
    }

    @Override
    public void start() throws Exception {
      ((ContextInternal)context).addCloseHook(promise -> {
        completion = promise;
        promise.succeed();
      });
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

  @Test
  public void testVerticleNotLeakedWithSharedServers() {
    class HttpServer extends AbstractVerticle {
      @Override
      public void start(Promise<Void> startPromise) {
        vertx.createHttpServer()
          .requestHandler(request -> request.response().end())
          .listen(8080)
          .<Void>mapEmpty()
          .onComplete(startPromise);
      }
    }

    // Deploy first verticle
    vertx.deployVerticle(new HttpServer()).toCompletionStage().toCompletableFuture().join();

    // Deploy and undeploy other verticles
    List<WeakReference<HttpServer>> verticles = new ArrayList<>();
    for (int i = 0; i < 100; i++) {
      HttpServer verticle = new HttpServer();
      verticles.add(new WeakReference<>(verticle));
      vertx.deployVerticle(verticle)
        .compose(id -> vertx.undeploy(id))
        .toCompletionStage().toCompletableFuture().join();
    }

    VertxTest.runGC();

    for (WeakReference<HttpServer> verticle : verticles) {
      Assert.assertNull(verticle.get());
    }
  }

  private static class DeployableImpl implements Deployable {
    private Context context;
    @Override
    public Future<?> deploy(Context context) throws Exception {
      this.context = context;
      return Future.succeededFuture();
    }
  }

  @Test
  public void testClosingVertxDoesNotKeepRef() throws Exception {
    DeployableImpl deployable = new DeployableImpl();
    Context context;
    WeakReference<DeployableImpl> ref = new WeakReference<>(deployable);
    String deploymentID = vertx.deployVerticle(deployable).await();
    context = deployable.context;
    Assert.assertNotNull(context);
    deployable = null;
    vertx.undeploy(deploymentID).await();
    long now = System.currentTimeMillis();
    while (true) {
      Assert.assertTrue((System.currentTimeMillis() - now) <= 20_00000);
      VertxTest.runGC();
      Thread.sleep(10);
      if (ref.get() == null) {
        break;
      }
    }
  }

  @Test
  public void testVirtualThreadsNotAvailable() {
    Assume.assumeFalse(JdkDependent.VIRTUAL_THREAD_AVAILABLE);
    Assert.assertThrows(Throwable.class, vertx.deployVerticle(new AbstractVerticle() {
      @Override
      public void start() {
      }
    }, new DeploymentOptions().setThreadingModel(ThreadingModel.VIRTUAL_THREAD))::await);
  }
}
