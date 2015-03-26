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

import io.netty.channel.EventLoopGroup;
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
 * The entry point into the Vert.x Core API.
 * <p>
 * You use an instance of this class for functionality including:
 * <ul>
 *   <li>Creating TCP clients and servers</li>
 *   <li>Creating HTTP clients and servers</li>
 *   <li>Creating DNS clients</li>
 *   <li>Creating Datagram sockets</li>
 *   <li>Setting and cancelling periodic and one-shot timers</li>
 *   <li>Getting a reference to the event bus API</li>
 *   <li>Getting a reference to the file system API</li>
 *   <li>Getting a reference to the shared data API</li>
 *   <li>Deploying and undeploying verticles</li>
 * </ul>
 * <p>
 * Most functionality in Vert.x core is fairly low level.
 * <p>
 * To create an instance of this class you can use the static factory methods: {@link #vertx},
 * {@link #vertx(io.vertx.core.VertxOptions)} and {@link #clusteredVertx(io.vertx.core.VertxOptions, Handler)}.
 * <p>
 * Please see the user manual for more detailed usage information.
 *
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
@VertxGen
public interface Vertx extends Measured {

  /**
   * Creates a non clustered instance using default options.
   *
   * @return the instance
   */
  static Vertx vertx() {
    return factory.vertx();
  }

  /**
   * Creates a non clustered instance using the specified options
   *
   * @param options  the options to use
   * @return the instance
   */
  static Vertx vertx(VertxOptions options) {
    return factory.vertx(options);
  }

  /**
   * Creates a clustered instance using the specified options.
   * <p>
   * The instance is created asynchronously and the resultHandler is called with the result when it is ready.
   *
   * @param options  the options to use
   * @param resultHandler  the result handler that will receive the result
   */
  static void clusteredVertx(VertxOptions options, Handler<AsyncResult<Vertx>> resultHandler) {
    factory.clusteredVertx(options, resultHandler);
  }

  /**
   * Gets the current context
   *
   * @return The current context or null if no current context
   */
  static Context currentContext() {
    return factory.context();
  }

  /**
   * Gets the current context, or creates one if there isn't one
   *
   * @return The current context (created if didn't exist)
   */
  Context getOrCreateContext();

  /**
   * Create a TCP/SSL server using the specified options
   *
   * @param options  the options to use
   * @return the server
   */
  NetServer createNetServer(NetServerOptions options);

  /**
   * Create a TCP/SSL server using default options
   *
   * @return the server
   */
  NetServer createNetServer();

  /**
   * Create a TCP/SSL client using the specified options
   *
   * @param options  the options to use
   * @return the client
   */
  NetClient createNetClient(NetClientOptions options);

  /**
   * Create a TCP/SSL client using default options
   *
   * @return the client
   */
  NetClient createNetClient();

  /**
   * Create an HTTP/HTTPS server using the specified options
   *
   * @param options  the options to use
   * @return the server
   */
  HttpServer createHttpServer(HttpServerOptions options);

  /**
   * Create an HTTP/HTTPS server using default options
   *
   * @return the server
   */
  HttpServer createHttpServer();

  /**
   * Create a HTTP/HTTPS client using the specified options
   *
   * @param options  the options to use
   * @return the client
   */
  HttpClient createHttpClient(HttpClientOptions options);

  /**
   * Create a HTTP/HTTPS client using default options
   *
   * @return the client
   */
  HttpClient createHttpClient();

  /**
   * Create a datagram socket using the specified options
   *
   * @param options  the options to use
   * @return the socket
   */
  DatagramSocket createDatagramSocket(DatagramSocketOptions options);

  /**
   * Create a datagram socket using default options
   *
   * @return the socket
   */
  DatagramSocket createDatagramSocket();

  /**
   * Get the filesystem object. There is a single instance of FileSystem per Vertx instance.
   *
   * @return the filesystem object
   */
  @CacheReturn
  FileSystem fileSystem();

  /**
   * Get the event bus object. There is a single instance of EventBus per Vertx instance.
   *
   * @return the event bus object
   */
  @CacheReturn
  EventBus eventBus();

  /**
   * Create a DNS client to connect to a DNS server at the specified host and port
   *
   * @param port  the port
   * @param host  the host
   * @return the DNS client
   */
  DnsClient createDnsClient(int port, String host);

  /**
   * Get the shared data object. There is a single instance of SharedData per Vertx instance.
   *
   * @return the shared data object
   */
  @CacheReturn
  SharedData sharedData();

  /**
   * Set a one-shot timer to fire after {@code delay} milliseconds, at which point {@code handler} will be called with
   * the id of the timer.
   *
   * @param delay  the delay in milliseconds, after which the timer will fire
   * @param handler  the handler that will be called with the timer ID when the timer fires
   * @return the unique ID of the timer
   */
  long setTimer(long delay, Handler<Long> handler);

  /**
   * Returns a one-shot timer as a read stream. The timer will be fired after {@code delay} milliseconds after
   * the {@link ReadStream#handler} has been called.
   *
   * @param delay  the delay in milliseconds, after which the timer will fire
   * @return the timer stream
   */
  TimeoutStream timerStream(long delay);

  /**
   * Set a periodic timer to fire every {@code delay} milliseconds, at which point {@code handler} will be called with
   * the id of the timer.
   *
   *
   * @param delay  the delay in milliseconds, after which the timer will fire
   * @param handler  the handler that will be called with the timer ID when the timer fires
   * @return the unique ID of the timer
   */
  long setPeriodic(long delay, Handler<Long> handler);

  /**
   * Returns a periodic timer as a read stream. The timer will be fired every {@code delay} milliseconds after
   * the {@link ReadStream#handler} has been called.
   *
   * @param delay  the delay in milliseconds, after which the timer will fire
   * @return the periodic stream
   */
  TimeoutStream periodicStream(long delay);

  /**
   * Cancels the timer with the specified {@code id}.
   *
   * @param id  The id of the timer to cancel
   * @return true if the timer was successfully cancelled, or false if the timer does not exist.
   */
  boolean cancelTimer(long id);

  /**
   * Puts the handler on the event queue for the current context so it will be run asynchronously ASAP after all
   * preceeding events have been handled.
   *
   * @param action - a handler representing the action to execute
   */
  void runOnContext(Handler<Void> action);

  /**
	 * Stop the the Vertx instance and release any resources held by it.
   * <p>
   * The instance cannot be used after it has been closed.
   * <p>
   * The actual close is asynchronous and may not complete until after the call has returned.
	 */
	void close();

  /**
   * Like {@link #close} but the completionHandler will be called when the close is complete
   *
   * @param completionHandler  The handler will be notified when the close is complete.
   */
  void close(Handler<AsyncResult<Void>> completionHandler);

  /**
   * Deploy a verticle instance that you have created yourself.
   * <p>
   * Vert.x will assign the verticle a context and start the verticle.
   * <p>
   * The actual deploy happens asynchronously and may not complete until after the call has returned.
   *
   * @param verticle  the verticle instance to deploy.
   */
  @GenIgnore
  void deployVerticle(Verticle verticle);

  /**
   * Like {@link #deployVerticle(Verticle)} but the completionHandler will be notified when the deployment is complete.
   * <p>
   * If the deployment is successful the result will contain a string representing the unique deployment ID of the
   * deployment.
   * <p>
   * This deployment ID can subsequently be used to undeploy the verticle.
   *
   * @param verticle  the verticle instance to deploy
   * @param completionHandler  a handler which will be notified when the deployment is complete
   */
  @GenIgnore
  void deployVerticle(Verticle verticle, Handler<AsyncResult<String>> completionHandler);

  /**
   * Like {@link #deployVerticle(Verticle)} but {@link io.vertx.core.DeploymentOptions} are provided to configure the
   * deployment.
   *
   * @param verticle  the verticle instance to deploy
   * @param options  the deployment options.
   */
  @GenIgnore
  void deployVerticle(Verticle verticle, DeploymentOptions options);

  /**
   * Like {@link #deployVerticle(Verticle, Handler)} but {@link io.vertx.core.DeploymentOptions} are provided to configure the
   * deployment.
   *
   * @param verticle  the verticle instance to deploy
   * @param options  the deployment options.
   * @param completionHandler  a handler which will be notified when the deployment is complete
   */
  @GenIgnore
  void deployVerticle(Verticle verticle, DeploymentOptions options, Handler<AsyncResult<String>> completionHandler);

  /**
   * Deploy a verticle instance given a name.
   * <p>
   * Given the name, Vert.x selects a {@link VerticleFactory} instance to use to instantiate the verticle.
   * <p>
   * For the rules on how factories are selected please consult the user manual.
   *
   * @param name  the name.
   */
  void deployVerticle(String name);

  /**
   * Like {@link #deployVerticle(String)} but the completionHandler will be notified when the deployment is complete.
   * <p>
   * If the deployment is successful the result will contain a String representing the unique deployment ID of the
   * deployment.
   * <p>
   * This deployment ID can subsequently be used to undeploy the verticle.
   *
   * @param name  The identifier
   * @param completionHandler  a handler which will be notified when the deployment is complete
   */
  void deployVerticle(String name, Handler<AsyncResult<String>> completionHandler);


  /**
   * Like {@link #deployVerticle(Verticle)} but {@link io.vertx.core.DeploymentOptions} are provided to configure the
   * deployment.
   *
   * @param name  the name
   * @param options  the deployment options.
   */
  void deployVerticle(String name, DeploymentOptions options);

  /**
   * Like {@link #deployVerticle(String, Handler)} but {@link io.vertx.core.DeploymentOptions} are provided to configure the
   * deployment.
   *
   * @param name  the name
   * @param options  the deployment options.
   * @param completionHandler  a handler which will be notified when the deployment is complete
   */
  void deployVerticle(String name, DeploymentOptions options, Handler<AsyncResult<String>> completionHandler);

  /**
   * Undeploy a verticle deployment.
   * <p>
   * The actual undeployment happens asynchronously and may not complete until after the method has returned.
   *
   * @param deploymentID  the deployment ID
   */
  void undeploy(String deploymentID);

  /**
   * Like {@link #undeploy(String) } but the completionHandler will be notified when the undeployment is complete.
   *
   * @param deploymentID  the deployment ID
   * @param completionHandler  a handler which will be notified when the undeployment is complete
   */
  void undeploy(String deploymentID, Handler<AsyncResult<Void>> completionHandler);

  /**
   * Return a Set of deployment IDs for the currently deployed deploymentIDs.
   *
   * @return Set of deployment IDs
   */
  Set<String> deploymentIDs();

  /**
   * Register a {@code VerticleFactory} that can be used for deploying Verticles based on an identifier.
   *
   * @param factory the factory to register
   */
  @GenIgnore
  void registerVerticleFactory(VerticleFactory factory);

  /**
   * Unregister a {@code VerticleFactory}
   *
   * @param factory the factory to unregister
   */
  @GenIgnore
  void unregisterVerticleFactory(VerticleFactory factory);

  /**
   * Return the Set of currently registered verticle factories.
   *
   * @return the set of verticle factories
   */
  @GenIgnore
  Set<VerticleFactory> verticleFactories();

  /**
   * Is this Vert.x instance clustered?
   *
   * @return true if clustered
   */
  boolean isClustered();

  /**
   * Safely execute some blocking code.
   * <p>
   * Executes the blocking code in the handler {@code blockingCodeHandler} using a thread from the worker pool.
   * <p>
   * When the code is complete the handler {@code resultHandler} will be called with the result on the original context
   * (e.g. on the original event loop of the caller).
   * <p>
   * A {@code Future} instance is passed into {@code blockingCodeHandler}. When the blocking code successfully completes,
   * the handler should call the {@link Future#complete} or {@link Future#complete(Object)} method, or the {@link Future#fail}
   * method if it failed.
   *
   * @param blockingCodeHandler  handler representing the blocking code to run
   * @param resultHandler  handler that will be called when the blocking code is complete
   * @param <T> the type of the result
   */
  <T> void executeBlocking(Handler<Future<T>> blockingCodeHandler, Handler<AsyncResult<T>> resultHandler);

  /**
   * Return the Netty EventLoopGroup used by Vert.x
   *
   * @return the EventLoopGroup
   */
  @GenIgnore
  EventLoopGroup nettyEventLoopGroup();

  static final VertxFactory factory = ServiceHelper.loadFactory(VertxFactory.class);
}
