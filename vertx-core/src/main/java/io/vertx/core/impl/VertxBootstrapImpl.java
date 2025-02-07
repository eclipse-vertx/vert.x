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
import io.vertx.core.impl.transports.NioTransport;
import io.vertx.core.internal.VertxBootstrap;
import io.vertx.core.eventbus.impl.clustered.DefaultNodeSelector;
import io.vertx.core.spi.context.executor.EventExecutorProvider;
import io.vertx.core.spi.file.FileResolver;
import io.vertx.core.file.impl.FileResolverImpl;
import io.vertx.core.internal.logging.Logger;
import io.vertx.core.internal.logging.LoggerFactory;
import io.vertx.core.json.JsonObject;
import io.vertx.core.spi.transport.Transport;
import io.vertx.core.spi.ExecutorServiceFactory;
import io.vertx.core.spi.VertxMetricsFactory;
import io.vertx.core.spi.VertxServiceProvider;
import io.vertx.core.spi.VertxThreadFactory;
import io.vertx.core.spi.VertxTracerFactory;
import io.vertx.core.spi.cluster.ClusterManager;
import io.vertx.core.eventbus.impl.clustered.NodeSelector;
import io.vertx.core.spi.metrics.VertxMetrics;
import io.vertx.core.spi.tracing.VertxTracer;

import java.util.Collection;
import java.util.List;

/**
 * Bootstrap implementation.
 *
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class VertxBootstrapImpl implements VertxBootstrap {

  private static final Logger log = LoggerFactory.getLogger(VertxBootstrapImpl.class);

  private VertxOptions options;
  private JsonObject config;
  private Transport transport;
  private EventExecutorProvider eventExecutorProvider;
  private ClusterManager clusterManager;
  private NodeSelector clusterNodeSelector;
  private VertxTracerFactory tracerFactory;
  private VertxTracer tracer;
  private VertxThreadFactory threadFactory;
  private ExecutorServiceFactory executorServiceFactory;
  private VertxMetricsFactory metricsFactory;
  private VertxMetrics metrics;
  private FileResolver fileResolver;

  public VertxBootstrapImpl(JsonObject config) {
    this(new VertxOptions(config));
    this.config = config;
  }

  public VertxBootstrapImpl(VertxOptions options) {
    this.options = options;
  }

  public VertxBootstrapImpl() {
    this(new VertxOptions());
  }

  public VertxOptions options() {
    return options;
  }

  @Override
  public VertxBootstrap options(VertxOptions options) {
    this.options = options;
    return this;
  }

  /**
   * @return the optional config when instantiated from the command line or {@code null}
   */
  public JsonObject config() {
    return config;
  }

  @Override
  public VertxBootstrap eventExecutorProvider(EventExecutorProvider provider) {
    this.eventExecutorProvider = provider;
    return this;
  }

  @Override
  public EventExecutorProvider eventExecutorProvider() {
    return eventExecutorProvider;
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
  public VertxBootstrapImpl transport(Transport transport) {
    this.transport = transport;
    return this;
  }

  public ClusterManager clusterManager() {
    return clusterManager;
  }

  public VertxBootstrapImpl clusterManager(ClusterManager clusterManager) {
    this.clusterManager = clusterManager;
    return this;
  }

  @Override
  public VertxMetricsFactory metricsFactory() {
    return metricsFactory;
  }

  public VertxBootstrapImpl metricsFactory(VertxMetricsFactory factory) {
    this.metricsFactory = factory;
    return this;
  }

  public NodeSelector clusterNodeSelector() {
    return clusterNodeSelector;
  }

  public VertxBootstrapImpl clusterNodeSelector(NodeSelector selector) {
    this.clusterNodeSelector = selector;
    return this;
  }

  @Override
  public VertxTracerFactory tracerFactory() {
    return tracerFactory;
  }

  public VertxBootstrapImpl tracerFactory(VertxTracerFactory factory) {
    this.tracerFactory = factory;
    return this;
  }

  public VertxTracer tracer() {
    return tracer;
  }

  public VertxBootstrapImpl tracer(VertxTracer tracer) {
    this.tracer = tracer;
    return this;
  }

  public VertxMetrics metrics() {
    return metrics;
  }

  public VertxBootstrapImpl metrics(VertxMetrics metrics) {
    this.metrics = metrics;
    return this;
  }

  /**
   * @return the {@code FileResolver} instance to use
   */
  public FileResolver fileResolver() {
    return fileResolver;
  }

  /**
   * Set the {@code FileResolver} instance to use.
   * @param resolver the file resolver
   * @return this builder instance
   */
  public VertxBootstrapImpl fileResolver(FileResolver resolver) {
    this.fileResolver = resolver;
    return this;
  }

  public VertxThreadFactory threadFactory() {
    return threadFactory;
  }

  public VertxBootstrapImpl threadFactory(VertxThreadFactory factory) {
    this.threadFactory = factory;
    return this;
  }

  public ExecutorServiceFactory executorServiceFactory() {
    return executorServiceFactory;
  }

  public VertxBootstrapImpl executorServiceFactory(ExecutorServiceFactory factory) {
    this.executorServiceFactory = factory;
    return this;
  }

  private VertxImpl instantiateVertx(ClusterManager clusterManager, NodeSelector nodeSelector) {
    checkBeforeInstantiating();
    Transport tr = transport;
    Throwable transportUnavailabilityCause = null;
    if (tr != null) {
      if (!tr.isAvailable()) {
        transportUnavailabilityCause = tr.unavailabilityCause();
        tr = NioTransport.INSTANCE;
      }
    } else {
      tr = NioTransport.INSTANCE;
    }
    return new VertxImpl(
      options,
      clusterManager,
      nodeSelector,
      metrics,
      tracer,
      tr,
      transportUnavailabilityCause,
      fileResolver,
      threadFactory,
      executorServiceFactory,
      eventExecutorProvider);
  }

  public Vertx vertx() {
    VertxImpl vertx = instantiateVertx(null, null);
    vertx.init();
    return vertx;
  }

  /**
   * Build and return the clustered vertx instance
   */
  public Future<Vertx> clusteredVertx() {
    if (clusterManager == null) {
      throw new IllegalStateException("No ClusterManagerFactory instances found on classpath");
    }
    NodeSelector nodeSelector = clusterNodeSelector;
    if (nodeSelector == null) {
      nodeSelector = new DefaultNodeSelector();
    }
    VertxImpl vertx = instantiateVertx(clusterManager, nodeSelector);
    return vertx.initClustered(options);
  }

  /**
   * Initialize the service providers.
   * @return this builder instance
   */
  public VertxBootstrapImpl init() {
    initTransport();
    initMetrics();
    initTracing();
    List<VertxServiceProvider> providers = ServiceHelper.loadFactories(VertxServiceProvider.class);
    initProviders(providers);
    initThreadFactory();
    initExecutorServiceFactory();
    initFileResolver();
    return this;
  }

  private void initProviders(Collection<VertxServiceProvider> providers) {
    for (VertxServiceProvider provider : providers) {
      if (provider instanceof VertxMetricsFactory && (options.getMetricsOptions() == null || !options.getMetricsOptions().isEnabled())) {
        continue;
      } else if (provider instanceof VertxTracerFactory && (options.getTracingOptions() == null)) {
        continue;
      }
      provider.init(this);
    }
  }

  private void initMetrics() {
    VertxMetricsFactory provider = metricsFactory;
    if (provider != null) {
      provider.init(this);
    }
  }

  private void initTracing() {
    VertxTracerFactory provider = tracerFactory;
    if (provider != null) {
      provider.init(this);
    }
  }

  private void initTransport() {
  }

  private void initFileResolver() {
    if (fileResolver != null) {
      return;
    }
    fileResolver = new FileResolverImpl(options.getFileSystemOptions());
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
