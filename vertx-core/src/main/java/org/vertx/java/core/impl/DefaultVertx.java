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

import org.jboss.netty.channel.socket.nio.NioWorker;
import org.jboss.netty.channel.socket.nio.NioWorkerPool;
import org.jboss.netty.util.HashedWheelTimer;
import org.jboss.netty.util.Timeout;
import org.jboss.netty.util.TimerTask;
import org.vertx.java.core.Handler;
import org.vertx.java.core.eventbus.EventBus;
import org.vertx.java.core.eventbus.impl.DefaultEventBus;
import org.vertx.java.core.file.FileSystem;
import org.vertx.java.core.file.impl.DefaultFileSystem;
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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class DefaultVertx extends VertxInternal {

  private static final Logger log = LoggerFactory.getLogger(DefaultVertx.class);

  private final FileSystem fileSystem = new DefaultFileSystem(this);
  private final EventBus eventBus;
  private final SharedData sharedData = new SharedData();

  private int backgroundPoolSize = 20;
  private int corePoolSize = Runtime.getRuntime().availableProcessors();
  private ExecutorService backgroundPool;
  private OrderedExecutorFactory orderedFact;
  private NioWorkerPool workerPool;
  private ExecutorService acceptorPool;
  private final Map<ServerID, DefaultHttpServer> sharedHttpServers = new HashMap<>();
  private final Map<ServerID, DefaultNetServer> sharedNetServers = new HashMap<>();

  //For now we use a hashed wheel with it's own thread for timeouts - ideally the event loop would have
  //it's own hashed wheel
  private final HashedWheelTimer timer = new HashedWheelTimer(new VertxThreadFactory("vert.x-timer-thread"), 20,
      TimeUnit.MILLISECONDS, 8192);
  {
    timer.start();
  }
  private final AtomicLong timeoutCounter = new AtomicLong(0);
  private final Map<Long, TimeoutHolder> timeouts = new ConcurrentHashMap<>();

  public DefaultVertx() {
    this.eventBus = new DefaultEventBus(this);
    configure();
  }

  public DefaultVertx(String hostname) {
    this.eventBus = new DefaultEventBus(this, hostname);
    configure();
  }

  public DefaultVertx(int port, String hostname) {
    this.eventBus = new DefaultEventBus(this, port, hostname);
    configure();
  }

  /**
   * deal with configuration parameters
   */
  private void configure() {
    this.backgroundPoolSize = Integer.getInteger("vertx.backgroundPoolSize", 20);
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

  public Context startOnEventLoop(final Runnable runnable) {
    Context context  = createEventLoopContext();
    context.execute(runnable);
    return context;
  }

  public Context startInBackground(final Runnable runnable) {
    Context context  = createWorkerContext();
    context.execute(runnable);
    return context;
  }

  public boolean isEventLoop() {
    Context context = Context.getContext();
    if (context != null) {
      return context instanceof EventLoopContext;
    }
    return false;
  }

  public boolean isWorker() {
    Context context = Context.getContext();
    if (context != null) {
      return context instanceof WorkerContext;
    }
    return false;
  }

  public long setPeriodic(long delay, final Handler<Long> handler) {
    return setTimeout(delay, true, handler);
  }

  public long setTimer(long delay, final Handler<Long> handler) {
    return setTimeout(delay, false, handler);
  }

  public void runOnLoop(final Handler<Void> handler) {
    Context context = getOrAssignContext();
    context.execute(new Runnable() {
      public void run() {
        handler.handle(null);
      }
    });
  }

  //The worker pool is used for making blocking calls to legacy synchronous APIs
  public ExecutorService getBackgroundPool() {
    //This is a correct implementation of double-checked locking idiom
    ExecutorService result = backgroundPool;
    if (result == null) {
      synchronized (this) {
        result = backgroundPool;
        if (result == null) {
          backgroundPool = result = Executors.newFixedThreadPool(backgroundPoolSize, new VertxThreadFactory("vert.x-worker-thread-"));
          orderedFact = new OrderedExecutorFactory(backgroundPool);
        }
      }
    }
    return result;
  }

  public NioWorkerPool getWorkerPool() {
    //This is a correct implementation of double-checked locking idiom
    NioWorkerPool result = workerPool;
    if (result == null) {
      synchronized (this) {
        result = workerPool;
        if (result == null) {
          ExecutorService corePool = Executors.newFixedThreadPool(corePoolSize, new VertxThreadFactory("vert.x-core-thread-"));
          workerPool = result = new NioWorkerPool(corePool, corePoolSize, false);
        }
      }
    }
    return result;
  }

  //We use a cached pool, but it will never get large since only used for acceptors.
  //There will be one thread for each port listening on
  public Executor getAcceptorPool() {
    //This is a correct implementation of double-checked locking idiom
    ExecutorService result = acceptorPool;
    if (result == null) {
      synchronized (this) {
        result = acceptorPool;
        if (result == null) {
          acceptorPool = result = Executors.newCachedThreadPool(new VertxThreadFactory("vert.x-acceptor-thread-"));
        }
      }
    }
    return result;
  }

  public Context getOrAssignContext() {
    Context ctx = Context.getContext();
    if (ctx == null) {
      // Assign a context
      ctx = createEventLoopContext();
    }
    return ctx;
  }

  public void reportException(Throwable t) {
    Context ctx = Context.getContext();
    if (ctx != null) {
      ctx.reportException(t);
    } else {
      log.error(" default vertx Unhandled exception ", t);
    }
  }

  public Map<ServerID, DefaultHttpServer> sharedHttpServers() {
    return sharedHttpServers;
  }

  public Map<ServerID, DefaultNetServer> sharedNetServers() {
    return sharedNetServers;
  }

  private long setTimeout(final long delay, boolean periodic, final Handler<Long> handler) {
    final Context context = getOrAssignContext();

    InternalTimerHandler myHandler;
    if (periodic) {
      myHandler = new InternalTimerHandler(handler) {
        public void run() {
          super.run();
          scheduleTimeout(timerID, context, this, delay); // And reschedule
        }
      };
    } else {
      myHandler = new InternalTimerHandler(handler) {
        public void run() {
          super.run();
          timeouts.remove(timerID);
        }
      };
    }
    long timerID = scheduleTimeout(-1, context, myHandler, delay);
    myHandler.timerID = timerID;
    return timerID;
  }

  public boolean cancelTimer(long id) {
    return cancelTimeout(id);
  }

  public Context createEventLoopContext() {
    getBackgroundPool();
    NioWorker worker = getWorkerPool().nextWorker();
    return new EventLoopContext(orderedFact.getExecutor(), worker);
  }

  private boolean cancelTimeout(long id) {
    TimeoutHolder holder = timeouts.remove(id);
    if (holder != null) {
      holder.timeout.cancel();
      return true;
    } else {
      return false;
    }
  }

  private long scheduleTimeout(long id, final Context context, final Runnable task, long delay) {
    TimerTask ttask = new TimerTask() {
      public void run(Timeout timeout) throws Exception {
        context.execute(task);
      }
    };
    if (id != -1 && timeouts.get(id) == null) {
      //Been cancelled
      return -1;
    }
    Timeout timeout = timer.newTimeout(ttask, delay, TimeUnit.MILLISECONDS);
    id = id != -1 ? id : timeoutCounter.getAndIncrement();
    timeouts.put(id, new TimeoutHolder(timeout, context));
    return id;
  }

  private Context createWorkerContext() {
    getBackgroundPool();
    return new WorkerContext(orderedFact.getExecutor());
  }

  private static class InternalTimerHandler implements Runnable {
    final Handler<Long> handler;
    long timerID;

    InternalTimerHandler(Handler<Long> runnable) {
      this.handler = runnable;
    }

    public void run() {
      handler.handle(timerID);
    }
  }

  private static class TimeoutHolder {
    final Timeout timeout;
    final Context context;

    TimeoutHolder(Timeout timeout, Context context) {
      this.timeout = timeout;
      this.context = context;
    }
  }

}
