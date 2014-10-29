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

import io.vertx.codegen.annotations.CacheReturn;
import io.vertx.codegen.annotations.GenIgnore;
import io.vertx.codegen.annotations.VertxGen;
import io.vertx.core.datagram.DatagramSocket;
import io.vertx.core.datagram.DatagramSocketOptions;
import io.vertx.core.dns.DnsClient;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.file.FileSystem;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.metrics.Measured;
import io.vertx.core.net.NetClient;
import io.vertx.core.net.NetClientOptions;
import io.vertx.core.net.NetServer;
import io.vertx.core.net.NetServerOptions;
import io.vertx.core.shareddata.SharedData;
import io.vertx.core.spi.VerticleFactory;
import io.vertx.core.spi.VertxFactory;
import io.vertx.core.streams.ReadStream;

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
public interface Vertx extends Measured {

  static Vertx vertx() {
    return factory.vertx();
  }

  static Vertx vertx(VertxOptions options) {
    return factory.vertx(options);
  }

  static void vertxAsync(VertxOptions options, Handler<AsyncResult<Vertx>> resultHandler) {
    factory.vertxAsync(options, resultHandler);
  }

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

  DatagramSocket createDatagramSocket(DatagramSocketOptions options);

  /**
   * The File system object
   */
  @CacheReturn
  FileSystem fileSystem();

  /**
   * The event bus
   */
  @CacheReturn
  EventBus eventBus();

  /**
   * Return the {@link DnsClient}
   */
  DnsClient createDnsClient(int port, String host);

  /**
   * The shared data object
   */
  @CacheReturn
  SharedData sharedData();

  /**
   * Set a one-shot timer to fire after {@code delay} milliseconds, at which point {@code handler} will be called with
   * the id of the timer.
   *
   * @return the unique ID of the timer
   */
  long setTimer(long delay, Handler<Long> handler);

  /**
   * Returns a one-shot timer as a read stream. The timer will be fired after {@code delay} milliseconds after
   * the {@link ReadStream#handler} has been called.
   *
   * @return the timer stream
   */
  TimeoutStream timerStream(long delay);

  /**
   * Set a periodic timer to fire every {@code delay} milliseconds, at which point {@code handler} will be called with
   * the id of the timer.
   * @return the unique ID of the timer
   */
  long setPeriodic(long delay, Handler<Long> handler);

  /**
   * Returns a periodic timer as a read stream. The timer will be fired every {@code delay} milliseconds after
   * the {@link ReadStream#handler} has been called.
   *
   * @return the periodic stream
   */
  TimeoutStream periodicStream(long delay);

  /**
   * Cancel the timer with the specified {@code id}. Returns {@code} true if the timer was successfully cancelled, or
   * {@code false} if the timer does not exist.
   */
  boolean cancelTimer(long id);

  /**
   * @return The current context
   */
  Context context();

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
  void close(Handler<AsyncResult<Void>> completionHandler);

  @GenIgnore
  void deployVerticle(Verticle verticle);

  @GenIgnore
  void deployVerticle(Verticle verticle, Handler<AsyncResult<String>> completionHandler);

  @GenIgnore
  void deployVerticle(Verticle verticle, DeploymentOptions options);

  @GenIgnore
  void deployVerticle(Verticle verticle, DeploymentOptions options, Handler<AsyncResult<String>> completionHandler);



  void deployVerticle(String identifier);

  void deployVerticle(String identifier, Handler<AsyncResult<String>> completionHandler);

  void deployVerticle(String identifier, DeploymentOptions options);

  void deployVerticle(String identifier, DeploymentOptions options, Handler<AsyncResult<String>> completionHandler);

  void undeployVerticle(String deploymentID);

  void undeployVerticle(String deploymentID, Handler<AsyncResult<Void>> completionHandler);

  Set<String> deployments();

  @GenIgnore
  void registerVerticleFactory(VerticleFactory factory);

  @GenIgnore
  void unregisterVerticleFactory(VerticleFactory factory);

  @GenIgnore
  Set<VerticleFactory> verticleFactories();

  static final VertxFactory factory = ServiceHelper.loadFactory(VertxFactory.class);

}
