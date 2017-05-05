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
import io.netty.resolver.AddressResolverGroup;
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
import java.net.InetSocketAddress;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
// TODO: 16/12/14 by zmyer
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

    //文件系统对象
    private final FileSystem fileSystem = getFileSystem();
    //共享数据结构
    private final SharedData sharedData;
    //vertx节点对象
    private final VertxMetrics metrics;
    //超时集合
    private final ConcurrentMap<Long, InternalTimerHandler> timeouts = new ConcurrentHashMap<>();
    //超时集合长度
    private final AtomicLong timeoutCounter = new AtomicLong(0);
    //集群管理器
    private final ClusterManager clusterManager;
    //部署管理器
    private final DeploymentManager deploymentManager;
    //文件解析器
    private final FileResolver fileResolver;
    //共享HTTP服务器集合
    private final Map<ServerID, HttpServerImpl> sharedHttpServers = new HashMap<>();
    //共享Net服务器集合
    private final Map<ServerID, NetServerImpl> sharedNetServers = new HashMap<>();
    //工作线程池
    private final WorkerPool workerPool;
    //内部阻塞线程池
    private final WorkerPool internalBlockingPool;
    //线程工厂
    private final ThreadFactory eventLoopThreadFactory;
    //事件处理器对象
    private final NioEventLoopGroup eventLoopGroup;
    //接收链接请求事件处理器
    private final NioEventLoopGroup acceptorEventLoopGroup;
    //线程检查对象
    private final BlockedThreadChecker checker;
    private final boolean haEnabled;
    //地址解析对象
    private final AddressResolver addressResolver;
    //事件总线对象
    private EventBus eventBus;
    //HA管理器
    private HAManager haManager;
    private boolean closed;
    //异常处理对象
    private volatile Handler<Throwable> exceptionHandler;
    //工作线程池集合
    private final Map<String, SharedWorkerPool> namedWorkerPools;
    //默认的工作线程池集合大小
    private final int defaultWorkerPoolSize;
    //默认工作线程最大执行时间
    private final long defaultWorkerMaxExecTime;
    //关闭hook对象
    private final CloseHooks closeHooks;

    VertxImpl() {
        this(new VertxOptions());
    }

    VertxImpl(VertxOptions options) {
        this(options, null);
    }

    // TODO: 16/12/14 by zmyer
    VertxImpl(VertxOptions options, Handler<AsyncResult<Vertx>> resultHandler) {
        // Sanity check
        if (Vertx.currentContext() != null) {
            log.warn("You're already on a Vert.x context, are you sure you want to create a new Vertx instance?");
        }
        closeHooks = new CloseHooks(log);
        checker = new BlockedThreadChecker(options.getBlockedThreadCheckInterval(), options.getWarningExceptionTime());
        //创建vertx线程创建工厂对象
        eventLoopThreadFactory = new VertxThreadFactory("vert.x-eventloop-thread-", checker, false, options.getMaxEventLoopExecuteTime());
        //创建事件处理器对象
        eventLoopGroup = new NioEventLoopGroup(options.getEventLoopPoolSize(), eventLoopThreadFactory);
        //设置IO比率
        eventLoopGroup.setIoRatio(NETTY_IO_RATIO);
        //创建接收链接请求事件处理线程创建工厂对象
        ThreadFactory acceptorEventLoopThreadFactory = new VertxThreadFactory("vert.x-acceptor-thread-", checker, false, options.getMaxEventLoopExecuteTime());
        // The acceptor event loop thread needs to be from a different pool otherwise can get lags in accepted connections
        // under a lot of load
        //创建接收链接请求的事件处理对象
        acceptorEventLoopGroup = new NioEventLoopGroup(1, acceptorEventLoopThreadFactory);
        acceptorEventLoopGroup.setIoRatio(100);

        //初始化metrics
        metrics = initialiseMetrics(options);

        //创建工作线程池
        ExecutorService workerExec = Executors.newFixedThreadPool(options.getWorkerPoolSize(),
                new VertxThreadFactory("vert.x-worker-thread-", checker, true, options.getMaxWorkerExecuteTime()));
        PoolMetrics workerPoolMetrics = isMetricsEnabled() ? metrics.createMetrics(workerExec, "worker", "vert.x-worker-thread", options.getWorkerPoolSize()) : null;

        //创建内部工作阻塞线程池
        ExecutorService internalBlockingExec = Executors.newFixedThreadPool(options.getInternalBlockingPoolSize(),
                new VertxThreadFactory("vert.x-internal-blocking-", checker, true, options.getMaxWorkerExecuteTime()));
        PoolMetrics internalBlockingPoolMetrics = isMetricsEnabled() ? metrics.createMetrics(internalBlockingExec, "worker", "vert.x-internal-blocking", options.getInternalBlockingPoolSize()) : null;
        //
        internalBlockingPool = new WorkerPool(internalBlockingExec, internalBlockingPoolMetrics);
        namedWorkerPools = new HashMap<>();
        workerPool = new WorkerPool(workerExec, workerPoolMetrics);
        defaultWorkerPoolSize = options.getWorkerPoolSize();
        defaultWorkerMaxExecTime = options.getMaxWorkerExecuteTime();

        this.fileResolver = new FileResolver(this);
        this.addressResolver = new AddressResolver(this, options.getAddressResolverOptions());
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
        //创建共享数据结构
        this.sharedData = new SharedDataImpl(this, clusterManager);
    }

    // TODO: 16/12/14 by zmyer
    private void createAndStartEventBus(VertxOptions options, Handler<AsyncResult<Vertx>> resultHandler) {
        if (options.isClustered()) {
            //创建集群模式的事件总线
            eventBus = new ClusteredEventBus(this, options, clusterManager, haManager);
        } else {
            //创建一般的事件总线
            eventBus = new EventBusImpl(this);
        }

        //启动事件总线
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
    // TODO: 16/12/14 by zmyer
    protected FileSystem getFileSystem() {
        return Utils.isWindows() ? new WindowsFileSystem(this) : new FileSystemImpl(this);
    }

    // TODO: 16/12/14 by zmyer
    @Override
    public DatagramSocket createDatagramSocket(DatagramSocketOptions options) {
        //创建基于数据报的socket对象
        return new DatagramSocketImpl(this, options);
    }

    // TODO: 16/12/14 by zmyer
    @Override
    public DatagramSocket createDatagramSocket() {
        return createDatagramSocket(new DatagramSocketOptions());
    }

    // TODO: 16/12/14 by zmyer
    public NetServer createNetServer(NetServerOptions options) {
        return new NetServerImpl(this, options);
    }

    // TODO: 16/12/14 by zmyer
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

    // TODO: 16/12/14 by zmyer
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

    // TODO: 16/12/14 by zmyer
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

    // TODO: 16/12/14 by zmyer
    public ContextImpl getOrCreateContext() {
        ContextImpl ctx = getContext();
        if (ctx == null) {
            // We are running embedded - Create a context
            //创建事件循环上下文对象
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

    // TODO: 16/12/14 by zmyer
    public boolean cancelTimer(long id) {
        //读取指定的定时器对象
        InternalTimerHandler handler = timeouts.remove(id);
        if (handler != null) {
            //直接取消定时器
            handler.context.removeCloseHook(handler);
            return handler.cancel();
        } else {
            return false;
        }
    }

    // TODO: 16/12/14 by zmyer
    public EventLoopContext createEventLoopContext(String deploymentID, WorkerPool workerPool, JsonObject config, ClassLoader tccl) {
        return new EventLoopContext(this, internalBlockingPool, workerPool != null ? workerPool : this.workerPool, deploymentID, config, tccl);
    }

    // TODO: 16/12/14 by zmyer
    public ContextImpl createWorkerContext(boolean multiThreaded, String deploymentID, WorkerPool workerPool, JsonObject config,
                                           ClassLoader tccl) {
        if (workerPool == null) {
            workerPool = this.workerPool;
        }
        if (multiThreaded) {
            //多线程工作线程上下文对象
            return new MultiThreadedWorkerContext(this, internalBlockingPool, workerPool, deploymentID, config, tccl);
        } else {
            //工作线程上下文
            return new WorkerContext(this, internalBlockingPool, workerPool, deploymentID, config, tccl);
        }
    }

    // TODO: 16/12/14 by zmyer
    @Override
    public DnsClient createDnsClient(int port, String host) {
        return new DnsClientImpl(this, port, host);
    }

    // TODO: 16/12/14 by zmyer
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
                VertxMetrics metrics = factory.metrics(this, options);
                Objects.requireNonNull(metrics, "The metric instance created from " + factory + " cannot be null");
                return metrics;
            }
        }
        return DummyVertxMetrics.INSTANCE;
    }

    // TODO: 16/12/14 by zmyer
    private ClusterManager getClusterManager(VertxOptions options) {
        if (options.isClustered()) {
            if (options.getClusterManager() != null) {
                //从配置对象中读取集群管理器对象
                return options.getClusterManager();
            } else {
                ClusterManager mgr;
                //首先从系统变量中读取集群管理器类对象
                String clusterManagerClassName = System.getProperty("vertx.cluster.managerClass");
                if (clusterManagerClassName != null) {
                    // We allow specify a sys prop for the cluster manager factory which overrides ServiceLoader
                    try {
                        //加载类对象
                        Class<?> clazz = Class.forName(clusterManagerClassName);
                        //根据类对象构造集群管理器
                        mgr = (ClusterManager) clazz.newInstance();
                    } catch (Exception e) {
                        throw new IllegalStateException("Failed to instantiate " + clusterManagerClassName, e);
                    }
                } else {
                    //直接加载类对象
                    mgr = ServiceHelper.loadFactoryOrNull(ClusterManager.class);
                    if (mgr == null) {
                        throw new IllegalStateException("No ClusterManagerFactory instances found on classpath");
                    }
                }
                return mgr;
            }
        } else {
            return null;
        }
    }

    // TODO: 16/12/14 by zmyer
    private long scheduleTimeout(ContextImpl context, Handler<Long> handler, long delay, boolean periodic) {
        if (delay < 1) {
            throw new IllegalArgumentException("Cannot schedule a timer with delay < 1 ms");
        }
        //获取定时器ID
        long timerId = timeoutCounter.getAndIncrement();
        //根据定时器id,创建定时器
        InternalTimerHandler task = new InternalTimerHandler(timerId, handler, periodic, delay, context);
        //将定时器插入到定时器集合中
        timeouts.put(timerId, task);
        //在线程上下文中注册该定时器
        context.addCloseHook(task);
        return timerId;
    }

    // TODO: 16/12/14 by zmyer
    public static Context context() {
        //读取当前的线程对象
        Thread current = Thread.currentThread();
        if (current instanceof VertxThread) {
            //如果当前线程是vertx线程,则直接返回对象的执行上下文
            return ((VertxThread) current).getContext();
        }
        return null;
    }

    // TODO: 16/12/14 by zmyer
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

    // TODO: 16/12/14 by zmyer
    private void closeClusterManager(Handler<AsyncResult<Void>> completionHandler) {
        if (clusterManager != null) {
            // Workaround fo Hazelcast bug https://github.com/hazelcast/hazelcast/issues/5220
            if (clusterManager instanceof ExtendedClusterManager) {
                //直接关闭集群管理器
                ExtendedClusterManager ecm = (ExtendedClusterManager) clusterManager;
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

    // TODO: 16/12/14 by zmyer
    @Override
    public synchronized void close(Handler<AsyncResult<Void>> completionHandler) {
        if (closed || eventBus == null) {
            // Just call the handler directly since pools shutdown
            //直接调用完成处理对象
            if (completionHandler != null) {
                completionHandler.handle(Future.succeededFuture());
            }
            return;
        }
        closed = true;

        closeHooks.run(ar -> {
            deploymentManager.undeployAll(ar1 -> {
                if (haManager() != null) {
                    haManager().stop();
                }
                addressResolver.close(ar2 -> {
                    eventBus.close(ar3 -> {
                        closeClusterManager(ar4 -> {
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
            });
        });
    }

    // TODO: 16/12/14 by zmyer
    @Override
    public void deployVerticle(Verticle verticle) {
        deployVerticle(verticle, new DeploymentOptions(), null);
    }

    // TODO: 16/12/14 by zmyer
    @Override
    public void deployVerticle(Verticle verticle, Handler<AsyncResult<String>> completionHandler) {
        deployVerticle(verticle, new DeploymentOptions(), completionHandler);
    }

    // TODO: 16/12/14 by zmyer
    @Override
    public void deployVerticle(String name, Handler<AsyncResult<String>> completionHandler) {
        deployVerticle(name, new DeploymentOptions(), completionHandler);
    }

    // TODO: 16/12/14 by zmyer
    @Override
    public void deployVerticle(Verticle verticle, DeploymentOptions options) {
        deployVerticle(verticle, options, null);
    }

    // TODO: 16/12/14 by zmyer
    @Override
    public void deployVerticle(Verticle verticle, DeploymentOptions options, Handler<AsyncResult<String>> completionHandler) {
        boolean closed;
        synchronized (this) {
            closed = this.closed;
        }
        if (closed) {
            if (completionHandler != null) {
                completionHandler.handle(Future.failedFuture("Vert.x closed"));
            }
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
            //部署心跳管理器节点
            haManager().deployVerticle(name, options, completionHandler);
        } else {
            //直接部署指定的节点对象
            deploymentManager.deployVerticle(name, options, completionHandler);
        }
    }

    @Override
    public String getNodeID() {
        return clusterManager.getNodeID();
    }

    // TODO: 16/12/14 by zmyer
    @Override
    public void undeploy(String deploymentID) {
        undeploy(deploymentID, res -> {
        });
    }

    // TODO: 16/12/14 by zmyer
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

    // TODO: 16/12/14 by zmyer
    @Override
    public void registerVerticleFactory(VerticleFactory factory) {
        deploymentManager.registerVerticleFactory(factory);
    }

    // TODO: 16/12/14 by zmyer
    @Override
    public void unregisterVerticleFactory(VerticleFactory factory) {
        deploymentManager.unregisterVerticleFactory(factory);
    }

    @Override
    public Set<VerticleFactory> verticleFactories() {
        return deploymentManager.verticleFactories();
    }

    // TODO: 16/12/14 by zmyer
    @Override
    public <T> void executeBlockingInternal(Action<T> action, Handler<AsyncResult<T>> resultHandler) {
        //首先创建执行上下文对象
        ContextImpl context = getOrCreateContext();
        //开始在执行上下文对象中执行指定的action对象
        context.executeBlocking(action, resultHandler);
    }

    // TODO: 16/12/14 by zmyer
    @Override
    public <T> void executeBlocking(Handler<Future<T>> blockingCodeHandler, boolean ordered,
                                    Handler<AsyncResult<T>> asyncResultHandler) {
        ContextImpl context = getOrCreateContext();
        context.executeBlocking(blockingCodeHandler, ordered, asyncResultHandler);
    }

    // TODO: 16/12/14 by zmyer
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

    // TODO: 16/12/14 by zmyer
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

    // TODO: 16/12/14 by zmyer
    @SuppressWarnings("unchecked")
    private void deleteCacheDirAndShutdown(Handler<AsyncResult<Void>> completionHandler) {
        fileResolver.close(res -> {
            //关闭工作线程池
            workerPool.close();
            //关闭内部阻塞工作线程池
            internalBlockingPool.close();
            //关闭所有的工作线程池
            new ArrayList<>(namedWorkerPools.values()).forEach(WorkerPool::close);
            //关闭接收连接事件处理器对象
            acceptorEventLoopGroup.shutdownGracefully(0, 10, TimeUnit.SECONDS).addListener(new GenericFutureListener() {
                @Override
                public void operationComplete(io.netty.util.concurrent.Future future) throws Exception {
                    if (!future.isSuccess()) {
                        log.warn("Failure in shutting down acceptor event loop group", future.cause());
                    }
                    //关闭事件处理器
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

    // TODO: 16/12/14 by zmyer
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

    // TODO: 16/12/14 by zmyer
    private class InternalTimerHandler implements Handler<Void>, Closeable {
        //事件处理器
        final Handler<Long> handler;
        final boolean periodic;
        //定时器ID
        final long timerID;
        //执行上下文对象
        final ContextImpl context;
        //异步事件对象
        final java.util.concurrent.Future<?> future;

        // TODO: 16/12/14 by zmyer
        boolean cancel() {
            metrics.timerEnded(timerID, true);
            return future.cancel(false);
        }

        // TODO: 16/12/14 by zmyer
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

        // TODO: 16/12/14 by zmyer
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

        // TODO: 16/12/14 by zmyer
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
    // TODO: 16/12/14 by zmyer
    private class TimeoutStreamImpl implements TimeoutStream, Handler<Long> {
        //延时时间间隔
        private final long delay;
        private final boolean periodic;

        private boolean paused;
        private Long id;
        //事件处理器
        private Handler<Long> handler;
        //结束事件处理器
        private Handler<Void> endHandler;

        public TimeoutStreamImpl(long delay, boolean periodic) {
            this.delay = delay;
            this.periodic = periodic;
        }

        // TODO: 16/12/14 by zmyer
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

        // TODO: 16/12/14 by zmyer
        @Override
        public TimeoutStream exceptionHandler(Handler<Throwable> handler) {
            return this;
        }

        // TODO: 16/12/14 by zmyer
        @Override
        public void cancel() {
            if (id != null) {
                VertxImpl.this.cancelTimer(id);
            }
        }

        // TODO: 16/12/14 by zmyer
        @Override
        public synchronized TimeoutStream handler(Handler<Long> handler) {
            if (handler != null) {
                if (id != null) {
                    throw new IllegalStateException();
                }
                //设置事件处理器对象
                this.handler = handler;
                //开始定时调用
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

    // TODO: 16/12/14 by zmyer
    class SharedWorkerPool extends WorkerPool {
        //共享工作线程池名称
        private final String name;
        //引用计数器
        private int refCount = 1;

        // TODO: 16/12/15 by zmyer
        SharedWorkerPool(String name, ExecutorService workerExec, PoolMetrics workerMetrics) {
            super(workerExec, workerMetrics);
            this.name = name;
        }

        // TODO: 16/12/15 by zmyer
        @Override
        void close() {
            synchronized (VertxImpl.this) {
                if (refCount > 0) {
                    refCount = 0;
                    super.close();
                }
            }
        }

        // TODO: 16/12/15 by zmyer
        void release() {
            synchronized (VertxImpl.this) {
                if (--refCount == 0) {
                    releaseWorkerExecutor(name);
                    super.close();
                }
            }
        }
    }

    // TODO: 16/12/15 by zmyer
    @Override
    public WorkerExecutorImpl createSharedWorkerExecutor(String name) {
        //创建共享工作线程执行对象
        return createSharedWorkerExecutor(name, defaultWorkerPoolSize);
    }

    // TODO: 16/12/15 by zmyer
    @Override
    public WorkerExecutorImpl createSharedWorkerExecutor(String name, int poolSize) {
        return createSharedWorkerExecutor(name, poolSize, defaultWorkerMaxExecTime);
    }

    // TODO: 16/12/15 by zmyer
    @Override
    public synchronized WorkerExecutorImpl createSharedWorkerExecutor(String name, int poolSize, long maxExecuteTime) {
        if (maxExecuteTime < 1) {
            throw new IllegalArgumentException("poolSize must be > 0");
        }
        if (maxExecuteTime < 1) {
            throw new IllegalArgumentException("maxExecuteTime must be > 0");
        }
        //获取共享线程池对象
        SharedWorkerPool sharedWorkerPool = namedWorkerPools.get(name);
        if (sharedWorkerPool == null) {
            //创建执行线程池对象
            ExecutorService workerExec = Executors.newFixedThreadPool(poolSize, new VertxThreadFactory(name + "-", checker, true, maxExecuteTime));
            PoolMetrics workerMetrics = isMetricsEnabled() ? metrics.createMetrics(workerExec, "worker", name, poolSize) : null;
            //注册共享线程池对象
            namedWorkerPools.put(name, sharedWorkerPool = new SharedWorkerPool(name, workerExec, workerMetrics));
        } else {
            //增加引用计数器
            sharedWorkerPool.refCount++;
        }
        //创建执行上下文对象
        ContextImpl context = getOrCreateContext();
        WorkerExecutorImpl namedExec = new WorkerExecutorImpl(context, sharedWorkerPool, true);
        context.addCloseHook(namedExec);
        return namedExec;
    }

    synchronized void releaseWorkerExecutor(String name) {
        namedWorkerPools.remove(name);
    }

    // TODO: 16/12/15 by zmyer
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
