/*
 * Copyright 2011 VMware, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.nodex.java.core;

import org.jboss.netty.channel.socket.nio.NioWorker;
import org.jboss.netty.channel.socket.nio.NioWorkerPool;
import org.jboss.netty.util.HashedWheelTimer;
import org.jboss.netty.util.Timeout;
import org.jboss.netty.util.TimerTask;
import org.nodex.java.core.internal.NodexInternal;
import org.nodex.java.core.shared.SharedUtils;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

class NodexImpl implements NodexInternal {

  private int backgroundPoolSize = 20;
  private int corePoolSize = Runtime.getRuntime().availableProcessors();
  private volatile ExecutorService backgroundPool;
  private volatile ExecutorService corePool;
  private volatile NioWorkerPool workerPool;
  private volatile ExecutorService acceptorPool;
  private Map<Long, NioWorker> workerMap = new ConcurrentHashMap<>();
  private static final ThreadLocal<Long> contextIDTL = new ThreadLocal<>();
  private Map<Long, ActorHolder> actors = new ConcurrentHashMap<>();
  //For now we use a hashed wheel with it's own thread for timeouts - ideally the event loop would have
  //it's own hashed wheel
  private final HashedWheelTimer timer = new HashedWheelTimer(new NodeThreadFactory("node.x-timer-thread"), 20,
      TimeUnit.MILLISECONDS);
  private final AtomicLong timeoutCounter = new AtomicLong(0);
  private final Map<Long, TimeoutHolder> timeouts = new ConcurrentHashMap<>();
  private final AtomicLong contextIDSeq = new AtomicLong(0);
  private final AtomicLong actorSeq = new AtomicLong(0);


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
      throw new IllegalStateException("Cannot set background size after pool has been created");
    }
    backgroundPoolSize = size;
  }

  public synchronized int getBackgroundThreadPoolSize() {
    return backgroundPoolSize;
  }

  public <T> long registerHandler(EventHandler<T> actor) {
    Long contextID = getContextID();
    if (contextID == null) {
      throw new IllegalStateException("Cannot register handler with no context");
    }
    long actorID = actorSeq.getAndIncrement();
    actors.put(actorID, new ActorHolder(actor, getContextID()));
    return actorID;
  }

  public boolean unregisterHandler(long handlerID) {
    long contextID = getContextID();
    ActorHolder holder = actors.remove(handlerID);
    if (holder != null) {
      if (contextID != holder.contextID) {
        actors.put(handlerID, holder);
        throw new IllegalStateException("Cannot unregister handler from different context");
      } else {
        return true;
      }
    } else {
      return false;
    }
  }

  public <T> boolean sendToHandler(long handlerID, T message) {
    final T msg = SharedUtils.checkObject(message);
    final ActorHolder holder = actors.get(handlerID);
    if (holder != null) {
      final EventHandler<T> actor = (EventHandler<T>) holder.actor; // FIXME - unchecked cast
      executeOnContext(holder.contextID, new Runnable() {
        public void run() {
          setContextID(holder.contextID);
          actor.onEvent(msg);
        }
      });
      return true;
    } else {
      return false;
    }
  }

  public void executeInBackground(Runnable runnable) {
    getBackgroundPool().execute(runnable);
  }

  public void go(final Runnable runnable) {
    final long contextID = NodexInternal.instance.createAndAssociateContext();
    NodexInternal.instance.executeOnContext(contextID, new Runnable() {
      public void run() {
        NodexInternal.instance.setContextID(contextID);
        try {
          runnable.run();
        } catch (Throwable t) {
          t.printStackTrace(System.err);
        }
      }
    });
  }

  // Internal API -----------------------------------------------------------------------------------------

  //The background pool is used for making blocking calls to legacy synchronous APIs
  public ExecutorService getBackgroundPool() {
    //This is a correct implementation of double-checked locking idiom
    ExecutorService result = backgroundPool;
    if (result == null) {
      synchronized (this) {
        result = backgroundPool;
        if (result == null) {
          backgroundPool = result = Executors.newFixedThreadPool(backgroundPoolSize, new NodeThreadFactory("node.x-background-thread-"));
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
          corePool = Executors.newFixedThreadPool(corePoolSize, new NodeThreadFactory("node.x-core-thread-"));
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
          acceptorPool = result = Executors.newCachedThreadPool(new NodeThreadFactory("node.x-acceptor-thread-"));
        }
      }
    }
    return result;
  }

  public long createAndAssociateContext() {
    NioWorker worker = getWorkerPool().nextWorker();
    return associateContextWithWorker(worker);
  }

  public long associateContextWithWorker(NioWorker worker) {
    long contextID = contextIDSeq.getAndIncrement();
    workerMap.put(contextID, worker);
    return contextID;
  }

  public boolean destroyContext(long contextID) {
    return workerMap.remove(contextID) != null;
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
    executeOnContext(contextID, runnable, true);
  }

  public void nextTick(final EventHandler<Void> handler) {
    Long contextID = getContextID();
    if (contextID == null) {
      throw new IllegalStateException("No context id");
    }
    executeOnContext(contextID, new Runnable() {
      public void run() {
        handler.onEvent(null);
      }
    }, false);
  }

  private void executeOnContext(long contextID, Runnable runnable, boolean sameThreadOptimise) {
    NioWorker worker = workerMap.get(contextID);
    if (worker != null) {
      if (sameThreadOptimise && (worker.getThread() == Thread.currentThread())) {
        runnable.run();
      } else {
        // TODO currently this will still run directly if current thread = desired thread
        // Take a look at NioWorker.scheduleOtherTask
        worker.scheduleOtherTask(runnable);
      }
    } else {
      throw new IllegalStateException("Context is not registered " + contextID + " has it been destroyed?");
    }
  }

  public long setPeriodic(long delay, final EventHandler<Long> handler) {
    return setTimeout(delay, true, handler);
  }

  public long setTimer(long delay, final EventHandler<Long> handler) {
    return setTimeout(delay, false, handler);
  }


  NodexImpl() {
    timer.start();
  }

  // Private --------------------------------------------------------------------------------------------------

  private long checkContextID() {
    Long contextID = getContextID();
    if (contextID == null) throw new IllegalStateException("No context id");
    return contextID;
  }

  private long setTimeout(final long delay, boolean periodic, final EventHandler<Long> handler) {
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
        NodexInternal.instance.executeOnContext(contextID, task);
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

  private static class InternalTimerHandler implements Runnable {
    final long contextID;
    final EventHandler<Long> handler;
    long timerID;

    InternalTimerHandler(long contextID, EventHandler<Long> runnable) {
      this.contextID = contextID;
      this.handler = runnable;
    }

    public void run() {
      NodexInternal.instance.setContextID(contextID);
      handler.onEvent(timerID);
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
    final EventHandler<?> actor;
    final long contextID;

    ActorHolder(EventHandler<?> actor, long contextID) {
      this.actor = actor;
      this.contextID = contextID;
    }
  }

  private static class NodeThreadFactory implements ThreadFactory {

    private String prefix;
    private AtomicInteger threadCount = new AtomicInteger(0);

    NodeThreadFactory(String prefix) {
      this.prefix = prefix;
    }

    public Thread newThread(Runnable runnable) {
      Thread t = new Thread(runnable, prefix + threadCount.getAndIncrement());
      // All node.x threads are daemons
      t.setDaemon(true);
      return t;
    }
  }
}
