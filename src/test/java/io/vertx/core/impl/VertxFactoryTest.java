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

import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.core.metrics.MetricsOptions;
import io.vertx.core.net.impl.transport.Transport;
import io.vertx.core.spi.VertxMetricsFactory;
import io.vertx.core.spi.VertxTracerFactory;
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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.*;

public class VertxFactoryTest {

  @Rule
  public TemporaryFolder testFolder = new TemporaryFolder();

  @Test
  public void testCreate() {
    VertxFactory factory = new VertxFactory();
    Vertx vertx = factory.vertx();
    assertNotNull(vertx);
  }

  @Test
  public void testCreateClustered() throws Exception {
    VertxFactory factory = new VertxFactory();
    CompletableFuture<Vertx> fut = new CompletableFuture<>();
    factory.clusteredVertx(ar -> {
      if (ar.succeeded()) {
        fut.complete(ar.result());
      } else {
        fut.completeExceptionally(ar.cause());
      }
    });
    Vertx vertx = fut.get(10, TimeUnit.SECONDS);
    assertNotNull(vertx);
    assertNotNull(((VertxInternal)vertx).getClusterManager());
  }

  @Test
  public void testFactoryMetricsOverridesMetaInf() {
    runWithServiceFromMetaInf(VertxMetricsFactory.class, FakeVertxMetrics.class.getName(), () -> {
      FakeVertxMetrics metrics = new FakeVertxMetrics();
      MetricsOptions metricsOptions = new MetricsOptions().setEnabled(true);
      VertxFactory factory = new VertxFactory(new VertxOptions().setMetricsOptions(metricsOptions));
      factory.metrics(metrics);
      Vertx vertx = factory.vertx();
      assertSame(metrics, ((VertxInternal)vertx).metricsSPI());
    });
  }

  @Test
  public void testFactoryMetricsFactoryOverridesOptions() {
    FakeVertxMetrics metrics = new FakeVertxMetrics();
    MetricsOptions metricsOptions = new MetricsOptions().setEnabled(true).setFactory(options -> {
      throw new AssertionError();
    });
    VertxFactory factory = new VertxFactory(new VertxOptions().setMetricsOptions(metricsOptions));
    factory.metrics(metrics);
    Vertx vertx = factory.vertx();
    assertSame(metrics, ((VertxInternal)vertx).metricsSPI());
  }

  @Test
  public void testFactoryTracerOverridesMetaInf() {
    runWithServiceFromMetaInf(VertxTracerFactory.class, FakeTracerFactory.class.getName(), () -> {
      FakeTracer tracer = new FakeTracer();
      TracingOptions tracingOptions = new TracingOptions();
      VertxFactory factory = new VertxFactory(new VertxOptions().setTracingOptions(tracingOptions));
      factory.tracer(tracer);
      Vertx vertx = factory.vertx();
      assertSame(tracer, ((VertxInternal)vertx).getOrCreateContext().tracer());
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
    VertxFactory factory = new VertxFactory(new VertxOptions().setTracingOptions(tracingOptions));
    factory.tracer(tracer);
    Vertx vertx = factory.vertx();
    assertSame(tracer, ((VertxInternal)vertx).getOrCreateContext().tracer());
  }

  @Test
  public void testFactoryClusterManagerOverridesMetaInf() throws Exception {
    FakeClusterManager clusterManager = new FakeClusterManager();
    CompletableFuture<Vertx> res = new CompletableFuture<>();
    runWithServiceFromMetaInf(ClusterManager.class, FakeClusterManager.class.getName(), () -> {
      VertxFactory factory = new VertxFactory(new VertxOptions());
      factory.clusterManager(clusterManager);
      factory.clusteredVertx(ar -> {
        if (ar.succeeded()) {
          res.complete(ar.result());
        } else {
          res.completeExceptionally(ar.cause());
        }
      });
    });
    Vertx vertx = res.get(10, TimeUnit.SECONDS);
    assertSame(clusterManager, ((VertxInternal)vertx).getClusterManager());
  }

  @Test
  public void testFactoryTransportOverridesDefault() {
    VertxFactory factory = new VertxFactory();
    // JDK transport
    Transport override = new Transport() {
    };
    factory.transport(override);
    Vertx vertx = factory.vertx();
    assertSame(override, ((VertxInternal)vertx).transport());
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
