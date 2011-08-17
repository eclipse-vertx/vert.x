/*
 * Copyright 2002-2011 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use
 * this file except in compliance with the License. You may obtain a copy of the
 * License at http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed
 * under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR
 * CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */

package org.nodex.core;

import org.jboss.netty.channel.socket.nio.NioWorker;
import org.jboss.netty.channel.socket.nio.NioWorkerPool;
import org.jboss.netty.util.HashedWheelTimer;
import org.jboss.netty.util.Timeout;
import org.jboss.netty.util.TimerTask;
import org.nodex.core.shared.SharedUtils;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public final class NodexImpl implements NodexInternal {

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

  public long setPeriodic(long delay, final Runnable handler) {
    //TODO
    return -1;
  }

  public long setTimeout(long delay, final Runnable handler) {
    final long contextID = checkContextID();
    TimerTask task = new TimerTask() {
      public void run(Timeout timeout) {
        executeOnContext(contextID, handler);
      }
    };
    return scheduleTimeout(contextID, task, delay);
  }

  public boolean cancelTimeout(long id) {
    return cancelTimeout(id, true);
  }

  public <T> long registerActor(Actor<T> actor) {
    Long contextID = getContextID();
    if (contextID == null) {
      throw new IllegalStateException("Cannot register actor with no context");
    }
    long actorID = actorSeq.getAndIncrement();
    actors.put(actorID, new ActorHolder(actor, getContextID()));
    return actorID;
  }

  public boolean unregisterActor(long actorID) {
    long contextID = getContextID();
    ActorHolder holder = actors.remove(actorID);
    if (holder != null) {
      if (contextID != holder.contextID) {
        actors.put(actorID, holder);
        throw new IllegalStateException("Cannot unregister actor from different context");
      }
      else {
        return true;
      }
    } else {
      return false;
    }
  }

  public <T> boolean sendMessage(long actorID, T message) {
    final T msg = SharedUtils.checkObject(message);
    final ActorHolder holder = actors.get(actorID);
    if (holder != null) {
      final Actor<T> actor = (Actor<T>)holder.actor; // FIXME - unchecked cast
      executeOnContext(holder.contextID, new Runnable() {
        public void run() {
          setContextID(holder.contextID);
          actor.onMessage(msg);
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
    NioWorker worker = workerMap.get(contextID);
    if (worker != null) {
      if (worker.getThread() != Thread.currentThread()) {
        worker.scheduleOtherTask(runnable);
      } else {
        runnable.run();
      }
    } else {
      throw new IllegalStateException("Context is not registered " + contextID + " has it been destroyed?");
    }
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

  private long scheduleTimeout(long contextID, TimerTask task, long delay) {
    Timeout timeout = timer.newTimeout(task, delay, TimeUnit.MILLISECONDS);
    long id = timeoutCounter.getAndIncrement();
    timeouts.put(id, new TimeoutHolder(timeout, contextID));
    return id;
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
    final Actor<?> actor;
    final long contextID;
    ActorHolder(Actor<?> actor, long contextID) {
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
