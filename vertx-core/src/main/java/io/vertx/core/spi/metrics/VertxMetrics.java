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

package io.vertx.core.spi.metrics;

import io.vertx.core.Vertx;
import io.vertx.core.datagram.DatagramSocket;
import io.vertx.core.datagram.DatagramSocketOptions;
import io.vertx.core.http.*;
import io.vertx.core.metrics.Measured;
import io.vertx.core.net.*;
import io.vertx.core.net.QuicEndpointConfig;

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
   * Version of {@link #createHttpServerMetrics(HttpServerOptions, SocketAddress)} for HTTP/3
   */
  default HttpServerMetrics<?, ?, ?> createHttpServerMetrics(Http3ServerConfig config, SocketAddress localAddress) {
    return null;
  }

  /**
   * Provides the client metrics SPI when a client has been created.<p/>
   * <p>
   * No specific thread and context can be expected when this method is called.
   *
   * @param remoteAddress the server remote address
   * @param type the metrics type, e.g {@code http} or {@code ws}
   * @param namespace an optional namespace for scoping the metrics
   * @return the client metrics SPI or {@code null} when metrics are disabled
   */
  default ClientMetrics<?, ?, ?> createClientMetrics(SocketAddress remoteAddress, String type, String namespace) {
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
  default HttpClientMetrics<?, ?, ?> createHttpClientMetrics(HttpClientOptions options) {
    return null;
  }

  /**
   * Version of {@link #createHttpClientMetrics(HttpClientOptions)} for HTTP/3
   */
  default HttpClientMetrics<?, ?, ?> createHttpClientMetrics(HttpClientConfig config) {
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
    default TransportMetrics<?> createNetServerMetrics(NetServerOptions options, SocketAddress localAddress) {
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
  default TransportMetrics<?> createNetClientMetrics(NetClientOptions options) {
    return null;
  }

  /**
   * <p>Provides the quic endpoint metrics SPI when a quic endpoint is created.</p>
   * <p><Note: this method can be called more than one time for the same {@code localAddress} when a server is
   * scaled, it is the responsibility of the metrics implementation to eventually merge metrics. In this case
   * the provided {@code server} argument can be used to distinguish the different metrics instances.</p>
   *
   * @param config      the config used to create the {@link NetServer}
   * @param localAddress localAddress the local address the net socket is listening on
   * @return the net server metrics SPI or {@code null} when metrics are disabled
   */
  default TransportMetrics<?> createQuicEndpointMetrics(QuicEndpointConfig config, SocketAddress localAddress) {
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
   * @param type the type of the pool e.g. worker, datasource, etc...
   * @param name the name of the resource the inherent pool belongs to
   * @param maxSize the max size, or {@code -1} if the number cannot be determined
   * @return the pool metrics SPI or {@code null} when metrics are disabled
   */
  default PoolMetrics<?, ?> createPoolMetrics(String type, String name, int maxSize) {
    return null;
  }

  /**
   * Callback to signal when the Vertx instance is fully initialized. Other methods can be called before this method
   * when the instance is being constructed.
   *
   * @param vertx the instance of Vertx
   */
  default void vertxCreated(Vertx vertx) {
  }
}
