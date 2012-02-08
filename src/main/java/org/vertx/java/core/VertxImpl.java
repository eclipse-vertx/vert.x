/*
 * Copyright 2011 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.vertx.java.core;

import org.jboss.netty.channel.socket.nio.NioWorker;
import org.jboss.netty.channel.socket.nio.NioWorkerPool;
import org.jboss.netty.util.HashedWheelTimer;
import org.jboss.netty.util.Timeout;
import org.jboss.netty.util.TimerTask;
import org.vertx.java.core.app.VerticleManager;
import org.vertx.java.core.json.JsonObject;
import org.vertx.java.core.logging.Logger;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

class VertxImpl implements VertxInternal {

  private static final Logger log = Logger.getLogger(VertxImpl.class);

  private int backgroundPoolSize = 1;
  private int corePoolSize = Runtime.getRuntime().availableProcessors();
  private ExecutorService backgroundPool;
  private OrderedExecutorFactory orderedFact;
  private ExecutorService corePool;
  private NioWorkerPool workerPool;
  private ExecutorService acceptorPool;
  private Map<Long, NioWorker> workerMap = new ConcurrentHashMap<>();
  private Map<Long, Executor> backgroundExecutors = new ConcurrentHashMap<>();
  private static final ThreadLocal<Long> contextIDTL = new ThreadLocal<>();
  //For now we use a hashed wheel with it's own thread for timeouts - ideally the event loop would have
  //it's own hashed wheel
  private final HashedWheelTimer timer = new HashedWheelTimer(new VertxThreadFactory("vert.x-timer-thread"), 20,
      TimeUnit.MILLISECONDS);
  private final AtomicLong timeoutCounter = new AtomicLong(0);
  private final Map<Long, TimeoutHolder> timeouts = new ConcurrentHashMap<>();
  private final AtomicLong contextIDSeq = new AtomicLong(10); // Start at 10 for easier debugging

  // Public API ------------------------------------------------

  public synchronized void setCoreThreadPoolSize(int size) {
    if (corePool != null) {
      throw new IllegalStateException("Cannot set core pool size after pool has been created");
    }
    corePoolSize = size;
  }

  public synchronized int getCoreThreadPoolSize() {
    return corePoolSize;
  }

  public synchronized void setBackgroundThreadPoolSize(int size) {
    if (backgroundPool != null) {
      throw new IllegalStateException("Cannot set worker size after pool has been created");
    }
    backgroundPoolSize = size;
  }

  public synchronized int getBackgroundThreadPoolSize() {
    return backgroundPoolSize;
  }

  public long startOnEventLoop(final Runnable runnable) {
    final long contextID = createEventLoopContext();
    executeOnContext(contextID, new Runnable() {
      public void run() {
        VertxInternal.instance.setContextID(contextID);
        try {
          runnable.run();
        } catch (Throwable t) {
          log.error("Failed to run on event loop", t);
        }
      }
    });
    return contextID;
  }

  public long startInBackground(final Runnable runnable) {
    final long contextID = createBackgroundContext();
    executeOnContext(contextID, new Runnable() {
      public void run() {
        VertxInternal.instance.setContextID(contextID);
        try {
          runnable.run();
        } catch (Throwable t) {
          log.error("Failed to run in worker", t);
        }
      }
    });
    return contextID;
  }

  public boolean isEventLoop() {
    return workerMap.containsKey(getContextID());
  }

  public long setPeriodic(long delay, final Handler<Long> handler) {
    return setTimeout(delay, true, handler);
  }

  public long setTimer(long delay, final Handler<Long> handler) {
    return setTimeout(delay, false, handler);
  }

  public void nextTick(final Handler<Void> handler) {
    Long contextID = getContextID();
    if (contextID == null) {
      throw new IllegalStateException("No context id");
    }
    executeOnContext(contextID, new Runnable() {
      public void run() {
        handler.handle(null);
      }
    }, false);
  }

  public String deployWorkerVerticle(String main) {
    return deployWorkerVerticle(main, null, 1);
  }

  public String deployWorkerVerticle(String main, int instances) {
    return deployWorkerVerticle(main, null, 1);
  }

  public String deployWorkerVerticle(String main, JsonObject config) {
    return deployWorkerVerticle(main, config, 1);
  }

  public String deployWorkerVerticle(String main, JsonObject config, int instances) {
    return deployWorkerVerticle(main, config, instances, null);
  }

  public String deployWorkerVerticle(String main, JsonObject config, int instances, Handler<Void> doneHandler) {
    return VerticleManager.instance.deploy(true, null, main, config, ".", instances, doneHandler);
  }

  public String deployVerticle(String main) {
    return deployVerticle(main, null, 1);
  }

  public String deployVerticle(String main, int instances) {
    return deployVerticle(main, null, 1);
  }

  public String deployVerticle(String main, JsonObject config) {
    return deployVerticle(main, config, 1);
  }

  public String deployVerticle(String main, JsonObject config, int instances) {
    return deployVerticle(main, config, instances, null);
  }

  public String deployVerticle(String main, JsonObject config, int instances, Handler<Void> doneHandler) {
    String currPath = VerticleManager.instance.getAppPath();
    return VerticleManager.instance.deploy(false, null, main, config, currPath, instances, doneHandler);
  }

  public void undeployVerticle(String deploymentID) {
    undeployVerticle(deploymentID, null);
  }

  public void undeployVerticle(String deploymentID, Handler<Void> doneHandler) {
    VerticleManager.instance.undeploy(deploymentID, doneHandler);
  }

  public void exit() {
    VerticleManager vm  = VerticleManager.instance;
    String appName = vm.getAppName();
    vm.undeploy(appName, null);
  }

  public JsonObject getConfig() {
    return VerticleManager.instance.getConfig();
  }

  // Internal API -----------------------------------------------------------------------------------------

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
          corePool = Executors.newFixedThreadPool(corePoolSize, new VertxThreadFactory("vert.x-core-thread-"));
          workerPool = result = new NioWorkerPool(corePoolSize, corePool);
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

  public boolean destroyContext(long contextID) {
    return workerMap.remove(contextID) != null || backgroundExecutors.remove(contextID) != null;
  }

  public void setContextID(long contextID) {
    contextIDTL.set(contextID);
  }

  public Long getContextID() {
    return contextIDTL.get();
  }

  public NioWorker getWorkerForContextID(long contextID) {
    NioWorker worker = workerMap.get(contextID);
    if (worker == null) {
      throw new IllegalStateException("Context is not registered " + contextID);
    }
    return worker;
  }

  public void executeOnContext(long contextID, Runnable runnable) {
    executeOnContext(contextID, runnable, false);
  }

  private void executeOnContext(final long contextID, final Runnable runnable, boolean sameThreadOptimise) {
    NioWorker worker = workerMap.get(contextID);
    if (worker != null) {
      // Will be run on an event loop
      if (sameThreadOptimise && (worker.getThread() == Thread.currentThread())) {
        runnable.run();
      } else {
        worker.scheduleOtherTask(runnable);
      }
    } else {
      // Will be run using a worker executor
      Executor bgExec = backgroundExecutors.get(contextID);
      if (bgExec != null) {
        bgExec.execute(new Runnable() {
          public void run() {
            runnable.run();
          }
        });
      } else {
        throw new IllegalStateException("Context is not registered " + contextID + " has it been destroyed?");
      }
    }
  }

  VertxImpl() {
    timer.start();
  }

  // Private --------------------------------------------------------------------------------------------------

  private long checkContextID() {
    Long contextID = getContextID();
    if (contextID == null) throw new IllegalStateException("No context id");
    return contextID;
  }

  private long setTimeout(final long delay, boolean periodic, final Handler<Long> handler) {
    final long contextID = checkContextID();

    InternalTimerHandler myHandler;
    if (periodic) {
      myHandler = new InternalTimerHandler(contextID, handler) {
        public void run() {
          super.run();
          scheduleTimeout(timerID, contextID, this, delay); // And reschedule
        }
      };
    } else {
      myHandler = new InternalTimerHandler(contextID, handler) {
        public void run() {
          super.run();
          timeouts.remove(timerID);
        }
      };
    }
    long timerID = scheduleTimeout(-1, contextID, myHandler, delay);
    myHandler.timerID = timerID;
    return timerID;
  }

  public boolean cancelTimer(long id) {
    return cancelTimeout(id, true);
  }

  private boolean cancelTimeout(long id, boolean check) {
    TimeoutHolder holder = timeouts.remove(id);
    if (holder != null) {
      if (check && holder.contextID != checkContextID()) {
        throw new IllegalStateException("Timer can only be cancelled in the context that set it");
      }
      holder.timeout.cancel();
      return true;
    } else {
      return false;
    }
  }

  private long scheduleTimeout(long id, final long contextID, final Runnable task, long delay) {
    TimerTask ttask = new TimerTask() {
      public void run(Timeout timeout) throws Exception {
        VertxInternal.instance.executeOnContext(contextID, task);
      }
    };
    if (id != -1 && timeouts.get(id) == null) {
      //Been cancelled
      return -1;
    }
    Timeout timeout = timer.newTimeout(ttask, delay, TimeUnit.MILLISECONDS);
    id = id != -1 ? id : timeoutCounter.getAndIncrement();
    timeouts.put(id, new TimeoutHolder(timeout, contextID));
    return id;
  }

  private long createEventLoopContext() {
    long contextID = contextIDSeq.getAndIncrement();
    NioWorker worker = getWorkerPool().nextWorker();
    workerMap.put(contextID, worker);
    return contextID;
  }

  private long createBackgroundContext() {
    getBackgroundPool();
    long contextID = contextIDSeq.getAndIncrement();
    backgroundExecutors.put(contextID, orderedFact.getExecutor());
    return contextID;
  }

  private static class InternalTimerHandler implements Runnable {
    final long contextID;
    final Handler<Long> handler;
    long timerID;

    InternalTimerHandler(long contextID, Handler<Long> runnable) {
      this.contextID = contextID;
      this.handler = runnable;
    }

    public void run() {
      VertxInternal.instance.setContextID(contextID);
      handler.handle(timerID);
    }
  }

  private static class TimeoutHolder {
    final Timeout timeout;
    final long contextID;

    TimeoutHolder(Timeout timeout, long contextID) {
      this.timeout = timeout;
      this.contextID = contextID;
    }
  }

  private static class ActorHolder {
    final Handler<?> actor;
    final long contextID;

    ActorHolder(Handler<?> actor, long contextID) {
      this.actor = actor;
      this.contextID = contextID;
    }
  }

  private static class VertxThreadFactory implements ThreadFactory {

    private String prefix;
    private AtomicInteger threadCount = new AtomicInteger(0);

    VertxThreadFactory(String prefix) {
      this.prefix = prefix;
    }

    public Thread newThread(Runnable runnable) {
      Thread t = new Thread(runnable, prefix + threadCount.getAndIncrement());
      // All vert.x threads are daemons
      t.setDaemon(true);
      return t;
    }
  }
}
