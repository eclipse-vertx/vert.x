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
import io.vertx.core.Timer;
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
import io.vertx.core.http.impl.*;
import io.vertx.core.impl.btc.BlockedThreadChecker;
import io.vertx.core.net.impl.NetClientBuilder;
import io.vertx.core.impl.transports.JDKTransport;
import io.vertx.core.spi.file.FileResolver;
import io.vertx.core.file.impl.FileSystemImpl;
import io.vertx.core.file.impl.WindowsFileSystem;
import io.vertx.core.http.impl.HttpClientImpl;
import io.vertx.core.http.impl.HttpServerImpl;
import io.vertx.core.impl.future.PromiseInternal;
import io.vertx.core.impl.logging.Logger;
import io.vertx.core.impl.logging.LoggerFactory;
import io.vertx.core.impl.resolver.DnsResolverProvider;
import io.vertx.core.net.NetClient;
import io.vertx.core.net.NetClientOptions;
import io.vertx.core.net.NetServer;
import io.vertx.core.net.NetServerOptions;
import io.vertx.core.net.impl.NetServerImpl;
import io.vertx.core.net.impl.ServerID;
import io.vertx.core.net.impl.TCPServerBase;
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
import java.lang.ref.WeakReference;
import java.lang.reflect.Method;
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

  // Not cached for graalvm
  private static ThreadFactory virtualThreadFactory() {
    try {
      Class<?> builderClass = ClassLoader.getSystemClassLoader().loadClass("java.lang.Thread$Builder");
      Class<?> ofVirtualClass = ClassLoader.getSystemClassLoader().loadClass("java.lang.Thread$Builder$OfVirtual");
      Method ofVirtualMethod = Thread.class.getDeclaredMethod("ofVirtual");
      Object builder = ofVirtualMethod.invoke(null);
      Method nameMethod = ofVirtualClass.getDeclaredMethod("name", String.class, long.class);
      Method factoryMethod = builderClass.getDeclaredMethod("factory");
      builder = nameMethod.invoke(builder, "vert.x-virtual-thread-", 0L);
      return (ThreadFactory) factoryMethod.invoke(builder);
    } catch (Exception e) {
      return null;
    }
  }

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
  private final int contextLocalsLength;
  final WorkerPool workerPool;
  final WorkerPool internalWorkerPool;
  final WorkerPool virtualThreaWorkerPool;
  private final VertxThreadFactory threadFactory;
  private final ExecutorServiceFactory executorServiceFactory;
  private final ThreadFactory eventLoopThreadFactory;
  private final EventLoopGroup eventLoopGroup;
  private final EventLoopGroup acceptorEventLoopGroup;
  private final ExecutorService virtualThreadExecutor;
  private final BlockedThreadChecker checker;
  private final AddressResolver addressResolver;
  private final AddressResolverOptions addressResolverOptions;
  private final EventBusInternal eventBus;
  private volatile HAManager haManager;
  private boolean closed;
  private volatile Handler<Throwable> exceptionHandler;
  private final Map<String, SharedWorkerPool> namedWorkerPools;
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

    ThreadFactory virtualThreadFactory = virtualThreadFactory();

    contextLocalsLength = LocalSeq.get();
    closeFuture = new CloseFuture(log);
    maxEventLoopExecTime = maxEventLoopExecuteTime;
    maxEventLoopExecTimeUnit = maxEventLoopExecuteTimeUnit;
    eventLoopThreadFactory = createThreadFactory(threadFactory, checker, useDaemonThread, maxEventLoopExecTime, maxEventLoopExecTimeUnit, "vert.x-eventloop-thread-", false);
    eventLoopGroup = transport.eventLoopGroup(Transport.IO_EVENT_LOOP_GROUP, options.getEventLoopPoolSize(), eventLoopThreadFactory, NETTY_IO_RATIO);
    // The acceptor event loop thread needs to be from a different pool otherwise can get lags in accepted connections
    // under a lot of load
    acceptorEventLoopGroup = transport.eventLoopGroup(Transport.ACCEPTOR_EVENT_LOOP_GROUP, 1, acceptorEventLoopThreadFactory, 100);
    virtualThreadExecutor = virtualThreadFactory != null ? new ThreadPerTaskExecutorService(virtualThreadFactory) : null;
    virtualThreaWorkerPool = virtualThreadFactory != null ? new WorkerPool(virtualThreadExecutor, null) : null;
    internalWorkerPool = new WorkerPool(internalWorkerExec, internalBlockingPoolMetrics);
    namedWorkerPools = new HashMap<>();
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

  void initClustered(VertxOptions options, Handler<AsyncResult<Vertx>> resultHandler) {
    nodeSelector.init(this, clusterManager);
    clusterManager.init(this, nodeSelector);
    Promise<Void> initPromise = getOrCreateContext().promise();
    initPromise.future().onComplete(ar -> {
      if (ar.succeeded()) {
        if (metrics != null) {
          metrics.vertxCreated(this);
        }
        resultHandler.handle(Future.succeededFuture(this));
      } else {
        log.error("Failed to initialize clustered Vert.x", ar.cause());
        close().onComplete(ignore -> resultHandler.handle(Future.failedFuture(ar.cause())));
      }
    });
    Promise<Void> joinPromise = Promise.promise();
    joinPromise.future().onComplete(ar -> {
      if (ar.succeeded()) {
        createHaManager(options, initPromise);
      } else {
        initPromise.fail(ar.cause());
      }
    });
    clusterManager.join(joinPromise);
  }

  private void createHaManager(VertxOptions options, Promise<Void> initPromise) {
    if (options.isHAEnabled()) {
      this.<HAManager>executeBlocking(fut -> {
        haManager = new HAManager(this, deploymentManager, verticleManager, clusterManager, clusterManager.getSyncMap(CLUSTER_MAP_NAME), options.getQuorumSize(), options.getHAGroup());
        fut.complete(haManager);
      }, false, ar -> {
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
    this.executeBlocking(fut -> {
      // Init the manager (i.e register listener and check the quorum)
      // after the event bus has been fully started and updated its state
      // it will have also set the clustered changed view handler on the ha manager
      haManager.init();
      fut.complete();
    }, false, initPromise);
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
    CloseFuture closeFuture = new CloseFuture(log);
    CloseFuture fut = resolveCloseFuture();
    fut.add(closeFuture);
    NetClientBuilder builder = new NetClientBuilder(this, options);
    builder.metrics(metricsSPI() != null ? metricsSPI().createNetClientMetrics(options) : null);
    builder.closeFuture(closeFuture);
    return builder.build();
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

  @Override
  public WebSocketClient createWebSocketClient(WebSocketClientOptions options, CloseFuture closeFuture) {
    HttpClientOptions o = new HttpClientOptions(options);
    o.setDefaultHost(options.getDefaultHost());
    o.setDefaultPort(options.getDefaultPort());
    o.setVerifyHost(options.isVerifyHost());
    o.setMaxWebSocketFrameSize(options.getMaxFrameSize());
    o.setMaxWebSocketMessageSize(options.getMaxMessageSize());
    o.setMaxWebSockets(options.getMaxConnections());
    o.setSendUnmaskedFrames(options.isSendUnmaskedFrames());
    o.setTryUsePerFrameWebSocketCompression(options.getTryUsePerFrameCompression());
    o.setTryUsePerMessageWebSocketCompression(options.getTryUsePerMessageCompression());
    o.setWebSocketCompressionLevel(options.getCompressionLevel());
    o.setWebSocketCompressionAllowClientNoContext(options.getCompressionAllowClientNoContext());
    o.setWebSocketCompressionRequestServerNoContext(options.getCompressionRequestServerNoContext());
    o.setWebSocketClosingTimeout(options.getClosingTimeout());
    o.setShared(options.isShared());
    o.setName(options.getName());
    WebSocketClientImpl client = new WebSocketClientImpl(this, o, closeFuture);
    closeFuture.add(client);
    return client;
  }

  @Override
  public WebSocketClient createWebSocketClient(WebSocketClientOptions options) {
    CloseFuture closeFuture = new CloseFuture();
    WebSocketClient client;
    if (options.isShared()) {
      client = createSharedClient(SharedWebSocketClient.SHARED_MAP_NAME, options.getName(), closeFuture, cf -> createWebSocketClient(options, cf));
      client = new SharedWebSocketClient(this, closeFuture, client);
    } else {
      client = createWebSocketClient(options, closeFuture);
    }
    resolveCloseFuture().add(closeFuture);
    return client;
  }

  @Override
  public HttpClient createHttpPoolClient(HttpClientOptions clientOptions, PoolOptions poolOptions, CloseFuture closeFuture) {
    HttpClientImpl client = new HttpClientImpl(this, clientOptions, poolOptions, closeFuture);
    closeFuture.add(client);
    return client;
  }

  @Override
  public HttpClientBuilder httpClientBuilder() {
    return new HttpClientBuilderImpl(this);
  }

  public EventBus eventBus() {
    return eventBus;
  }

  @Override
  public long setPeriodic(long initialDelay, long delay, Handler<Long> handler) {
    ContextInternal ctx = getOrCreateContext();
    return scheduleTimeout(ctx, true, initialDelay, delay, TimeUnit.MILLISECONDS, ctx.isDeployment(), handler);
  }

  @Override
  public TimeoutStream periodicStream(long initialDelay, long delay) {
    return new TimeoutStreamImpl(initialDelay, delay, true);
  }

  public long setTimer(long delay, Handler<Long> handler) {
    ContextInternal ctx = getOrCreateContext();
    return scheduleTimeout(ctx, false, delay, TimeUnit.MILLISECONDS, ctx.isDeployment(), handler);
  }

  @Override
  public TimeoutStream timerStream(long delay) {
    return new TimeoutStreamImpl(delay, false);
  }

  @Override
  public Timer timer(long delay, TimeUnit unit) {
    Objects.requireNonNull(unit);
    if (delay <= 0) {
      throw new IllegalArgumentException("Invalid delay: " + delay);
    }
    ContextInternal ctx = getOrCreateContext();
    io.netty.util.concurrent.ScheduledFuture<Void> fut = ctx.nettyEventLoop().schedule(() -> null, delay, unit);
    TimerImpl promise = new TimerImpl(ctx, fut);
    fut.addListener(promise);
    return promise;
  }

  @Override
  public <T> PromiseInternal<T> promise() {
    ContextInternal context = getOrCreateContext();
    return context.promise();
  }

  @Override
  public <T> PromiseInternal<T> promise(Handler<AsyncResult<T>> handler) {
    if (handler instanceof PromiseInternal) {
      PromiseInternal<T> promise = (PromiseInternal<T>) handler;
      if (promise.context() != null) {
        return promise;
      }
    }
    PromiseInternal<T> promise = promise();
    promise.future().onComplete(handler);
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

  private ContextImpl createEventLoopContext(EventLoop eventLoop, CloseFuture closeFuture, WorkerPool workerPool, Deployment deployment, ClassLoader tccl) {
    return new ContextImpl(this, contextLocalsLength, ThreadingModel.EVENT_LOOP, eventLoop, new EventLoopExecutor(eventLoop), internalWorkerPool, workerPool != null ? workerPool : this.workerPool, new TaskQueue(), deployment, closeFuture, disableTCCL ? null : tccl);
  }

  @Override
  public ContextImpl createEventLoopContext(Deployment deployment, CloseFuture closeFuture, WorkerPool workerPool, ClassLoader tccl) {
    return createEventLoopContext(eventLoopGroup.next(), closeFuture, workerPool, deployment, tccl);
  }

  @Override
  public ContextImpl createEventLoopContext(EventLoop eventLoop, WorkerPool workerPool, ClassLoader tccl) {
    return createEventLoopContext(eventLoop, closeFuture, workerPool, null, tccl);
  }

  @Override
  public ContextImpl createEventLoopContext() {
    return createEventLoopContext(null, closeFuture, null, Thread.currentThread().getContextClassLoader());
  }

  private ContextImpl createWorkerContext(EventLoop eventLoop, CloseFuture closeFuture, WorkerPool workerPool, Deployment deployment, ClassLoader tccl) {
    TaskQueue orderedTasks = new TaskQueue();
    WorkerPool wp = workerPool != null ? workerPool : this.workerPool;
    return new ContextImpl(this, contextLocalsLength, ThreadingModel.WORKER, eventLoop, new WorkerExecutor(wp, orderedTasks), internalWorkerPool, wp, orderedTasks, deployment, closeFuture, disableTCCL ? null : tccl);
  }

  @Override
  public ContextInternal createWorkerContext(EventLoop eventLoop, WorkerPool workerPool, ClassLoader tccl) {
    return createWorkerContext(eventLoop, closeFuture, workerPool, null, tccl);
  }

  @Override
  public ContextImpl createWorkerContext(Deployment deployment, CloseFuture closeFuture, WorkerPool workerPool, ClassLoader tccl) {
    return createWorkerContext(eventLoopGroup.next(), closeFuture, workerPool, deployment, tccl);
  }

  @Override
  public ContextImpl createWorkerContext() {
    return createWorkerContext(null, closeFuture, null, Thread.currentThread().getContextClassLoader());
  }

  private ContextImpl createVirtualThreadContext(EventLoop eventLoop, CloseFuture closeFuture, Deployment deployment, ClassLoader tccl) {
    if (!isVirtualThreadAvailable()) {
      throw new IllegalStateException("This Java runtime does not support virtual threads");
    }
    TaskQueue orderedTasks = new TaskQueue();
    return new ContextImpl(this, contextLocalsLength, ThreadingModel.VIRTUAL_THREAD, eventLoop, new WorkerExecutor(virtualThreaWorkerPool, orderedTasks), internalWorkerPool, virtualThreaWorkerPool, orderedTasks, deployment, closeFuture, disableTCCL ? null : tccl);
  }

  @Override
  public ContextImpl createVirtualThreadContext(Deployment deployment, CloseFuture closeFuture, ClassLoader tccl) {
    return createVirtualThreadContext(eventLoopGroup.next(), closeFuture, deployment, tccl);
  }

  @Override
  public ContextImpl createVirtualThreadContext(EventLoop eventLoop, ClassLoader tccl) {
    return createVirtualThreadContext(eventLoop, closeFuture, null, tccl);
  }

  @Override
  public ContextImpl createVirtualThreadContext() {
    return createVirtualThreadContext(null, closeFuture, Thread.currentThread().getContextClassLoader());
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
      DnsResolverProvider provider = DnsResolverProvider.create(this, addressResolverOptions);
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

  @Override
  public Future<Void> close() {
    // Create this promise purposely without a context because the close operation will close thread pools
    Promise<Void> promise = Promise.promise();
    close(promise);
    return promise.future();
  }

  private void closeClusterManager(Handler<AsyncResult<Void>> completionHandler) {
    Promise<Void> leavePromise = getOrCreateContext().promise();
    if (clusterManager != null) {
      clusterManager.leave(leavePromise);
    } else {
      leavePromise.complete();
    }
    leavePromise.future().onComplete(ar -> {
      if (ar.failed()) {
        log.error("Failed to leave cluster", ar.cause());
      }
      if (completionHandler != null) {
        completionHandler.handle(Future.succeededFuture());
      }
    });
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
    closeFuture.close().onComplete(ar -> {
      deploymentManager.undeployAll().onComplete(ar1 -> {
        HAManager haManager = haManager();
        Promise<Void> haPromise = Promise.promise();
        if (haManager != null) {
          this.executeBlocking(fut -> {
            haManager.stop();
            fut.complete();
          }, false, haPromise);
        } else {
          haPromise.complete();
        }
        haPromise.future().onComplete(ar2 -> {
          addressResolver.close(ar3 -> {
            Promise<Void> ebClose = getOrCreateContext().promise();
            eventBus.close(ebClose);
            ebClose.future().onComplete(ar4 -> {
              closeClusterManager(ar5 -> {
                // Copy set to prevent ConcurrentModificationException
                deleteCacheDirAndShutdown(completionHandler);
              });
            });
          });
        });
      });
    });
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
  public void deployVerticle(String name, DeploymentOptions options, Handler<AsyncResult<String>> completionHandler) {
    Future<String> fut = deployVerticle(name, options);
    if (completionHandler != null) {
      fut.onComplete(completionHandler);
    }
  }

  @Override
  public void deployVerticle(Verticle verticle, Handler<AsyncResult<String>> completionHandler) {
    Future<String> fut = deployVerticle(verticle);
    if (completionHandler != null) {
      fut.onComplete(completionHandler);
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
  public void deployVerticle(Verticle verticle, DeploymentOptions options, Handler<AsyncResult<String>> completionHandler) {
    Future<String> fut = deployVerticle(verticle, options);
    if (completionHandler != null) {
      fut.onComplete(completionHandler);
    }
  }

  @Override
  public Future<String> deployVerticle(Class<? extends Verticle> verticleClass, DeploymentOptions options) {
    return deployVerticle((Callable<Verticle>) verticleClass::newInstance, options);
  }

  @Override
  public void deployVerticle(Class<? extends Verticle> verticleClass, DeploymentOptions options, Handler<AsyncResult<String>> completionHandler) {
    Future<String> fut = deployVerticle(verticleClass, options);
    if (completionHandler != null) {
      fut.onComplete(completionHandler);
    }
  }

  @Override
  public Future<String> deployVerticle(Supplier<Verticle> verticleSupplier, DeploymentOptions options) {
    return deployVerticle((Callable<Verticle>) verticleSupplier::get, options);
  }

  @Override
  public void deployVerticle(Supplier<Verticle> verticleSupplier, DeploymentOptions options, Handler<AsyncResult<String>> completionHandler) {
    Future<String> fut = deployVerticle(verticleSupplier, options);
    if (completionHandler != null) {
      fut.onComplete(completionHandler);
    }
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
      future = this.executeBlocking(fut -> {
        haManager.removeFromHA(deploymentID);
        fut.complete();
      }, false);
    } else {
      future = getOrCreateContext().succeededFuture();
    }
    return future.compose(v -> deploymentManager.undeployVerticle(deploymentID));
  }

  @Override
  public void undeploy(String deploymentID, Handler<AsyncResult<Void>> completionHandler) {
    Future<Void> fut = undeploy(deploymentID);
    if (completionHandler != null) {
      fut.onComplete(completionHandler);
    }
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

  @Override
  public FileResolver fileResolver() {
    return fileResolver;
  }

  @Override
  public BlockedThreadChecker blockedThreadChecker() {
    return checker;
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
      internalWorkerPool.close();
      new ArrayList<>(namedWorkerPools.values()).forEach(WorkerPool::close);

      if (virtualThreadExecutor != null) {
        virtualThreadExecutor.shutdown();
        try {
          virtualThreadExecutor.awaitTermination(10, TimeUnit.SECONDS);
        } catch (InterruptedException ignore) {
        }
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

    private final long initialDelay;
    private final long delay;
    private final boolean periodic;

    private Long id;
    private Handler<Long> handler;
    private Handler<Void> endHandler;
    private long demand;

    public TimeoutStreamImpl(long delay, boolean periodic) {
      this(delay, delay, periodic);
    }

    public TimeoutStreamImpl(long initialDelay, long delay, boolean periodic) {
      this.initialDelay = initialDelay;
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
        ContextInternal ctx = getOrCreateContext();
        this.handler = handler;
        this.id = scheduleTimeout(ctx, periodic, initialDelay, delay, TimeUnit.MILLISECONDS, ctx.isDeployment(), this);
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
        if (--refCount > 0) {
          return;
        }
        namedWorkerPools.remove(name);
      }
      super.close();
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
    SharedWorkerPool sharedWorkerPool = createSharedWorkerPool(name, poolSize, maxExecuteTime, maxExecuteTimeUnit);
    CloseFuture parentCf = resolveCloseFuture();
    CloseFuture execCf = new CloseFuture();
    parentCf.add(execCf);
    WorkerExecutorImpl namedExec = new WorkerExecutorImpl(this, execCf, sharedWorkerPool);
    execCf.add(namedExec);
    return namedExec;
  }

  public synchronized SharedWorkerPool createSharedWorkerPool(String name, int poolSize, long maxExecuteTime, TimeUnit maxExecuteTimeUnit) {
    if (poolSize < 1) {
      throw new IllegalArgumentException("poolSize must be > 0");
    }
    if (maxExecuteTime < 1) {
      throw new IllegalArgumentException("maxExecuteTime must be > 0");
    }
    SharedWorkerPool sharedWorkerPool = namedWorkerPools.get(name);
    if (sharedWorkerPool == null) {
      ThreadFactory workerThreadFactory = createThreadFactory(threadFactory, checker, useDaemonThread, maxExecuteTime, maxExecuteTimeUnit, name + "-", true);
      ExecutorService workerExec = executorServiceFactory.createExecutor(workerThreadFactory, poolSize, poolSize);
      PoolMetrics workerMetrics = metrics != null ? metrics.createPoolMetrics("worker", name, poolSize) : null;
      namedWorkerPools.put(name, sharedWorkerPool = new SharedWorkerPool(name, workerExec, workerMetrics));
    } else {
      sharedWorkerPool.refCount++;
    }
    return sharedWorkerPool;
  }

  @Override
  public WorkerPool wrapWorkerPool(ExecutorService executor) {
    PoolMetrics workerMetrics = metrics != null ? metrics.createPoolMetrics("worker", null, -1) : null;
    return new WorkerPool(executor, workerMetrics);
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

  @Override
  public boolean isVirtualThreadAvailable() {
    return virtualThreadExecutor != null;
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
      if (!ContextImpl.DISABLE_TIMINGS) {
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
      if (!ContextImpl.DISABLE_TIMINGS) {
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
}
