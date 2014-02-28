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
import io.netty.util.ResourceLeakDetector;
import org.vertx.java.core.*;
import org.vertx.java.core.datagram.DatagramSocket;
import org.vertx.java.core.datagram.InternetProtocolFamily;
import org.vertx.java.core.datagram.impl.DefaultDatagramSocket;
import org.vertx.java.core.dns.DnsClient;
import org.vertx.java.core.dns.impl.DefaultDnsClient;
import org.vertx.java.core.eventbus.EventBus;
import org.vertx.java.core.eventbus.impl.DefaultEventBus;
import org.vertx.java.core.file.FileSystem;
import org.vertx.java.core.file.impl.DefaultFileSystem;
import org.vertx.java.core.file.impl.WindowsFileSystem;
import org.vertx.java.core.http.HttpClient;
import org.vertx.java.core.http.HttpServer;
import org.vertx.java.core.http.impl.DefaultHttpClient;
import org.vertx.java.core.http.impl.DefaultHttpServer;
import org.vertx.java.core.logging.Logger;
import org.vertx.java.core.logging.impl.LoggerFactory;
import org.vertx.java.core.net.NetClient;
import org.vertx.java.core.net.NetServer;
import org.vertx.java.core.net.impl.DefaultNetClient;
import org.vertx.java.core.net.impl.DefaultNetServer;
import org.vertx.java.core.net.impl.ServerID;
import org.vertx.java.core.shareddata.SharedData;
import org.vertx.java.core.sockjs.SockJSServer;
import org.vertx.java.core.sockjs.impl.DefaultSockJSServer;
import org.vertx.java.core.spi.Action;
import org.vertx.java.core.spi.cluster.ClusterManager;
import org.vertx.java.core.spi.cluster.ClusterManagerFactory;

import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.concurrent.*;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicLong;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class DefaultVertx implements VertxInternal {

  private static final Logger log = LoggerFactory.getLogger(DefaultVertx.class);

  static {
    // Netty resource leak detection has a performance overhead and we do not need it in Vert.x
    ResourceLeakDetector.setLevel(ResourceLeakDetector.Level.DISABLED);
    // Use the JDK deflater/inflater by default
    System.setProperty("io.netty.noJdkZlibDecoder", "false");
  }

  private final FileSystem fileSystem = getFileSystem();
  private final EventBus eventBus;
  private final SharedData sharedData = new SharedData();

  private ExecutorService backgroundPool = VertxExecutorFactory.workerPool("vert.x-worker-thread-");
  private final OrderedExecutorFactory orderedFact = new OrderedExecutorFactory(backgroundPool);
  private EventLoopGroup eventLoopGroup = VertxExecutorFactory.eventLoopGroup("vert.x-eventloop-thread-");

  private Map<ServerID, DefaultHttpServer> sharedHttpServers = new HashMap<>();
  private Map<ServerID, DefaultNetServer> sharedNetServers = new HashMap<>();

  private final ConcurrentMap<Long, InternalTimerHandler> timeouts = new ConcurrentHashMap<>();
  private final AtomicLong timeoutCounter = new AtomicLong(0);
  private final ClusterManager clusterManager;

  public DefaultVertx() {
    this.eventBus = new DefaultEventBus(this);
    this.clusterManager = null;
  }

  public DefaultVertx(String hostname) {
    this(0, hostname, null);
  }

  public DefaultVertx(int port, String hostname, final Handler<AsyncResult<Vertx>> resultHandler) {
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
    final Vertx inst = this;
    this.eventBus = new DefaultEventBus(this, port, hostname, clusterManager, new AsyncResultHandler<Void>() {
      @Override
      public void handle(AsyncResult<Void> res) {
        if (resultHandler != null) {
          if (res.succeeded()) {
            resultHandler.handle(new DefaultFutureResult<>(inst));
          } else {
            resultHandler.handle(new DefaultFutureResult<Vertx>(res.cause()));
          }
        } else if (res.failed()) {
          log.error("Failed to start event bus", res.cause());
        }
      }
    });
  }

  /**
   * @return The FileSystem implementation for the OS
   */
  protected FileSystem getFileSystem() {
  	return Windows.isWindows() ? new WindowsFileSystem(this) : new DefaultFileSystem(this);
  }

  @Override
  public DatagramSocket createDatagramSocket(InternetProtocolFamily family) {
    return new DefaultDatagramSocket(this, family);
  }

  public NetServer createNetServer() {
    return new DefaultNetServer(this);
  }

  public NetClient createNetClient() {
    return new DefaultNetClient(this);
  }

  public FileSystem fileSystem() {
    return fileSystem;
  }

  public SharedData sharedData() {
    return sharedData;
  }

  public HttpServer createHttpServer() {
    return new DefaultHttpServer(this);
  }

  public HttpClient createHttpClient() {
    return new DefaultHttpClient(this);
  }

  public SockJSServer createSockJSServer(HttpServer httpServer) {
    return new DefaultSockJSServer(this, httpServer);
  }

  public EventBus eventBus() {
    return eventBus;
  }

  public DefaultContext startOnEventLoop(final Runnable runnable) {
    DefaultContext context  = createEventLoopContext();
    context.execute(runnable);
    return context;
  }

  public DefaultContext startInBackground(final Runnable runnable, final boolean multiThreaded) {
    DefaultContext context  = createWorkerContext(multiThreaded);
    context.execute(runnable);
    return context;
  }

  public boolean isEventLoop() {
    DefaultContext context = getContext();
    if (context != null) {
      return context instanceof EventLoopContext;
    }
    return false;
  }

  public boolean isWorker() {
    DefaultContext context = getContext();
    if (context != null) {
      return context instanceof WorkerContext;
    }
    return false;
  }

  public long setPeriodic(long delay, final Handler<Long> handler) {
    return scheduleTimeout(getOrCreateContext(), handler, delay, true);
  }

  public long setTimer(long delay, final Handler<Long> handler) {
    return scheduleTimeout(getOrCreateContext(), handler, delay, false);
  }

  public void runOnContext(final Handler<Void> task) {
    DefaultContext context = getOrCreateContext();
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

  public DefaultContext getOrCreateContext() {
    DefaultContext ctx = getContext();
    if (ctx == null) {
      // Create a context
      ctx = createEventLoopContext();
    }
    return ctx;
  }

  public void reportException(Throwable t) {
    DefaultContext ctx = getContext();
    if (ctx != null) {
      ctx.reportException(t);
    } else {
      log.error("default vertx Unhandled exception ", t);
    }
  }

  public Map<ServerID, DefaultHttpServer> sharedHttpServers() {
    return sharedHttpServers;
  }

  public Map<ServerID, DefaultNetServer> sharedNetServers() {
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
  public DnsClient createDnsClient(InetSocketAddress... dnsServers) {
    return new DefaultDnsClient(this, dnsServers);
  }

  private long scheduleTimeout(final DefaultContext context, final Handler<Long> handler, long delay, boolean periodic) {
    if (delay < 1) {
      throw new IllegalArgumentException("Cannot schedule a timer with delay < 1 ms");
    }
    long timerId = timeoutCounter.getAndIncrement();
    final InternalTimerHandler task = new InternalTimerHandler(timerId, handler, periodic, context);
    final Runnable wrapped = context.wrapTask(task);

    final Runnable toRun;
    final EventLoop el = context.getEventLoop();
    if (context instanceof EventLoopContext) {
      toRun = wrapped;
    } else {
      // On worker context
      toRun = new Runnable() {
        public void run() {
          // Make sure the timer gets executed on the worker context
          context.execute(wrapped);
        }
      };
    }
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

  private DefaultContext createWorkerContext(boolean multiThreaded) {
    if (multiThreaded) {
      return new MultiThreadedWorkerContext(this, orderedFact.getExecutor(), backgroundPool);
    } else {
      return new WorkerContext(this, orderedFact.getExecutor());
    }
  }

  public void setContext(DefaultContext context) {
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

  public DefaultContext getContext() {
    Thread current = Thread.currentThread();
    if (current instanceof VertxThread) {
      return ((VertxThread)current).getContext();
    }
    return null;
  }

  @Override
  public void stop() {
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
        backgroundPool = null;
      }
    } catch (InterruptedException ex) {
      // ignore
    }

    if (eventLoopGroup != null) {
      eventLoopGroup.shutdownGracefully();
    }

    eventBus.close(null);

    setContext(null);
  }

  @Override
  public <T> void executeBlocking(final Action<T> action, final Handler<AsyncResult<T>> resultHandler) {
    final DefaultContext context = getOrCreateContext();

    Runnable runner = new Runnable() {
      public void run() {
        final DefaultFutureResult<T> res = new DefaultFutureResult<>();
        try {
          T result = action.perform();
          res.setResult(result);
        } catch (Exception e) {
          res.setFailure(e);
        }
        if (resultHandler != null) {
          context.execute(new Runnable() {
              public void run() {
                res.setHandler(resultHandler);
              }
            });
        }
      }
    };

    context.executeOnOrderedWorkerExec(runner);
  }

  public ClusterManager clusterManager() {
    return clusterManager;
  }

  private class InternalTimerHandler implements Runnable, Closeable {
    final Handler<Long> handler;
    final boolean periodic;
    final long timerID;
    final DefaultContext context;
    volatile Future<?> future;
    boolean cancelled;

    boolean cancel() {
      cancelled = true;
      return future.cancel(false);
    }

    InternalTimerHandler(long timerID, Handler<Long> runnable, boolean periodic, DefaultContext context) {
      this.context = context;
      this.timerID = timerID;
      this.handler = runnable;
      this.periodic = periodic;
    }

    public void run() {
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
      DefaultVertx.this.timeouts.remove(timerID);
      DefaultContext context = getContext();
      context.removeCloseHook(this);
    }

    // Called via Context close hook when Verticle is undeployed
    public void close(Handler<AsyncResult<Void>> doneHandler) {
      DefaultVertx.this.timeouts.remove(timerID);
      cancel();
      doneHandler.handle(new DefaultFutureResult<>((Void)null));
    }

  }
}
