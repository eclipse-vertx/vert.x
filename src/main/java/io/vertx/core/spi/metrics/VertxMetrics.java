/*
 * Copyright (c) 2011-2014 The original author or authors
 * ------------------------------------------------------
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * and Apache License v2.0 which accompanies this distribution.
 *
 *     The Eclipse Public License is available at
 *     http://www.eclipse.org/legal/epl-v10.html
 *
 *     The Apache License v2.0 is available at
 *     http://www.opensource.org/licenses/apache2.0.php
 *
 * You may elect to redistribute this code under either of these licenses.
 */

package io.vertx.core.spi.metrics;

import io.vertx.core.Verticle;
import io.vertx.core.datagram.DatagramSocket;
import io.vertx.core.datagram.DatagramSocketOptions;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.metrics.Measured;
import io.vertx.core.net.NetClient;
import io.vertx.core.net.NetClientOptions;
import io.vertx.core.net.NetServer;
import io.vertx.core.net.NetServerOptions;
import io.vertx.core.net.SocketAddress;

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
   *
   * This method is invoked with {@link io.vertx.core.Context} and thread of the deployed verticle and therefore
   * might be  different on every invocation.
   *
   * @param verticle the verticle which was deployed
   */
  void verticleDeployed(Verticle verticle);

  /**
   * Called when a verticle is undeployed in Vert.x .<p/>
   *
   * This method is invoked with {@link io.vertx.core.Context} and thread of the deployed verticle and therefore
   * might be  different on every invocation, however these are the same than the {@link #verticleDeployed} invocation.
   *
   * @param verticle the verticle which was undeployed
   */
  void verticleUndeployed(Verticle verticle);

  /**
   * Called when a timer is created
   *
   * No specific thread and context can be expected when this method is called.
   *
   * @param id the id of the timer
   */
  void timerCreated(long id);

  /**
   * Called when a timer has ended (setTimer) or has been cancelled.<p/>
   *
   * No specific thread and context can be expected when this method is called.
   *
   * @param id the id of the timer
   * @param cancelled if the timer was cancelled by the user
   */
  void timerEnded(long id, boolean cancelled);

  /**
   * Provides the event bus metrics SPI when the event bus is created.<p/>
   *
   * No specific thread and context can be expected when this method is called.<p/>
   *
   * This method should be called only once.
   *
   * @param eventBus the Vert.x event bus
   * @return the event bus metrics SPI
   */
  EventBusMetrics createMetrics(EventBus eventBus);

  /**
   * Provides the http server metrics SPI when an http server is created.<p/>
   *
   * No specific thread and context can be expected when this method is called.<p/>
   *
   * Note: this method can be called more than one time for the same {@code localAddress} when a server is
   * scaled, it is the responsibility of the metrics implementation to eventually merge metrics.
   *
   * @param server the Vert.x http server
   * @param localAddress localAddress the local address the net socket is listening on
   * @param options the options used to create the {@link io.vertx.core.http.HttpServer}
   * @return the http server metrics SPI
   */
  HttpServerMetrics<?, ?, ?> createMetrics(HttpServer server, SocketAddress localAddress, HttpServerOptions options);

  /**
   * Provides the http client metrics SPI when an http client has been created.<p/>
   *
   * No specific thread and context can be expected when this method is called.
   *
   * @param client the Vert.x http client
   * @param options the options used to create the {@link io.vertx.core.http.HttpClient}
   * @return the http client metrics SPI
   */
  HttpClientMetrics<?, ?, ?> createMetrics(HttpClient client, HttpClientOptions options);

  /**
   * Provides the net server metrics SPI when a net server is created.<p/>
   *
   * No specific thread and context can be expected when this method is called.<p/>
   *
   * Note: this method can be called more than one time for the same {@code localAddress} when a server is
   * scaled, it is the responsibility of the metrics implementation to eventually merge metrics.
   * 
   * @param server the Vert.x net server
   * @param localAddress localAddress the local address the net socket is listening on
   * @param options the options used to create the {@link io.vertx.core.net.NetServer}
   * @return the net server metrics SPI
   */
  TCPMetrics<?> createMetrics(NetServer server, SocketAddress localAddress, NetServerOptions options);

  /**
   * Provides the net client metrics SPI when a net client is created.<p/>
   *
   * No specific thread and context can be expected when this method is called.
   *
   * @param client the Vert.x net client
   * @param options the options used to create the {@link io.vertx.core.net.NetClient}
   * @return the net client metrics SPI
   */
  TCPMetrics<?> createMetrics(NetClient client, NetClientOptions options);

  /**
   * Provides the datagram/udp metrics SPI when a datagram socket is created.<p/>
   *
   * No specific thread and context can be expected when this method is called.
   *
   * @param socket the Vert.x datagram socket
   * @param options the options used to create the {@link io.vertx.core.datagram.DatagramSocket}
   * @return the datagram metrics SPI
   */
  DatagramSocketMetrics createMetrics(DatagramSocket socket, DatagramSocketOptions options);
}
