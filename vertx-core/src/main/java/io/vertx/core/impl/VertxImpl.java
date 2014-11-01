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
import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.TimeoutStream;
import io.vertx.core.Verticle;
import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.core.datagram.DatagramSocket;
import io.vertx.core.datagram.DatagramSocketOptions;
import io.vertx.core.datagram.impl.DatagramSocketImpl;
import io.vertx.core.dns.DnsClient;
import io.vertx.core.dns.impl.DnsClientImpl;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.eventbus.impl.EventBusImpl;
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
import io.vertx.core.logging.impl.LoggerFactory;
import io.vertx.core.metrics.impl.DummyVertxMetrics;
import io.vertx.core.metrics.spi.VertxMetrics;
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
import io.vertx.core.spi.cluster.Action;
import io.vertx.core.spi.cluster.ClusterManager;

import java.io.File;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class VertxImpl implements VertxInternal {

  private static final Logger log = LoggerFactory.getLogger(VertxImpl.class);

  static {
    // Netty resource leak detection has a performance overhead and we do not need it in Vert.x
    ResourceLeakDetector.setLevel(ResourceLeakDetector.Level.DISABLED);
    // Use the JDK deflater/inflater by default
    System.setProperty("io.netty.noJdkZlibDecoder", "false");
  }

  private final FileSystem fileSystem = getFileSystem();
  private EventBusImpl eventBus;
  private final SharedData sharedData;
  private final VertxMetrics metrics;

  private ExecutorService workerPool;
  private ExecutorService internalBlockingPool;
  private OrderedExecutorFactory workerOrderedFact;
  private OrderedExecutorFactory internalOrderedFact;
  private EventLoopGroup eventLoopGroup;
  private BlockedThreadChecker checker;

  private Map<ServerID, HttpServerImpl> sharedHttpServers = new HashMap<>();
  private Map<ServerID, NetServerImpl> sharedNetServers = new HashMap<>();

  private final ConcurrentMap<Long, InternalTimerHandler> timeouts = new ConcurrentHashMap<>();
  private final AtomicLong timeoutCounter = new AtomicLong(0);
  private final ClusterManager clusterManager;
  private final DeploymentManager deploymentManager;
  private final FileResolver fileResolver;
  private boolean closed;
  private HAManager haManager;

  VertxImpl() {
    this(new VertxOptions());
  }

  VertxImpl(VertxOptions options) {
    this(options, null);
  }

  VertxImpl(VertxOptions options, Handler<AsyncResult<Vertx>> resultHandler) {
    configurePools(options);
    this.fileResolver = new FileResolver(this);
    this.deploymentManager = new DeploymentManager(this);
    this.metrics = initialiseMetrics(options);
    if (options.isClustered()) {
      this.clusterManager = getClusterManager(options);
      this.clusterManager.setVertx(this);
      this.clusterManager.join(ar -> {
        if (ar.failed()) {
          log.error("Failed to join cluster", ar.cause());
        }
        if (options.isHAEnabled()) {
          haManager = new HAManager(this, deploymentManager, clusterManager, options.getQuorumSize(), options.getHAGroup());
        }
        Vertx inst = this;
        eventBus = new EventBusImpl(this, options.getClusterPingInterval(),
            options.getClusterPingReplyInterval(),  options.getClusterPort(), options.getClusterHost(), clusterManager, res -> {
          if (resultHandler != null) {
            if (res.succeeded()) {
              resultHandler.handle(Future.completedFuture(inst));
            } else {
              resultHandler.handle(Future.completedFuture(res.cause()));
            }
          } else if (res.failed()) {
            log.error("Failed to start event bus", res.cause());
          }
        });
      });
      this.sharedData = new SharedDataImpl(this, clusterManager);
    } else {
      this.clusterManager = null;
      this.sharedData = new SharedDataImpl(this, clusterManager);
      this.eventBus = new EventBusImpl(this);
      if (resultHandler != null) {
        resultHandler.handle(Future.completedFuture(this));
      }
    }
  }

  /**
   * @return The FileSystem implementation for the OS
   */
  protected FileSystem getFileSystem() {
  	return Windows.isWindows() ? new WindowsFileSystem(this) : new FileSystemImpl(this);
  }

  @Override
  public DatagramSocket createDatagramSocket(DatagramSocketOptions options) {
    return new DatagramSocketImpl(this, options);
  }

  public NetServer createNetServer(NetServerOptions options) {
    return new NetServerImpl(this, options);
  }

  public NetClient createNetClient(NetClientOptions options) {
    return new NetClientImpl(this, options);
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

  public HttpClient createHttpClient(HttpClientOptions options) {
    return new HttpClientImpl(this, options);
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

  public Context context() {
    return getOrCreateContext();
  }

  // The background pool is used for making blocking calls to legacy synchronous APIs
  public ExecutorService getWorkerPool() {
    return workerPool;
  }

  public EventLoopGroup getEventLoopGroup() {
    return eventLoopGroup;
  }

  public ContextImpl getOrCreateContext() {
    ContextImpl ctx = getContext();
    if (ctx == null) {
      // We are running embedded - Create a context
      ctx = createEventLoopContext(null, new JsonObject(), Thread.currentThread().getContextClassLoader());
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
  public String metricBaseName() {
    return metrics.baseName();
  }

  @Override
  public Map<String, JsonObject> metrics() {
    String name = metricBaseName();
    return metrics.metrics().entrySet().stream()
      .filter(e -> e.getKey().startsWith(name))
      .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
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

  public EventLoopContext createEventLoopContext(String deploymentID, JsonObject config, ClassLoader tccl) {
    return new EventLoopContext(this, workerOrderedFact.getExecutor(), deploymentID, config, tccl);
  }

  @Override
  public DnsClient createDnsClient(int port, String host) {
    return new DnsClientImpl(this, port, host);
  }

  private VertxMetrics initialiseMetrics(VertxOptions options) {
    if (options.isMetricsEnabled()) {
      ServiceLoader<VertxMetricsFactory> factories = ServiceLoader.load(VertxMetricsFactory.class);
      if (factories.iterator().hasNext()) {
        VertxMetricsFactory factory = factories.iterator().next();
        return factory.metrics(this, options);
      } else {
        log.warn("Metrics has been set to enabled but no VertxMetricsFactory found on classpath");
        return new DummyVertxMetrics();
      }
    } else {
      return new DummyVertxMetrics();
    }
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
    InternalTimerHandler task = new InternalTimerHandler(timerId, handler, periodic, context);
    Runnable toRun = () -> context.execute(task, false);
    EventLoop el = context.getEventLoop();
    java.util.concurrent.Future<?> future;
    if (periodic) {
      future = el.scheduleAtFixedRate(toRun, delay, delay, TimeUnit.MILLISECONDS);
    } else {
      future = el.schedule(toRun, delay, TimeUnit.MILLISECONDS);
    }
    task.future = future;
    timeouts.put(timerId, task);
    context.addCloseHook(task);
    return timerId;
  }

  public ContextImpl createWorkerContext(boolean multiThreaded, String deploymentID, JsonObject config,
                                         ClassLoader tccl) {
    if (multiThreaded) {
      return new MultiThreadedWorkerContext(this, internalOrderedFact.getExecutor(), workerPool, deploymentID, config, tccl);
    } else {
      return new WorkerContext(this, internalOrderedFact.getExecutor(), workerOrderedFact.getExecutor(), deploymentID,
                               config, tccl);
    }
  }

  public void setContext(ContextImpl context) {
    Thread current = Thread.currentThread();
    if (current instanceof VertxThread) {
      ((VertxThread)current).setContext(context);
    }
    if (context != null) {
      context.setTCCL();
    } else {
      Thread.currentThread().setContextClassLoader(null);
    }
  }

  public ContextImpl getContext() {
    Thread current = Thread.currentThread();
    if (current instanceof VertxThread) {
      return ((VertxThread)current).getContext();
    }
    return null;
  }

  @Override
  public void close() {
    close(null);
  }

  @Override
  public synchronized void close(Handler<AsyncResult<Void>> completionHandler) {
    if (closed || eventBus == null) {
      runOnContext(v -> {
        completionHandler.handle(Future.completedFuture());
      });
    }
    closed = true;
    deploymentManager.undeployAll(ar -> {
      if (haManager != null) {
        haManager.stop();
      }
      eventBus.close(ar2 -> {

        if (sharedHttpServers != null) {
          // Copy set to prevent ConcurrentModificationException
          for (HttpServer server : new HashSet<>(sharedHttpServers.values())) {
            server.close();
          }
          sharedHttpServers.clear();
        }

        if (sharedNetServers != null) {
          // Copy set to prevent ConcurrentModificationException
          for (NetServer server : new HashSet<>(sharedNetServers.values())) {
            server.close();
          }
          sharedNetServers.clear();
        }

        fileResolver.deleteCacheDir(res -> {

          if (workerPool != null) {
            workerPool.shutdownNow();
          }

          if (eventLoopGroup != null) {
            eventLoopGroup.shutdownNow();
          }

          if (metrics != null) {
            metrics.close();
          }

          checker.close();

          setContext(null);

          if (completionHandler != null) {
            // Call directly - we have no context
            completionHandler.handle(Future.completedFuture());
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
  public void deployVerticle(String identifier, Handler<AsyncResult<String>> completionHandler) {
    deployVerticle(identifier, new DeploymentOptions(), completionHandler);
  }

  @Override
  public void deployVerticle(Verticle verticle, DeploymentOptions options) {
    deployVerticle(verticle, options, null);
  }

  @Override
  public void deployVerticle(Verticle verticle, DeploymentOptions options, Handler<AsyncResult<String>> completionHandler) {
    deploymentManager.deployVerticle(verticle, options, completionHandler);
  }

  @Override
  public void deployVerticle(String identifier) {
    deployVerticle(identifier, new DeploymentOptions(), null);
  }

  @Override
  public void deployVerticle(String identifier, DeploymentOptions options) {
    deployVerticle(identifier, options, null);
  }

  @Override
  public void deployVerticle(String identifier, DeploymentOptions options, Handler<AsyncResult<String>> completionHandler) {
    if (options.isHa() && haManager != null) {
      haManager.deployVerticle(identifier, options, completionHandler);
    } else {
      deploymentManager.deployVerticle(identifier, options, completionHandler);
    }
  }

  @Override
  public String getNodeID() {
    return clusterManager.getNodeID();
  }

  @Override
  public void undeployVerticle(String deploymentID) {
    undeployVerticle(deploymentID, res -> {
    });
  }

  @Override
  public void undeployVerticle(String deploymentID, Handler<AsyncResult<Void>> completionHandler) {
    if (haManager != null) {
      haManager.removeFromHA(deploymentID);
    }
    deploymentManager.undeployVerticle(deploymentID, completionHandler);
  }

  @Override
  public Set<String> deployments() {
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
  public <T> void executeBlocking(Action<T> action, Handler<AsyncResult<T>> resultHandler) {
    ContextImpl context = getOrCreateContext();
    context.executeBlocking(action, resultHandler);
  }

  // For testing
  public void simulateKill() {
    if (haManager != null) {
      haManager.simulateKill();
    }
  }

  @Override
  public void simulateEventBusUnresponsive() {
    eventBus.simulateUnresponsive();
  }

  @Override
  public Deployment getDeployment(String deploymentID) {
    return deploymentManager.getDeployment(deploymentID);
  }

  @Override
  public void failoverCompleteHandler(Handler<Boolean> failoverCompleteHandler) {
    if (haManager != null) {
      haManager.failoverCompleteHandler(failoverCompleteHandler);
    }
  }

  @Override
  public boolean isKilled() {
    return haManager.isKilled();
  }

  @Override
  public void failDuringFailover(boolean fail) {
    if (haManager != null) {
      haManager.failDuringFailover(fail);
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

  private void configurePools(VertxOptions options) {
    checker = new BlockedThreadChecker(options.getBlockedThreadCheckPeriod(), options.getMaxEventLoopExecuteTime(),
                                       options.getMaxWorkerExecuteTime());
    eventLoopGroup = new NioEventLoopGroup(options.getEventLoopPoolSize(),
                                           new VertxThreadFactory("vert.x-eventloop-thread-", checker, false));
    workerPool = Executors.newFixedThreadPool(options.getWorkerPoolSize(),
      new VertxThreadFactory("vert.x-worker-thread-", checker, true));
    internalBlockingPool = Executors.newFixedThreadPool(options.getInternalBlockingPoolSize(),
      new VertxThreadFactory("vert.x-internal-blocking-", checker, true));
    workerOrderedFact = new OrderedExecutorFactory(workerPool);
    internalOrderedFact = new OrderedExecutorFactory(internalBlockingPool);
  }

  private class InternalTimerHandler implements ContextTask, Closeable {
    final Handler<Long> handler;
    final boolean periodic;
    final long timerID;
    final ContextImpl context;
    volatile java.util.concurrent.Future<?> future;
    boolean cancelled;

    boolean cancel() {
      cancelled = true;
      metrics.timerEnded(timerID, true);
      return future.cancel(false);
    }

    InternalTimerHandler(long timerID, Handler<Long> runnable, boolean periodic, ContextImpl context) {
      this.context = context;
      this.timerID = timerID;
      this.handler = runnable;
      this.periodic = periodic;
      metrics.timerCreated(timerID);
    }

    public void run() throws Exception {
      if (!cancelled) {
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
      metrics.timerEnded(timerID, false);
      ContextImpl context = getContext();
      context.removeCloseHook(this);
    }

    // Called via Context close hook when Verticle is undeployed
    public void close(Handler<AsyncResult<Void>> completionHandler) {
      VertxImpl.this.timeouts.remove(timerID);
      cancel();
      completionHandler.handle(Future.completedFuture());
    }

  }

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
    public void handle(Long event) {
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
      handler(null);
    }

    @Override
    public TimeoutStream handler(Handler<Long> handler) {
      if (handler != null) {
        if (id != null) {
          throw new IllegalStateException();
        }
        this.handler = handler;
        id = scheduleTimeout(getOrCreateContext(), this, delay, periodic);
      } else {
        if (id != null) {
          VertxImpl.this.cancelTimer(id);
          if (endHandler != null) {
            endHandler.handle(null);
          }
        }
      }
      return this;
    }

    @Override
    public TimeoutStream pause() {
      this.paused = true;
      return this;
    }

    @Override
    public TimeoutStream resume() {
      this.paused = false;
      return null;
    }

    @Override
    public TimeoutStream endHandler(Handler<Void> endHandler) {
      this.endHandler = endHandler;
      return this;
    }
  }
}
