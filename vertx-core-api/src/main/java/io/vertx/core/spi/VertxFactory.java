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
 * Vertx builder for creating vertx instances with SPI overrides.
 *
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public interface VertxFactory {

  static VertxFactory factory() {
    return ServiceHelper.loadFactory(VertxFactory.class);
  }

  VertxFactory options(VertxOptions options);

  /**
   * @return the vertx options
   */
  VertxOptions options();

  /**
   * @return the optional config when instantiated from the command line or {@code null}
   */
  JsonObject config();

  /**
   * @return the transport to use
   */
  Transport findTransport();

  /**
   * Set the transport to for building Vertx.
   * @param transport the transport
   * @return this builder instance
   */
  VertxFactory findTransport(Transport transport);

  /**
   * @return the cluster manager to use
   */
  ClusterManager clusterManager();

  /**
   * Set the cluster manager to use.
   * @param clusterManager the cluster manager
   * @return this builder instance
   */
  VertxFactory clusterManager(ClusterManager clusterManager);

  VertxFactory metricsFactory(VertxMetricsFactory factory);

  /**
   * @return the node selector to use
   */
  NodeSelector clusterNodeSelector();

  /**
   * Set the cluster node selector to use.
   * @param selector the selector
   * @return this builder instance
   */
  VertxFactory clusterNodeSelector(NodeSelector selector);

  VertxFactory tracerFactory(VertxTracerFactory factory);

  /**
   * @return the tracer instance to use
   */
  VertxTracer tracer();

  /**
   * Set the tracer to use.
   * @param tracer the tracer
   * @return this builder instance
   */
  VertxFactory tracer(VertxTracer tracer);

  /**
   * @return the metrics instance to use
   */
  VertxMetrics metrics();

  /**
   * Set the metrics instance to use.
   * @param metrics the metrics
   * @return this builder instance
   */
  VertxFactory metrics(VertxMetrics metrics);

  /**
   * @return the {@code FileResolver} instance to use
   */
  FileResolver fileResolver();

  /**
   * Set the {@code FileResolver} instance to use.
   * @param resolver the file resolver
   * @return this builder instance
   */
  VertxFactory fileResolver(FileResolver resolver);

  /**
   * Build and return the vertx instance
   */
  Vertx vertx();

  /**
   * Build and return the clustered vertx instance
   */
  Future<Vertx> clusteredVertx();

  /**
   * Initialize the service providers.
   * @return this builder instance
   */
  VertxFactory init();

}
