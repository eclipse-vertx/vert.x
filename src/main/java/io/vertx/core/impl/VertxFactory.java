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
import io.vertx.core.net.impl.transport.Transport;
import io.vertx.core.spi.VertxMetricsFactory;
import io.vertx.core.spi.VertxTracerFactory;
import io.vertx.core.spi.cluster.ClusterManager;
import io.vertx.core.spi.cluster.NodeSelector;
import io.vertx.core.spi.cluster.impl.DefaultNodeSelector;
import io.vertx.core.spi.metrics.VertxMetrics;
import io.vertx.core.spi.tracing.VertxTracer;

import java.util.Collection;
import java.util.Objects;

/**
 * Internal factory for creating vertx instances with SPI services overrides.
 *
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class VertxFactory {

  private VertxOptions options;
  private Transport transport;
  private ClusterManager clusterManager;
  private NodeSelector clusterNodeSelector;
  private VertxTracer tracer;
  private VertxMetrics metrics;
  private FileResolver fileResolver;

  public VertxFactory(VertxOptions options) {
    this.options = options;
    this.clusterManager = options.getClusterManager();
  }

  public VertxFactory() {
    this(new VertxOptions());
  }

  public VertxFactory transport(Transport transport) {
    this.transport = transport;
    return this;
  }

  public VertxFactory clusterManager(ClusterManager clusterManager) {
    this.clusterManager = clusterManager;
    return this;
  }

  public VertxFactory clusterNodeSelector(NodeSelector clusterNodeSelector) {
    this.clusterNodeSelector = clusterNodeSelector;
    return this;
  }

  public VertxFactory tracer(VertxTracer tracer) {
    this.tracer = tracer;
    return this;
  }

  public VertxFactory metrics(VertxMetrics metrics) {
    this.metrics = metrics;
    return this;
  }

  public Vertx vertx() {
    VertxImpl vertx = new VertxImpl(options, null, null, createMetrics(), createTracer(), createTransport(), createFileResolver());
    vertx.init();
    return vertx;
  }

  public void clusteredVertx(Handler<AsyncResult<Vertx>> handler) {
    VertxImpl vertx = new VertxImpl(options, createClusterManager(), createNodeSelector(), createMetrics(), createTracer(), createTransport(), createFileResolver());
    vertx.initClustered(options, handler);
  }

  private ClusterManager createClusterManager() {
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
      } else {
        clusterManager = ServiceHelper.loadFactoryOrNull(ClusterManager.class);
        if (clusterManager == null) {
          throw new IllegalStateException("No ClusterManagerFactory instances found on classpath");
        }
      }
    }
    return clusterManager;
  }

  private NodeSelector createNodeSelector() {
    if (clusterNodeSelector == null) {
      Collection<NodeSelector> selectors = ServiceHelper.loadFactories(NodeSelector.class);
      clusterNodeSelector = !selectors.isEmpty() ? selectors.iterator().next() : new DefaultNodeSelector();
    }
    return clusterNodeSelector;
  }

  private Transport createTransport() {
    if (transport == null) {
      transport = Transport.transport(options.getPreferNativeTransport());
    }
    return transport;
  }

  private VertxMetrics createMetrics() {
    if (metrics == null) {
      if (options.getMetricsOptions() != null && options.getMetricsOptions().isEnabled()) {
        VertxMetricsFactory factory = options.getMetricsOptions().getFactory();
        if (factory == null) {
          factory = ServiceHelper.loadFactoryOrNull(VertxMetricsFactory.class);
          if (factory == null) {
            // log.warn("Metrics has been set to enabled but no VertxMetricsFactory found on classpath");
          }
        }
        if (factory != null) {
          metrics = factory.metrics(options);
          Objects.requireNonNull(metrics, "The metric instance created from " + factory + " cannot be null");
        }
      }
    }
    return metrics;
  }

  private VertxTracer createTracer() {
    if (tracer == null) {
      if (options.getTracingOptions() != null) {
        VertxTracerFactory factory = options.getTracingOptions().getFactory();
        if (factory == null) {
          factory = ServiceHelper.loadFactoryOrNull(VertxTracerFactory.class);
          if (factory == null) {
            // log.warn("Metrics has been set to enabled but no TracerFactory found on classpath");
          }
        }
        if (factory != null) {
          tracer = factory.tracer(options.getTracingOptions());
          Objects.requireNonNull(tracer, "The tracer instance created from " + factory + " cannot be null");
        }
      }
    }
    return tracer;
  }

  private FileResolver createFileResolver() {
    if (fileResolver == null) {
      fileResolver = new FileResolver(options.getFileSystemOptions());
    }
    return fileResolver;
  }
}
