/*
 * Copyright (c) 2011-2013 The original author or authors
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

package io.vertx.core;

import io.vertx.core.datagram.DatagramSocket;
import io.vertx.core.datagram.DatagramSocketOptions;
import io.vertx.core.datagram.InternetProtocolFamily;
import io.vertx.core.dns.DnsClient;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.file.FileSystem;
import io.vertx.core.gen.GenIgnore;
import io.vertx.core.gen.VertxGen;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.net.NetClient;
import io.vertx.core.net.NetClientOptions;
import io.vertx.core.net.NetServer;
import io.vertx.core.net.NetServerOptions;
import io.vertx.core.shareddata.SharedData;

import java.util.Set;

/**
 * The control centre of the Vert.x Core API.<p>
 * You should normally only use a single instance of this class throughout your application. If you are running in the
 * Vert.x container an instance will be provided to you.<p>
 * If you are using Vert.x embedded, you can create an instance using one of the static {@code VertxFactory.newVertx}
 * methods.<p>
 * This class acts as a factory for TCP/SSL and HTTP/HTTPS servers and clients, SockJS servers, and provides an
 * instance of the event bus, file system and shared data classes, as well as methods for setting and cancelling
 * timers.<p>
 * Instances of this class are thread-safe.<p>
 *
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
@VertxGen
public interface Vertx {

  /**
   * Create a TCP/SSL server
   */
  NetServer createNetServer(NetServerOptions options);

  /**
   * Create a TCP/SSL client
   */
  NetClient createNetClient(NetClientOptions options);

  /**
   * Create an HTTP/HTTPS server
   */
  HttpServer createHttpServer(HttpServerOptions options);

  /**
   * Create a HTTP/HTTPS client
   */
  HttpClient createHttpClient(HttpClientOptions options);

  /**
   * Create a new {@link io.vertx.core.datagram.DatagramSocket}.
   *
   * @param family  use {@link InternetProtocolFamily} to use for multicast. If {@code null} is used it's up to the
   *                operation system to detect it's default.
   * @return socket the created {@link io.vertx.core.datagram.DatagramSocket}.
   */
  DatagramSocket createDatagramSocket(InternetProtocolFamily family, DatagramSocketOptions options);

  /**
   * The File system object
   */
  FileSystem fileSystem();

  /**
   * The event bus
   */
  EventBus eventBus();

  /**
   * Return the {@link DnsClient}
   */
  DnsClient createDnsClient(int port, String host);

  /**
   * The shared data object
   */
  SharedData sharedData();

  /**
   * Set a one-shot timer to fire after {@code delay} milliseconds, at which point {@code handler} will be called with
   * the id of the timer.
   * @return the unique ID of the timer
   */
  long setTimer(long delay, Handler<Long> handler);

  /**
   * Set a periodic timer to fire every {@code delay} milliseconds, at which point {@code handler} will be called with
   * the id of the timer.
   * @return the unique ID of the timer
   */
  long setPeriodic(long delay, Handler<Long> handler);

  /**
   * Cancel the timer with the specified {@code id}. Returns {@code} true if the timer was successfully cancelled, or
   * {@code false} if the timer does not exist.
   */
  boolean cancelTimer(long id);

  /**
   * @return The current context
   */
  Context currentContext();

  /**
   * Put the handler on the event queue for the current loop (or worker context) so it will be run asynchronously ASAP after this event has
   * been processed
   */
  void runOnContext(Handler<Void> action);

  /**
	 * Stop the eventbus and any resource managed by the eventbus.
	 */
	void close();

  /**
   * Stop the eventbus and any resource managed by the eventbus.
   */
  void close(Handler<AsyncResult<Void>> doneHandler);

  void deployVerticle(Verticle verticle);

  void deployVerticle(String verticleClass);

  void deployVerticle(Verticle verticle, Handler<AsyncResult<String>> doneHandler);

  void deployVerticle(String verticleClass, Handler<AsyncResult<String>> doneHandler);

  void deployVerticle(Verticle verticle, DeploymentOptions options);

  void deployVerticle(String verticleClass, DeploymentOptions options);

  void deployVerticle(Verticle verticle, DeploymentOptions options, Handler<AsyncResult<String>> doneHandler);

  void deployVerticle(String verticleClass, DeploymentOptions options, Handler<AsyncResult<String>> doneHandler);

  void undeployVerticle(String deploymentID, Handler<AsyncResult<Void>> doneHandler);

  @GenIgnore
  Set<String> deployments();

}
