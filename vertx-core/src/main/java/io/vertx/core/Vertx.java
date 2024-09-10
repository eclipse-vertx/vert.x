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

package io.vertx.core;

import io.vertx.codegen.annotations.*;
import io.vertx.core.datagram.DatagramSocket;
import io.vertx.core.datagram.DatagramSocketOptions;
import io.vertx.core.dns.DnsClient;
import io.vertx.core.dns.DnsClientOptions;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.file.FileSystem;
import io.vertx.core.http.*;
import io.vertx.core.impl.VertxImpl;
import io.vertx.core.impl.transports.TransportInternal;
import io.vertx.core.internal.ContextInternal;
import io.vertx.core.dns.impl.DnsAddressResolverProvider;
import io.vertx.core.internal.VertxBootstrap;
import io.vertx.core.metrics.Measured;
import io.vertx.core.net.NetClient;
import io.vertx.core.net.NetClientOptions;
import io.vertx.core.net.NetServer;
import io.vertx.core.net.NetServerOptions;
import io.vertx.core.shareddata.SharedData;
import io.vertx.core.spi.VerticleFactory;
import io.vertx.core.spi.VertxMetricsFactory;
import io.vertx.core.spi.VertxTracerFactory;
import io.vertx.core.spi.cluster.ClusterManager;
import io.vertx.core.transport.Transport;

import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

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
 * {@link #vertx(io.vertx.core.VertxOptions)} and {@link #clusteredVertx(io.vertx.core.VertxOptions)}.
 * <p>
 * Please see the user manual for more detailed usage information.
 *
 * @author <a href="http://tfox.org">Tim Fox</a>
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
@VertxGen
public interface Vertx extends Measured {

  /**
   * Return a builder for Vert.x instances which allows to specify SPI such as cluster manager, metrics or tracing.
   *
   * @return a Vert.x instance builder
   */
  static io.vertx.core.VertxBuilder builder() {
    return new io.vertx.core.VertxBuilder() {
      private VertxOptions options;
      private ClusterManager clusterManager;
      private VertxMetricsFactory metricsFactory;
      private VertxTracerFactory tracerFactory;
      private Transport transport;
      @Override
      public io.vertx.core.VertxBuilder with(VertxOptions options) {
        this.options = options;
        return this;
      }
      @Override
      public io.vertx.core.VertxBuilder withMetrics(VertxMetricsFactory factory) {
        this.metricsFactory = factory;
        return this;
      }
      @Override
      public io.vertx.core.VertxBuilder withTracer(VertxTracerFactory factory) {
        this.tracerFactory = factory;
        return this;
      }
      @Override
      public io.vertx.core.VertxBuilder withClusterManager(ClusterManager clusterManager) {
        this.clusterManager = clusterManager;
        return this;
      }
      @Override
      public VertxBuilder withTransport(Transport transport) {
        this.transport = transport;
        return this;
      }
      private VertxBootstrap bootstrap() {
        VertxBootstrap bootstrap = VertxBootstrap.create();
        if (options != null) {
          bootstrap.options(options);
        }
        bootstrap.metricsFactory(metricsFactory);
        bootstrap.tracerFactory(tracerFactory);
        Transport tr = transport;
        if (tr == null && options != null && options.getPreferNativeTransport()) {
          tr = Transport.nativeTransport();
        }
        if (tr == null) {
          tr = Transport.NIO;
        }
        bootstrap.transport(tr.implementation());
        return bootstrap;
      }
      @Override
      public Vertx build() {
        return bootstrap()
          .init()
          .vertx();
      }
      @Override
      public Future<Vertx> buildClustered() {
        return bootstrap()
          .clusterManager(clusterManager)
          .init()
          .clusteredVertx();
      }
    };
  }

  /**
   * Creates a non clustered instance using default options.
   *
   * @return the instance
   */
  static Vertx vertx() {
    return vertx(new VertxOptions());
  }

  /**
   * Creates a non clustered instance using the specified options
   *
   * @param options  the options to use
   * @return the instance
   */
  static Vertx vertx(VertxOptions options) {
    return builder().with(options).build();
  }

  /**
   * Creates a clustered instance using the specified options.
   * <p>
   * The instance is created asynchronously and the returned future is completed with the result when it is ready.
   *
   * @param options  the options to use
   * @return a future completed with the clustered vertx
   */
  static Future<Vertx> clusteredVertx(VertxOptions options) {
    return builder().with(options).buildClustered();
  }

  /**
   * Gets the current context
   *
   * @return The current context or {@code null} if there is no current context
   */
  static @Nullable Context currentContext() {
    return VertxImpl.currentContext(Thread.currentThread());
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
  default NetServer createNetServer() {
    return createNetServer(new NetServerOptions());
  }

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
  default NetClient createNetClient() {
    return createNetClient(new NetClientOptions());
  }

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
  default HttpServer createHttpServer() {
    return createHttpServer(new HttpServerOptions());
  }

  /**
   * Create a WebSocket client using default options
   *
   * @return the client
   */
  default WebSocketClient createWebSocketClient() {
    return createWebSocketClient(new WebSocketClientOptions());
  }

  /**
   * Create a WebSocket client using the specified options
   *
   * @param options  the options to use
   * @return the client
   */
  WebSocketClient createWebSocketClient(WebSocketClientOptions options);

  /**
   * Provide a builder for {@link HttpClient}, it can be used to configure advanced
   * HTTP client settings like a redirect handler or a connection handler.
   * <p>
   * Example usage: {@code HttpClient client = vertx.httpClientBuilder().with(options).withConnectHandler(conn -> ...).build()}
   */
  HttpClientBuilder httpClientBuilder();

  /**
   * Create a HTTP/HTTPS client using the specified client and pool options
   *
   * @param clientOptions  the client options to use
   * @param poolOptions  the pool options to use
   * @return the client
   */
  default HttpClientAgent createHttpClient(HttpClientOptions clientOptions, PoolOptions poolOptions) {
    return httpClientBuilder().with(clientOptions).with(poolOptions).build();
  }

  /**
   * Create a HTTP/HTTPS client using the specified client options
   *
   * @param clientOptions  the options to use
   * @return the client
   */
  default HttpClientAgent createHttpClient(HttpClientOptions clientOptions) {
    return createHttpClient(clientOptions, new PoolOptions());
  }

  /**
   * Create a HTTP/HTTPS client using the specified pool options
   *
   * @param poolOptions  the pool options to use
   * @return the client
   */
  default HttpClientAgent createHttpClient(PoolOptions poolOptions) {
    return createHttpClient(new HttpClientOptions(), poolOptions);
  }

  /**
   * Create a HTTP/HTTPS client using default options
   *
   * @return the client
   */
  default HttpClientAgent createHttpClient() {
    return createHttpClient(new HttpClientOptions(), new PoolOptions());
  }

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
  default DatagramSocket createDatagramSocket() {
    return createDatagramSocket(new DatagramSocketOptions());
  }

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
   * Create a DNS client to connect to a DNS server at the specified host and port, with the default query timeout (5 seconds)
   * <p/>
   *
   * @param port  the port
   * @param host  the host
   * @return the DNS client
   */
  DnsClient createDnsClient(int port, String host);

  /**
   * Create a DNS client to connect to the DNS server configured by {@link VertxOptions#getAddressResolverOptions()}
   * <p>
   * DNS client takes the first configured resolver address provided by {@link DnsAddressResolverProvider#nameServerAddresses()}}
   *
   * @return the DNS client
   */
  DnsClient createDnsClient();

  /**
   * Create a DNS client to connect to a DNS server
   *
   * @param options the client options
   * @return the DNS client
   */
  DnsClient createDnsClient(DnsClientOptions options);

  /**
   * Get the shared data object. There is a single instance of SharedData per Vertx instance.
   *
   * @return the shared data object
   */
  @CacheReturn
  SharedData sharedData();

  /**
   * Like {@link #timer(long, TimeUnit)} with a unit in millis.
   */
  default Timer timer(long delay) {
    return timer(delay, TimeUnit.MILLISECONDS);
  }

  /**
   * Create a timer task configured with the specified {@code delay}, when the timeout fires the timer future
   * is succeeded, when the timeout is cancelled the timer future is failed with a {@link java.util.concurrent.CancellationException}
   * instance.
   *
   * @param delay the delay
   * @param unit the delay unit
   * @return the timer object
   */
  default Timer timer(long delay, TimeUnit unit) {
    ContextInternal ctx = (ContextInternal) getOrCreateContext();
    return ctx.timer(delay, unit);
  }

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
   * Set a periodic timer to fire every {@code delay} milliseconds, at which point {@code handler} will be called with
   * the id of the timer.
   *
   * @param delay  the delay in milliseconds, after which the timer will fire
   * @param handler  the handler that will be called with the timer ID when the timer fires
   * @return the unique ID of the timer
   */
  default long setPeriodic(long delay, Handler<Long> handler) {
    return setPeriodic(delay, delay, handler);
  }

  /**
   * Set a periodic timer to fire every {@code delay} milliseconds with initial delay, at which point {@code handler} will be called with
   * the id of the timer.
   *
   * @param initialDelay the initial delay in milliseconds
   * @param delay the delay in milliseconds, after which the timer will fire
   * @param handler the handler that will be called with the timer ID when the timer fires
   * @return the unique ID of the timer
   */
  long setPeriodic(long initialDelay, long delay, Handler<Long> handler);

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
   * Stop the Vertx instance and release any resources held by it.
   * <p>
   * The instance cannot be used after it has been closed.
   * <p>
   * The actual close is asynchronous and may not complete until after the call has returned.
   *
   * @return a future completed with the result
   */
  Future<Void> close();

  /**
   * Deploy a verticle instance that you have created yourself.
   * <p>
   * Vert.x will assign the verticle a context and start the verticle.
   * <p>
   * The actual deploy happens asynchronously and may not complete until after the call has returned.
   * <p>
   * If the deployment is successful the result will contain a string representing the unique deployment ID of the
   * deployment.
   * <p>
   * This deployment ID can subsequently be used to undeploy the verticle.
   *
   * @param verticle  the verticle instance to deploy.
   * @return a future completed with the result
   */
  @GenIgnore(GenIgnore.PERMITTED_TYPE)
  default Future<String> deployVerticle(Deployable verticle) {
    return deployVerticle(verticle, new DeploymentOptions());
  }

  /**
   * Like {@link #deployVerticle(Deployable)} but {@link io.vertx.core.DeploymentOptions} are provided to configure the
   * deployment.
   *
   * @param verticle  the verticle instance to deploy
   * @param options  the deployment options.
   * @return a future completed with the result
   */
  @GenIgnore(GenIgnore.PERMITTED_TYPE)
  default Future<String> deployVerticle(Deployable verticle, DeploymentOptions options) {
    return deployVerticle(() -> verticle, options);
  }

  /**
   * Like {@link #deployVerticle(Deployable, DeploymentOptions)} but {@link Deployable} instance is created by invoking the
   * {@code verticleSupplier}.
   * <p>
   * The supplier will be invoked as many times as {@link DeploymentOptions#getInstances()}.
   * It must not return the same instance twice.
   * <p>
   * Note that the supplier will be invoked on the caller thread.
   *
   * @return a future completed with the result
   */
  @GenIgnore
  Future<String> deployVerticle(Supplier<? extends Deployable> supplier, DeploymentOptions options);

  /**
   * Like {@link #deployVerticle(Deployable, DeploymentOptions)} but {@link Deployable} instance is created by invoking the
   * default constructor of {@code verticleClass}.
   * @return a future completed with the result
   */
  @GenIgnore
  Future<String> deployVerticle(Class<? extends Deployable> verticleClass, DeploymentOptions options);

  /**
   * Deploy a verticle instance given a name.
   * <p>
   * Given the name, Vert.x selects a {@link VerticleFactory} instance to use to instantiate the verticle.
   * <p>
   * For the rules on how factories are selected please consult the user manual.
   * <p>
   * If the deployment is successful the result will contain a String representing the unique deployment ID of the
   * deployment.
   * <p>
   * This deployment ID can subsequently be used to undeploy the verticle.
   *
   * @param name  the name.
   * @return a future completed with the result
   */
  default Future<String> deployVerticle(String name) {
    return deployVerticle(name, new DeploymentOptions());
  }

  /**
   * Like {@link #deployVerticle(Deployable)} but {@link io.vertx.core.DeploymentOptions} are provided to configure the
   * deployment.
   *
   * @param name  the name
   * @param options  the deployment options.
   * @return a future completed with the result
   */
  Future<String> deployVerticle(String name, DeploymentOptions options);

  /**
   * Undeploy a verticle deployment.
   * <p>
   * The actual undeployment happens asynchronously and may not complete until after the method has returned.
   *
   * @param deploymentID  the deployment ID
   * @return a future completed with the result
   */
  Future<Void> undeploy(String deploymentID);

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
  @GenIgnore(GenIgnore.PERMITTED_TYPE)
  void registerVerticleFactory(VerticleFactory factory);

  /**
   * Unregister a {@code VerticleFactory}
   *
   * @param factory the factory to unregister
   */
  @GenIgnore(GenIgnore.PERMITTED_TYPE)
  void unregisterVerticleFactory(VerticleFactory factory);

  /**
   * Return the Set of currently registered verticle factories.
   *
   * @return the set of verticle factories
   */
  @GenIgnore(GenIgnore.PERMITTED_TYPE)
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
   * The returned future will be completed with the result on the original context (i.e. on the original event loop of the caller)
   * or failed when the handler throws an exception.
   * <p>
   * In the {@code blockingCodeHandler} the current context remains the original context and therefore any task
   * scheduled in the {@code blockingCodeHandler} will be executed on this context and not on the worker thread.
   * <p>
   * The blocking code should block for a reasonable amount of time (i.e no more than a few seconds). Long blocking operations
   * or polling operations (i.e a thread that spin in a loop polling events in a blocking fashion) are precluded.
   * <p>
   * When the blocking operation lasts more than the 10 seconds, a message will be printed on the console by the
   * blocked thread checker.
   * <p>
   * Long blocking operations should use a dedicated thread managed by the application, which can interact with
   * verticles using the event-bus or {@link Context#runOnContext(Handler)}
   *
   * @param blockingCodeHandler  handler representing the blocking code to run
   * @param ordered  if true then if executeBlocking is called several times on the same context, the executions
   *                 for that context will be executed serially, not in parallel. if false then they will be no ordering
   *                 guarantees
   * @param <T> the type of the result
   * @return a future completed when the blocking code is complete
   */
  @GenIgnore(GenIgnore.PERMITTED_TYPE)
  default <T> Future<@Nullable T> executeBlocking(Callable<T> blockingCodeHandler, boolean ordered) {
    Context context = getOrCreateContext();
    return context.executeBlocking(blockingCodeHandler, ordered);
  }

  /**
   * Like {@link #executeBlocking(Callable, boolean)} called with ordered = true.
   */
  @GenIgnore(GenIgnore.PERMITTED_TYPE)
  default <T> Future<@Nullable T> executeBlocking(Callable<T> blockingCodeHandler) {
    return executeBlocking(blockingCodeHandler, true);
  }

  /**
   * Like {@link #createSharedWorkerExecutor(String, int)} but with the {@link VertxOptions#setWorkerPoolSize} {@code poolSize}.
   */
  WorkerExecutor createSharedWorkerExecutor(String name);

  /**
   * Like {@link #createSharedWorkerExecutor(String, int, long)} but with the {@link VertxOptions#setMaxWorkerExecuteTime} {@code maxExecuteTime}.
   */
  WorkerExecutor createSharedWorkerExecutor(String name, int poolSize);

  /**
   * Like {@link #createSharedWorkerExecutor(String, int, long, TimeUnit)} but with the {@link VertxOptions#setMaxWorkerExecuteTimeUnit(TimeUnit)} {@code maxExecuteTimeUnit}.
   */
  WorkerExecutor createSharedWorkerExecutor(String name, int poolSize, long maxExecuteTime);

  /**
   * Create a named worker executor, the executor should be closed when it's not needed anymore to release
   * resources.<p/>
   *
   * This method can be called mutiple times with the same {@code name}. Executors with the same name will share
   * the same worker pool. The worker pool size , max execute time and unit of max execute time are set when the worker pool is created and
   * won't change after.<p>
   *
   * The worker pool is released when all the {@link WorkerExecutor} sharing the same name are closed.
   *
   * @param name the name of the worker executor
   * @param poolSize the size of the pool
   * @param maxExecuteTime the value of max worker execute time
   * @param maxExecuteTimeUnit the value of unit of max worker execute time
   * @return the named worker executor
   */
  WorkerExecutor createSharedWorkerExecutor(String name, int poolSize, long maxExecuteTime, TimeUnit maxExecuteTimeUnit);

  /**
   * @return whether the native transport is used
   */
  @CacheReturn
  boolean isNativeTransportEnabled();

  /**
   * @return the error (if any) that cause the unavailability of native transport when {@link #isNativeTransportEnabled()}  returns {@code false}.
   */
  @CacheReturn
  Throwable unavailableNativeTransportCause();

  /**
   * Set a default exception handler for {@link Context}, set on {@link Context#exceptionHandler(Handler)} at creation.
   *
   * @param handler the exception handler
   * @return a reference to this, so the API can be used fluently
   */
  @Fluent
  Vertx exceptionHandler(@Nullable Handler<Throwable> handler);

  /**
   * @return the current default exception handler
   */
  @GenIgnore
  @Nullable Handler<Throwable> exceptionHandler();
}
