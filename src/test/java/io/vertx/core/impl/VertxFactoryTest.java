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
package io.vertx.core.impl;

import io.vertx.core.Promise;
import io.vertx.core.Verticle;
import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.core.metrics.MetricsOptions;
import io.vertx.core.impl.transports.JDKTransport;
import io.vertx.core.spi.*;
import io.vertx.core.spi.transport.Transport;
import io.vertx.core.spi.cluster.ClusterManager;
import io.vertx.core.spi.tracing.VertxTracer;
import io.vertx.core.tracing.TracingOptions;
import io.vertx.test.fakecluster.FakeClusterManager;
import io.vertx.test.fakemetrics.FakeVertxMetrics;
import io.vertx.test.faketracer.FakeTracer;
import io.vertx.test.faketracer.FakeTracerFactory;
import junit.framework.AssertionFailedError;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.util.Collections;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;

public class VertxFactoryTest {

  @Rule
  public TemporaryFolder testFolder = new TemporaryFolder();

  @Test
  public void testCreate() {
    VertxBuilder factory = new VertxBuilder();
    Vertx vertx = factory.init().vertx();
    try {
      assertNotNull(vertx);
    } finally {
      vertx.close();
    }
  }

  @Test
  public void testCreateClustered() throws Exception {
    VertxBuilder factory = new VertxBuilder().init();
    CompletableFuture<Vertx> fut = new CompletableFuture<>();
    factory.init();
    factory.clusteredVertx(ar -> {
      if (ar.succeeded()) {
        fut.complete(ar.result());
      } else {
        fut.completeExceptionally(ar.cause());
      }
    });
    Vertx vertx = fut.get(10, TimeUnit.SECONDS);
    try {
      assertNotNull(vertx);
      assertNotNull(((VertxInternal)vertx).getClusterManager());
    } finally {
      vertx.close();
    }
  }

  @Test
  public void testFactoryMetricsOverridesMetaInf() {
    runWithServiceFromMetaInf(VertxMetricsFactory.class, FakeVertxMetrics.class.getName(), () -> {
      FakeVertxMetrics metrics = new FakeVertxMetrics();
      MetricsOptions metricsOptions = new MetricsOptions().setEnabled(true);
      VertxBuilder factory = new VertxBuilder(new VertxOptions().setMetricsOptions(metricsOptions));
      factory.metrics(metrics);
      factory.init();
      Vertx vertx = factory.vertx();
      try {
        assertSame(metrics, ((VertxInternal)vertx).metricsSPI());
      } finally {
        vertx.close();
      }
    });
  }

  @Test
  public void testFactoryMetricsFactoryOverridesOptions() {
    FakeVertxMetrics metrics = new FakeVertxMetrics();
    MetricsOptions metricsOptions = new MetricsOptions().setEnabled(true).setFactory(options -> {
      throw new AssertionError();
    });
    VertxBuilder factory = new VertxBuilder(new VertxOptions().setMetricsOptions(metricsOptions));
    factory.metrics(metrics);
    factory.init();
    Vertx vertx = factory.vertx();
    try {
      assertSame(metrics, ((VertxInternal)vertx).metricsSPI());
    } finally {
      vertx.close();
    }
  }

  @Test
  public void testFactoryTracerOverridesMetaInf() {
    runWithServiceFromMetaInf(VertxTracerFactory.class, FakeTracerFactory.class.getName(), () -> {
      FakeTracer tracer = new FakeTracer();
      TracingOptions tracingOptions = new TracingOptions();
      VertxBuilder factory = new VertxBuilder(new VertxOptions().setTracingOptions(tracingOptions));
      factory.tracer(tracer);
      factory.init();
      Vertx vertx = factory.vertx();
      try {
        assertSame(tracer, ((VertxInternal)vertx).getOrCreateContext().tracer());
      } finally {
        vertx.close();
      }
    });
  }

  @Test
  public void testFactoryTracerFactoryOverridesOptions() {
    FakeTracer tracer = new FakeTracer();
    TracingOptions tracingOptions = new TracingOptions().setFactory(new VertxTracerFactory() {
      @Override
      public VertxTracer tracer(TracingOptions options) {
        throw new AssertionError();
      }
    });
    VertxBuilder factory = new VertxBuilder(new VertxOptions().setTracingOptions(tracingOptions));
    factory.tracer(tracer);
    factory.init();
    Vertx vertx = factory.vertx();
    try {
      assertSame(tracer, ((VertxInternal)vertx).getOrCreateContext().tracer());
    } finally {
      vertx.close();
    }
  }

  @Test
  public void testFactoryClusterManagerOverridesMetaInf() throws Exception {
    FakeClusterManager clusterManager = new FakeClusterManager();
    CompletableFuture<Vertx> res = new CompletableFuture<>();
    runWithServiceFromMetaInf(ClusterManager.class, FakeClusterManager.class.getName(), () -> {
      VertxBuilder factory = new VertxBuilder(new VertxOptions());
      factory.clusterManager(clusterManager);
      factory.init();
      factory.clusteredVertx(ar -> {
        if (ar.succeeded()) {
          res.complete(ar.result());
        } else {
          res.completeExceptionally(ar.cause());
        }
      });
    });
    Vertx vertx = res.get(10, TimeUnit.SECONDS);
    try {
      assertSame(clusterManager, ((VertxInternal)vertx).getClusterManager());
    } finally {
      vertx.close();
    }
  }

  @Test
  public void testFactoryTransportOverridesDefault() {
    VertxBuilder factory = new VertxBuilder();
    // JDK transport
    Transport override = new JDKTransport() {
    };
    factory.findTransport(override);
    factory.init();
    Vertx vertx = factory.vertx();
    try {
      assertSame(override, ((VertxInternal)vertx).transport());
    } finally {
      vertx.close();
    }
  }

  @Test
  public void testThatThreadFactoryCanCreateThreadsDuringTheirInitialization() {
    VertxBuilder factory = new VertxBuilder();
    VertxThreadFactory tf = new VertxThreadFactory() {
      @Override
      public VertxThread newVertxThread(Runnable target, String name, boolean worker, long maxExecTime, TimeUnit maxExecTimeUnit) {
        return VertxThreadFactory.INSTANCE.newVertxThread(target, name, worker, maxExecTime, maxExecTimeUnit);
      }
    };
    factory
      .threadFactory(tf)
      .executorServiceFactory(new CustomExecutorServiceFactory())
      .init()
      .vertx()
      .close();
  }

  private class CustomExecutorServiceFactory implements ExecutorServiceFactory {

    @Override
    public ExecutorService createExecutor(ThreadFactory threadFactory, Integer concurrency, Integer maxConcurrency) {
      // Simulate the behavior of the JBoss enhanced queue executor.
      // It uses the thread factory to create a thread used to as scheduler thread.
      threadFactory.newThread(() -> {});
      return Executors.newCachedThreadPool();
    }
  }

  private void runWithServiceFromMetaInf(Class<?> service, String implementationName, Runnable runnable) {
    ClassLoader classLoader;
    try {
      File dir;
      int cnt = 0;
      while (true) {
        try {
          dir = testFolder.newFolder("service-loader-" + cnt, "META-INF", "services");
          break;
        } catch (IOException e) {
          if (e.getMessage().contains("already exists")) {
            cnt++;
            assertTrue(cnt < 100);
          } else{
            throw e;
          }
        }
      }
      File desc = new File(dir, service.getName());
      Files.write(desc.toPath(), implementationName.getBytes());
      assertTrue(desc.exists());
      classLoader = new URLClassLoader(new URL[]{new File(testFolder.getRoot(), "service-loader-" + cnt).toURI().toURL()});
    } catch (IOException e) {
      AssertionFailedError afe = new AssertionFailedError();
      afe.initCause(e);
      throw afe;
    }
    Thread thread = Thread.currentThread();
    ClassLoader prev = thread.getContextClassLoader();
    thread.setContextClassLoader(classLoader);
    try {
      runnable.run();
    } finally {
      thread.setContextClassLoader(prev);
    }
  }


  public static class TestVertxServiceProvider implements VertxServiceProvider {
    private static final AtomicInteger initialized = new AtomicInteger();
    @Override
    public void init(VertxBuilder builder) {
      initialized.incrementAndGet();
    }
  }

  @Test
  public void testExplicitServiceProviders() {
    TestVertxServiceProvider.initialized.set(0);
    runWithServiceFromMetaInf(VertxServiceProvider.class, TestVertxServiceProvider.class.getName(), () -> {
      VertxBuilder factory = new VertxBuilder();
      Vertx vertx = factory
        .init()
        .vertx();
      vertx.close();
    });
    assertEquals(1, TestVertxServiceProvider.initialized.get());
    AtomicInteger initialized = new AtomicInteger();
    VertxServiceProvider provider = builder -> initialized.incrementAndGet();
    runWithServiceFromMetaInf(VertxServiceProvider.class, TestVertxServiceProvider.class.getName(), () -> {
      VertxBuilder factory = new VertxBuilder();
      Vertx vertx = factory
        .serviceProviders(Collections.singletonList(provider))
        .init()
        .vertx();
      vertx.close();
    });
    assertEquals(1, TestVertxServiceProvider.initialized.get());
    assertEquals(1, initialized.get());
  }

  public static class TestVerticleFactory implements VerticleFactory {
    private static final AtomicInteger initialized = new AtomicInteger();
    @Override
    public void init(Vertx vertx) {
      initialized.incrementAndGet();
    }
    @Override
    public String prefix() {
      return "test";
    }
    @Override
    public void createVerticle(String verticleName, ClassLoader classLoader, Promise<Callable<Verticle>> promise) {
      promise.fail("Cannot deploy");
    }
  }

  @Test
  public void testExplicitVerticleFactories() {
    runWithServiceFromMetaInf(VerticleFactory.class, TestVerticleFactory.class.getName(), () -> {
      VertxBuilder factory = new VertxBuilder();
      Vertx vertx = factory
        .init()
        .vertx();
      vertx.close();
    });
    assertEquals(1, TestVerticleFactory.initialized.get());
    AtomicInteger initialized = new AtomicInteger();
    VerticleFactory verticleFactory = new VerticleFactory() {
      @Override
      public void init(Vertx vertx) {
        initialized.incrementAndGet();
      }
      @Override
      public String prefix() {
        return "test2";
      }
      @Override
      public void createVerticle(String verticleName, ClassLoader classLoader, Promise<Callable<Verticle>> promise) {
        promise.fail("Cannot deploy");
      }
    };
    runWithServiceFromMetaInf(VertxServiceProvider.class, TestVertxServiceProvider.class.getName(), () -> {
      VertxBuilder factory = new VertxBuilder();
      Vertx vertx = factory
        .verticleFactories(Collections.singletonList(verticleFactory))
        .init()
        .vertx();
      vertx.close();
    });
    assertEquals(1, TestVerticleFactory.initialized.get());
    assertEquals(1, initialized.get());
  }
}
