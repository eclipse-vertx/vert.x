package org.nodex.core;

import org.jboss.netty.channel.socket.nio.NioWorker;
import org.jboss.netty.channel.socket.nio.NioWorkerPool;
import org.jboss.netty.util.HashedWheelTimer;
import org.jboss.netty.util.Timeout;
import org.jboss.netty.util.TimerTask;
import org.nodex.core.util.OrderedExecutor;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * User: timfox
 * Date: 25/06/2011
 * Time: 19:39
 */
public final class NodexImpl implements NodexInternal {

  private int backgroundPoolSize = 20;
  private int corePoolSize = Runtime.getRuntime().availableProcessors();
  private ExecutorService backgroundPool;
  private ExecutorService corePool;
  private NioWorkerPool workerPool;
  private ExecutorService acceptorPool;
  private Map<String, NioWorker> workerMap = new ConcurrentHashMap<String, NioWorker>();
  private static final ThreadLocal<String> contextIDTL = new ThreadLocal<String>();
  private Map<String, ActorHolder> actors = new ConcurrentHashMap<String, ActorHolder>();
  //For now we use a hashed wheel with it's own thread for timeouts - ideally the event loop would have
  //it's own hashed wheel
  private final HashedWheelTimer timer = new HashedWheelTimer(new NodeThreadFactory("node.x-timer-thread"), 20,
      TimeUnit.MILLISECONDS);
  private final AtomicLong timeoutCounter = new AtomicLong(0);
  private final Map<Long, TimeoutHolder> timeouts = new ConcurrentHashMap<Long, TimeoutHolder>();

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

  public void executeInBackground(Runnable task) {
    getBackgroundPool().execute(task);
  }

  public long setPeriodic(long delay, final Runnable handler) {
    //TODO
    return -1;
  }

  public long setTimeout(long delay, final Runnable handler) {
    final String contextID = checkContextID();
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

  public <T> String registerActor(Actor<T> actor) {
    String contextID = getContextID();
    if (contextID == null) {
      throw new IllegalStateException("Cannot register actor with no context");
    }
    String actorID = UUID.randomUUID().toString();
    actors.put(actorID, new ActorHolder(actor, getContextID()));
    return actorID;
  }

  private static class ActorHolder {
    ActorHolder(Actor<?> actor, String contextID) {
      this.actor = actor;
      this.contextID = contextID;
    }
    final Actor<?> actor;
    final String contextID;
  }

  public boolean unregisterActor(String actorID) {
    String contextID = getContextID();
    ActorHolder holder = actors.remove(actorID);
    if (holder != null) {
      if (!contextID.equals(holder.contextID)) {
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

  public <T> boolean sendMessage(String actorID, final T message) {
    ActorHolder holder = actors.remove(actorID);
    if (holder != null) {
      final Actor<T> actor = (Actor<T>)holder.actor; // FIXME - unchecked cast
      executeOnContext(holder.contextID, new Runnable() {
        public void run() {
          actor.onMessage(message);
        }
      });
      return true;
    } else {
      return false;
    }
  }


  // Internal API -----------------------------------------------------------------------------------------

  //The background pool is used for making blocking calls to legacy synchronous APIs
  public synchronized Executor getBackgroundPool() {
    if (backgroundPool == null) {
      backgroundPool = Executors.newFixedThreadPool(backgroundPoolSize, new NodeThreadFactory("node.x-background-thread-"));
    }
    return backgroundPool;
  }

  public synchronized NioWorkerPool getWorkerPool() {
    if (workerPool == null) {
      corePool = Executors.newFixedThreadPool(corePoolSize, new NodeThreadFactory("node.x-core-thread-"));
      workerPool = new NioWorkerPool(corePoolSize, corePool);
    }
    return workerPool;
  }

  //We use a cache pool, but it will never get large since only used for acceptors.
  //There will be one thread for each port listening on
  public synchronized Executor getAcceptorPool() {
    if (acceptorPool == null) {
      acceptorPool = Executors.newCachedThreadPool(new NodeThreadFactory("node.x-acceptor-thread-"));
    }
    return acceptorPool;
  }

  public Executor getOrderedBackgroundExecutor() {
    return new OrderedExecutor(getBackgroundPool());
  }

  public String createContext(NioWorker worker) {
    String contextID = UUID.randomUUID().toString();
    workerMap.put(contextID, worker);
    return contextID;
  }

  public boolean destroyContext(String contextID) {
    return workerMap.remove(contextID) != null;
  }

  public void setContextID(String contextID) {
    contextIDTL.set(contextID);
  }

  public String getContextID() {
    return contextIDTL.get();
  }

  public void executeOnContext(String contextID, Runnable runnable) {
    NioWorker worker = workerMap.get(contextID);
    if (worker != null) {
      worker.scheduleOtherTask(runnable);
    } else {
      throw new IllegalStateException("Worker is not registered for " + contextID);
    }
  }

  NodexImpl() {
    timer.start();
  }

  // Private --------------------------------------------------------------------------------------------------

  private String checkContextID() {
    String contextID = getContextID();
    if (contextID == null) throw new IllegalStateException("No context id");
    return contextID;
  }

  private boolean cancelTimeout(long id, boolean check) {
    TimeoutHolder holder = timeouts.remove(id);
    if (holder != null) {
      if (check && !holder.contextID.equals(checkContextID())) {
        throw new IllegalStateException("Timer can only be cancelled in the context that set it");
      }
      holder.timeout.cancel();
      return true;
    } else {
      return false;
    }
  }

  private long scheduleTimeout(String contextID, TimerTask task, long delay) {
    Timeout timeout = timer.newTimeout(task, delay, TimeUnit.MILLISECONDS);
    long id = timeoutCounter.getAndIncrement();
    timeouts.put(id, new TimeoutHolder(timeout, contextID));
    return id;
  }

  private static class TimeoutHolder {
    final Timeout timeout;
    final String contextID;

    TimeoutHolder(Timeout timeout, String contextID) {
      this.timeout = timeout;
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
