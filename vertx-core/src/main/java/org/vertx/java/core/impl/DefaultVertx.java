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

import org.jboss.netty.channel.DefaultChannelFuture;
import org.jboss.netty.channel.socket.nio.NioClientBossPool;
import org.jboss.netty.channel.socket.nio.NioServerBossPool;
import org.jboss.netty.channel.socket.nio.NioWorker;
import org.jboss.netty.channel.socket.nio.NioWorkerPool;
import org.jboss.netty.util.*;
import org.vertx.java.core.Handler;
import org.vertx.java.core.eventbus.EventBus;
import org.vertx.java.core.eventbus.impl.ClusterManager;
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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**                                                e
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class DefaultVertx extends VertxInternal {

  private static final Logger log = LoggerFactory.getLogger(DefaultVertx.class);

  public static final int DEFAULT_CLUSTER_PORT = 2550;

  private final FileSystem fileSystem = getFileSystem();
  private final EventBus eventBus;
  private final SharedData sharedData = new SharedData();

  //For now we use a hashed wheel with it's own thread for timeouts - ideally the event loop would have
  //it's own hashed wheel
  private HashedWheelTimer timer = new HashedWheelTimer(new VertxThreadFactory("vert.x-timer-thread"), 1,
      TimeUnit.MILLISECONDS, 8192);
  {
    timer.start();
  }

  private ExecutorService backgroundPool = VertxExecutorFactory.workerPool("vert.x-worker-thread-");
  private OrderedExecutorFactory orderedFact = new OrderedExecutorFactory(backgroundPool);
  private NioWorkerPool corePool = VertxExecutorFactory.corePool("vert.x-core-thread-");
  private NioServerBossPool serverBossPool = VertxExecutorFactory.serverAcceptorPool("vert.x-server-acceptor-thread-");
  private NioClientBossPool clientBossPool = VertxExecutorFactory.clientAcceptorPool(this, "vert.x-client-acceptor-thread-");

  private Map<ServerID, DefaultHttpServer> sharedHttpServers = new HashMap<>();
  private Map<ServerID, DefaultNetServer> sharedNetServers = new HashMap<>();

  private final ThreadLocal<Context> contextTL = new ThreadLocal<>();

  private final AtomicLong timeoutCounter = new AtomicLong(0);
  private final Map<Long, TimeoutHolder> timeouts = new ConcurrentHashMap<>();

  private ClusterManager clusterManager;

  public DefaultVertx() {
    this.eventBus = new DefaultEventBus(this);
  }

  public DefaultVertx(String hostname) {
    this(DEFAULT_CLUSTER_PORT, hostname);
  }

  public DefaultVertx(int port, String hostname) {
    this.clusterManager = new HazelcastClusterManager(this);
    this.eventBus = new DefaultEventBus(this, port, hostname, clusterManager);
  }

  static {
    // Stop netty renaming threads!
    ThreadRenamingRunnable.setThreadNameDeterminer(ThreadNameDeterminer.CURRENT);
    DefaultChannelFuture.setUseDeadLockChecker(false);
  }

  /**
   * @return The FileSystem implementation for the OS
   */
  protected FileSystem getFileSystem() {
  	return Windows.isWindows() ? new WindowsFileSystem(this) : new DefaultFileSystem(this);
  }
  
  public Timer getTimer() {
  	return timer;
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

  public Context startInBackground(final Runnable runnable, final boolean multiThreaded) {
    Context context  = createWorkerContext(multiThreaded);
    context.execute(runnable);
    return context;
  }

  public boolean isEventLoop() {
    Context context = getContext();
    if (context != null) {
      return context instanceof EventLoopContext;
    }
    return false;
  }

  public boolean isWorker() {
    Context context = getContext();
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

  // The background pool is used for making blocking calls to legacy synchronous APIs
  public ExecutorService getBackgroundPool() {
    return backgroundPool;
  }

  public NioWorkerPool getCorePool() {
    return corePool;
  }

  public NioServerBossPool getServerAcceptorPool() {
    return serverBossPool;
  }

  public NioClientBossPool getClientAcceptorPool() {
    return clientBossPool;
  }

  public Context getOrAssignContext() {
    Context ctx = getContext();
    if (ctx == null) {
      // Assign a context
      ctx = createEventLoopContext();
    }
    return ctx;
  }

  public void reportException(Throwable t) {
    Context ctx = getContext();
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
    final long timerID = scheduleTimeout(-1, context, myHandler, delay);
    myHandler.timerID = timerID;
    return timerID;
  }

  public boolean cancelTimer(long id) {
    return cancelTimeout(id);
  }

  public EventLoopContext createEventLoopContext() {
    getBackgroundPool();
    NioWorker worker = getCorePool().nextWorker();
    return new EventLoopContext(this, orderedFact.getExecutor(), worker);
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
    timeouts.put(id, new TimeoutHolder(timeout));
    return id;
  }

  private Context createWorkerContext(boolean multiThreaded) {
    getBackgroundPool();
    if (multiThreaded) {
      return new MultiThreadedWorkerContext(this, orderedFact.getExecutor(), backgroundPool);
    } else {
      return new WorkerContext(this, orderedFact.getExecutor());
    }
  }

  public void setContext(Context context) {
    contextTL.set(context);
    if (context != null) {
      context.setTCCL();
    }
  }

  public Context getContext() {
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

		if (timer != null) {
			timer.stop();
			timer = null;
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

    if (clientBossPool != null) {
      clientBossPool.releaseExternalResources();
      clientBossPool = null;
    }

    if (serverBossPool != null) {
      serverBossPool.releaseExternalResources();
      serverBossPool = null;
    }

		if (corePool != null) {
			corePool.releaseExternalResources();
			corePool = null;
		}

		setContext(null);
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

    TimeoutHolder(Timeout timeout) {
      this.timeout = timeout;
    }
  }
}
