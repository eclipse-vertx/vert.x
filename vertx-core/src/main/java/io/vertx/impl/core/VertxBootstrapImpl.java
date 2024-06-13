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

package io.vertx.impl.core;

import io.vertx.core.*;
import io.vertx.impl.core.transports.EpollTransport;
import io.vertx.impl.core.transports.JDKTransport;
import io.vertx.impl.core.transports.KQueueTransport;
import io.vertx.core.spi.*;
import io.vertx.core.spi.file.FileResolver;
import io.vertx.impl.core.file.FileResolverImpl;
import io.vertx.core.impl.logging.Logger;
import io.vertx.core.impl.logging.LoggerFactory;
import io.vertx.core.json.JsonObject;
import io.vertx.core.spi.transport.Transport;
import io.vertx.core.spi.cluster.ClusterManager;
import io.vertx.core.spi.cluster.NodeSelector;
import io.vertx.impl.core.spi.ExecutorServiceFactory;
import io.vertx.impl.core.spi.VertxThreadFactory;
import io.vertx.impl.core.cluster.DefaultNodeSelector;
import io.vertx.core.spi.metrics.VertxMetrics;
import io.vertx.core.spi.tracing.VertxTracer;

import java.util.Collection;
import java.util.Iterator;
import java.util.List;

/**
 * Vertx builder for creating vertx instances with overrides.
 *
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class VertxBootstrapImpl implements VertxBootstrap {

  private static final Logger log = LoggerFactory.getLogger(io.vertx.core.spi.VertxBootstrap.class);

  private VertxOptions options = new VertxOptions();
  private JsonObject config;
  private Transport transport;
  private ClusterManager clusterManager;
  private NodeSelector clusterNodeSelector;
  private VertxTracerFactory tracerFactory;
  private VertxTracer<?, ?> tracer;
  private VertxThreadFactory threadFactory;
  private ExecutorServiceFactory executorServiceFactory;
  private VertxMetricsFactory metricsFactory;
  private VertxMetrics metrics;
  private FileResolver fileResolver;

  /**
   * @return the vertx options
   */
  public VertxOptions options() {
    return options;
  }

  @Override
  public VertxBootstrapImpl options(VertxOptions options) {
    this.options = options;
    return this;
  }

  public JsonObject config() {
    return config;
  }

  public Transport transport() {
    return transport;
  }

  public VertxBootstrapImpl transport(Transport transport) {
    this.transport = transport;
    return this;
  }

  public ClusterManager clusterManager() {
    return clusterManager;
  }

  public VertxBootstrapImpl clusterManager(ClusterManager manager) {
    this.clusterManager = manager;
    return this;
  }

  public VertxBootstrapImpl metricsFactory(VertxMetricsFactory factory) {
    this.metricsFactory = factory;
    return this;
  }

  @Override
  public VertxMetricsFactory metricsFactory() {
    return metricsFactory;
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
  public VertxBootstrapImpl clusterNodeSelector(NodeSelector selector) {
    this.clusterNodeSelector = selector;
    return this;
  }

  public VertxBootstrapImpl tracerFactory(VertxTracerFactory factory) {
    this.tracerFactory = factory;
    return this;
  }

  @Override
  public VertxTracerFactory tracerFactory() {
    return tracerFactory;
  }

  public VertxTracer<?, ?> tracer() {
    return tracer;
  }

  public VertxBootstrapImpl tracer(VertxTracer<?, ?> tracer) {
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

  public FileResolver fileResolver() {
    return fileResolver;
  }

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
  public Future<Vertx> clusteredVertx() {
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
    return vertx.initClustered(options);
  }

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
    if (transport != null) {
      return;
    }
    transport = transport(options.getPreferNativeTransport());
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

  /**
   * The native transport, it may be {@code null} or failed.
   */
  public static Transport nativeTransport() {
    Transport transport = null;
    try {
      Transport epoll = new EpollTransport();
      if (epoll.isAvailable()) {
        return epoll;
      } else {
        transport = epoll;
      }
    } catch (Throwable ignore) {
      // Jar not here
    }
    try {
      Transport kqueue = new KQueueTransport();
      if (kqueue.isAvailable()) {
        return kqueue;
      } else if (transport == null) {
        transport = kqueue;
      }
    } catch (Throwable ignore) {
      // Jar not here
    }
    return transport;
  }

  static Transport transport(boolean preferNative) {
    if (preferNative) {
      Collection<Transport> transports = ServiceHelper.loadFactories(Transport.class);
      Iterator<Transport> it = transports.iterator();
      while (it.hasNext()) {
        Transport transport = it.next();
        if (transport.isAvailable()) {
          return transport;
        }
      }
      Transport nativeTransport = nativeTransport();
      if (nativeTransport != null && nativeTransport.isAvailable()) {
        return nativeTransport;
      } else {
        return JDKTransport.INSTANCE;
      }
    } else {
      return JDKTransport.INSTANCE;
    }
  }
}
