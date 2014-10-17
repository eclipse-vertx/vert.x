package io.vertx.spi.cluster.impl.zookeeper;

import io.vertx.core.*;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.impl.LoggerFactory;
import io.vertx.core.shareddata.AsyncMap;
import io.vertx.core.shareddata.Counter;
import io.vertx.core.shareddata.Lock;
import io.vertx.core.shareddata.MapOptions;
import io.vertx.core.spi.cluster.*;
import org.apache.curator.RetryPolicy;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.framework.api.CuratorEventType;
import org.apache.curator.framework.recipes.atomic.DistributedAtomicLong;
import org.apache.curator.framework.recipes.cache.PathChildrenCache;
import org.apache.curator.framework.recipes.cache.PathChildrenCacheEvent;
import org.apache.curator.framework.recipes.cache.PathChildrenCacheListener;
import org.apache.curator.framework.recipes.locks.InterProcessSemaphoreMutex;
import org.apache.curator.framework.state.ConnectionState;
import org.apache.curator.framework.state.ConnectionStateListener;
import org.apache.curator.retry.ExponentialBackoffRetry;
import org.apache.zookeeper.CreateMode;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * A cluster manager that uses Zookeeper
 *
 * @author Stream.Liu
 */
public class ZookeeperClusterManager implements ClusterManager, PathChildrenCacheListener, ConnectionStateListener {

  private static final Logger log = LoggerFactory.getLogger(ZookeeperClusterManager.class);
  private VertxSPI vertxSPI;

  private NodeListener nodeListener;
  private PathChildrenCache clusterNodes;
  private volatile boolean active;

  private String nodeID;
  private CuratorFramework curator;
  private RetryPolicy retryPolicy;
  private Map<String, ZKLock> locks = new ConcurrentHashMap<>();
  // zookeeper config file
  private static final String DEFAULT_CONFIG_FILE = "default-zookeeper.properties";
  private static final String CONFIG_FILE = "zookeeper.properties";
  private Properties conf;

  private static final String ZK_PATH_LOCKS = "/locks/";
  private static final String ZK_PATH_COUNTERS = "/counters/";
  private static final String ZK_PATH_CLUSTER_NODE = "/cluster/nodes/";

  public ZookeeperClusterManager() {
    if (conf == null) {
      InputStream is = getConfigStream();
      try {
        conf.load(is);
      } catch (IOException e) {
        log.error("Failed to load zookeeper config", e);
      }
    }
  }

  //just for unit testing
  ZookeeperClusterManager(RetryPolicy retryPolicy, CuratorFramework curator) {
    this.retryPolicy = retryPolicy;
    this.curator = curator;
  }

  public ZookeeperClusterManager(Properties config) {
    this.conf = config;
  }

  private InputStream getConfigStream() {
    ClassLoader ctxClsLoader = Thread.currentThread().getContextClassLoader();
    InputStream is = null;
    if (ctxClsLoader != null) {
      is = ctxClsLoader.getResourceAsStream(CONFIG_FILE);
    }
    if (is == null) {
      is = getClass().getClassLoader().getResourceAsStream(CONFIG_FILE);
      if (is == null) {
        is = getClass().getClassLoader().getResourceAsStream(DEFAULT_CONFIG_FILE);
      }
    }
    return is;
  }

  @Override
  public void setVertx(VertxSPI vertx) {
    this.vertxSPI = vertx;
  }

  /**
   * Every eventbus handler has an ID. SubsMap (subscriber map) is a MultiMap which
   * maps handler-IDs with server-IDs and thus allows the eventbus to determine where
   * to send messages.
   *
   * @param name A unique name by which the the MultiMap can be identified within the cluster.
   *             See the cluster config file (e.g. zookeeper.properties in case of ZookeeperClusterManager) for
   *             additional MultiMap config parameters.
   * @return subscription map
   */
  @Override
  public <K, V> void getAsyncMultiMap(String name, MapOptions options, Handler<AsyncResult<AsyncMultiMap<K, V>>> asyncResultHandler) {
    vertxSPI.runOnContext(event -> asyncResultHandler.handle(Future.completedFuture(new ZKAsyncMultiMap<>(vertxSPI, curator, name))));
  }

  @Override
  public <K, V> void getAsyncMap(String name, MapOptions options, Handler<AsyncResult<AsyncMap<K, V>>> asyncResultHandler) {
    vertxSPI.runOnContext(event -> asyncResultHandler.handle(Future.completedFuture(new ZKAsyncMap<>(vertxSPI, curator, name))));
  }

  @Override
  public <K, V> Map<K, V> getSyncMap(String name) {
    return new ZKSyncMap<>(curator, name);
  }

  @Override
  public void getLockWithTimeout(String name, long timeout, Handler<AsyncResult<Lock>> resultHandler) {
    vertxSPI.executeBlocking(() -> {
      ZKLock lock = locks.get(name);
      if (lock == null) {
        InterProcessSemaphoreMutex mutexLock = new InterProcessSemaphoreMutex(curator, ZK_PATH_LOCKS + name);
        lock = new ZKLock(mutexLock);
      }
      try {
        if (lock.getLock().acquire(timeout, TimeUnit.MILLISECONDS)) {
          locks.putIfAbsent(name, lock);
          return lock;
        } else {
          throw new VertxException("Timed out waiting to get lock " + name);
        }
      } catch (Exception e) {
        throw new VertxException("get lock exception", e);
      }
    }, resultHandler);
  }

  @Override
  public void getCounter(String name, Handler<AsyncResult<Counter>> resultHandler) {
    vertxSPI.executeBlocking(() -> {
      try {
        return new ZKCounter(name, retryPolicy);
      } catch (Exception e) {
        throw new VertxException(e);
      }
    }, resultHandler);
  }

  @Override
  public String getNodeID() {
    return nodeID;
  }

  @Override
  public List<String> getNodes() {
    return clusterNodes.getCurrentData().stream().map(e -> new String(e.getData())).collect(Collectors.toList());
  }

  @Override
  public void nodeListener(NodeListener listener) {
    this.nodeListener = listener;
  }

  @Override
  public synchronized void join(Handler<AsyncResult<Void>> resultHandler) {
    vertxSPI.executeBlocking(() -> {
      if (active) return null;
      else {
        active = true;
        if (curator == null) {
          retryPolicy = new ExponentialBackoffRetry(Integer.valueOf(conf.getProperty("retry.initialSleepTime")), Integer.valueOf(conf.getProperty("retry.maxTimes")), Integer.valueOf(conf.getProperty("retry.intervalTimes")));
          curator = CuratorFrameworkFactory.builder().connectString(conf.getProperty("hosts.zookeeper"))
              .namespace(conf.getProperty("path.root"))
              .sessionTimeoutMs(Integer.valueOf(conf.getProperty("timeout.session")))
              .connectionTimeoutMs(Integer.valueOf(conf.getProperty("timeout.connect")))
              .retryPolicy(retryPolicy).build();
        }
        curator.start();
        nodeID = UUID.randomUUID().toString();
        clusterNodes = new PathChildrenCache(curator, ZK_PATH_CLUSTER_NODE.substring(0, ZK_PATH_CLUSTER_NODE.length() - 1), true);
        clusterNodes.getListenable().addListener(this);
        try {
          clusterNodes.start(PathChildrenCache.StartMode.BUILD_INITIAL_CACHE);
          //Join to the cluster
          curator.create().withMode(CreateMode.EPHEMERAL).forPath(ZK_PATH_CLUSTER_NODE + nodeID, nodeID.getBytes());
        } catch (Exception e) {
          throw new VertxException(e);
        }
        return null;
      }
    }, resultHandler);
  }

  @Override
  public synchronized void leave(Handler<AsyncResult<Void>> resultHandler) {
    vertxSPI.executeBlocking(() -> {
      if (!active) return null;
      else {
        active = false;
        try {
          curator.delete().deletingChildrenIfNeeded().inBackground((client, event) -> {
            if (event.getType() == CuratorEventType.DELETE) {
              clusterNodes.getListenable().removeListener(ZookeeperClusterManager.this);
            }
          }).forPath(ZK_PATH_CLUSTER_NODE + nodeID);
        } catch (Exception e) {
          log.error(e);
        }
        return null;
      }
    }, resultHandler);
  }

  @Override
  public boolean isActive() {
    return active;
  }

  @Override
  public void childEvent(CuratorFramework client, PathChildrenCacheEvent event) throws Exception {
    if (!active) return;
    switch (event.getType()) {
      case CHILD_ADDED:
        try {
          if (nodeListener != null) {
            nodeListener.nodeAdded(new String(event.getData().getData()));
          }
        } catch (Throwable t) {
          log.error("Failed to handle memberAdded", t);
        }
        break;
      case CHILD_REMOVED:
        try {
          if (nodeListener != null) {
            nodeListener.nodeLeft(new String(event.getData().getData()));
          }
        } catch (Throwable t) {
          log.error("Failed to handle memberRemoved", t);
        }
        break;
      case CHILD_UPDATED:
        log.warn("Weird event that update cluster node. path:" + event.getData().getPath());
        break;
    }
  }

  /**
   * some state have effect to the lock, we have to dispose it.
   *
   * @param client   curator
   * @param newState the state of connection to the zookeeper.
   */
  @Override
  public void stateChanged(CuratorFramework client, ConnectionState newState) {
    switch (newState) {
      case LOST:
        //release locks and clean locks
        locks.values().stream().forEach(ZKLock::release);
        locks.clear();
        break;
      case SUSPENDED:
        //just release locks on this node.
        locks.values().stream().forEach(ZKLock::release);
        break;
      case RECONNECTED:
        //try to reacquire lock
        locks.values().stream().forEach(zkLock -> {
          if (!zkLock.lock.isAcquiredInThisProcess()) {
            try {
              zkLock.lock.acquire(1, TimeUnit.SECONDS);
            } catch (Exception e) {
              log.error(e);
            }
          }
        });
        break;
    }
  }


  /**
   * Counter implement
   */
  private class ZKCounter implements Counter {

    private DistributedAtomicLong atomicLong;
    private String counterPath;

    public ZKCounter(String nodeName, RetryPolicy retryPolicy) throws Exception {
      this.counterPath = ZK_PATH_COUNTERS + nodeName;
      this.atomicLong = new DistributedAtomicLong(curator, counterPath, retryPolicy);
    }

    @Override
    public void get(Handler<AsyncResult<Long>> resultHandler) {
      vertxSPI.executeBlocking(() -> {
        try {
          return atomicLong.get().preValue();
        } catch (Exception e) {
          throw new VertxException(e);
        }
      }, resultHandler);
    }

    @Override
    public void incrementAndGet(Handler<AsyncResult<Long>> resultHandler) {
      increment(true, resultHandler);
    }

    @Override
    public void getAndIncrement(Handler<AsyncResult<Long>> resultHandler) {
      increment(false, resultHandler);
    }

    private void increment(boolean post, Handler<AsyncResult<Long>> resultHandler) {
      vertxSPI.executeBlocking(() -> {
        try {
          long returnValue = 0;
          if (atomicLong.get().succeeded()) returnValue = atomicLong.get().preValue();
          if (atomicLong.increment().succeeded()) {
            return post ? atomicLong.get().postValue() : returnValue;
          } else {
            throw new VertxException("increment value failed.");
          }
        } catch (Exception e) {
          throw new VertxException(e);
        }
      }, resultHandler);
    }

    @Override
    public void decrementAndGet(Handler<AsyncResult<Long>> resultHandler) {
      vertxSPI.executeBlocking(() -> {
        try {
          if (atomicLong.decrement().succeeded()) {
            return atomicLong.get().postValue();
          } else {
            throw new VertxException("decrement value failed.");
          }
        } catch (Exception e) {
          throw new VertxException(e);
        }
      }, resultHandler);
    }

    @Override
    public void addAndGet(long value, Handler<AsyncResult<Long>> resultHandler) {
      add(value, true, resultHandler);
    }

    @Override
    public void getAndAdd(long value, Handler<AsyncResult<Long>> resultHandler) {
      add(value, false, resultHandler);
    }

    private void add(long value, boolean post, Handler<AsyncResult<Long>> resultHandler) {
      vertxSPI.executeBlocking(() -> {
        try {
          long returnValue = 0;
          if (atomicLong.get().succeeded()) returnValue = atomicLong.get().preValue();
          if (atomicLong.add(value).succeeded()) {
            return post ? atomicLong.get().postValue() : returnValue;
          } else {
            throw new VertxException("add value failed.");
          }
        } catch (Exception e) {
          throw new VertxException(e);
        }
      }, resultHandler);
    }

    @Override
    public void compareAndSet(long expected, long value, Handler<AsyncResult<Boolean>> resultHandler) {
      vertxSPI.executeBlocking(() -> {
        try {
          if (atomicLong.get().succeeded() && atomicLong.get().preValue() == 0)
            this.atomicLong.initialize(0L);
          return atomicLong.compareAndSet(expected, value).succeeded();
        } catch (Exception e) {
          throw new VertxException(e);
        }
      }, resultHandler);
    }
  }


  /**
   * Lock implement
   */
  private class ZKLock implements Lock {
    private InterProcessSemaphoreMutex lock;

    private ZKLock(InterProcessSemaphoreMutex lock) {
      this.lock = lock;
    }

    public InterProcessSemaphoreMutex getLock() {
      return lock;
    }

    @Override
    public void release() {
      try {
        lock.release();
      } catch (Exception e) {
        log.error(e);
      }
    }
  }

}
