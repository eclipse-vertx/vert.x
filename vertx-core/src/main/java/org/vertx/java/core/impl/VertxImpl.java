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

package org.vertx.java.core.impl;

import io.netty.channel.EventLoop;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.util.ResourceLeakDetector;
import org.vertx.java.core.*;
import org.vertx.java.core.datagram.DatagramSocket;
import org.vertx.java.core.datagram.DatagramSocketOptions;
import org.vertx.java.core.datagram.InternetProtocolFamily;
import org.vertx.java.core.datagram.impl.DatagramSocketImpl;
import org.vertx.java.core.dns.DnsClient;
import org.vertx.java.core.dns.impl.DnsClientImpl;
import org.vertx.java.core.eventbus.EventBus;
import org.vertx.java.core.eventbus.impl.EventBusImpl;
import org.vertx.java.core.file.FileSystem;
import org.vertx.java.core.file.impl.FileSystemImpl;
import org.vertx.java.core.file.impl.WindowsFileSystem;
import org.vertx.java.core.http.HttpClient;
import org.vertx.java.core.http.HttpClientOptions;
import org.vertx.java.core.http.HttpServer;
import org.vertx.java.core.http.HttpServerOptions;
import org.vertx.java.core.http.impl.HttpClientImpl;
import org.vertx.java.core.http.impl.HttpServerImpl;
import org.vertx.java.core.json.JsonObject;
import org.vertx.java.core.logging.Logger;
import org.vertx.java.core.logging.impl.LoggerFactory;
import org.vertx.java.core.net.*;
import org.vertx.java.core.net.impl.NetClientImpl;
import org.vertx.java.core.net.impl.NetServerImpl;
import org.vertx.java.core.net.impl.ServerID;
import org.vertx.java.core.shareddata.SharedData;
import org.vertx.java.core.sockjs.SockJSServer;
import org.vertx.java.core.sockjs.impl.SockJSServerImpl;
import org.vertx.java.core.spi.Action;
import org.vertx.java.core.spi.cluster.ClusterManager;
import org.vertx.java.core.spi.cluster.ClusterManagerFactory;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicLong;

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
  private final EventBus eventBus;
  private final SharedData sharedData = new SharedData();

  private ExecutorService backgroundPool;
  private OrderedExecutorFactory orderedFact;
  private EventLoopGroup eventLoopGroup;
  private BlockedThreadChecker checker;

  private Map<ServerID, HttpServerImpl> sharedHttpServers = new HashMap<>();
  private Map<ServerID, NetServerImpl> sharedNetServers = new HashMap<>();

  private final ConcurrentMap<Long, InternalTimerHandler> timeouts = new ConcurrentHashMap<>();
  private final AtomicLong timeoutCounter = new AtomicLong(0);
  private final ClusterManager clusterManager;
  private final DeploymentManager deploymentManager = new DeploymentManager(this);

  public VertxImpl() {
    this(new VertxOptions());
  }

  public VertxImpl(VertxOptions options) {
    this.clusterManager = null;
    this.eventBus = new EventBusImpl(this);
    configurePools(options);
  }

  public VertxImpl(VertxOptions options, Handler<AsyncResult<Vertx>> resultHandler) {
    configurePools(options);
    ClusterManagerFactory factory;
    String clusterManagerFactoryClassName = System.getProperty("vertx.clusterManagerFactory");
    if (clusterManagerFactoryClassName != null) {
      // We allow specify a sys prop for the cluster manager factory which overrides ServiceLoader
      try {
        Class<?> clazz = Class.forName(clusterManagerFactoryClassName);
        factory = (ClusterManagerFactory)clazz.newInstance();
      } catch (Exception e) {
        throw new IllegalStateException("Failed to instantiate " + clusterManagerFactoryClassName, e);
      }
    } else {
      ServiceLoader<ClusterManagerFactory> factories = ServiceLoader.load(ClusterManagerFactory.class);
      if (!factories.iterator().hasNext()) {
        throw new IllegalStateException("No ClusterManagerFactory instances found on classpath");
      }
      factory = factories.iterator().next();
    }
    this.clusterManager = factory.createClusterManager(this);
    this.clusterManager.join();
    Vertx inst = this;
    this.eventBus = new EventBusImpl(this, options.getClusterPort(), options.getClusterHost(), clusterManager, res -> {
      if (resultHandler != null) {
        if (res.succeeded()) {
          resultHandler.handle(new FutureResultImpl<>(inst));
        } else {
          resultHandler.handle(new FutureResultImpl<>(res.cause()));
        }
      } else if (res.failed()) {
        log.error("Failed to start event bus", res.cause());
      }
    });
  }

  /**
   * @return The FileSystem implementation for the OS
   */
  protected FileSystem getFileSystem() {
  	return Windows.isWindows() ? new WindowsFileSystem(this) : new FileSystemImpl(this);
  }

  @Override
  public DatagramSocket createDatagramSocket(InternetProtocolFamily family, DatagramSocketOptions options) {
    return new DatagramSocketImpl(this, family, options);
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

  public SockJSServer createSockJSServer(HttpServer httpServer) {
    return new SockJSServerImpl(this, httpServer);
  }

  public EventBus eventBus() {
    return eventBus;
  }

  public boolean isEventLoop() {
    ContextImpl context = getContext();
    if (context != null) {
      return context instanceof EventLoopContext;
    }
    return false;
  }

  public boolean isWorker() {
    ContextImpl context = getContext();
    if (context != null) {
      return context instanceof WorkerContext;
    }
    return false;
  }

  public long setPeriodic(long delay, Handler<Long> handler) {
    return scheduleTimeout(getOrCreateContext(), handler, delay, true);
  }

  public long setTimer(long delay, Handler<Long> handler) {
    return scheduleTimeout(getOrCreateContext(), handler, delay, false);
  }

  public void runOnContext(Handler<Void> task) {
    ContextImpl context = getOrCreateContext();
    context.runOnContext(task);
  }

  public Context currentContext() {
    return getContext();
  }

  // The background pool is used for making blocking calls to legacy synchronous APIs
  public ExecutorService getBackgroundPool() {
    return backgroundPool;
  }

  public EventLoopGroup getEventLoopGroup() {
    return eventLoopGroup;
  }

  public ContextImpl getOrCreateContext() {
    ContextImpl ctx = getContext();
    if (ctx == null) {
      // Create a context
      ctx = createEventLoopContext();
    }
    return ctx;
  }

  public void reportException(Throwable t) {
    ContextImpl ctx = getContext();
    if (ctx != null) {
      ctx.reportException(t);
    } else {
      log.error("Unhandled exception ", t);
    }
  }

  public Map<ServerID, HttpServerImpl> sharedHttpServers() {
    return sharedHttpServers;
  }

  public Map<ServerID, NetServerImpl> sharedNetServers() {
    return sharedNetServers;
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

  public EventLoopContext createEventLoopContext() {
    return new EventLoopContext(this, orderedFact.getExecutor());
  }

  @Override
  public DnsClient createDnsClient(SocketAddress... dnsServers) {
    return new DnsClientImpl(this, dnsServers);
  }

  private long scheduleTimeout(ContextImpl context, Handler<Long> handler, long delay, boolean periodic) {
    if (delay < 1) {
      throw new IllegalArgumentException("Cannot schedule a timer with delay < 1 ms");
    }
    long timerId = timeoutCounter.getAndIncrement();
    InternalTimerHandler task = new InternalTimerHandler(timerId, handler, periodic, context);
    Runnable toRun = () -> context.execute(task, false);
    EventLoop el = context.getEventLoop();
    Future<?> future;
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

  public ContextImpl createWorkerContext(boolean multiThreaded) {
    if (multiThreaded) {
      return new MultiThreadedWorkerContext(this, orderedFact.getExecutor(), backgroundPool);
    } else {
      return new WorkerContext(this, orderedFact.getExecutor());
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
  public void stop() {
    stop(null);
  }

  @Override
  public void stop(Handler<AsyncResult<Void>> doneHandler) {

    // TODO call deploymentManager.undeployAll

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

    if (backgroundPool != null) {
      backgroundPool.shutdown();
    }

    try {
      if (backgroundPool != null) {
        backgroundPool.awaitTermination(20, TimeUnit.SECONDS);
      }
    } catch (InterruptedException ex) {
      // ignore
    }

    if (eventLoopGroup != null) {
      eventLoopGroup.shutdownGracefully();
    }

    eventBus.close(doneHandler);

    checker.close();

    setContext(null);
  }

  @Override
  public void deployVerticle(Verticle verticle) {
    deploymentManager.deployVerticle(verticle, null, false, null);
  }

  @Override
  public void deployVerticle(Verticle verticle, JsonObject config) {
    deploymentManager.deployVerticle(verticle, config, false, null);
  }

  @Override
  public void deployVerticle(Verticle verticle, Handler<AsyncResult<String>> doneHandler) {
    deploymentManager.deployVerticle(verticle, null, false, doneHandler);
  }

  @Override
  public void deployVerticle(Verticle verticle, JsonObject config, Handler<AsyncResult<String>> doneHandler) {
    deploymentManager.deployVerticle(verticle, config, false, doneHandler);
  }

  @Override
  public void deployVerticle(String verticleClass, Handler<AsyncResult<String>> doneHandler) {
    deploymentManager.deployVerticle(verticleClass, null, false, null, doneHandler);
  }

  @Override
  public void deployVerticle(String verticleClass, JsonObject config, Handler<AsyncResult<String>> doneHandler) {
    deploymentManager.deployVerticle(verticleClass, config, false, null, doneHandler);
  }

  @Override
  public void deployVerticle(String verticleClass, String isolationGroup, Handler<AsyncResult<String>> doneHandler) {
    deploymentManager.deployVerticle(verticleClass, null, false, isolationGroup, doneHandler);
  }

  @Override
  public void deployVerticle(String verticleClass, JsonObject config, String isolationGroup, Handler<AsyncResult<String>> doneHandler) {
    deploymentManager.deployVerticle(verticleClass, config, false, isolationGroup, doneHandler);
  }

  @Override
  public void deployVerticle(String verticleClass, JsonObject config, boolean worker, Handler<AsyncResult<String>> doneHandler) {
    deploymentManager.deployVerticle(verticleClass, config, worker, null, doneHandler);
  }

  @Override
  public void deployVerticle(String verticleClass, String isolationGroup, boolean worker, Handler<AsyncResult<String>> doneHandler) {
    deploymentManager.deployVerticle(verticleClass, null, worker, isolationGroup, doneHandler);
  }

  @Override
  public void deployVerticle(String verticleClass, JsonObject config, String isolationGroup, boolean worker, Handler<AsyncResult<String>> doneHandler) {
    deploymentManager.deployVerticle(verticleClass, config, worker, isolationGroup, doneHandler);
  }

  @Override
  public void deployVerticle(Verticle verticle, boolean worker, Handler<AsyncResult<String>> doneHandler) {
    deploymentManager.deployVerticle(verticle, null, worker, doneHandler);
  }

  @Override
  public void deployVerticle(Verticle verticle, JsonObject config, boolean worker, Handler<AsyncResult<String>> doneHandler) {
    deploymentManager.deployVerticle(verticle, config, worker, doneHandler);
  }

  @Override
  public void undeployVerticle(String deploymentID, Handler<AsyncResult<Void>> doneHandler) {
    deploymentManager.undeployVerticle(deploymentID, doneHandler);
  }

  @Override
  public Set<String> deployments() {
    return deploymentManager.deployments();
  }

  @Override
  public <T> void executeBlocking(Action<T> action, Handler<AsyncResult<T>> resultHandler) {
    ContextImpl context = getOrCreateContext();
    context.executeOnOrderedWorkerExec(() -> {
      FutureResultImpl<T> res = new FutureResultImpl<>();
      try {
        T result = action.perform();
        res.setResult(result);
      } catch (Exception e) {
        res.setFailure(e);
      }
      if (resultHandler != null) {
        context.execute(() -> res.setHandler(resultHandler), false);
      }
    });
  }

  public ClusterManager clusterManager() {
    return clusterManager;
  }

  private void configurePools(VertxOptions options) {
    checker = new BlockedThreadChecker(options.getBlockedThreadCheckPeriod(), options.getMaxEventLoopExecuteTime(),
                                       options.getMaxWorkerExecuteTime());
    eventLoopGroup = new NioEventLoopGroup(options.getEventLoopPoolSize(),
                                           new VertxThreadFactory("vert.x-eventloop-thread-", checker, false));
    backgroundPool = Executors.newFixedThreadPool(options.getWorkerPoolSize(),
      new VertxThreadFactory("vert.x-worker-thread-", checker, true));
    orderedFact = new OrderedExecutorFactory(backgroundPool);
  }

  private class InternalTimerHandler implements ContextTask, Closeable {
    final Handler<Long> handler;
    final boolean periodic;
    final long timerID;
    final ContextImpl context;
    volatile Future<?> future;
    boolean cancelled;

    boolean cancel() {
      cancelled = true;
      return future.cancel(false);
    }

    InternalTimerHandler(long timerID, Handler<Long> runnable, boolean periodic, ContextImpl context) {
      this.context = context;
      this.timerID = timerID;
      this.handler = runnable;
      this.periodic = periodic;
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
      ContextImpl context = getContext();
      context.removeCloseHook(this);
    }

    // Called via Context close hook when Verticle is undeployed
    public void close(Handler<AsyncResult<Void>> doneHandler) {
      VertxImpl.this.timeouts.remove(timerID);
      cancel();
      doneHandler.handle(new FutureResultImpl<>((Void)null));
    }

  }
}
