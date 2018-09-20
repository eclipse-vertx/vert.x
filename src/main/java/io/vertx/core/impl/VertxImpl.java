/*
 * Copyright (c) 2011-2018 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */

package io.vertx.core.impl;

import io.netty.channel.EventLoop;
import io.netty.channel.EventLoopGroup;
import io.netty.resolver.AddressResolverGroup;
import io.netty.util.ResourceLeakDetector;
import io.netty.util.concurrent.GenericFutureListener;
import io.vertx.core.*;
import io.vertx.core.Future;
import io.vertx.core.datagram.DatagramSocket;
import io.vertx.core.datagram.DatagramSocketOptions;
import io.vertx.core.datagram.impl.DatagramSocketImpl;
import io.vertx.core.dns.AddressResolverOptions;
import io.vertx.core.dns.DnsClient;
import io.vertx.core.dns.DnsClientOptions;
import io.vertx.core.dns.impl.DnsClientImpl;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.eventbus.impl.EventBusImpl;
import io.vertx.core.eventbus.impl.clustered.ClusteredEventBus;
import io.vertx.core.file.FileSystem;
import io.vertx.core.file.impl.FileResolver;
import io.vertx.core.file.impl.FileSystemImpl;
import io.vertx.core.file.impl.WindowsFileSystem;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.http.impl.HttpClientImpl;
import io.vertx.core.http.impl.HttpServerImpl;
import io.vertx.core.impl.resolver.DnsResolverProvider;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.core.net.NetClient;
import io.vertx.core.net.NetClientOptions;
import io.vertx.core.net.NetServer;
import io.vertx.core.net.NetServerOptions;
import io.vertx.core.net.impl.NetClientImpl;
import io.vertx.core.net.impl.NetServerImpl;
import io.vertx.core.net.impl.ServerID;
import io.vertx.core.net.impl.transport.Transport;
import io.vertx.core.shareddata.SharedData;
import io.vertx.core.shareddata.impl.SharedDataImpl;
import io.vertx.core.spi.VerticleFactory;
import io.vertx.core.spi.VertxMetricsFactory;
import io.vertx.core.spi.cluster.ClusterManager;
import io.vertx.core.spi.metrics.Metrics;
import io.vertx.core.spi.metrics.MetricsProvider;
import io.vertx.core.spi.metrics.PoolMetrics;
import io.vertx.core.spi.metrics.VertxMetrics;
import io.vertx.core.streams.ReadStream;

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class VertxImpl implements VertxInternal, MetricsProvider {

  private static final Logger log = LoggerFactory.getLogger(VertxImpl.class);

  private static final String CLUSTER_MAP_NAME = "__vertx.haInfo";
  private static final String NETTY_IO_RATIO_PROPERTY_NAME = "vertx.nettyIORatio";
  private static final int NETTY_IO_RATIO = Integer.getInteger(NETTY_IO_RATIO_PROPERTY_NAME, 50);

  static {
    // Netty resource leak detection has a performance overhead and we do not need it in Vert.x
    ResourceLeakDetector.setLevel(ResourceLeakDetector.Level.DISABLED);
    // Use the JDK deflater/inflater by default
    System.setProperty("io.netty.noJdkZlibDecoder", "false");
  }

  static VertxImpl vertx(VertxOptions options) {
    VertxImpl vertx = new VertxImpl(options);
    vertx.init();
    return vertx;
  }

  static void clusteredVertx(VertxOptions options, Handler<AsyncResult<Vertx>> resultHandler) {
    VertxImpl vertx = new VertxImpl(options);
    vertx.joinCluster(options, resultHandler);
  }

  private final FileSystem fileSystem = getFileSystem();
  private final SharedData sharedData;
  private final VertxMetrics metrics;
  private final ConcurrentMap<Long, InternalTimerHandler> timeouts = new ConcurrentHashMap<>();
  private final AtomicLong timeoutCounter = new AtomicLong(0);
  private final ClusterManager clusterManager;
  private final DeploymentManager deploymentManager;
  private final FileResolver fileResolver;
  private final Map<ServerID, HttpServerImpl> sharedHttpServers = new HashMap<>();
  private final Map<ServerID, NetServerImpl> sharedNetServers = new HashMap<>();
  final WorkerPool workerPool;
  final WorkerPool internalBlockingPool;
  private final ThreadFactory eventLoopThreadFactory;
  private final EventLoopGroup eventLoopGroup;
  private final EventLoopGroup acceptorEventLoopGroup;
  private final BlockedThreadChecker checker;
  private final AddressResolver addressResolver;
  private final AddressResolverOptions addressResolverOptions;
  private final EventBus eventBus;
  private volatile HAManager haManager;
  private boolean closed;
  private volatile Handler<Throwable> exceptionHandler;
  private final Map<String, SharedWorkerPool> namedWorkerPools;
  private final int defaultWorkerPoolSize;
  private final long defaultWorkerMaxExecTime;
  private final TimeUnit defaultWorkerMaxExecTimeUnit;
  private final CloseHooks closeHooks;
  private final Transport transport;

  private VertxImpl(VertxOptions options) {
    // Sanity check
    if (Vertx.currentContext() != null) {
      log.warn("You're already on a Vert.x context, are you sure you want to create a new Vertx instance?");
    }
    if (options.getPreferNativeTransport()) {
      Transport nativeTransport = Transport.nativeTransport();
      if (nativeTransport != null && nativeTransport.isAvailable()) {
        transport = nativeTransport;
      } else {
        transport = Transport.JDK;
      }
    } else {
      transport = Transport.JDK;
    }
    closeHooks = new CloseHooks(log);
    checker = new BlockedThreadChecker(options.getBlockedThreadCheckInterval(), options.getBlockedThreadCheckIntervalUnit(), options.getWarningExceptionTime(), options.getWarningExceptionTimeUnit());
    eventLoopThreadFactory = new VertxThreadFactory("vert.x-eventloop-thread-", checker, false, options.getMaxEventLoopExecuteTime(), options.getMaxEventLoopExecuteTimeUnit());
    eventLoopGroup = transport.eventLoopGroup(options.getEventLoopPoolSize(), eventLoopThreadFactory, NETTY_IO_RATIO);
    ThreadFactory acceptorEventLoopThreadFactory = new VertxThreadFactory("vert.x-acceptor-thread-", checker, false, options.getMaxEventLoopExecuteTime(), options.getMaxEventLoopExecuteTimeUnit());
    // The acceptor event loop thread needs to be from a different pool otherwise can get lags in accepted connections
    // under a lot of load
    acceptorEventLoopGroup = transport.eventLoopGroup(1, acceptorEventLoopThreadFactory, 100);

    metrics = initialiseMetrics(options);

    ExecutorService workerExec = Executors.newFixedThreadPool(options.getWorkerPoolSize(),
        new VertxThreadFactory("vert.x-worker-thread-", checker, true, options.getMaxWorkerExecuteTime(), options.getMaxWorkerExecuteTimeUnit()));
    PoolMetrics workerPoolMetrics = metrics != null ? metrics.createPoolMetrics("worker", "vert.x-worker-thread", options.getWorkerPoolSize()) : null;
    ExecutorService internalBlockingExec = Executors.newFixedThreadPool(options.getInternalBlockingPoolSize(),
        new VertxThreadFactory("vert.x-internal-blocking-", checker, true, options.getMaxWorkerExecuteTime(), options.getMaxWorkerExecuteTimeUnit()));
    PoolMetrics internalBlockingPoolMetrics = metrics != null ? metrics.createPoolMetrics("worker", "vert.x-internal-blocking", options.getInternalBlockingPoolSize()) : null;
    internalBlockingPool = new WorkerPool(internalBlockingExec, internalBlockingPoolMetrics);
    namedWorkerPools = new HashMap<>();
    workerPool = new WorkerPool(workerExec, workerPoolMetrics);
    defaultWorkerPoolSize = options.getWorkerPoolSize();
    defaultWorkerMaxExecTime = options.getMaxWorkerExecuteTime();
    defaultWorkerMaxExecTimeUnit = options.getMaxWorkerExecuteTimeUnit();

    this.fileResolver = new FileResolver(options.getFileSystemOptions());
    this.addressResolverOptions = options.getAddressResolverOptions();
    this.addressResolver = new AddressResolver(this, options.getAddressResolverOptions());
    this.deploymentManager = new DeploymentManager(this);
    if (options.isClustered()) {
      this.clusterManager = getClusterManager(options);
      this.eventBus = new ClusteredEventBus(this, options, clusterManager);
    } else {
      this.clusterManager = null;
      this.eventBus = new EventBusImpl(this);
    }
    this.sharedData = new SharedDataImpl(this, clusterManager);
  }

  private void init() {
    eventBus.start(ar -> {});
  }

  private void joinCluster(VertxOptions options, Handler<AsyncResult<Vertx>> resultHandler) {
    clusterManager.setVertx(this);
    clusterManager.join(ar -> {
      if (ar.succeeded()) {
        createHaManager(options, resultHandler);
      } else {
        log.error("Failed to join cluster", ar.cause());
        resultHandler.handle(Future.failedFuture(ar.cause()));
      }
    });
  }

  private void createHaManager(VertxOptions options, Handler<AsyncResult<Vertx>> resultHandler) {
    this.<Map<String, String>>executeBlocking(fut -> {
      fut.complete(clusterManager.getSyncMap(CLUSTER_MAP_NAME));
    }, false, ar -> {
      if (ar.succeeded()) {
        Map<String, String> clusterMap = ar.result();
        haManager = new HAManager(this, deploymentManager, clusterManager, clusterMap, options.getQuorumSize(), options.getHAGroup(), options.isHAEnabled());
        startEventBus(resultHandler);
      } else {
        log.error("Failed to start HAManager", ar.cause());
        resultHandler.handle(Future.failedFuture(ar.cause()));
      }
    });
  }

  private void startEventBus(Handler<AsyncResult<Vertx>> resultHandler) {
    eventBus.start(ar -> {
      if (ar.succeeded()) {
        initializeHaManager(resultHandler);
      } else {
        log.error("Failed to start event bus", ar.cause());
        resultHandler.handle(Future.failedFuture(ar.cause()));
      }
    });
  }

  private void initializeHaManager(Handler<AsyncResult<Vertx>> resultHandler) {
    this.executeBlocking(fut -> {
      // Init the manager (i.e register listener and check the quorum)
      // after the event bus has been fully started and updated its state
      // it will have also set the clustered changed view handler on the ha manager
      haManager.init();
      fut.complete();
    }, false, ar -> {
      if (ar.succeeded()) {
        resultHandler.handle(Future.succeededFuture(this));
      } else {
        log.error("Failed to initialize HAManager", ar.cause());
        resultHandler.handle(Future.failedFuture(ar.cause()));
      }
    });
  }

  /**
   * @return The FileSystem implementation for the OS
   */
  protected FileSystem getFileSystem() {
    return Utils.isWindows() ? new WindowsFileSystem(this) : new FileSystemImpl(this);
  }

  @Override
  public DatagramSocket createDatagramSocket(DatagramSocketOptions options) {
    return DatagramSocketImpl.create(this, options);
  }

  @Override
  public DatagramSocket createDatagramSocket() {
    return createDatagramSocket(new DatagramSocketOptions());
  }

  public NetServer createNetServer(NetServerOptions options) {
    return new NetServerImpl(this, options);
  }

  @Override
  public NetServer createNetServer() {
    return createNetServer(new NetServerOptions());
  }

  public NetClient createNetClient(NetClientOptions options) {
    return new NetClientImpl(this, options);
  }

  @Override
  public NetClient createNetClient() {
    return createNetClient(new NetClientOptions());
  }

  @Override
  public Transport transport() {
    return transport;
  }

  @Override
  public boolean isNativeTransportEnabled() {
    return transport != Transport.JDK;
  }

  public FileSystem fileSystem() {
    return fileSystem;
  }

  public SharedData sharedData() {
    return sharedData;
  }

  public HttpServer createHttpServer(HttpServerOptions serverOptions) {
    return new HttpServerImpl(this, serverOptions);
  }

  @Override
  public HttpServer createHttpServer() {
    return createHttpServer(new HttpServerOptions());
  }

  public HttpClient createHttpClient(HttpClientOptions options) {
    return new HttpClientImpl(this, options);
  }

  @Override
  public HttpClient createHttpClient() {
    return createHttpClient(new HttpClientOptions());
  }

  public EventBus eventBus() {
    return eventBus;
  }

  public long setPeriodic(long delay, Handler<Long> handler) {
    return scheduleTimeout(getOrCreateContext(), handler, delay, true);
  }

  @Override
  public TimeoutStream periodicStream(long delay) {
    return new TimeoutStreamImpl(delay, true);
  }

  public long setTimer(long delay, Handler<Long> handler) {
    return scheduleTimeout(getOrCreateContext(), handler, delay, false);
  }

  @Override
  public TimeoutStream timerStream(long delay) {
    return new TimeoutStreamImpl(delay, false);
  }

  public void runOnContext(Handler<Void> task) {
    ContextImpl context = getOrCreateContext();
    context.runOnContext(task);
  }

  // The background pool is used for making blocking calls to legacy synchronous APIs
  public ExecutorService getWorkerPool() {
    return workerPool.executor();
  }

  public EventLoopGroup getEventLoopGroup() {
    return eventLoopGroup;
  }

  public EventLoopGroup getAcceptorEventLoopGroup() {
    return acceptorEventLoopGroup;
  }

  public ContextImpl getOrCreateContext() {
    ContextImpl ctx = getContext();
    if (ctx == null) {
      // We are running embedded - Create a context
      ctx = createEventLoopContext(null, null, new JsonObject(), Thread.currentThread().getContextClassLoader());
    }
    return ctx;
  }

  public Map<ServerID, HttpServerImpl> sharedHttpServers() {
    return sharedHttpServers;
  }

  public Map<ServerID, NetServerImpl> sharedNetServers() {
    return sharedNetServers;
  }

  @Override
  public boolean isMetricsEnabled() {
    return metrics != null;
  }

  @Override
  public Metrics getMetrics() {
    return metrics;
  }

  public boolean cancelTimer(long id) {
    InternalTimerHandler handler = timeouts.remove(id);
    if (handler != null) {
      handler.context.removeCloseHook(handler);
      return handler.cancel();
    } else {
      return false;
    }
  }

  @Override
  public EventLoopContext createEventLoopContext(String deploymentID, WorkerPool workerPool, JsonObject config, ClassLoader tccl) {
    return new EventLoopContext(this, internalBlockingPool, workerPool != null ? workerPool : this.workerPool, deploymentID, config, tccl);
  }

  @Override
  public ContextImpl createWorkerContext(boolean multiThreaded, String deploymentID, WorkerPool workerPool, JsonObject config,
                                         ClassLoader tccl) {
    if (workerPool == null) {
      workerPool = this.workerPool;
    }
    if (multiThreaded) {
      return new MultiThreadedWorkerContext(this, internalBlockingPool, workerPool, deploymentID, config, tccl);
    } else {
      return new WorkerContext(this, internalBlockingPool, workerPool, deploymentID, config, tccl);
    }
  }

  @Override
  public DnsClient createDnsClient(int port, String host) {
    return createDnsClient(new DnsClientOptions().setHost(host).setPort(port));
  }

  @Override
  public DnsClient createDnsClient() {
    return createDnsClient(new DnsClientOptions());
  }

  @Override
  public DnsClient createDnsClient(DnsClientOptions options) {
    String host = options.getHost();
    int port = options.getPort();
    if (host == null || port < 0) {
      DnsResolverProvider provider = new DnsResolverProvider(this, addressResolverOptions);
      InetSocketAddress address = provider.nameServerAddresses().get(0);
      // provide the host and port
      options = new DnsClientOptions(options)
      .setHost(address.getAddress().getHostAddress())
      .setPort(address.getPort());
    }
    return new DnsClientImpl(this, options);
  }

  private VertxMetrics initialiseMetrics(VertxOptions options) {
    if (options.getMetricsOptions() != null && options.getMetricsOptions().isEnabled()) {
      VertxMetricsFactory factory = options.getMetricsOptions().getFactory();
      if (factory == null) {
        factory = ServiceHelper.loadFactoryOrNull(VertxMetricsFactory.class);
        if (factory == null) {
          log.warn("Metrics has been set to enabled but no VertxMetricsFactory found on classpath");
        }
      }
      if (factory != null) {
        VertxMetrics metrics = factory.metrics(options);
        Objects.requireNonNull(metrics, "The metric instance created from " + factory + " cannot be null");
        return metrics;
      }
    }
    return null;
  }

  private ClusterManager getClusterManager(VertxOptions options) {
    ClusterManager mgr = options.getClusterManager();
    if (mgr == null) {
      String clusterManagerClassName = System.getProperty("vertx.cluster.managerClass");
      if (clusterManagerClassName != null) {
        // We allow specify a sys prop for the cluster manager factory which overrides ServiceLoader
        try {
          Class<?> clazz = Class.forName(clusterManagerClassName);
          mgr = (ClusterManager) clazz.newInstance();
        } catch (Exception e) {
          throw new IllegalStateException("Failed to instantiate " + clusterManagerClassName, e);
        }
      } else {
        mgr = ServiceHelper.loadFactoryOrNull(ClusterManager.class);
        if (mgr == null) {
          throw new IllegalStateException("No ClusterManagerFactory instances found on classpath");
        }
      }
    }
    return mgr;
  }

  private long scheduleTimeout(ContextImpl context, Handler<Long> handler, long delay, boolean periodic) {
    if (delay < 1) {
      throw new IllegalArgumentException("Cannot schedule a timer with delay < 1 ms");
    }
    long timerId = timeoutCounter.getAndIncrement();
    InternalTimerHandler task = new InternalTimerHandler(timerId, handler, periodic, delay, context);
    timeouts.put(timerId, task);
    context.addCloseHook(task);
    return timerId;
  }

  public static Context context() {
    Thread current = Thread.currentThread();
    if (current instanceof VertxThread) {
      return ((VertxThread) current).getContext();
    }
    return null;
  }

  public ContextImpl getContext() {
    ContextImpl context = (ContextImpl) context();
    if (context != null && context.owner == this) {
      return context;
    }
    return null;
  }

  public ClusterManager getClusterManager() {
    return clusterManager;
  }

  @Override
  public void close() {
    close(null);
  }

  private void closeClusterManager(Handler<AsyncResult<Void>> completionHandler) {
    if (clusterManager != null) {
      clusterManager.leave(ar -> {
        if (ar.failed()) {
          log.error("Failed to leave cluster", ar.cause());
        }
        if (completionHandler != null) {
          runOnContext(v -> completionHandler.handle(Future.succeededFuture()));
        }
      });
    } else if (completionHandler != null) {
      runOnContext(v -> completionHandler.handle(Future.succeededFuture()));
    }
  }

  @Override
  public synchronized void close(Handler<AsyncResult<Void>> completionHandler) {
    if (closed || eventBus == null) {
      // Just call the handler directly since pools shutdown
      if (completionHandler != null) {
        completionHandler.handle(Future.succeededFuture());
      }
      return;
    }
    closed = true;

    closeHooks.run(ar -> {
      deploymentManager.undeployAll(ar1 -> {
        HAManager haManager = haManager();
        Future<Void> haFuture = Future.future();
        if (haManager != null) {
          this.executeBlocking(fut -> {
            haManager.stop();
            fut.complete();
          }, false, haFuture);
        } else {
          haFuture.complete();
        }
        haFuture.setHandler(ar2 -> {
          addressResolver.close(ar3 -> {
            eventBus.close(ar4 -> {
              closeClusterManager(ar5 -> {
                // Copy set to prevent ConcurrentModificationException
                Set<HttpServer> httpServers = new HashSet<>(sharedHttpServers.values());
                Set<NetServerImpl> netServers = new HashSet<>(sharedNetServers.values());
                sharedHttpServers.clear();
                sharedNetServers.clear();

                int serverCount = httpServers.size() + netServers.size();

                AtomicInteger serverCloseCount = new AtomicInteger();

                Handler<AsyncResult<Void>> serverCloseHandler = res -> {
                  if (res.failed()) {
                    log.error("Failure in shutting down server", res.cause());
                  }
                  if (serverCloseCount.incrementAndGet() == serverCount) {
                    deleteCacheDirAndShutdown(completionHandler);
                  }
                };

                for (HttpServer server : httpServers) {
                  server.close(serverCloseHandler);
                }
                for (NetServerImpl server : netServers) {
                  server.close(serverCloseHandler);
                }
                if (serverCount == 0) {
                  deleteCacheDirAndShutdown(completionHandler);
                }
              });
            });
          });
        });
      });
    });
  }

  @Override
  public void deployVerticle(Verticle verticle) {
    deployVerticle(verticle, new DeploymentOptions(), null);
  }

  @Override
  public void deployVerticle(Verticle verticle, Handler<AsyncResult<String>> completionHandler) {
    deployVerticle(verticle, new DeploymentOptions(), completionHandler);
  }

  @Override
  public void deployVerticle(String name, Handler<AsyncResult<String>> completionHandler) {
    deployVerticle(name, new DeploymentOptions(), completionHandler);
  }

  @Override
  public void deployVerticle(Verticle verticle, DeploymentOptions options) {
    deployVerticle(verticle, options, null);
  }

  @Override
  public void deployVerticle(Class<? extends Verticle> verticleClass, DeploymentOptions options) {
    deployVerticle(() -> {
      try {
        return verticleClass.newInstance();
      } catch (Exception e) {
        throw new RuntimeException(e);
      }
    }, options);
  }

  @Override
  public void deployVerticle(Supplier<Verticle> verticleSupplier, DeploymentOptions options) {
    deployVerticle(verticleSupplier, options, null);
  }

  @Override
  public void deployVerticle(Verticle verticle, DeploymentOptions options, Handler<AsyncResult<String>> completionHandler) {
    if (options.getInstances() != 1) {
      throw new IllegalArgumentException("Can't specify > 1 instances for already created verticle");
    }
    deployVerticle(() -> verticle, options, completionHandler);
  }

  @Override
  public void deployVerticle(Class<? extends Verticle> verticleClass, DeploymentOptions options, Handler<AsyncResult<String>> completionHandler) {
    deployVerticle(() -> {
      try {
        return verticleClass.newInstance();
      } catch (Exception e) {
        throw new RuntimeException(e);
      }
    }, options, completionHandler);
  }

  @Override
  public void deployVerticle(Supplier<Verticle> verticleSupplier, DeploymentOptions options, Handler<AsyncResult<String>> completionHandler) {
    boolean closed;
    synchronized (this) {
      closed = this.closed;
    }
    if (closed) {
      if (completionHandler != null) {
        completionHandler.handle(Future.failedFuture("Vert.x closed"));
      }
    } else {
      deploymentManager.deployVerticle(verticleSupplier, options, completionHandler);
    }
  }

  @Override
  public void deployVerticle(String name) {
    deployVerticle(name, new DeploymentOptions(), null);
  }

  @Override
  public void deployVerticle(String name, DeploymentOptions options) {
    deployVerticle(name, options, null);
  }

  @Override
  public void deployVerticle(String name, DeploymentOptions options, Handler<AsyncResult<String>> completionHandler) {
    if (options.isHa() && haManager() != null && haManager().isEnabled()) {
      haManager().deployVerticle(name, options, completionHandler);
    } else {
      deploymentManager.deployVerticle(name, options, completionHandler);
    }
  }

  @Override
  public String getNodeID() {
    return clusterManager.getNodeID();
  }

  @Override
  public void undeploy(String deploymentID) {
    undeploy(deploymentID, res -> {
    });
  }

  @Override
  public void undeploy(String deploymentID, Handler<AsyncResult<Void>> completionHandler) {
    HAManager haManager = haManager();
    Future<Void> haFuture = Future.future();
    if (haManager != null && haManager.isEnabled()) {
      this.executeBlocking(fut -> {
        haManager.removeFromHA(deploymentID);
        fut.complete();
      }, false, haFuture);
    } else {
      haFuture.complete();
    }
    haFuture.compose(v -> {
      Future<Void> deploymentFuture = Future.future();
      deploymentManager.undeployVerticle(deploymentID, deploymentFuture);
      return deploymentFuture;
    }).setHandler(completionHandler);
  }

  @Override
  public Set<String> deploymentIDs() {
    return deploymentManager.deployments();
  }

  @Override
  public void registerVerticleFactory(VerticleFactory factory) {
    deploymentManager.registerVerticleFactory(factory);
  }

  @Override
  public void unregisterVerticleFactory(VerticleFactory factory) {
    deploymentManager.unregisterVerticleFactory(factory);
  }

  @Override
  public Set<VerticleFactory> verticleFactories() {
    return deploymentManager.verticleFactories();
  }

  @Override
  public <T> void executeBlockingInternal(Handler<Future<T>> blockingCodeHandler, Handler<AsyncResult<T>> resultHandler) {
    ContextImpl context = getOrCreateContext();

    context.executeBlockingInternal(blockingCodeHandler, resultHandler);
  }

  @Override
  public <T> void executeBlocking(Handler<Future<T>> blockingCodeHandler, boolean ordered,
                                  Handler<AsyncResult<T>> asyncResultHandler) {
    ContextImpl context = getOrCreateContext();
    context.executeBlocking(blockingCodeHandler, ordered, asyncResultHandler);
  }

  @Override
  public <T> void executeBlocking(Handler<Future<T>> blockingCodeHandler,
                                  Handler<AsyncResult<T>> asyncResultHandler) {
    executeBlocking(blockingCodeHandler, true, asyncResultHandler);
  }

  @Override
  public boolean isClustered() {
    return clusterManager != null;
  }

  @Override
  public EventLoopGroup nettyEventLoopGroup() {
    return eventLoopGroup;
  }

  // For testing
  public void simulateKill() {
    if (haManager() != null) {
      haManager().simulateKill();
    }
  }

  @Override
  public Deployment getDeployment(String deploymentID) {
    return deploymentManager.getDeployment(deploymentID);
  }

  @Override
  public synchronized void failoverCompleteHandler(FailoverCompleteHandler failoverCompleteHandler) {
    if (haManager() != null) {
      haManager().setFailoverCompleteHandler(failoverCompleteHandler);
    }
  }

  @Override
  public boolean isKilled() {
    return haManager().isKilled();
  }

  @Override
  public void failDuringFailover(boolean fail) {
    if (haManager() != null) {
      haManager().failDuringFailover(fail);
    }
  }

  @Override
  public VertxMetrics metricsSPI() {
    return metrics;
  }

  @Override
  public File resolveFile(String fileName) {
    return fileResolver.resolveFile(fileName);
  }

  @Override
  public void resolveAddress(String hostname, Handler<AsyncResult<InetAddress>> resultHandler) {
    addressResolver.resolveHostname(hostname, resultHandler);
  }

  @Override
  public AddressResolver addressResolver() {
    return addressResolver;
  }

  @Override
  public AddressResolverGroup<InetSocketAddress> nettyAddressResolverGroup() {
    return addressResolver.nettyAddressResolverGroup();
  }

  @SuppressWarnings("unchecked")
  private void deleteCacheDirAndShutdown(Handler<AsyncResult<Void>> completionHandler) {
    executeBlockingInternal(fut -> {
      try {
        fileResolver.close();
        fut.complete();
      } catch (IOException e) {
        fut.tryFail(e);
      }
    }, ar -> {

      workerPool.close();
      internalBlockingPool.close();
      new ArrayList<>(namedWorkerPools.values()).forEach(WorkerPool::close);

      acceptorEventLoopGroup.shutdownGracefully(0, 10, TimeUnit.SECONDS).addListener(new GenericFutureListener() {
        @Override
        public void operationComplete(io.netty.util.concurrent.Future future) throws Exception {
          if (!future.isSuccess()) {
            log.warn("Failure in shutting down acceptor event loop group", future.cause());
          }
          eventLoopGroup.shutdownGracefully(0, 10, TimeUnit.SECONDS).addListener(new GenericFutureListener() {
            @Override
            public void operationComplete(io.netty.util.concurrent.Future future) throws Exception {
              if (!future.isSuccess()) {
                log.warn("Failure in shutting down event loop group", future.cause());
              }
              if (metrics != null) {
                metrics.close();
              }

              checker.close();

              if (completionHandler != null) {
                eventLoopThreadFactory.newThread(() -> {
                  completionHandler.handle(Future.succeededFuture());
                }).start();
              }
            }
          });
        }
      });
    });
  }

  public HAManager haManager() {
    return haManager;
  }

  private class InternalTimerHandler implements Handler<Void>, Closeable {
    final Handler<Long> handler;
    final boolean periodic;
    final long timerID;
    final ContextImpl context;
    final java.util.concurrent.Future<?> future;
    final AtomicBoolean cancelled;

    boolean cancel() {
      if (cancelled.compareAndSet(false, true)) {
        if (metrics != null) {
          metrics.timerEnded(timerID, true);
        }
        future.cancel(false);
        return true;
      } else {
        return false;
      }
    }

    InternalTimerHandler(long timerID, Handler<Long> runnable, boolean periodic, long delay, ContextImpl context) {
      this.context = context;
      this.timerID = timerID;
      this.handler = runnable;
      this.periodic = periodic;
      this.cancelled = new AtomicBoolean();
      EventLoop el = context.nettyEventLoop();
      Runnable toRun = () -> context.runOnContext(this);
      if (periodic) {
        future = el.scheduleAtFixedRate(toRun, delay, delay, TimeUnit.MILLISECONDS);
      } else {
        future = el.schedule(toRun, delay, TimeUnit.MILLISECONDS);
      }
      if (metrics != null) {
        metrics.timerCreated(timerID);
      }
    }

    public void handle(Void v) {
      if (!cancelled.get()) {
        try {
          handler.handle(timerID);
        } finally {
          if (!periodic) {
            // Clean up after it's fired
            cleanupNonPeriodic();
          }
        }
      }
    }

    private void cleanupNonPeriodic() {
      VertxImpl.this.timeouts.remove(timerID);
      if (metrics != null) {
        metrics.timerEnded(timerID, false);
      }
      ContextImpl context = getContext();
      if (context != null) {
        context.removeCloseHook(this);
      }
    }

    // Called via Context close hook when Verticle is undeployed
    public void close(Handler<AsyncResult<Void>> completionHandler) {
      VertxImpl.this.timeouts.remove(timerID);
      cancel();
      completionHandler.handle(Future.succeededFuture());
    }

  }

  /*
   *
   * This class is optimised for performance when used on the same event loop that is was passed to the handler with.
   * However it can be used safely from other threads.
   *
   * The internal state is protected using the synchronized keyword. If always used on the same event loop, then
   * we benefit from biased locking which makes the overhead of synchronized near zero.
   *
   */
  private class TimeoutStreamImpl implements TimeoutStream, Handler<Long> {

    private final long delay;
    private final boolean periodic;

    private Long id;
    private Handler<Long> handler;
    private Handler<Void> endHandler;
    private long demand;

    public TimeoutStreamImpl(long delay, boolean periodic) {
      this.delay = delay;
      this.periodic = periodic;
      this.demand = Long.MAX_VALUE;
    }

    @Override
    public synchronized void handle(Long event) {
      try {
        if (demand > 0) {
          demand--;
          handler.handle(event);
        }
      } finally {
        if (!periodic && endHandler != null) {
          endHandler.handle(null);
        }
      }
    }

    @Override
    public synchronized TimeoutStream fetch(long amount) {
      demand += amount;
      if (demand < 0) {
        demand = Long.MAX_VALUE;
      }
      return this;
    }

    @Override
    public TimeoutStream exceptionHandler(Handler<Throwable> handler) {
      return this;
    }

    @Override
    public void cancel() {
      if (id != null) {
        VertxImpl.this.cancelTimer(id);
      }
    }

    @Override
    public synchronized TimeoutStream handler(Handler<Long> handler) {
      if (handler != null) {
        if (id != null) {
          throw new IllegalStateException();
        }
        this.handler = handler;
        id = scheduleTimeout(getOrCreateContext(), this, delay, periodic);
      } else {
        cancel();
      }
      return this;
    }

    @Override
    public synchronized TimeoutStream pause() {
      demand = 0;
      return this;
    }

    @Override
    public synchronized TimeoutStream resume() {
      demand = Long.MAX_VALUE;
      return this;
    }

    @Override
    public synchronized TimeoutStream endHandler(Handler<Void> endHandler) {
      this.endHandler = endHandler;
      return this;
    }
  }

  class SharedWorkerPool extends WorkerPool {

    private final String name;
    private int refCount = 1;

    SharedWorkerPool(String name, ExecutorService workerExec, PoolMetrics workerMetrics) {
      super(workerExec, workerMetrics);
      this.name = name;
    }

    @Override
    void close() {
      synchronized (VertxImpl.this) {
        if (refCount > 0) {
          refCount = 0;
          super.close();
        }
      }
    }

    void release() {
      synchronized (VertxImpl.this) {
        if (--refCount == 0) {
          namedWorkerPools.remove(name);
          super.close();
        }
      }
    }
  }

  @Override
  public WorkerExecutorImpl createSharedWorkerExecutor(String name) {
    return createSharedWorkerExecutor(name, defaultWorkerPoolSize);
  }

  @Override
  public WorkerExecutorImpl createSharedWorkerExecutor(String name, int poolSize) {
    return createSharedWorkerExecutor(name, poolSize, defaultWorkerMaxExecTime);
  }

  @Override
  public synchronized WorkerExecutorImpl createSharedWorkerExecutor(String name, int poolSize, long maxExecuteTime) {
    return createSharedWorkerExecutor(name, poolSize, maxExecuteTime, TimeUnit.NANOSECONDS);
  }

  @Override
  public synchronized WorkerExecutorImpl createSharedWorkerExecutor(String name, int poolSize, long maxExecuteTime, TimeUnit maxExecuteTimeUnit) {
    if (poolSize < 1) {
      throw new IllegalArgumentException("poolSize must be > 0");
    }
    if (maxExecuteTime < 1) {
      throw new IllegalArgumentException("maxExecuteTime must be > 0");
    }
    SharedWorkerPool sharedWorkerPool = namedWorkerPools.get(name);
    if (sharedWorkerPool == null) {
      ExecutorService workerExec = Executors.newFixedThreadPool(poolSize, new VertxThreadFactory(name + "-", checker, true, maxExecuteTime, maxExecuteTimeUnit));
      PoolMetrics workerMetrics = metrics != null ? metrics.createPoolMetrics("worker", name, poolSize) : null;
      namedWorkerPools.put(name, sharedWorkerPool = new SharedWorkerPool(name, workerExec, workerMetrics));
    } else {
      sharedWorkerPool.refCount++;
    }
    ContextImpl context = getOrCreateContext();
    WorkerExecutorImpl namedExec = new WorkerExecutorImpl(context, sharedWorkerPool);
    context.addCloseHook(namedExec);
    return namedExec;
  }

  @Override
  public Vertx exceptionHandler(Handler<Throwable> handler) {
    exceptionHandler = handler;
    return this;
  }

  @Override
  public Handler<Throwable> exceptionHandler() {
    return exceptionHandler;
  }

  @Override
  public void addCloseHook(Closeable hook) {
    closeHooks.add(hook);
  }

  @Override
  public void removeCloseHook(Closeable hook) {
    closeHooks.remove(hook);
  }
}
