/*
 * Copyright (c) 2011-2017 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */

package io.vertx.core.spi.metrics;

import io.vertx.core.Verticle;
import io.vertx.core.datagram.DatagramSocket;
import io.vertx.core.datagram.DatagramSocketOptions;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.metrics.Measured;
import io.vertx.core.net.*;

/**
 * The main Vert.x metrics SPI which Vert.x will use internally. This interface serves two purposes, one
 * to be called by Vert.x itself for events like verticles deployed, timers created, etc. The other
 * to provide Vert.x with other metrics SPI's which will be used for specific components i.e.
 * {@link io.vertx.core.http.HttpServer}, {@link io.vertx.core.spi.metrics.EventBusMetrics}, etc.
 *
 * @author <a href="mailto:nscavell@redhat.com">Nick Scavelli</a>
 */
public interface VertxMetrics extends Metrics, Measured {

  /**
   * Called when a verticle is deployed in Vert.x .<p/>
   * <p>
   * This method is invoked with {@link io.vertx.core.Context} and thread of the deployed verticle and therefore
   * might be  different on every invocation.
   *
   * @param verticle the verticle which was deployed
   */
  default void verticleDeployed(Verticle verticle) {
  }

  /**
   * Called when a verticle is undeployed in Vert.x .<p/>
   * <p>
   * This method is invoked with {@link io.vertx.core.Context} and thread of the deployed verticle and therefore
   * might be  different on every invocation, however these are the same than the {@link #verticleDeployed} invocation.
   *
   * @param verticle the verticle which was undeployed
   */
  default void verticleUndeployed(Verticle verticle) {
  }

  /**
   * Called when a timer is created
   * <p>
   * No specific thread and context can be expected when this method is called.
   *
   * @param id the id of the timer
   */
  default void timerCreated(long id) {
  }

  /**
   * Called when a timer has ended (setTimer) or has been cancelled.<p/>
   * <p>
   * No specific thread and context can be expected when this method is called.
   *
   * @param id        the id of the timer
   * @param cancelled if the timer was cancelled by the user
   */
  default void timerEnded(long id, boolean cancelled) {
  }

  /**
   * Provides the event bus metrics SPI when the event bus is created.<p/>
   * <p>
   * No specific thread and context can be expected when this method is called.<p/>
   * <p>
   * This method should be called only once.
   *
   * @return the event bus metrics SPI or {@code null} when metrics are disabled
   */
  default EventBusMetrics createEventBusMetrics() {
    return null;
  }

  /**
   * Provides the http server metrics SPI when an http server is created.<p/>
   * <p>
   * No specific thread and context can be expected when this method is called.<p/>
   * <p>
   * Note: this method can be called more than one time for the same {@code localAddress} when a server is
   * scaled, it is the responsibility of the metrics implementation to eventually merge metrics. In this case
   * the provided {@code server} argument can be used to distinguish the different {@code HttpServerMetrics}
   * instances.
   *
   * @param options      the options used to create the {@link HttpServer}
   * @param localAddress localAddress the local address the net socket is listening on
   * @return the http server metrics SPI or {@code null} when metrics are disabled
   */
  default HttpServerMetrics<?, ?, ?> createHttpServerMetrics(HttpServerOptions options, SocketAddress localAddress) {
    return null;
  }

  /**
   * Provides the http client metrics SPI when an http client has been created.<p/>
   * <p>
   * No specific thread and context can be expected when this method is called.
   *
   * @param options the options used to create the {@link HttpClient}
   * @return the http client metrics SPI or {@code null} when metrics are disabled
   */
  default HttpClientMetrics<?, ?, ?, ?, ?> createHttpClientMetrics(HttpClientOptions options) {
    return null;
  }

  /**
   * Provides the net server metrics SPI when a net server is created.<p/>
   * <p>
   * No specific thread and context can be expected when this method is called.<p/>
   * <p>
   * Note: this method can be called more than one time for the same {@code localAddress} when a server is
   * scaled, it is the responsibility of the metrics implementation to eventually merge metrics. In this case
   * the provided {@code server} argument can be used to distinguish the different {@code TCPMetrics}
   * instances.
   *
   * @param options      the options used to create the {@link NetServer}
   * @param localAddress localAddress the local address the net socket is listening on
   * @return the net server metrics SPI or {@code null} when metrics are disabled
   */
  default TCPMetrics<?> createNetServerMetrics(NetServerOptions options, SocketAddress localAddress) {
    return null;
  }

  /**
   * Provides the net client metrics SPI when a net client is created.<p/>
   * <p>
   * No specific thread and context can be expected when this method is called.
   *
   * @param options the options used to create the {@link NetClient}
   * @return the net client metrics SPI or {@code null} when metrics are disabled
   */
  default TCPMetrics<?> createNetClientMetrics(NetClientOptions options) {
    return null;
  }

  /**
   * Provides the datagram/udp metrics SPI when a datagram socket is created.<p/>
   * <p>
   * No specific thread and context can be expected when this method is called.
   *
   * @param options the options used to create the {@link DatagramSocket}
   * @return the datagram metrics SPI or {@code null} when metrics are disabled
   */
  default DatagramSocketMetrics createDatagramSocketMetrics(DatagramSocketOptions options) {
    return null;
  }

  /**
   * Provides the pool metrics SPI.
   *
   * @param poolType the type of the pool e.g worker, datasource, etc..
   * @param poolName the name of the pool
   * @param maxPoolSize the pool max size, or -1 if the number cannot be determined
   * @return the thread pool metrics SPI or {@code null} when metrics are disabled
   */
  default PoolMetrics<?> createPoolMetrics(String poolType, String poolName, int maxPoolSize) {
    return null;
  }
}
