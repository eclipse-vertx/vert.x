/*
 * Copyright (c) 2011-2021 Contributors to the Eclipse Foundation
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
import io.vertx.core.Future;
import io.vertx.core.*;
import io.vertx.core.datagram.DatagramSocket;
import io.vertx.core.datagram.DatagramSocketOptions;
import io.vertx.core.datagram.impl.DatagramSocketImpl;
import io.vertx.core.dns.AddressResolverOptions;
import io.vertx.core.dns.DnsClient;
import io.vertx.core.dns.DnsClientOptions;
import io.vertx.core.dns.impl.DnsClientImpl;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.eventbus.impl.EventBusImpl;
import io.vertx.core.eventbus.impl.EventBusInternal;
import io.vertx.core.eventbus.impl.clustered.ClusteredEventBus;
import io.vertx.core.file.FileSystem;
import io.vertx.core.http.*;
import io.vertx.core.http.impl.CleanableHttpClient;
import io.vertx.core.http.impl.HttpClientInternal;
import io.vertx.core.impl.btc.BlockedThreadChecker;
import io.vertx.core.net.*;
import io.vertx.core.net.impl.*;
import io.vertx.core.impl.transports.JDKTransport;
import io.vertx.core.spi.file.FileResolver;
import io.vertx.core.file.impl.FileSystemImpl;
import io.vertx.core.file.impl.WindowsFileSystem;
import io.vertx.core.http.impl.HttpClientImpl;
import io.vertx.core.http.impl.HttpServerImpl;
import io.vertx.core.impl.future.PromiseInternal;
import io.vertx.core.impl.logging.Logger;
import io.vertx.core.impl.logging.LoggerFactory;
import io.vertx.core.dns.impl.DnsAddressResolverProvider;
import io.vertx.core.spi.transport.Transport;
import io.vertx.core.shareddata.SharedData;
import io.vertx.core.shareddata.impl.SharedDataImpl;
import io.vertx.core.spi.ExecutorServiceFactory;
import io.vertx.core.spi.VerticleFactory;
import io.vertx.core.spi.VertxThreadFactory;
import io.vertx.core.spi.cluster.ClusterManager;
import io.vertx.core.spi.cluster.NodeSelector;
import io.vertx.core.spi.metrics.Metrics;
import io.vertx.core.spi.metrics.MetricsProvider;
import io.vertx.core.spi.metrics.PoolMetrics;
import io.vertx.core.spi.metrics.VertxMetrics;
import io.vertx.core.spi.tracing.VertxTracer;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.ref.Cleaner;
import java.lang.ref.WeakReference;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
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

  private static String version;

  /**
   * Default shared cleaner for Vert.x
   */
  private static final Cleaner cleaner = Cleaner.create();

  /**
   * Context dispatch info for context running with non vertx threads (Loom).
   */
  static final ThreadLocal<ContextDispatch> nonVertxContextDispatch = new ThreadLocal<>();
  // We need to initialize nonVertxContextDispatch before log because log may use the context on startup.
  // https://github.com/eclipse-vertx/vert.x/issues/4611

  private static final Logger log = LoggerFactory.getLogger(VertxImpl.class);

  private static final String CLUSTER_MAP_NAME = "__vertx.haInfo";
  private static final String NETTY_IO_RATIO_PROPERTY_NAME = "vertx.nettyIORatio";
  private static final int NETTY_IO_RATIO = Integer.getInteger(NETTY_IO_RATIO_PROPERTY_NAME, 50);

  static {
    // Disable Netty's resource leak detection to reduce the performance overhead if not set by user
    // Supports both the default netty leak detection system property and the deprecated one
    if (System.getProperty("io.netty.leakDetection.level") == null &&
        System.getProperty("io.netty.leakDetectionLevel") == null) {
      ResourceLeakDetector.setLevel(ResourceLeakDetector.Level.DISABLED);
    }
  }

  private final FileSystem fileSystem = getFileSystem();
  private final SharedData sharedData;
  private final VertxMetrics metrics;
  private final ConcurrentMap<Long, InternalTimerHandler> timeouts = new ConcurrentHashMap<>();
  private final AtomicLong timeoutCounter = new AtomicLong(0);
  private final ClusterManager clusterManager;
  private final NodeSelector nodeSelector;
  private final DeploymentManager deploymentManager;
  private final VerticleManager verticleManager;
  private final FileResolver fileResolver;
  private final Map<ServerID, HttpServerImpl> sharedHttpServers = new HashMap<>();
  private final Map<ServerID, NetServerImpl> sharedNetServers = new HashMap<>();
  final WorkerPool workerPool;
  final WorkerPool internalWorkerPool;
  private final VertxThreadFactory threadFactory;
  private final ExecutorServiceFactory executorServiceFactory;
  private final ThreadFactory eventLoopThreadFactory;
  private final EventLoopGroup eventLoopGroup;
  private final EventLoopGroup acceptorEventLoopGroup;
  private final BlockedThreadChecker checker;
  private final AddressResolver addressResolver;
  private final AddressResolverOptions addressResolverOptions;
  private final EventBusInternal eventBus;
  private volatile HAManager haManager;
  private boolean closed;
  private volatile Handler<Throwable> exceptionHandler;
  private final int defaultWorkerPoolSize;
  private final long maxWorkerExecTime;
  private final TimeUnit maxWorkerExecTimeUnit;
  private final long maxEventLoopExecTime;
  private final TimeUnit maxEventLoopExecTimeUnit;
  private final CloseFuture closeFuture;
  private final Transport transport;
  private final VertxTracer tracer;
  private final ThreadLocal<WeakReference<ContextInternal>> stickyContext = new ThreadLocal<>();
  private final boolean disableTCCL;
  private final Boolean useDaemonThread;

  VertxImpl(VertxOptions options, ClusterManager clusterManager, NodeSelector nodeSelector, VertxMetrics metrics,
            VertxTracer<?, ?> tracer, Transport transport, FileResolver fileResolver, VertxThreadFactory threadFactory,
            ExecutorServiceFactory executorServiceFactory) {
    // Sanity check
    if (Vertx.currentContext() != null) {
      log.warn("You're already on a Vert.x context, are you sure you want to create a new Vertx instance?");
    }

    Boolean useDaemonThread = options.getUseDaemonThread();
    int workerPoolSize = options.getWorkerPoolSize();
    int internalBlockingPoolSize = options.getInternalBlockingPoolSize();
    BlockedThreadChecker checker = new BlockedThreadChecker(options.getBlockedThreadCheckInterval(), options.getBlockedThreadCheckIntervalUnit(), options.getWarningExceptionTime(), options.getWarningExceptionTimeUnit());
    long maxEventLoopExecuteTime = options.getMaxEventLoopExecuteTime();
    TimeUnit maxEventLoopExecuteTimeUnit = options.getMaxEventLoopExecuteTimeUnit();
    ThreadFactory acceptorEventLoopThreadFactory = createThreadFactory(threadFactory, checker, useDaemonThread, maxEventLoopExecuteTime, maxEventLoopExecuteTimeUnit, "vert.x-acceptor-thread-", false);
    TimeUnit maxWorkerExecuteTimeUnit = options.getMaxWorkerExecuteTimeUnit();
    long maxWorkerExecuteTime = options.getMaxWorkerExecuteTime();

    ThreadFactory workerThreadFactory = createThreadFactory(threadFactory, checker, useDaemonThread, maxWorkerExecuteTime, maxWorkerExecuteTimeUnit, "vert.x-worker-thread-", true);
    ExecutorService workerExec = executorServiceFactory.createExecutor(workerThreadFactory, workerPoolSize, workerPoolSize);
    PoolMetrics workerPoolMetrics = metrics != null ? metrics.createPoolMetrics("worker", "vert.x-worker-thread", options.getWorkerPoolSize()) : null;
    ThreadFactory internalWorkerThreadFactory = createThreadFactory(threadFactory, checker, useDaemonThread, maxWorkerExecuteTime, maxWorkerExecuteTimeUnit, "vert.x-internal-blocking-", true);
    ExecutorService internalWorkerExec = executorServiceFactory.createExecutor(internalWorkerThreadFactory, internalBlockingPoolSize, internalBlockingPoolSize);
    PoolMetrics internalBlockingPoolMetrics = metrics != null ? metrics.createPoolMetrics("worker", "vert.x-internal-blocking", internalBlockingPoolSize) : null;

    closeFuture = new CloseFuture(log);
    maxEventLoopExecTime = maxEventLoopExecuteTime;
    maxEventLoopExecTimeUnit = maxEventLoopExecuteTimeUnit;
    eventLoopThreadFactory = createThreadFactory(threadFactory, checker, useDaemonThread, maxEventLoopExecTime, maxEventLoopExecTimeUnit, "vert.x-eventloop-thread-", false);
    eventLoopGroup = transport.eventLoopGroup(Transport.IO_EVENT_LOOP_GROUP, options.getEventLoopPoolSize(), eventLoopThreadFactory, NETTY_IO_RATIO);
    // The acceptor event loop thread needs to be from a different pool otherwise can get lags in accepted connections
    // under a lot of load
    acceptorEventLoopGroup = transport.eventLoopGroup(Transport.ACCEPTOR_EVENT_LOOP_GROUP, 1, acceptorEventLoopThreadFactory, 100);
    internalWorkerPool = new WorkerPool(internalWorkerExec, internalBlockingPoolMetrics);
    workerPool = new WorkerPool(workerExec, workerPoolMetrics);
    defaultWorkerPoolSize = options.getWorkerPoolSize();
    maxWorkerExecTime = maxWorkerExecuteTime;
    maxWorkerExecTimeUnit = maxWorkerExecuteTimeUnit;
    disableTCCL = options.getDisableTCCL();
    this.checker = checker;
    this.useDaemonThread = useDaemonThread;
    this.executorServiceFactory = executorServiceFactory;
    this.threadFactory = threadFactory;
    this.metrics = metrics;
    this.transport = transport;
    this.fileResolver = fileResolver;
    this.addressResolverOptions = options.getAddressResolverOptions();
    this.addressResolver = new AddressResolver(this, options.getAddressResolverOptions());
    this.tracer = tracer == VertxTracer.NOOP ? null : tracer;
    this.clusterManager = clusterManager;
    this.nodeSelector = nodeSelector;
    this.eventBus = clusterManager != null ? new ClusteredEventBus(this, options, clusterManager, nodeSelector) : new EventBusImpl(this);
    this.sharedData = new SharedDataImpl(this, clusterManager);
    this.deploymentManager = new DeploymentManager(this);
    this.verticleManager = new VerticleManager(this, deploymentManager);
  }

  void init() {
    eventBus.start(Promise.promise());
    if (metrics != null) {
      metrics.vertxCreated(this);
    }
  }

  Future<Vertx> initClustered(VertxOptions options) {
    nodeSelector.init(this, clusterManager);
    clusterManager.init(this, nodeSelector);
    Promise<Void> initPromise = Promise.promise();
    Promise<Void> joinPromise = Promise.promise();
    joinPromise.future().onComplete(ar -> {
      if (ar.succeeded()) {
        createHaManager(options, initPromise);
      } else {
        initPromise.fail(ar.cause());
      }
    });
    clusterManager.join(joinPromise);
    return initPromise
      .future()
      .transform(ar -> {
        if (ar.succeeded()) {
          if (metrics != null) {
            metrics.vertxCreated(this);
          }
          return Future.succeededFuture(this);
        } else {
          log.error("Failed to initialize clustered Vert.x", ar.cause());
          return close().transform(v -> Future.failedFuture(ar.cause()));
        }
      });
  }

  private void createHaManager(VertxOptions options, Promise<Void> initPromise) {
    if (options.isHAEnabled()) {
      this.executeBlocking(() -> {
        haManager = new HAManager(this, deploymentManager, verticleManager, clusterManager, clusterManager.getSyncMap(CLUSTER_MAP_NAME), options.getQuorumSize(), options.getHAGroup());
        return haManager;
      }, false).onComplete(ar -> {
        if (ar.succeeded()) {
          startEventBus(true, initPromise);
        } else {
          initPromise.fail(ar.cause());
        }
      });
    } else {
      startEventBus(false, initPromise);
    }
  }

  private void startEventBus(boolean haEnabled, Promise<Void> initPromise) {
    Promise<Void> promise = Promise.promise();
    eventBus.start(promise);
    promise.future().onComplete(ar -> {
      if (ar.succeeded()) {
        if (haEnabled) {
          initializeHaManager(initPromise);
        } else {
          initPromise.complete();
        }
      } else {
        initPromise.fail(ar.cause());
      }
    });
  }

  private void initializeHaManager(Promise<Void> initPromise) {
    this.<Void>executeBlocking(() -> {
      // Init the manager (i.e register listener and check the quorum)
      // after the event bus has been fully started and updated its state
      // it will have also set the clustered changed view handler on the ha manager
      haManager.init();
      return null;
    }, false).onComplete(initPromise);
  }

  /**
   * @return The FileSystem implementation for the OS
   */
  protected FileSystem getFileSystem() {
    return Utils.isWindows() ? new WindowsFileSystem(this) : new FileSystemImpl(this);
  }

  @Override
  public long maxEventLoopExecTime() {
    return maxEventLoopExecTime;
  }

  @Override
  public TimeUnit maxEventLoopExecTimeUnit() {
    return maxEventLoopExecTimeUnit;
  }

  @Override
  public DatagramSocket createDatagramSocket(DatagramSocketOptions options) {
    CloseFuture closeFuture = new CloseFuture(log);
    DatagramSocketImpl so = DatagramSocketImpl.create(this, closeFuture, options);
    closeFuture.add(so);
    CloseFuture fut = resolveCloseFuture();
    fut.add(closeFuture);
    return so;
  }

  public NetServer createNetServer(NetServerOptions options) {
    return new NetServerImpl(this, options);
  }

  public NetClient createNetClient(NetClientOptions options) {
    CloseFuture fut = resolveCloseFuture();
    NetClientBuilder builder = new NetClientBuilder(this, options);
    builder.metrics(metricsSPI() != null ? metricsSPI().createNetClientMetrics(options) : null);
    NetClientInternal netClient = builder.build();
    fut.add(netClient);
    return new CleanableNetClient(netClient, cleaner);
  }

  @Override
  public Transport transport() {
    return transport;
  }

  @Override
  public boolean isNativeTransportEnabled() {
    return !(transport instanceof JDKTransport);
  }

  @Override
  public Throwable unavailableNativeTransportCause() {
    if (isNativeTransportEnabled()) {
      return null;
    }
    return transport.unavailabilityCause();
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
    CloseFuture cf = resolveCloseFuture();
    HttpClient client;
    Closeable closeable;
    if (options.isShared()) {
      CloseFuture closeFuture = new CloseFuture();
      client = createSharedResource("__vertx.shared.httpClients", options.getName(), closeFuture, cf_ -> {
        HttpClientImpl impl = new HttpClientImpl(this, options);
        cf_.add(completion -> impl.close().onComplete(completion));
        return impl;
      });
      client = new CleanableHttpClient((HttpClientInternal) client, cleaner, (timeout, timeunit) -> closeFuture.close());
      closeable = closeFuture;
    } else {
      HttpClientImpl impl = new HttpClientImpl(this, options);
      closeable = impl;
      client = new CleanableHttpClient(impl, cleaner, impl::close);
    }
    cf.add(closeable);
    return client;
  }

  public EventBus eventBus() {
    return eventBus;
  }

  @Override
  public long setPeriodic(long initialDelay, long delay, Handler<Long> handler) {
    ContextInternal ctx = getOrCreateContext();
    return scheduleTimeout(ctx, true, initialDelay, delay, TimeUnit.MILLISECONDS, ctx.isDeployment(), handler);
  }

  public long setTimer(long delay, Handler<Long> handler) {
    ContextInternal ctx = getOrCreateContext();
    return scheduleTimeout(ctx, false, delay, TimeUnit.MILLISECONDS, ctx.isDeployment(), handler);
  }

  @Override
  public <T> PromiseInternal<T> promise() {
    ContextInternal context = getOrCreateContext();
    return context.promise();
  }

  public <T> PromiseInternal<T> promise(Promise<T> p) {
    if (p instanceof PromiseInternal) {
      PromiseInternal<T> promise = (PromiseInternal<T>) p;
      if (promise.context() != null) {
        return promise;
      }
    }
    PromiseInternal<T> promise = promise();
    promise.future().onComplete(p);
    return promise;
  }

  public void runOnContext(Handler<Void> task) {
    ContextInternal context = getOrCreateContext();
    context.runOnContext(task);
  }

  // The background pool is used for making blocking calls to legacy synchronous APIs
  public WorkerPool getWorkerPool() {
    return workerPool;
  }

  @Override
  public WorkerPool getInternalWorkerPool() {
    return internalWorkerPool;
  }

  public EventLoopGroup getEventLoopGroup() {
    return eventLoopGroup;
  }

  public EventLoopGroup getAcceptorEventLoopGroup() {
    return acceptorEventLoopGroup;
  }

  public ContextInternal getOrCreateContext() {
    ContextInternal ctx = getContext();
    if (ctx == null) {
      // We are running embedded - Create a context
      ctx = createEventLoopContext();
      stickyContext.set(new WeakReference<>(ctx));
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
  public <S extends TCPServerBase> Map<ServerID, S> sharedTCPServers(Class<S> type) {
    if (NetServerImpl.class.isAssignableFrom(type)) {
      return (Map<ServerID, S>) sharedNetServers;
    } else if (HttpServerImpl.class.isAssignableFrom(type)) {
      return (Map<ServerID, S>) sharedHttpServers;
    } else {
      throw new IllegalStateException();
    }
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
    InternalTimerHandler handler = timeouts.get(id);
    if (handler != null) {
      return handler.cancel();
    } else {
      return false;
    }
  }

  @Override
  public EventLoopContext createEventLoopContext(Deployment deployment, CloseFuture closeFuture, WorkerPool workerPool, ClassLoader tccl) {
    return new EventLoopContext(this, eventLoopGroup.next(), internalWorkerPool, workerPool != null ? workerPool : this.workerPool, deployment, closeFuture, disableTCCL ? null : tccl);
  }

  @Override
  public EventLoopContext createEventLoopContext(EventLoop eventLoop, WorkerPool workerPool, ClassLoader tccl) {
    return new EventLoopContext(this, eventLoop, internalWorkerPool, workerPool != null ? workerPool : this.workerPool, null, closeFuture, disableTCCL ? tccl : null);
  }

  @Override
  public EventLoopContext createEventLoopContext() {
    return createEventLoopContext(null, closeFuture, null, Thread.currentThread().getContextClassLoader());
  }

  @Override
  public WorkerContext createWorkerContext(Deployment deployment, CloseFuture closeFuture, WorkerPool workerPool, ClassLoader tccl) {
    return new WorkerContext(this, internalWorkerPool, workerPool != null ? workerPool : this.workerPool, deployment, closeFuture, disableTCCL ? null : tccl);
  }

  @Override
  public WorkerContext createWorkerContext() {
    return createWorkerContext(null, closeFuture, null, Thread.currentThread().getContextClassLoader());
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
      DnsAddressResolverProvider provider = DnsAddressResolverProvider.create(this, addressResolverOptions);
      InetSocketAddress address = provider.nameServerAddresses().get(0);
      // provide the host and port
      options = new DnsClientOptions(options)
      .setHost(address.getAddress().getHostAddress())
      .setPort(address.getPort());
    }
    return new DnsClientImpl(this, options);
  }

  private long scheduleTimeout(ContextInternal context,
                              boolean periodic,
                              long initialDelay,
                              long delay,
                              TimeUnit timeUnit,
                              boolean addCloseHook,
                              Handler<Long> handler) {
    if (delay < 1) {
      throw new IllegalArgumentException("Cannot schedule a timer with delay < 1 ms");
    }
    if (initialDelay < 0) {
      throw new IllegalArgumentException("Cannot schedule a timer with initialDelay < 0");
    }
    long timerId = timeoutCounter.getAndIncrement();
    InternalTimerHandler task = new InternalTimerHandler(timerId, handler, periodic, context);
    timeouts.put(timerId, task);
    if (addCloseHook) {
      context.addCloseHook(task);
    }
    EventLoop el = context.nettyEventLoop();
    if (periodic) {
      task.future = el.scheduleAtFixedRate(task, initialDelay, delay, timeUnit);
    } else {
      task.future = el.schedule(task, delay, timeUnit);
    }
    return task.id;
  }

  public long scheduleTimeout(ContextInternal context,
                                              boolean periodic,
                                              long delay,
                                              TimeUnit timeUnit,
                                              boolean addCloseHook,
                                              Handler<Long> handler) {
    return scheduleTimeout(context, periodic, delay, delay, timeUnit, addCloseHook, handler);
  }

  public ContextInternal getContext() {
    ContextInternal context = ContextInternal.current();
    if (context != null && context.owner() == this) {
      return context;
    } else {
      WeakReference<ContextInternal> ref = stickyContext.get();
      return ref != null ? ref.get() : null;
    }
  }

  public ClusterManager getClusterManager() {
    return clusterManager;
  }

  private Future<Void> closeClusterManager() {
    Future<Void> fut;
    if (clusterManager != null) {
      Promise<Void> leavePromise = getOrCreateContext().promise();
      clusterManager.leave(leavePromise);
      fut = leavePromise.future();
    } else {
      fut = getOrCreateContext().succeededFuture();
    }
    return fut.transform(ar -> {
      if (ar.failed()) {
        log.error("Failed to leave cluster", ar.cause());
      }
      return Future.succeededFuture();
    });
  }

  @Override
  public synchronized Future<Void> close() {
    // Create this promise purposely without a context because the close operation will close thread pools
    if (closed || eventBus == null) {
      // Just call the handler directly since pools shutdown
      return Future.succeededFuture();
    }
    closed = true;
    Future<Void> fut = closeFuture
      .close()
      .transform(ar -> deploymentManager.undeployAll());
    if (haManager != null) {
      fut = fut.transform(ar -> executeBlocking(() -> {
        haManager.stop();
        return null;
      }, false));
    }
    fut = fut
      .transform(ar -> addressResolver.close())
      .transform(ar -> Future.future(h -> eventBus.close((Promise) h)))
      .transform(ar -> closeClusterManager())
      .transform(ar -> {
        Promise<Void> promise = Promise.promise();
        deleteCacheDirAndShutdown(promise);
        return promise.future();
      });
    Future<Void> val = fut;
    Promise<Void> p = Promise.promise();
    val.onComplete(ar -> {
      eventLoopThreadFactory.newThread(() -> {
        if (ar.succeeded()) {
          p.complete();
        } else {
          p.fail(ar.cause());
        }
      }).start();
    });
    return p.future();
  }

  @Override
  public Future<String> deployVerticle(String name, DeploymentOptions options) {
    if (options.isHa() && haManager() != null) {
      Promise<String> promise = getOrCreateContext().promise();
      haManager().deployVerticle(name, options, promise);
      return promise.future();
    } else {
      return verticleManager.deployVerticle(name, options).map(Deployment::deploymentID);
    }
  }

  @Override
  public Future<String> deployVerticle(Verticle verticle, DeploymentOptions options) {
    if (options.getInstances() != 1) {
      throw new IllegalArgumentException("Can't specify > 1 instances for already created verticle");
    }
    return deployVerticle((Callable<Verticle>) () -> verticle, options);
  }

  @Override
  public Future<String> deployVerticle(Class<? extends Verticle> verticleClass, DeploymentOptions options) {
    return deployVerticle((Callable<Verticle>) verticleClass::newInstance, options);
  }

  @Override
  public Future<String> deployVerticle(Supplier<Verticle> verticleSupplier, DeploymentOptions options) {
    return deployVerticle((Callable<Verticle>) verticleSupplier::get, options);
  }

  private Future<String> deployVerticle(Callable<Verticle> verticleSupplier, DeploymentOptions options) {
    boolean closed;
    synchronized (this) {
      closed = this.closed;
    }
    if (closed) {
      // If we are closed use a context less future
      return Future.failedFuture("Vert.x closed");
    } else {
      return deploymentManager.deployVerticle(verticleSupplier, options);
    }
  }

  @Override
  public Future<Void> undeploy(String deploymentID) {
    Future<Void> future;
    HAManager haManager = haManager();
    if (haManager != null) {
      future = this.executeBlocking(() -> {
        haManager.removeFromHA(deploymentID);
        return null;
      }, false);
    } else {
      future = getOrCreateContext().succeededFuture();
    }
    return future.compose(v -> deploymentManager.undeployVerticle(deploymentID));
  }

  @Override
  public Set<String> deploymentIDs() {
    return deploymentManager.deployments();
  }

  @Override
  public void registerVerticleFactory(VerticleFactory factory) {
    verticleManager.registerVerticleFactory(factory);
  }

  @Override
  public void unregisterVerticleFactory(VerticleFactory factory) {
    verticleManager.unregisterVerticleFactory(factory);
  }

  @Override
  public Set<VerticleFactory> verticleFactories() {
    return verticleManager.verticleFactories();
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
  public Future<InetAddress> resolveAddress(String hostname) {
    return addressResolver.resolveHostname(hostname);
  }

  @Override
  public AddressResolver addressResolver() {
    return addressResolver;
  }

  @Override
  public AddressResolverGroup<InetSocketAddress> nettyAddressResolverGroup() {
    return addressResolver.nettyAddressResolverGroup();
  }

  @Override
  public FileResolver fileResolver() {
    return fileResolver;
  }

  @Override
  public BlockedThreadChecker blockedThreadChecker() {
    return checker;
  }

  @SuppressWarnings("unchecked")
  private void deleteCacheDirAndShutdown(Promise<Void> promise) {
    executeBlockingInternal(() -> {
      fileResolver.close();
      return null;
    }).onComplete(ar -> {
      workerPool.close();
      internalWorkerPool.close();
      List<WorkerPool> objects = SharedResourceHolder.clearSharedResource(this, "__vertx.shared.workerPools");
      for (WorkerPool workerPool : objects) {
        workerPool.close();
      }
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
              if (tracer != null) {
                tracer.close();
              }
              checker.close();
              eventLoopThreadFactory.newThread(promise::complete).start();
            }
          });
        }
      });
    });
  }

  public HAManager haManager() {
    return haManager;
  }

  /**
   * Timers are stored in the {@link #timeouts} map at creation time.
   * <p/>
   * Timers are removed from the {@link #timeouts} map when they are cancelled or are fired. The thread
   * removing the timer successfully owns the timer termination (i.e cancel or timer) to avoid race conditions
   * between timeout and cancellation.
   * <p/>
   * This class does not rely on the internal {@link #future} for the termination to handle the worker case
   * since the actual timer {@link #handler} execution is scheduled when the {@link #future} executes.
   */
  class InternalTimerHandler implements Handler<Void>, Closeable, Runnable {

    private final Handler<Long> handler;
    private final boolean periodic;
    private final long id;
    private final ContextInternal context;
    private final AtomicBoolean disposed = new AtomicBoolean();
    private volatile java.util.concurrent.Future<?> future;

    InternalTimerHandler(long id, Handler<Long> runnable, boolean periodic, ContextInternal context) {
      this.context = context;
      this.id = id;
      this.handler = runnable;
      this.periodic = periodic;
    }

    @Override
    public void run() {
      context.emit(this);
    }

    public void handle(Void v) {
      if (periodic) {
        if (!disposed.get()) {
          handler.handle(id);
        }
      } else if (disposed.compareAndSet(false, true)) {
        timeouts.remove(id);
        try {
          handler.handle(id);
        } finally {
          // Clean up after it's fired
          context.removeCloseHook(this);
        }
      }
    }

    private boolean cancel() {
      boolean cancelled = tryCancel();
      if (cancelled) {
        if (context.isDeployment()) {
          context.removeCloseHook(this);
        }
      }
      return cancelled;
    }

    private boolean tryCancel() {
      if  (disposed.compareAndSet(false, true)) {
        timeouts.remove(id);
        future.cancel(false);
        return true;
      } else {
        return false;
      }
    }

    // Called via Context close hook when Verticle is undeployed
    public void close(Promise<Void> completion) {
      tryCancel();
      completion.complete();
    }
  }

  @Override
  public WorkerExecutorImpl createSharedWorkerExecutor(String name) {
    return createSharedWorkerExecutor(name, defaultWorkerPoolSize);
  }

  @Override
  public WorkerExecutorImpl createSharedWorkerExecutor(String name, int poolSize) {
    return createSharedWorkerExecutor(name, poolSize, maxWorkerExecTime);
  }

  @Override
  public synchronized WorkerExecutorImpl createSharedWorkerExecutor(String name, int poolSize, long maxExecuteTime) {
    return createSharedWorkerExecutor(name, poolSize, maxExecuteTime, maxWorkerExecTimeUnit);
  }

  @Override
  public synchronized WorkerExecutorImpl createSharedWorkerExecutor(String name, int poolSize, long maxExecuteTime, TimeUnit maxExecuteTimeUnit) {
    CloseFuture execCf = new CloseFuture();
    WorkerPool sharedWorkerPool = createSharedWorkerPool(execCf, name, poolSize, maxExecuteTime, maxExecuteTimeUnit);
    CloseFuture parentCf = resolveCloseFuture();
    parentCf.add(execCf);
    return new WorkerExecutorImpl(this, cleaner, sharedWorkerPool);
  }

  public WorkerPool createSharedWorkerPool(String name, int poolSize, long maxExecuteTime, TimeUnit maxExecuteTimeUnit) {
    return createSharedWorkerPool(new CloseFuture(), name, poolSize, maxExecuteTime, maxExecuteTimeUnit);
  }

  private synchronized WorkerPool createSharedWorkerPool(CloseFuture closeFuture, String name, int poolSize, long maxExecuteTime, TimeUnit maxExecuteTimeUnit) {
    if (poolSize < 1) {
      throw new IllegalArgumentException("poolSize must be > 0");
    }
    if (maxExecuteTime < 1) {
      throw new IllegalArgumentException("maxExecuteTime must be > 0");
    }
    WorkerPool shared = createSharedResource("__vertx.shared.workerPools", name, closeFuture, cf -> {
      ThreadFactory workerThreadFactory = createThreadFactory(threadFactory, checker, useDaemonThread, maxExecuteTime, maxExecuteTimeUnit, name + "-", true);
      ExecutorService workerExec = executorServiceFactory.createExecutor(workerThreadFactory, poolSize, poolSize);
      PoolMetrics workerMetrics = metrics != null ? metrics.createPoolMetrics("worker", name, poolSize) : null;
      WorkerPool pool = new WorkerPool(workerExec, workerMetrics);
      cf.add(completion -> {
        pool.close();
        completion.complete();
      });
      return pool;
    });
    return new WorkerPool(shared.executor(), shared.metrics()) {
      @Override
      void close() {
        closeFuture.close();
      }
    };
  }

  private static ThreadFactory createThreadFactory(VertxThreadFactory threadFactory, BlockedThreadChecker checker, Boolean useDaemonThread, long maxExecuteTime, TimeUnit maxExecuteTimeUnit, String prefix, boolean worker) {
    AtomicInteger threadCount = new AtomicInteger(0);
    return runnable -> {
      VertxThread thread = threadFactory.newVertxThread(runnable, prefix + threadCount.getAndIncrement(), worker, maxExecuteTime, maxExecuteTimeUnit);
      checker.registerThread(thread, thread.info);
      if (useDaemonThread != null && thread.isDaemon() != useDaemonThread) {
        thread.setDaemon(useDaemonThread);
      }
      return thread;
    };
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
  public CloseFuture closeFuture() {
    return closeFuture;
  }

  @Override
  public VertxTracer tracer() {
    return tracer;
  }

  @Override
  public void addCloseHook(Closeable hook) {
    closeFuture.add(hook);
  }

  @Override
  public void removeCloseHook(Closeable hook) {
    closeFuture.remove(hook);
  }

  private CloseFuture resolveCloseFuture() {
    ContextInternal context = getContext();
    return context != null ? context.closeFuture() : closeFuture;
  }

  /**
   * Execute the {@code task} disabling the thread-local association for the duration
   * of the execution. {@link Vertx#currentContext()} will return {@code null},
   * @param task the task to execute
   * @throws IllegalStateException if the current thread is not a Vertx thread
   */
  void executeIsolated(Handler<Void> task) {
    if (Thread.currentThread() instanceof VertxThread) {
      ContextInternal prev = beginDispatch(null);
      try {
        task.handle(null);
      } finally {
        endDispatch(prev);
      }
    } else {
      task.handle(null);
    }
  }

  static class ContextDispatch {
    ContextInternal context;
    ClassLoader topLevelTCCL;
  }

  /**
   * Begin the emission of a context event.
   * <p>
   * This is a low level interface that should not be used, instead {@link ContextInternal#dispatch(Object, io.vertx.core.Handler)}
   * shall be used.
   *
   * @param context the context on which the event is emitted on
   * @return the current context that shall be restored
   */
  ContextInternal beginDispatch(ContextInternal context) {
    Thread thread = Thread.currentThread();
    ContextInternal prev;
    if (thread instanceof VertxThread) {
      VertxThread vertxThread = (VertxThread) thread;
      prev = vertxThread.context;
      if (!ContextBase.DISABLE_TIMINGS) {
        vertxThread.executeStart();
      }
      vertxThread.context = context;
      if (!disableTCCL) {
        if (prev == null) {
          vertxThread.topLevelTCCL = Thread.currentThread().getContextClassLoader();
        }
        if (context != null) {
          thread.setContextClassLoader(context.classLoader());
        }
      }
    } else {
      prev = beginDispatch2(thread, context);
    }
    return prev;
  }

  private ContextInternal beginDispatch2(Thread thread, ContextInternal context) {
    ContextDispatch current = nonVertxContextDispatch.get();
    ContextInternal prev;
    if (current != null) {
      prev = current.context;
    } else {
      current = new ContextDispatch();
      nonVertxContextDispatch.set(current);
      prev = null;
    }
    current.context = context;
    if (!disableTCCL) {
      if (prev == null) {
        current.topLevelTCCL = Thread.currentThread().getContextClassLoader();
      }
      thread.setContextClassLoader(context.classLoader());
    }
    return prev;
  }

  /**
   * End the emission of a context task.
   * <p>
   * This is a low level interface that should not be used, instead {@link ContextInternal#dispatch(Object, io.vertx.core.Handler)}
   * shall be used.
   *
   * @param prev the previous context thread to restore, might be {@code null}
   */
  void endDispatch(ContextInternal prev) {
    Thread thread = Thread.currentThread();
    if (thread instanceof VertxThread) {
      VertxThread vertxThread = (VertxThread) thread;
      vertxThread.context = prev;
      if (!disableTCCL) {
        ClassLoader tccl;
        if (prev == null) {
          tccl = vertxThread.topLevelTCCL;
          vertxThread.topLevelTCCL = null;
        } else {
          tccl = prev.classLoader();
        }
        Thread.currentThread().setContextClassLoader(tccl);
      }
      if (!ContextBase.DISABLE_TIMINGS) {
        vertxThread.executeEnd();
      }
    } else {
      endDispatch2(prev);
    }
  }

  private void endDispatch2(ContextInternal prev) {
    ClassLoader tccl;
    ContextDispatch current = nonVertxContextDispatch.get();
    if (prev != null) {
      current.context = prev;
      tccl = prev.classLoader();
    } else {
      nonVertxContextDispatch.remove();
      tccl = current.topLevelTCCL;
    }
    if (!disableTCCL) {
      Thread.currentThread().setContextClassLoader(tccl);
    }
  }

  /**
   * Reads the version from the {@code vertx-version.txt} file.
   *
   * @return the version
   */
  public static String version() {
    if (version != null) {
      return version;
    }
    try (InputStream is = VertxImpl.class.getClassLoader().getResourceAsStream("META-INF/vertx/vertx-version.txt")) {
      if (is == null) {
        throw new IllegalStateException("Cannot find vertx-version.txt on classpath");
      }
      try (Scanner scanner = new Scanner(is, StandardCharsets.UTF_8).useDelimiter("\\A")) {
        return version = scanner.hasNext() ? scanner.next().trim() : "";
      }
    } catch (IOException e) {
      throw new IllegalStateException(e.getMessage());
    }
  }}
