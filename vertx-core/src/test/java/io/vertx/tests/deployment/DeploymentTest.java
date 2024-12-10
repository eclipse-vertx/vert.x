/*
 * Copyright (c) 2011-2020 Contributors to the Eclipse Foundation
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
import io.vertx.core.impl.deployment.Deployment;
import io.vertx.core.internal.ContextInternal;
import io.vertx.core.impl.deployment.DeploymentContext;
import io.vertx.core.internal.VertxInternal;
import io.vertx.core.json.JsonObject;
import io.vertx.test.core.TestUtils;
import io.vertx.test.core.VertxTestBase;
import org.junit.Ignore;
import org.junit.Test;

import java.io.File;
import java.net.URL;
import java.nio.file.Files;
import java.util.*;
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
    String rand = TestUtils.randomUnicodeString(1000);
    assertFalse(options.isHa());
    assertEquals(options, options.setHa(true));
    assertTrue(options.isHa());
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
    options.setHa(ha);
    options.setWorkerPoolName(poolName);
    options.setWorkerPoolSize(poolSize);
    options.setMaxWorkerExecuteTime(maxWorkerExecuteTime);
    options.setMaxWorkerExecuteTimeUnit(maxWorkerExecuteTimeUnit);
    DeploymentOptions copy = new DeploymentOptions(options);
    assertNotSame(config, copy.getConfig());
    assertEquals("bar", copy.getConfig().getString("foo"));
    assertEquals(ha, copy.isHa());
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
    assertEquals(def.isHa(), json.isHa());
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
    options.setHa(ha);
    options.setWorkerPoolName(poolName);
    options.setWorkerPoolSize(poolSize);
    options.setMaxWorkerExecuteTime(maxWorkerExecuteTime);
    options.setMaxWorkerExecuteTimeUnit(maxWorkerExecuteTimeUnit);
    JsonObject json = options.toJson();
    DeploymentOptions copy = new DeploymentOptions(json);
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
    vertx.deployVerticle(verticle).onComplete(ar -> {
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
    vertx.deployVerticle(verticle, new DeploymentOptions().setConfig(config)).onComplete(ar -> {
      assertDeployment(1, verticle, config, ar);
      testComplete();
    });
    await();
  }

  @Test
  public void testDeployFromContext() throws Exception {
    MyVerticle verticle = new MyVerticle();
    vertx.deployVerticle(verticle).onComplete(ar -> {
      assertTrue(ar.succeeded());
      Context ctx = Vertx.currentContext();
      MyVerticle verticle2 = new MyVerticle();
      vertx.deployVerticle(verticle2).onComplete(ar2 -> {
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
    vertx.deployVerticle(verticle, new DeploymentOptions().setThreadingModel(ThreadingModel.WORKER)).onComplete(ar -> {
      assertDeployment(1, verticle, null, ar);
      assertTrue(verticle.startContext.isWorkerContext());
      vertx.undeploy(ar.result()).onComplete(ar2 -> {
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
    vertx.deployVerticle(verticle, new DeploymentOptions().setConfig(conf).setThreadingModel(ThreadingModel.WORKER)).onComplete(ar -> {
      assertDeployment(1, verticle, conf, ar);
      assertTrue(verticle.startContext.isWorkerContext());
      assertFalse(verticle.startContext.isEventLoopContext());
      vertx.undeploy(ar.result()).onComplete(ar2 -> {
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
    vertx.deployVerticle(verticle, new DeploymentOptions().setThreadingModel(ThreadingModel.WORKER))
      .onComplete(onSuccess(res -> {
        assertTrue(Context.isOnVertxThread());
        assertFalse(Context.isOnWorkerThread());
        assertTrue(Context.isOnEventLoopThread());
        vertx.undeploy(res)
          .onComplete(onSuccess(res2 -> {
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
    vertx.deployVerticle(verticle)
      .onComplete(onSuccess(res -> {
        assertTrue(Context.isOnVertxThread());
        assertFalse(Context.isOnWorkerThread());
        assertTrue(Context.isOnEventLoopThread());
        vertx.undeploy(res)
          .onComplete(onSuccess(res2 -> {
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
    MyVerticle verticle2 = new MyVerticle(startAction, MyVerticle.NOOP);
    vertx.deployVerticle(verticle).onComplete(onSuccess(v -> {
      Context ctx = Vertx.currentContext();
      vertx.deployVerticle(verticle2).onComplete(onFailure(err -> {
        assertEquals(expectedThrowable, err.getClass());
        assertEquals("FooBar!", err.getMessage());
        assertEquals(1, vertx.deploymentIDs().size());
        Context ctx2 = Vertx.currentContext();
        assertEquals(ctx, ctx2);
        testComplete();
      }));
    }));
    await();
    assertTrue(verticle2.completion.future().isComplete());
  }

  @Test
  public void testDeployFromContextExceptionInStop() throws Exception {
    testDeployFromContextThrowableInStop(MyVerticle.THROW_EXCEPTION, Exception.class);
  }

  @Test
  public void testDeployFromContextErrorInStop() throws Exception {
    testDeployFromContextThrowableInStop(MyVerticle.THROW_ERROR, Error.class);
  }

  private void testDeployFromContextThrowableInStop(int stopAction, Class<? extends Throwable> expectedThrowable) throws Exception {
    MyVerticle verticle = new MyVerticle();
    vertx.deployVerticle(verticle).onComplete(onSuccess(id1 -> {
      Context ctx = Vertx.currentContext();
      MyVerticle verticle2 = new MyVerticle(MyVerticle.NOOP, stopAction);
      vertx.deployVerticle(verticle2).onComplete(onSuccess(id2 -> {
        vertx.undeploy(id2).onComplete(onFailure(err -> {
          assertEquals(expectedThrowable, err.getClass());
          assertEquals("BooFar!", err.getMessage());
          assertEquals(1, vertx.deploymentIDs().size());
          assertEquals(ctx, Vertx.currentContext());
          testComplete();
        }));
      }));
    }));
    await();
  }

  @Test
  public void testUndeploy() throws Exception {
    MyVerticle verticle = new MyVerticle();
    vertx.deployVerticle(verticle).onComplete(onSuccess(id -> {
      vertx.undeploy(id).onComplete(onSuccess(v -> {
        assertFalse(vertx.deploymentIDs().contains(id));
        assertEquals(verticle.startContext, verticle.stopContext);
        Context currentContext = Vertx.currentContext();
        assertNotSame(currentContext, verticle.startContext);
        testComplete();
      }));
    }));
    await();
  }

  @Test
  public void testUndeployTwice() {
    MyVerticle verticle = new MyVerticle();
    vertx.deployVerticle(verticle).onComplete(onSuccess(id -> {
      vertx.undeploy(id).onComplete(onSuccess(v -> {
        vertx.undeploy(id).onComplete(onFailure(err -> {
          assertTrue(err instanceof IllegalStateException);
          testComplete();
        }));
      }));
    }));
    await();
  }

  @Test
  public void testUndeployInvalidID() {
    vertx
      .undeploy("uqhwdiuhqwd")
      .onComplete(onFailure(err -> {
        assertTrue(err instanceof IllegalStateException);
        testComplete();
      }));
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
    vertx.deployVerticle(verticle).onComplete(onFailure(err -> {
      assertEquals(expectedThrowable, err.getClass());
      assertEquals("FooBar!", err.getMessage());
      assertTrue(vertx.deploymentIDs().isEmpty());
      testComplete();
    }));
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
    vertx.deployVerticle(verticle).onComplete(onSuccess(id -> {
      vertx.undeploy(id).onComplete(onFailure(err -> {
        assertEquals(expectedThrowable, err.getClass());
        assertEquals("BooFar!", err.getMessage());
        assertTrue(vertx.deploymentIDs().isEmpty());
        testComplete();
      }));
    }));
    await();
  }

  @Test
  public void testDeployUndeployMultiple() throws Exception {
    int num = 10;
    CountDownLatch deployLatch = new CountDownLatch(num);
    for (int i = 0; i < num; i++) {
      MyVerticle verticle = new MyVerticle();
      vertx.deployVerticle(verticle).onComplete(onSuccess(id -> {
        assertTrue(vertx.deploymentIDs().contains(id));
        deployLatch.countDown();
      }));
    }
    assertTrue(deployLatch.await(10, TimeUnit.SECONDS));
    assertEquals(num, vertx.deploymentIDs().size());
    CountDownLatch undeployLatch = new CountDownLatch(num);
    for (String deploymentID: vertx.deploymentIDs()) {
      vertx.undeploy(deploymentID).onComplete(onSuccess(v -> {
        assertFalse(vertx.deploymentIDs().contains(deploymentID));
        undeployLatch.countDown();
      }));
    }
    assertTrue(undeployLatch.await(10, TimeUnit.SECONDS));
    assertTrue(vertx.deploymentIDs().isEmpty());
  }

  @Test(expected = VertxException.class)
  public void testDeployInstanceSetInstances() throws Exception {
    vertx.deployVerticle(new MyVerticle(), new DeploymentOptions().setInstances(2)).await();
  }

  @Test
  public void testDeployUsingClassName() throws Exception {
    vertx.deployVerticle("java:" + TestVerticle.class.getCanonicalName())
      .onComplete(onSuccess(id -> testComplete()));
    await();
  }

  @Test
  public void testDeployUsingClassAndConfig() throws Exception {
    JsonObject config = generateJSONObject();
    vertx.deployVerticle("java:" + TestVerticle.class.getCanonicalName(), new DeploymentOptions().setConfig(config))
      .onComplete(onSuccess(id -> testComplete()));
    await();
  }

  @Test
  public void testDeployUsingClassFails() throws Exception {
    vertx.deployVerticle("java:uhqwuhiqwduhwd")
      .onComplete(onFailure(err -> {
        assertTrue(err instanceof ClassNotFoundException);
        testComplete();
      }));
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
    vertx.deployVerticle(TestVerticle2.class.getCanonicalName(), options).onComplete(onSuccess(depID -> {
        assertEquals(1, deployHandlerCount.incrementAndGet());
        deployLatch.countDown();
    }));
    awaitLatch(deployLatch);
    assertWaitUntil(() -> deployCount.get() == numInstances);
    assertEquals(1, vertx.deploymentIDs().size());
    DeploymentContext deployment = ((VertxInternal) vertx).getDeployment(vertx.deploymentIDs().iterator().next());
    Set<Deployable> verticles = ((Deployment)deployment.deployment()).instances();
    assertEquals(numInstances, verticles.size());
    CountDownLatch undeployLatch = new CountDownLatch(1);
    assertEquals(numInstances, deployCount.get());
    vertx.undeploy(deployment.deploymentID()).onComplete(onSuccess(v -> {
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
    vertx.deployVerticle(className).onComplete(onFailure(err -> {
      // No prefix or suffix so should be interpreted as a java class
      assertTrue(err instanceof ClassNotFoundException);
      testComplete();
    }));
    await();
  }

  @Ignore("does not seem to work in module mode but works in IDE")
  @Test
  public void testDeployAsSource() throws Exception {
    String sourceFile = "io/vertx/test/sourceverticle/SourceVerticle.java";
    vertx.deployVerticle("java:" + sourceFile).onComplete(onSuccess(res -> {
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
      vertx.deployVerticle(child1).onComplete(onSuccess(id -> complete()));
      f.complete();
    };
    Verticle verticle = new MyAsyncVerticle(start, Promise::complete);
    vertx.deployVerticle(verticle).onComplete(onSuccess(id -> complete()));
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
      vertx.deployVerticle(child1).onComplete(onSuccess(id -> {
        childDepID.set(id);
        f.complete(null);
      }));
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
    vertx.deployVerticle(verticle).onComplete(onSuccess(id -> {
      parentDepID.set(id);
      deployLatch.countDown();
    }));
    assertTrue(deployLatch.await(10, TimeUnit.SECONDS));
    assertTrue(vertx.deploymentIDs().contains(parentDepID.get()));
    assertTrue(vertx.deploymentIDs().contains(childDepID.get()));

    // Now they're deployed, undeploy them
    vertx.undeploy(parentDepID.get()).onComplete(onSuccess(v -> {}));
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
          }).onComplete(onSuccess(child -> {
            startPromise.fail("Undeployed");
          }));
        }
      })
      .onComplete(onFailure(expected -> {
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
    }).onComplete(onFailure(expected -> {
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
    vertx.deployVerticle(verticle).onComplete(onSuccess(id -> {
      testComplete();
    }));
    await();
  }

  @Test
  public void testAsyncDeployFailureCalledSynchronously() throws Exception {
    MyAsyncVerticle verticle = new MyAsyncVerticle(f -> f.fail(new Exception("foobar")), null);
    vertx.deployVerticle(verticle).onComplete(onFailure(err -> {
      assertEquals("foobar", err.getMessage());
      testComplete();
    }));
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
    vertx.deployVerticle(verticle).onComplete(onSuccess(id -> {
      long now = System.currentTimeMillis();
      assertTrue(now - start >= delay);
      assertTrue(vertx.deploymentIDs().contains(id));
      testComplete();
    }));
    Thread.sleep(delay / 2);
    assertTrue(vertx.deploymentIDs().isEmpty());
    await();
  }

  @Test
  public void testAsyncDeployFailure() throws Exception {
    long start = System.currentTimeMillis();
    long delay = 1000;
    MyAsyncVerticle verticle = new MyAsyncVerticle(f -> vertx.setTimer(delay, id -> f.fail(new Exception("foobar"))), null);
    vertx.deployVerticle(verticle).onComplete(onFailure(err -> {
      assertEquals("foobar", err.getMessage());
      long now = System.currentTimeMillis();
      assertTrue(now - start >= delay);
      assertTrue(vertx.deploymentIDs().isEmpty());
      testComplete();
    }));
    await();
  }


  @Test
  public void testAsyncUndeployCalledSynchronously() throws Exception {
    MyAsyncVerticle verticle = new MyAsyncVerticle(f -> f.complete(null), f ->  f.complete(null));
    vertx.deployVerticle(verticle).onComplete(onSuccess(id -> {
      vertx.undeploy(id).onComplete(onSuccess(v -> {
        assertFalse(vertx.deploymentIDs().contains(id));
        testComplete();
      }));
    }));
    await();
  }

  @Test
  public void testAsyncUndeployFailureCalledSynchronously() throws Exception {
    MyAsyncVerticle verticle = new MyAsyncVerticle(f -> f.complete(null), f -> f.fail(new Exception("foobar")));
    vertx.deployVerticle(verticle).onComplete(onSuccess(id -> {
      vertx.undeploy(id).onComplete(onFailure(err -> {
        assertEquals("foobar", err.getMessage());
        assertFalse(vertx.deploymentIDs().contains(id));
        testComplete();
      }));
    }));
    await();
  }

  @Test
  public void testAsyncUndeploy() throws Exception {
    long delay = 1000;
    MyAsyncVerticle verticle = new MyAsyncVerticle(f-> f.complete(null), f -> vertx.setTimer(delay, id -> f.complete(null)));
    vertx.deployVerticle(verticle).onComplete(onSuccess(id -> {
      long start = System.currentTimeMillis();
      vertx.undeploy(id).onComplete(onSuccess(v -> {
        long now = System.currentTimeMillis();
        assertTrue(now - start >= delay);
        assertFalse(vertx.deploymentIDs().contains(id));
        testComplete();
      }));
      vertx.setTimer(delay / 2, timerID -> assertFalse(vertx.deploymentIDs().isEmpty()));
    }));
    await();
  }

  @Test
  public void testAsyncUndeployFailure() throws Exception {
    long delay = 1000;
    MyAsyncVerticle verticle = new MyAsyncVerticle(f-> f.complete(null), f -> vertx.setTimer(delay, id -> f.fail(new Exception("foobar"))));
    vertx.deployVerticle(verticle).onComplete(onSuccess(id -> {
      long start = System.currentTimeMillis();
      vertx.undeploy(id).onComplete(onFailure(err -> {
        long now = System.currentTimeMillis();
        assertTrue(now - start >= delay);
        assertFalse(vertx.deploymentIDs().contains(id));
        testComplete();
      }));
    }));
    await();
  }

  @Test
  public void testAsyncUndeployFailsAfterSuccess() {
    Verticle verticle = new AbstractVerticle() {
      @Override
      public void stop(Promise<Void> stopPromise) throws Exception {
        stopPromise.complete();
        throw new Exception();
      }
    };
    Context ctx = vertx.getOrCreateContext();
    ctx.runOnContext(v1 -> {
      vertx.deployVerticle(verticle).onComplete(onSuccess(id -> {
        vertx.undeploy(id).onComplete(onSuccess(v2 -> {
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

        vertx.deployVerticle(child).onComplete(onSuccess(depID -> {
          startPromise.complete();
        }));

      }
    };

    vertx.deployVerticle(parent).onComplete(onSuccess(depID -> {
      vertx.setTimer(10, tid -> vertx.undeploy(depID).onComplete(onSuccess(v -> {
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
    vertx.deployVerticle(verticle).onComplete(onSuccess(id -> {
      assertEquals(0, closedCount.get());
      // Now undeploy
      vertx.undeploy(id).onComplete(onSuccess(v -> {
        assertEquals(2, closedCount.get());
        testComplete();
      }));
    }));
    await();
  }

  @Test
  public void testDeployWhenClosedShouldFail() throws Exception {
    CountDownLatch closed = new CountDownLatch(1);
    vertx.close().onComplete(onSuccess(v -> {
      closed.countDown();
    }));
    awaitLatch(closed);
    vertx.deployVerticle(new AbstractVerticle() {
    }).onComplete(onFailure(err -> {
      assertEquals("Vert.x closed", err.getMessage());
      testComplete();
    }));
    await();
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
    assertNotNull(bytes);

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
    CountDownLatch latch = new CountDownLatch(numVerticles);
    for (int i = 0; i < numVerticles; i++) {
      MyVerticle verticle = new MyVerticle();
      verticles.add(verticle);
      vertx.deployVerticle("java:" + ParentVerticle.class.getName()).onComplete(onSuccess(res -> {
        latch.countDown();
      }));
    }
    awaitLatch(latch);
    assertEquals(2 * numVerticles, vertx.deploymentIDs().size());
    vertx.close().onComplete(onSuccess(v -> {
      assertEquals(0, vertx.deploymentIDs().size());
      testComplete();
    }));
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
          .onComplete(stopPromise);
      }
    }

    vertx.deployVerticle(new ParentVerticle()).onComplete(onSuccess(id ->
      vertx.undeploy(id).onComplete(onFailure(u -> testComplete()))));
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
      vertx.deployVerticle(verticle).onComplete(onSuccess(v -> {
        latch.countDown();
      }));
    }
    awaitLatch(latch);
    vertx.close().onComplete(onSuccess(v -> {
      for (MyVerticle verticle: verticles) {
        assertFalse(verticle.stopCalled);
      }
      testComplete();
    }));
    await();
    vertx = null;
  }

  @Test
  public void testUndeployAllNoDeployments() throws Exception {
    vertx.close().onComplete(onSuccess(v -> testComplete()));
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

    vertx.deployVerticle(new MultiInstanceVerticle())
      .onComplete(onSuccess(v -> testComplete()));
    await();
  }

  @Test
  public void testGetInstanceCountMultipleVerticles() throws Exception {
    AtomicInteger messageCount = new AtomicInteger(0);
    AtomicInteger totalReportedInstances = new AtomicInteger(0);
    vertx.eventBus().consumer("instanceCount", event -> {
      totalReportedInstances.addAndGet((int)event.body());
      messageCount.incrementAndGet();
    });
    awaitFuture(vertx.deployVerticle(TestVerticle3.class.getCanonicalName(), new DeploymentOptions().setInstances(3)));
    assertWaitUntil(() -> messageCount.get() == 3);
    assertEquals(9, totalReportedInstances.get());
    assertWaitUntil(() -> vertx.deploymentIDs().size() == 1);
    DeploymentContext deployment = ((VertxInternal) vertx).getDeployment(vertx.deploymentIDs().iterator().next());
    awaitFuture(vertx.undeploy(deployment.deploymentID()));
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
      public void start(Promise<Void> startPromise) {
        vertx.deployVerticle(verticleChild).onComplete(onFailure(v -> {
          startPromise.complete();
        }));
      }
    };
    vertx.deployVerticle(verticleParent).onComplete(onSuccess(depID -> {
      vertx.undeploy(depID).onComplete(onSuccess(v -> {
        testComplete();
      }));
    }));
    await();
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
    vertx.deployVerticle(supplier, options).onComplete(onSuccess(deploymentId -> {
      check.accept(deploymentId);
      testComplete();
    }));
    await();
  }

  @Test
  public void testDeploySupplierNull() {
    Supplier<Verticle> supplier = () -> null;
    DeploymentOptions options = new DeploymentOptions();
    vertx.deployVerticle(supplier, options).onComplete(onFailure(t -> {
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
    vertx.deployVerticle(supplier, options).onComplete(onFailure(t -> {
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
    vertx.deployVerticle(supplier, new DeploymentOptions().setInstances(1))
      .onComplete(onFailure(t -> {
        assertEquals(Collections.emptySet(), vertx.deploymentIDs());
        testComplete();
      }));
    await();
  }

  @Test
  public void testDeployClass() {
    JsonObject config = generateJSONObject();
    vertx.deployVerticle(ReferenceSavingMyVerticle.class, new DeploymentOptions().setInstances(4).setConfig(config))
      .onComplete(onSuccess(deploymentId -> {
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
    vertx.deployVerticle(NoDefaultPublicConstructorVerticle.class, new DeploymentOptions()).onComplete(onFailure(t -> {
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
      public void start(Promise<Void> startPromise) {
        ((ContextInternal)context).addCloseHook(closeable);
        startPromise.fail("Fail to deploy.");
      }
    };
    vertx.deployVerticle(v).onComplete(onFailure(err -> {
      assertTrue(closeHookCalledBeforeDeployFailure.get());
      testComplete();
    }));
    await();
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
    awaitFuture(fut);
    assertEquals(1, eventLoops.size());
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
    }, options).onComplete(onFailure(err -> {
      if (!called.compareAndSet(false, true)) {
        fail("Completion handler called more than once");
      }
      vertx.setTimer(30, id -> {
        testComplete();
      });

    }));
    await();
  }

  @Test
  public void testUndeployParentDuringChildDeployment() throws Exception {
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
      parentVerticleContext.owner().deployVerticle(childVerticle).onComplete(onFailure(t -> {
        assertSame(parentVerticleContext, Vertx.currentContext());
        testComplete();
      }));
      startPromise.complete();
    }, stopPromise -> {
      undeployLatch.countDown();
    });
    AtomicReference<String> deploymentID = new AtomicReference<>();
    vertx.deployVerticle(verticle).onComplete(onSuccess(id -> {
      deploymentID.set(id);
      deployLatch.countDown();
    }));
    awaitLatch(deployLatch);
    vertx.undeploy(deploymentID.get());
    await();
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
            completion.complete();
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
    fut.onComplete(onFailure(expected -> {
      for (int j = 0;j < numberOfInstances;j++) {
        if (instanceToFail != j) {
          assertTrue(stopped.containsKey(j));
        }
        assertTrue(closeHooks.contains(j));
      }
      testComplete();
    }));
    await();
  }

  @Test
  public void testCloseDeploymentInProgress() {
    Vertx vertx = Vertx.vertx();
    waitFor(3);
    vertx.deployVerticle(new AbstractVerticle() {
      Promise<Void> startPromise;
      @Override
      public void start(Promise<Void> startPromise) {
        this.startPromise = startPromise;
        AtomicBoolean hookCompletion = new AtomicBoolean();
        ((ContextInternal)context).addCloseHook(completion -> {
          complete();
          new Thread(() -> {
            try {
              Thread.sleep(500);
            } catch (InterruptedException e) {
              fail(e);
            }
            hookCompletion.set(true);
            completion.complete();
          }).start();
        });
        vertx.close().onComplete(onSuccess(v -> {
          assertTrue(hookCompletion.get());
          complete();
        }));
      }
      @Override
      public void stop(Promise<Void> stopPromise) {
        fail();
      }
    }).onComplete(onFailure(err -> complete()));
    await();
  }

  @Test
  public void testContextClassLoader() throws Exception {
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
        assertSame(cl, Thread.currentThread().getContextClassLoader());
        assertSame(cl, ((ContextInternal)context).classLoader());
        vertx.fileSystem().props("foo.txt").onComplete(onSuccess(props -> {
          assertEquals(5, props.size());
          assertTrue(used.get());
          testComplete();
        }));
      }
    }, new DeploymentOptions().setClassLoader(cl)).onComplete(onSuccess(id -> {
    }));
    await();
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
    Promise<Void> completion;

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
        promise.complete();
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
}

