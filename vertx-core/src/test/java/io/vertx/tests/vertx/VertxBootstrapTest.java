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
package io.vertx.tests.vertx;

import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.core.impl.VertxThread;
import io.vertx.core.internal.VertxBootstrap;
import io.vertx.core.internal.VertxInternal;
import io.vertx.core.metrics.MetricsOptions;
import io.vertx.core.impl.transports.NioTransport;
import io.vertx.core.spi.transport.Transport;
import io.vertx.core.spi.ExecutorServiceFactory;
import io.vertx.core.spi.VertxMetricsFactory;
import io.vertx.core.spi.VertxThreadFactory;
import io.vertx.core.spi.VertxTracerFactory;
import io.vertx.core.spi.cluster.ClusterManager;
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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.*;

public class VertxBootstrapTest {

  @Rule
  public TemporaryFolder testFolder = new TemporaryFolder();

  @Test
  public void testCreate() {
    VertxBootstrap factory = VertxBootstrap.create().init();
    Vertx vertx = factory.init().vertx();
    assertNotNull(vertx);
  }

  @Test
  public void testCreateClustered() throws Exception {
    VertxBootstrap factory = VertxBootstrap.create().init();
    CompletableFuture<Vertx> fut = new CompletableFuture<>();
    factory.init();
    factory.clusterManager(new FakeClusterManager());
    factory.clusteredVertx().onComplete(ar -> {
      if (ar.succeeded()) {
        fut.complete(ar.result());
      } else {
        fut.completeExceptionally(ar.cause());
      }
    });
    Vertx vertx = fut.get(10, TimeUnit.SECONDS);
    assertNotNull(vertx);
    assertNotNull(((VertxInternal)vertx).clusterManager());
  }

  @Test
  public void testFactoryMetricsOverridesMetaInf() {
    runWithServiceFromMetaInf(VertxMetricsFactory.class, FakeVertxMetrics.class.getName(), () -> {
      FakeVertxMetrics metrics = new FakeVertxMetrics();
      MetricsOptions metricsOptions = new MetricsOptions().setEnabled(true);
      VertxBootstrap factory = VertxBootstrap.create().options(new VertxOptions().setMetricsOptions(metricsOptions));
      factory.metricsFactory(options -> metrics);
      factory.init();
      Vertx vertx = factory.vertx();
      assertSame(metrics, ((VertxInternal)vertx).metrics());
    });
  }

/*
  @Test
  public void testFactoryMetricsFactoryOverridesOptions() {
    FakeVertxMetrics metrics = new FakeVertxMetrics();
    VertxBootstrap factory = VertxBootstrap.create().options(new VertxOptions().setMetricsOptions(new MetricsOptions().setEnabled(true)));
    factory.metrics(metrics);
    factory.metricsFactory(options -> {
      throw new AssertionError();
    });
    factory.init();
    Vertx vertx = factory.vertx();
    assertSame(metrics, ((VertxInternal)vertx).metricsSPI());
  }
*/

  @Test
  public void testFactoryTracerOverridesMetaInf() {
    runWithServiceFromMetaInf(VertxTracerFactory.class, FakeTracerFactory.class.getName(), () -> {
      FakeTracer tracer = new FakeTracer();
      TracingOptions tracingOptions = new TracingOptions();
      VertxBootstrap factory = VertxBootstrap.create().options(new VertxOptions().setTracingOptions(tracingOptions));
      factory.tracerFactory(options -> tracer);
      factory.init();
      Vertx vertx = factory.vertx();
      assertSame(tracer, ((VertxInternal)vertx).getOrCreateContext().tracer());
    });
  }

/*
  @Test
  public void testFactoryTracerFactoryOverridesOptions() {
    FakeTracer tracer = new FakeTracer();
    VertxOptions factory = VertxBootstrap.create().options(new VertxOptions().setTracingOptions(new TracingOptions()))
      .tracerFactory(options -> {
        throw new AssertionError();
      });
    factory.tracer(tracer);
    factory.init();
    Vertx vertx = factory.vertx();
    assertSame(tracer, ((VertxInternal)vertx).getOrCreateContext().tracer());
  }
*/

  @Test
  public void testFactoryClusterManagerOverridesMetaInf() throws Exception {
    FakeClusterManager clusterManager = new FakeClusterManager();
    CompletableFuture<Vertx> res = new CompletableFuture<>();
    runWithServiceFromMetaInf(ClusterManager.class, FakeClusterManager.class.getName(), () -> {
      VertxBootstrap factory = VertxBootstrap.create().options(new VertxOptions());
      factory.clusterManager(clusterManager);
      factory.init();
      factory.clusteredVertx().onComplete(ar -> {
        if (ar.succeeded()) {
          res.complete(ar.result());
        } else {
          res.completeExceptionally(ar.cause());
        }
      });
    });
    Vertx vertx = res.get(10, TimeUnit.SECONDS);
    assertSame(clusterManager, ((VertxInternal)vertx).clusterManager());
  }

  @Test
  public void testFactoryTransportOverridesDefault() {
    VertxBootstrap factory = VertxBootstrap.create();
    // NIO transport
    Transport override = new NioTransport() {
    };
    factory.transport(override);
    factory.init();
    Vertx vertx = factory.vertx();
    assertSame(override, ((VertxInternal)vertx).transport());
  }

  @Test
  public void testThatThreadFactoryCanCreateThreadsDuringTheirInitialization() {
    VertxBootstrap factory = VertxBootstrap.create();
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
      .close().await();
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
      File dir = new File(testFolder.newFolder("META-INF"), "services");
      dir.mkdirs();
      assertTrue(dir.exists());
      File desc = new File(dir, service.getName());
      Files.write(desc.toPath(), implementationName.getBytes());
      assertTrue(desc.exists());
      classLoader = new URLClassLoader(new URL[]{testFolder.getRoot().toURI().toURL()});
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
}
