/*
 * Copyright 2011-2012 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.vertx.java.core.impl;

import io.netty.channel.EventLoop;
import io.netty.channel.EventLoopGroup;
import org.vertx.java.core.Context;
import org.vertx.java.core.Handler;
import org.vertx.java.core.eventbus.EventBus;
import org.vertx.java.core.eventbus.impl.DefaultEventBus;
import org.vertx.java.core.eventbus.impl.hazelcast.HazelcastClusterManager;
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

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

/**                                                e
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class DefaultVertx implements VertxInternal {

  private static final Logger log = LoggerFactory.getLogger(DefaultVertx.class);

  public static final int DEFAULT_CLUSTER_PORT = 2550;

  private final FileSystem fileSystem = getFileSystem();
  private final EventBus eventBus;
  private final SharedData sharedData = new SharedData();

  private ExecutorService backgroundPool = VertxExecutorFactory.workerPool("vert.x-worker-thread-");
  private OrderedExecutorFactory orderedFact = new OrderedExecutorFactory(backgroundPool);
  private EventLoopGroup eventLoopGroup = VertxExecutorFactory.eventLoopGroup("vert.x-eventloop-thread-");

  private Map<ServerID, DefaultHttpServer> sharedHttpServers = new HashMap<>();
  private Map<ServerID, DefaultNetServer> sharedNetServers = new HashMap<>();
  private final ThreadLocal<DefaultContext> contextTL = new ThreadLocal<>();

  private final ConcurrentMap<Long, InternalTimerHandler> timeouts = new ConcurrentHashMap<>();
  private final AtomicLong timeoutCounter = new AtomicLong(0);

  public DefaultVertx() {
    this.eventBus = new DefaultEventBus(this);
  }

  public DefaultVertx(String hostname) {
    this(DEFAULT_CLUSTER_PORT, hostname);
  }

  public DefaultVertx(int port, String hostname) {
    this.eventBus = new DefaultEventBus(this, port, hostname, new HazelcastClusterManager(this));
  }

  /**
   * @return The FileSystem implementation for the OS
   */
  protected FileSystem getFileSystem() {
  	return Windows.isWindows() ? new WindowsFileSystem(this) : new DefaultFileSystem(this);
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
    return scheduleTimeout(getOrAssignContext(), handler, delay, true);
  }

  public long setTimer(long delay, final Handler<Long> handler) {
    return scheduleTimeout(getOrAssignContext(), handler, delay, false);
  }

  public void runOnContext(final Handler<Void> task) {
    DefaultContext context = getOrAssignContext();
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

  public DefaultContext getOrAssignContext() {
    DefaultContext ctx = getContext();
    if (ctx == null) {
      // Assign a context
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
    return cancelTimeout(id);
  }

  public EventLoopContext createEventLoopContext() {
    return new EventLoopContext(this, orderedFact.getExecutor());
  }

  private boolean cancelTimeout(long id) {
    InternalTimerHandler handler = timeouts.remove(id);
    if (handler != null) {
      return handler.future.cancel(false);
    } else {
      return false;
    }
  }

  private long scheduleTimeout(final DefaultContext context, final Handler<Long> handler, long delay, boolean periodic) {

    if (delay < 1) {
      throw new IllegalArgumentException("Cannot schedule a timer with delay < 1 ms");
    }

    long timerId = timeoutCounter.getAndIncrement();
    final InternalTimerHandler task = new InternalTimerHandler(timerId, handler);
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
    contextTL.set(context);
    if (context != null) {
      context.setTCCL();
    }
  }

  public DefaultContext getContext() {
    return contextTL.get();
  }

  @Override
  public void stop() {
    if (sharedHttpServers != null) {
      for (HttpServer server : sharedHttpServers.values()) {
        server.close();
      }
      sharedHttpServers = null;
    }

    if (sharedNetServers != null) {
      for (NetServer server : sharedNetServers.values()) {
        server.close();
      }
      sharedNetServers = null;
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
      eventLoopGroup.shutdown();
      eventLoopGroup = null;
    }

    setContext(null);
  }

  private static class InternalTimerHandler implements Runnable {
    final Handler<Long> handler;
    final long timerID;
    volatile Future<?> future;

    InternalTimerHandler(long timerID, Handler<Long> runnable) {
      this.timerID = timerID;
      this.handler = runnable;
    }

    public void run() {
      handler.handle(timerID);
    }
  }
}
