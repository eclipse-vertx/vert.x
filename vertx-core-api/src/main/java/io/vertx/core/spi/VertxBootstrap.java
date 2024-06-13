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

package io.vertx.core.spi;

import io.vertx.core.*;
import io.vertx.core.spi.file.FileResolver;
import io.vertx.core.json.JsonObject;
import io.vertx.core.spi.transport.Transport;
import io.vertx.core.spi.cluster.ClusterManager;
import io.vertx.core.spi.cluster.NodeSelector;
import io.vertx.core.spi.metrics.VertxMetrics;
import io.vertx.core.spi.tracing.VertxTracer;

/**
 * Vertx bootstrap for creating vertx instances with SPI overrides.
 *
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public interface VertxBootstrap {

  /**
   * @return create and return a new bootstrap
   */
  static VertxBootstrap bootstrap() {
    return ServiceHelper.loadFactory(VertxBootstrap.class);
  }

  /**
   * Set the vertx {@code options} to use.
   * @param options the options
   * @return a reference to this, so the API can be used fluently
   */
  VertxBootstrap options(VertxOptions options);

  /**
   * @return the vertx options to use
   */
  VertxOptions options();

  /**
   * Set the {@code transport} to use.
   * @param transport the transport
   * @return a reference to this, so the API can be used fluently
   */
  VertxBootstrap transport(Transport transport);

  /**
   * @return the transport to use
   */
  Transport transport();

  /**
   * Set the cluster {@code manager} to use.
   * @param manager the cluster manager
   * @return a reference to this, so the API can be used fluently
   */
  VertxBootstrap clusterManager(ClusterManager manager);

  /**
   * @return the cluster manager to use
   */
  ClusterManager clusterManager();

  /**
   * Set the metrics {@code factory} to use.
   * @param factory the metrics factory
   * @return a reference to this, so the API can be used fluently
   */
  VertxBootstrap metricsFactory(VertxMetricsFactory factory);

  /**
   * @return the metrics factory to use
   */
  VertxMetricsFactory metricsFactory();

  /**
   * Set the {@code metrics} to use.
   * @param metrics the metrics
   * @return a reference to this, so the API can be used fluently
   */
  VertxBootstrap metrics(VertxMetrics metrics);

  /**
   * @return the metrics to use
   */
  VertxMetrics metrics();

  /**
   * Set the cluster node {@code selector} to use.
   * @param selector the selector
   * @return a reference to this, so the API can be used fluently
   */
  VertxBootstrap clusterNodeSelector(NodeSelector selector);

  /**
   * @return the node selector to use
   */
  NodeSelector clusterNodeSelector();

  /**
   * Set the tracer {@code factory} to use.
   * @param factory the tracer factory
   * @return a reference to this, so the API can be used fluently
   */
  VertxBootstrap tracerFactory(VertxTracerFactory factory);

  /**
   * @return the tracer factory to use
   */
  VertxTracerFactory tracerFactory();

  /**
   * Set the {@code tracer} to use.
   * @param tracer the tracer
   * @return a reference to this, so the API can be used fluently
   */
  VertxBootstrap tracer(VertxTracer<?, ?> tracer);

  /**
   * @return the tracer to use
   */
  VertxTracer<?, ?> tracer();

  /**
   * Set the {@code resolver} to use.
   * @param resolver the file resolver
   * @return a reference to this, so the API can be used fluently
   */
  VertxBootstrap fileResolver(FileResolver resolver);

  /**
   * @return the file resolver instance to use
   */
  FileResolver fileResolver();

  /**
   * Build and return the vertx instance
   * @return the vertx instance
   */
  Vertx vertx();

  /**
   * Build and return the clustered vertx instance
   * @return a future notified with the clustered vertx instance
   */
  Future<Vertx> clusteredVertx();

  /**
   * Initialize the service providers.
   * @return a reference to this, so the API can be used fluently
   */
  VertxBootstrap init();

}
