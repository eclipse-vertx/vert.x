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

import io.vertx.core.*;
import io.vertx.core.file.impl.FileResolver;
import io.vertx.core.impl.logging.Logger;
import io.vertx.core.impl.logging.LoggerFactory;
import io.vertx.core.json.JsonObject;
import io.vertx.core.metrics.MetricsOptions;
import io.vertx.core.net.impl.transport.Transport;
import io.vertx.core.spi.ExecutorServiceFactory;
import io.vertx.core.spi.VertxMetricsFactory;
import io.vertx.core.spi.VertxServiceProvider;
import io.vertx.core.spi.VertxThreadFactory;
import io.vertx.core.spi.VertxTracerFactory;
import io.vertx.core.spi.cluster.ClusterManager;
import io.vertx.core.spi.cluster.NodeSelector;
import io.vertx.core.spi.cluster.impl.DefaultNodeSelector;
import io.vertx.core.spi.metrics.VertxMetrics;
import io.vertx.core.spi.tracing.VertxTracer;
import io.vertx.core.tracing.TracingOptions;

import java.util.ArrayList;
import java.util.Collection;

/**
 * Vertx builder for creating vertx instances with SPI overrides.
 *
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class VertxBuilder {

  private static final Logger log = LoggerFactory.getLogger(VertxBuilder.class);

  private VertxOptions options;
  private JsonObject config;
  private Transport transport;
  private ClusterManager clusterManager;
  private NodeSelector clusterNodeSelector;
  private VertxTracer tracer;
  private VertxThreadFactory threadFactory;
  private ExecutorServiceFactory executorServiceFactory;
  private VertxMetrics metrics;
  private FileResolver fileResolver;

  public VertxBuilder(JsonObject config) {
    this(new VertxOptions(config));
    this.config = config;
  }

  public VertxBuilder(VertxOptions options) {
    this.options = options;
  }

  public VertxBuilder() {
    this(new VertxOptions());
  }

  /**
   * @return the vertx options
   */
  public VertxOptions options() {
    return options;
  }

  /**
   * @return the optional config when instantiated from the command line or {@code null}
   */
  public JsonObject config() {
    return config;
  }

  /**
   * @return the transport to use
   */
  public Transport transport() {
    return transport;
  }

  /**
   * Set the transport to for building Vertx.
   * @param transport the transport
   * @return this builder instance
   */
  public VertxBuilder transport(Transport transport) {
    this.transport = transport;
    return this;
  }

  /**
   * @return the cluster manager to use
   */
  public ClusterManager clusterManager() {
    return clusterManager;
  }

  /**
   * Set the cluster manager to use.
   * @param clusterManager the cluster manager
   * @return this builder instance
   */
  public VertxBuilder clusterManager(ClusterManager clusterManager) {
    this.clusterManager = clusterManager;
    return this;
  }

  /**
   * @return the node selector to use
   */
  public NodeSelector clusterNodeSelector() {
    return clusterNodeSelector;
  }

  /**
   * Set the cluster node selector to use.
   * @param selector the selector
   * @return this builder instance
   */
  public VertxBuilder clusterNodeSelector(NodeSelector selector) {
    this.clusterNodeSelector = selector;
    return this;
  }

  /**
   * @return the tracer instance to use
   */
  public VertxTracer tracer() {
    return tracer;
  }

  /**
   * Set the tracer to use.
   * @param tracer the tracer
   * @return this builder instance
   */
  public VertxBuilder tracer(VertxTracer tracer) {
    this.tracer = tracer;
    return this;
  }

  /**
   * @return the metrics instance to use
   */
  public VertxMetrics metrics() {
    return metrics;
  }

  /**
   * Set the metrics instance to use.
   * @param metrics the metrics
   * @return this builder instance
   */
  public VertxBuilder metrics(VertxMetrics metrics) {
    this.metrics = metrics;
    return this;
  }

  /**
   * @return the {@code VertxThreadFactory} to use
   */
  public VertxThreadFactory threadFactory() {
    return threadFactory;
  }

  /**
   * Set the {@code VertxThreadFactory} instance to use.
   * @param factory the metrics
   * @return this builder instance
   */
  public VertxBuilder threadFactory(VertxThreadFactory factory) {
    this.threadFactory = factory;
    return this;
  }

  /**
   * @return the {@code ExecutorServiceFactory} to use
   */
  public ExecutorServiceFactory executorServiceFactory() {
    return executorServiceFactory;
  }

  /**
   * Set the {@code ExecutorServiceFactory} instance to use.
   * @param factory the factory
   * @return this builder instance
   */
  public VertxBuilder executorServiceFactory(ExecutorServiceFactory factory) {
    this.executorServiceFactory = factory;
    return this;
  }

  /**
   * Build and return the vertx instance
   */
  public Vertx vertx() {
    checkBeforeInstantiating();
    VertxImpl vertx = new VertxImpl(
      options,
      null,
      null,
      metrics,
      tracer,
      transport,
      fileResolver,
      threadFactory,
      executorServiceFactory);
    vertx.init();
    return vertx;
  }

  /**
   * Build and return the clustered vertx instance
   */
  public void clusteredVertx(Handler<AsyncResult<Vertx>> handler) {
    checkBeforeInstantiating();
    if (clusterManager == null) {
      throw new IllegalStateException("No ClusterManagerFactory instances found on classpath");
    }
    VertxImpl vertx = new VertxImpl(
      options,
      clusterManager,
      clusterNodeSelector == null ? new DefaultNodeSelector() : clusterNodeSelector,
      metrics,
      tracer,
      transport,
      fileResolver,
      threadFactory,
      executorServiceFactory);
    vertx.initClustered(options, handler);
  }

  /**
   * Initialize the service providers.
   * @return this builder instance
   */
  public VertxBuilder init() {
    initTransport();
    initFileResolver();
    Collection<VertxServiceProvider> providers = new ArrayList<>();
    initMetrics(options, providers);
    initTracing(options, providers);
    initClusterManager(options, providers);
    providers.addAll(ServiceHelper.loadFactories(VertxServiceProvider.class));
    initProviders(providers);
    initThreadFactory();
    initExecutorServiceFactory();
    return this;
  }

  private void initProviders(Collection<VertxServiceProvider> providers) {
    for (VertxServiceProvider provider : providers) {
      provider.init(this);
    }
  }

  private static void initMetrics(VertxOptions options, Collection<VertxServiceProvider> providers) {
    MetricsOptions metricsOptions = options.getMetricsOptions();
    if (metricsOptions != null) {
      VertxMetricsFactory factory = metricsOptions.getFactory();
      if (factory != null) {
        providers.add(factory);
      }
    }
  }

  private static void initTracing(VertxOptions options, Collection<VertxServiceProvider> providers) {
    TracingOptions tracingOptions = options.getTracingOptions();
    if (tracingOptions != null) {
      VertxTracerFactory factory = tracingOptions.getFactory();
      if (factory != null) {
        providers.add(factory);
      }
    }
  }

  private static void initClusterManager(VertxOptions options, Collection<VertxServiceProvider> providers) {
    ClusterManager clusterManager = options.getClusterManager();
    if (clusterManager == null) {
      String clusterManagerClassName = System.getProperty("vertx.cluster.managerClass");
      if (clusterManagerClassName != null) {
        // We allow specify a sys prop for the cluster manager factory which overrides ServiceLoader
        try {
          Class<?> clazz = Class.forName(clusterManagerClassName);
          clusterManager = (ClusterManager) clazz.newInstance();
        } catch (Exception e) {
          throw new IllegalStateException("Failed to instantiate " + clusterManagerClassName, e);
        }
      }
    }
    if (clusterManager != null) {
      providers.add(clusterManager);
    }
  }

  private void initTransport() {
    if (transport != null) {
      return;
    }
    transport = Transport.transport(options.getPreferNativeTransport());
  }

  private void initFileResolver() {
    if (fileResolver != null) {
      return;
    }
    fileResolver = new FileResolver(options.getFileSystemOptions());
  }

  private void initThreadFactory() {
    if (threadFactory != null) {
      return;
    }
    threadFactory = VertxThreadFactory.INSTANCE;
  }

  private void initExecutorServiceFactory() {
    if (executorServiceFactory != null) {
      return;
    }
    executorServiceFactory = ExecutorServiceFactory.INSTANCE;
  }

  private void checkBeforeInstantiating() {
    checkTracing();
    checkMetrics();
  }

  private void checkTracing() {
    if (options.getTracingOptions() != null && this.tracer == null) {
      log.warn("Tracing options are configured but no tracer is instantiated. " +
        "Make sure you have the VertxTracerFactory in your classpath and META-INF/services/io.vertx.core.spi.VertxServiceProvider " +
        "contains the factory FQCN, or tracingOptions.getFactory() returns a non null value");
    }
  }

  private void checkMetrics() {
    if (options.getMetricsOptions() != null && options.getMetricsOptions().isEnabled() && this.metrics == null) {
      log.warn("Metrics options are configured but no metrics object is instantiated. " +
        "Make sure you have the VertxMetricsFactory in your classpath and META-INF/services/io.vertx.core.spi.VertxServiceProvider " +
        "contains the factory FQCN, or metricsOptions.getFactory() returns a non null value");
    }
  }
}
