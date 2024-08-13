/*
 * Copyright (c) 2011-2024 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package io.vertx.core.internal;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.core.impl.VertxBootstrapImpl;
import io.vertx.core.spi.ExecutorServiceFactory;
import io.vertx.core.spi.VertxMetricsFactory;
import io.vertx.core.spi.VertxThreadFactory;
import io.vertx.core.spi.VertxTracerFactory;
import io.vertx.core.spi.cluster.ClusterManager;
import io.vertx.core.spi.context.executor.EventExecutorProvider;
import io.vertx.core.spi.file.FileResolver;
import io.vertx.core.spi.transport.Transport;

/**
 * Vertx bootstrap for creating vertx instances with SPI overrides.
 *
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public interface VertxBootstrap {

  /**
   * @return a new fresh to use bootstrap
   */
  static VertxBootstrap create() {
    return new VertxBootstrapImpl();
  }

  /**
   * @return the vertx options
   */
  VertxOptions options();

  /**
   * Set the {@code options} to use.
   *
   * @param options the options instance
   * @return this builder instance
   */
  VertxBootstrap options(VertxOptions options);

  /**
   * Set an event executor {@code provider} to use.
   *
   * @param provider a provider to use
   * @return this builder instance
   */
  VertxBootstrap eventExecutorProvider(EventExecutorProvider provider);

  /**
   * @return the event executor provider to use
   */
  EventExecutorProvider eventExecutorProvider();

  /**
   * @return the {@code FileResolver} instance to use
   */
  FileResolver fileResolver();

  /**
   * Set the {@code FileResolver} instance to use.
   *
   * @param resolver the file resolver
   * @return this builder instance
   */
  VertxBootstrap fileResolver(FileResolver resolver);

  /**
   * @return the tracer factory instance to use
   */
  VertxTracerFactory tracerFactory();

  /**
   * Set the tracer factory to use.
   *
   * @param factory the factory
   * @return this builder instance
   */
  VertxBootstrap tracerFactory(VertxTracerFactory factory);

  /**
   * @return the metrics factory instance to use
   */
  VertxMetricsFactory metricsFactory();

  /**
   * Set the metrics factory instance to use.
   *
   * @param factory the factory
   * @return this builder instance
   */
  VertxBootstrap metricsFactory(VertxMetricsFactory factory);

  /**
   * @return the {@code ExecutorServiceFactory} to use
   */
  ExecutorServiceFactory executorServiceFactory();

  /**
   * Set the {@code ExecutorServiceFactory} instance to use.
   *
   * @param factory the factory
   * @return this builder instance
   */
  VertxBootstrap executorServiceFactory(ExecutorServiceFactory factory);

  /**
   * @return the {@code VertxThreadFactory} to use
   */
  VertxThreadFactory threadFactory();

  /**
   * Set the {@code VertxThreadFactory} instance to use.
   *
   * @param factory the metrics
   * @return this builder instance
   */
  VertxBootstrap threadFactory(VertxThreadFactory factory);

  /**
   * @return the transport to use
   */
  Transport transport();

  /**
   * Set the transport to for building Vertx.
   * @param transport the transport
   * @return this builder instance
   */
  VertxBootstrapImpl transport(Transport transport);

  /**
   * @return the cluster manager to use
   */
  ClusterManager clusterManager();

  /**
   * Set the cluster manager to use.
   *
   * @param clusterManager the cluster manager
   * @return this builder instance
   */
  VertxBootstrap clusterManager(ClusterManager clusterManager);

  /**
   * Initialize the service providers.
   *
   * @return this builder instance
   */
  VertxBootstrap init();

  /**
   * Build and return the vertx instance
   */
  Vertx vertx();

  /**
   * Build and return the clustered vertx instance
   */
  Future<Vertx> clusteredVertx();
}
