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

package io.vertx.core.impl;

import io.netty.channel.EventLoop;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.util.ResourceLeakDetector;
import io.netty.util.concurrent.GenericFutureListener;
import io.vertx.core.*;
import io.vertx.core.Future;
import io.vertx.core.datagram.DatagramSocket;
import io.vertx.core.datagram.DatagramSocketOptions;
import io.vertx.core.datagram.impl.DatagramSocketImpl;
import io.vertx.core.dns.DnsClient;
import io.vertx.core.dns.impl.DnsClientImpl;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.eventbus.impl.EventBusImpl;
import io.vertx.core.eventbus.impl.clustered.ClusteredEventBus;
import io.vertx.core.file.FileSystem;
import io.vertx.core.file.impl.FileSystemImpl;
import io.vertx.core.file.impl.WindowsFileSystem;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.http.impl.HttpClientImpl;
import io.vertx.core.http.impl.HttpServerImpl;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.core.metrics.impl.DummyVertxMetrics;
import io.vertx.core.net.NetClient;
import io.vertx.core.net.NetClientOptions;
import io.vertx.core.net.NetServer;
import io.vertx.core.net.NetServerOptions;
import io.vertx.core.net.impl.NetClientImpl;
import io.vertx.core.net.impl.NetServerImpl;
import io.vertx.core.net.impl.ServerID;
import io.vertx.core.shareddata.SharedData;
import io.vertx.core.shareddata.impl.SharedDataImpl;
import io.vertx.core.spi.VerticleFactory;
import io.vertx.core.spi.VertxMetricsFactory;
import io.vertx.core.spi.cluster.ClusterManager;
import io.vertx.core.spi.metrics.Metrics;
import io.vertx.core.spi.metrics.MetricsProvider;
import io.vertx.core.spi.metrics.PoolMetrics;
import io.vertx.core.spi.metrics.VertxMetrics;

import java.io.File;
import java.net.InetAddress;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class VertxImpl implements VertxInternal, MetricsProvider {

  private static final Logger log = LoggerFactory.getLogger(VertxImpl.class);

  private static final String NETTY_IO_RATIO_PROPERTY_NAME = "vertx.nettyIORatio";
  private static final int NETTY_IO_RATIO = Integer.getInteger(NETTY_IO_RATIO_PROPERTY_NAME, 50);

  static {
    // Netty resource leak detection has a performance overhead and we do not need it in Vert.x
    ResourceLeakDetector.setLevel(ResourceLeakDetector.Level.DISABLED);
    // Use the JDK deflater/inflater by default
    System.setProperty("io.netty.noJdkZlibDecoder", "false");
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
  private final WorkerPool workerPool;
  private final WorkerPool internalBlockingPool;
  private final ThreadFactory eventLoopThreadFactory;
  private final NioEventLoopGroup eventLoopGroup;
  private final NioEventLoopGroup acceptorEventLoopGroup;
  private final BlockedThreadChecker checker;
  private final boolean haEnabled;
  private final HostnameResolver hostnameResolver;
  private EventBus eventBus;
  private HAManager haManager;
  private boolean closed;
  private volatile Handler<Throwable> exceptionHandler;
  private final Map<String, SharedWorkerPool> namedWorkerPools;
  private final int defaultWorkerPoolSize;
  private final long defaultWorkerMaxExecTime;

  VertxImpl() {
    this(new VertxOptions());
  }

  VertxImpl(VertxOptions options) {
    this(options, null);
  }

  VertxImpl(VertxOptions options, Handler<AsyncResult<Vertx>> resultHandler) {
    // Sanity check
    if (Vertx.currentContext() != null) {
      log.warn("You're already on a Vert.x context, are you sure you want to create a new Vertx instance?");
    }
    checker = new BlockedThreadChecker(options.getBlockedThreadCheckInterval(), options.getWarningExceptionTime());
    eventLoopThreadFactory = new VertxThreadFactory("vert.x-eventloop-thread-", checker, false, options.getMaxEventLoopExecuteTime());
    eventLoopGroup = new NioEventLoopGroup(options.getEventLoopPoolSize(), eventLoopThreadFactory);
    eventLoopGroup.setIoRatio(NETTY_IO_RATIO);
    ThreadFactory acceptorEventLoopThreadFactory = new VertxThreadFactory("vert.x-acceptor-thread-", checker, false, options.getMaxEventLoopExecuteTime());
    // The acceptor event loop thread needs to be from a different pool otherwise can get lags in accepted connections
    // under a lot of load
    acceptorEventLoopGroup = new NioEventLoopGroup(1, acceptorEventLoopThreadFactory);
    acceptorEventLoopGroup.setIoRatio(100);

    metrics = initialiseMetrics(options);

    ExecutorService workerExec = Executors.newFixedThreadPool(options.getWorkerPoolSize(),
        new VertxThreadFactory("vert.x-worker-thread-", checker, true, options.getMaxWorkerExecuteTime()));
    PoolMetrics workerPoolMetrics = isMetricsEnabled() ? metrics.createMetrics(workerExec, "vert.x-worker-thread", options.getWorkerPoolSize()) : null;
    ExecutorService internalBlockingExec = Executors.newFixedThreadPool(options.getInternalBlockingPoolSize(),
        new VertxThreadFactory("vert.x-internal-blocking-", checker, true, options.getMaxWorkerExecuteTime()));
    PoolMetrics internalBlockingPoolMetrics = isMetricsEnabled() ? metrics.createMetrics(internalBlockingExec, "vert.x-internal-blocking", options.getInternalBlockingPoolSize()) : null;
    internalBlockingPool = new WorkerPool(internalBlockingExec, internalBlockingPoolMetrics);
    namedWorkerPools = new HashMap<>();
    workerPool = new WorkerPool(workerExec, workerPoolMetrics);
    defaultWorkerPoolSize = options.getWorkerPoolSize();
    defaultWorkerMaxExecTime = options.getMaxWorkerExecuteTime();

    this.hostnameResolver = new HostnameResolver(this, options.getHostnameResolverOptions());
    this.fileResolver = new FileResolver(this);
    this.deploymentManager = new DeploymentManager(this);
    this.haEnabled = options.isClustered() && options.isHAEnabled();
    if (options.isClustered()) {
      this.clusterManager = getClusterManager(options);
      this.clusterManager.setVertx(this);
      this.clusterManager.join(ar -> {
        if (ar.failed()) {
          log.error("Failed to join cluster", ar.cause());
        } else {
          // Provide a memory barrier as we are setting from a different thread
          synchronized (VertxImpl.this) {
            haManager = new HAManager(this, deploymentManager, clusterManager, options.getQuorumSize(),
                                      options.getHAGroup(), haEnabled);
            createAndStartEventBus(options, resultHandler);
          }
        }
      });
    } else {
      this.clusterManager = null;
      createAndStartEventBus(options, resultHandler);
    }
    this.sharedData = new SharedDataImpl(this, clusterManager);
  }

  private void createAndStartEventBus(VertxOptions options, Handler<AsyncResult<Vertx>> resultHandler) {
    if (options.isClustered()) {
      eventBus = new ClusteredEventBus(this, options, clusterManager, haManager);
    } else {
      eventBus = new EventBusImpl(this);
    }
    eventBus.start(ar2 -> {
      if (ar2.succeeded()) {
        // If the metric provider wants to use the event bus, it cannot use it in its constructor as the event bus
        // may not be initialized yet. We invokes the eventBusInitialized so it can starts using the event bus.
        metrics.eventBusInitialized(eventBus);

        if (resultHandler != null) {
          resultHandler.handle(Future.succeededFuture(this));
        }
      } else {
        log.error("Failed to start event bus", ar2.cause());
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
    return new DatagramSocketImpl(this, options);
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
    if (eventBus == null) {
      // If reading from different thread possibility that it's been set but not visible - so provide
      // memory barrier
      synchronized (this) {
        return eventBus;
      }
    }
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
    return metrics != null && metrics.isEnabled();
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

  public EventLoopContext createEventLoopContext(String deploymentID, WorkerPool workerPool, JsonObject config, ClassLoader tccl) {
    return new EventLoopContext(this, internalBlockingPool, workerPool != null ? workerPool : this.workerPool, deploymentID, config, tccl);
  }

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
    return new DnsClientImpl(this, port, host);
  }

  private VertxMetrics initialiseMetrics(VertxOptions options) {
    if (options.getMetricsOptions() != null && options.getMetricsOptions().isEnabled()) {
      ServiceLoader<VertxMetricsFactory> factories = ServiceLoader.load(VertxMetricsFactory.class);
      if (factories.iterator().hasNext()) {
        VertxMetricsFactory factory = factories.iterator().next();
        VertxMetrics metrics = factory.metrics(this, options);
        Objects.requireNonNull(metrics, "The metric instance created from " + factory + " cannot be null");
        return metrics;
      } else {
        log.warn("Metrics has been set to enabled but no VertxMetricsFactory found on classpath");
      }
    }
    return new DummyVertxMetrics();
  }

  private ClusterManager getClusterManager(VertxOptions options) {
    if (options.isClustered()) {
      if (options.getClusterManager() != null) {
        return options.getClusterManager();
      } else {
        ClusterManager mgr;
        String clusterManagerClassName = System.getProperty("vertx.cluster.managerClass");
        if (clusterManagerClassName != null) {
          // We allow specify a sys prop for the cluster manager factory which overrides ServiceLoader
          try {
            Class<?> clazz = Class.forName(clusterManagerClassName);
            mgr = (ClusterManager)clazz.newInstance();
          } catch (Exception e) {
            throw new IllegalStateException("Failed to instantiate " + clusterManagerClassName, e);
          }
        } else {
          ServiceLoader<ClusterManager> mgrs = ServiceLoader.load(ClusterManager.class);
          if (!mgrs.iterator().hasNext()) {
            throw new IllegalStateException("No ClusterManagerFactory instances found on classpath");
          }
          mgr = mgrs.iterator().next();
        }
        return mgr;
      }
    } else {
      return null;
    }
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
      return ((VertxThread)current).getContext();
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
      // Workaround fo Hazelcast bug https://github.com/hazelcast/hazelcast/issues/5220
      if (clusterManager instanceof ExtendedClusterManager) {
        ExtendedClusterManager ecm = (ExtendedClusterManager)clusterManager;
        ecm.beforeLeave();
      }
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

    deploymentManager.undeployAll(ar -> {
      if (haManager() != null) {
        haManager().stop();
      }
      hostnameResolver.close();
      eventBus.close(ar2 -> {
        closeClusterManager(ar3 -> {
          // Copy set to prevent ConcurrentModificationException
          Set<HttpServer> httpServers = new HashSet<>(sharedHttpServers.values());
          Set<NetServer> netServers = new HashSet<>(sharedNetServers.values());
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
          for (NetServer server : netServers) {
            server.close(serverCloseHandler);
          }
          if (serverCount == 0) {
            deleteCacheDirAndShutdown(completionHandler);
          }
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
  public void deployVerticle(Verticle verticle, DeploymentOptions options, Handler<AsyncResult<String>> completionHandler) {
    boolean closed;
    synchronized (this) {
      closed = this.closed;
    }
    if (closed) {
      completionHandler.handle(Future.failedFuture("Vert.x closed"));
    } else {
      deploymentManager.deployVerticle(verticle, options, completionHandler);
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
    if (haManager() != null && haManager().isEnabled()) {
      haManager().removeFromHA(deploymentID);
    }
    deploymentManager.undeployVerticle(deploymentID, completionHandler);
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
  public <T> void executeBlockingInternal(Action<T> action, Handler<AsyncResult<T>> resultHandler) {
    ContextImpl context = getOrCreateContext();

    context.executeBlocking(action, resultHandler);
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
      haManager.setFailoverCompleteHandler(failoverCompleteHandler);
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
  public void resolveHostname(String hostname, Handler<AsyncResult<InetAddress>> resultHandler) {
    hostnameResolver.resolveHostname(hostname, resultHandler);
  }

  @SuppressWarnings("unchecked")
  private void deleteCacheDirAndShutdown(Handler<AsyncResult<Void>> completionHandler) {
    fileResolver.close(res -> {

      workerPool.close();
      internalBlockingPool.close();

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

  private HAManager haManager() {
    // If reading from different thread possibility that it's been set but not visible - so provide
    // memory barrier
    if (haManager == null && haEnabled) {
      synchronized (this) {
        return haManager;
      }
    } else {
      return haManager;
    }
  }

  private class InternalTimerHandler implements Handler<Void>, Closeable {
    final Handler<Long> handler;
    final boolean periodic;
    final long timerID;
    final ContextImpl context;
    final java.util.concurrent.Future<?> future;

    boolean cancel() {
      metrics.timerEnded(timerID, true);
      return future.cancel(false);
    }

    InternalTimerHandler(long timerID, Handler<Long> runnable, boolean periodic, long delay, ContextImpl context) {
      this.context = context;
      this.timerID = timerID;
      this.handler = runnable;
      this.periodic = periodic;
      EventLoop el = context.nettyEventLoop();
      Runnable toRun = () -> context.runOnContext(this);
      if (periodic) {
        future = el.scheduleAtFixedRate(toRun, delay, delay, TimeUnit.MILLISECONDS);
      } else {
        future = el.schedule(toRun, delay, TimeUnit.MILLISECONDS);
      }
      metrics.timerCreated(timerID);
    }

    public void handle(Void v) {
      try {
        handler.handle(timerID);
      } finally {
        if (!periodic) {
          // Clean up after it's fired
          cleanupNonPeriodic();
        }
      }
    }

    private void cleanupNonPeriodic() {
      VertxImpl.this.timeouts.remove(timerID);
      metrics.timerEnded(timerID, false);
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

    private boolean paused;
    private Long id;
    private Handler<Long> handler;
    private Handler<Void> endHandler;

    public TimeoutStreamImpl(long delay, boolean periodic) {
      this.delay = delay;
      this.periodic = periodic;
    }

    @Override
    public synchronized void handle(Long event) {
      try {
        if (!paused) {
          handler.handle(event);
        }
      } finally {
        if (!periodic && endHandler != null) {
          endHandler.handle(null);
        }
      }
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
      this.paused = true;
      return this;
    }

    @Override
    public synchronized TimeoutStream resume() {
      this.paused = false;
      return this;
    }

    @Override
    public synchronized TimeoutStream endHandler(Handler<Void> endHandler) {
      this.endHandler = endHandler;
      return this;
    }
  }

  class SharedWorkerPool extends WorkerPool {

    private final ExecutorService workerExec;
    private final String name;
    private int refCount = 1;

    public SharedWorkerPool(String name, ExecutorService workerExec, PoolMetrics workerMetrics) {
      super(workerExec, workerMetrics);
      this.workerExec = workerExec;
      this.name = name;
    }

    void release() {
      synchronized (VertxImpl.this) {
        if (--refCount == 0) {
          releaseWorkerExecutor(name);
          if (metrics != null) {
            metrics.close();
          }
          workerExec.shutdownNow();
        }
      }
    }
  }

  @Override
  public NamedWorkerExecutor createWorkerExecutor(String name) {
    return createWorkerExecutor(name, defaultWorkerPoolSize);
  }

  @Override
  public NamedWorkerExecutor createWorkerExecutor(String name, int poolSize) {
    return createWorkerExecutor(name, poolSize, defaultWorkerMaxExecTime);
  }

  @Override
  public synchronized NamedWorkerExecutor createWorkerExecutor(String name, int poolSize, long maxExecuteTime) {
    if (maxExecuteTime < 1) {
      throw new IllegalArgumentException("poolSize must be > 0");
    }
    if (maxExecuteTime < 1) {
      throw new IllegalArgumentException("maxExecuteTime must be > 0");
    }
    SharedWorkerPool sharedWorkerPool = namedWorkerPools.get(name);
    if (sharedWorkerPool == null) {
      ExecutorService workerExec = Executors.newFixedThreadPool(poolSize, new VertxThreadFactory(name + "-", checker, true, maxExecuteTime));
      PoolMetrics workerMetrics = isMetricsEnabled() ? metrics.createMetrics(workerExec, name, poolSize) : null;
      namedWorkerPools.put(name, sharedWorkerPool = new SharedWorkerPool(name, workerExec, workerMetrics));
    } else {
      sharedWorkerPool.refCount++;
    }
    ContextImpl context = getOrCreateContext();
    NamedWorkerExecutor namedExec = new NamedWorkerExecutor(context, sharedWorkerPool);
    context.addCloseHook(namedExec);
    return namedExec;
  }

  synchronized void releaseWorkerExecutor(String name) {
    namedWorkerPools.remove(name);
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
}
