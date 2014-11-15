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

package io.vertx.spi.cluster.impl.hazelcast;

import com.hazelcast.config.Config;
import com.hazelcast.config.XmlConfigBuilder;
import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.core.IAtomicLong;
import com.hazelcast.core.IMap;
import com.hazelcast.core.ISemaphore;
import com.hazelcast.core.Member;
import com.hazelcast.core.MemberAttributeEvent;
import com.hazelcast.core.MembershipEvent;
import com.hazelcast.core.MembershipListener;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.VertxException;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.impl.LoggerFactory;
import io.vertx.core.shareddata.AsyncMap;
import io.vertx.core.shareddata.Counter;
import io.vertx.core.shareddata.Lock;
import io.vertx.core.shareddata.MapOptions;
import io.vertx.core.spi.cluster.AsyncMultiMap;
import io.vertx.core.spi.cluster.ClusterManager;
import io.vertx.core.spi.cluster.NodeListener;
import io.vertx.core.spi.cluster.VertxSPI;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;

/**
 * A cluster manager that uses Hazelcast
 * 
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class HazelcastClusterManager implements ClusterManager, MembershipListener {

  private static final Logger log = LoggerFactory.getLogger(HazelcastClusterManager.class);

  private static final String LOCK_SEMAPHORE_PREFIX = "__vertx.";

  // Hazelcast config file
  private static final String DEFAULT_CONFIG_FILE = "default-cluster.xml";
  private static final String CONFIG_FILE = "cluster.xml";

  private VertxSPI vertx;

  private HazelcastInstance hazelcast;
  private String nodeID;
  private String membershipListenerId;

  private NodeListener nodeListener;
  private volatile boolean active;

  private Config conf;

  /**
   * Constructor - gets config from classpath
   */
  public HazelcastClusterManager() {
    // We have our own shutdown hook and need to ensure ours runs before Hazelcast is shutdown
    System.setProperty("hazelcast.shutdownhook.enabled", "false");
  }

  /**
   * Constructor - config supplied
   * @param conf
   */
  public HazelcastClusterManager(Config conf) {
    this.conf = conf;
  }

  public void setVertx(VertxSPI vertx) {
    this.vertx = vertx;
  }

  public synchronized void join(Handler<AsyncResult<Void>> resultHandler) {
    vertx.executeBlocking(() -> {
      if (active) {
        return null;
      } else {
        active = true;
        if (conf == null) {
          conf = loadConfigFromClasspath();
          if (conf == null) {
            log.warn("Cannot find cluster configuration on classpath and none specified programmatically. Using default hazelcast configuration");
          }
        }
        hazelcast = Hazelcast.newHazelcastInstance(conf);
        nodeID = hazelcast.getCluster().getLocalMember().getUuid();
        membershipListenerId = hazelcast.getCluster().addMembershipListener(this);
        return null;
      }
    }, resultHandler);
  }

	/**
	 * Every eventbus handler has an ID. SubsMap (subscriber map) is a MultiMap which 
	 * maps handler-IDs with server-IDs and thus allows the eventbus to determine where 
	 * to send messages.
	 * 
	 * @param name A unique name by which the the MultiMap can be identified within the cluster. 
	 *     See the cluster config file (e.g. cluster.xml in case of HazelcastClusterManager) for
	 *     additional MultiMap config parameters.
	 * @return subscription map
	 */
  @Override
  public <K, V> void getAsyncMultiMap(String name, MapOptions options, Handler<AsyncResult<AsyncMultiMap<K, V>>> resultHandler) {
    vertx.executeBlocking(() -> {
      com.hazelcast.core.MultiMap<K, V> multiMap = hazelcast.getMultiMap(name);
      return multiMap;
    }, ar -> {
      if (ar.succeeded()) {
        resultHandler.handle(Future.completedFuture(new HazelcastAsyncMultiMap<>(vertx, ar.result())));
      } else {
        resultHandler.handle(Future.completedFuture(ar.cause()));
      }
    });
  }

  @Override
  public String getNodeID() {
    return nodeID;
  }

  @Override
  public List<String> getNodes() {
    Set<Member> members = hazelcast.getCluster().getMembers();
    List<String> lMembers = new ArrayList<>();
    for (Member member: members) {
      lMembers.add(member.getUuid());
    }
    return lMembers;
  }

  @Override
  public void nodeListener(NodeListener listener) {
    this.nodeListener = listener;
  }

  @Override
  public <K, V> void getAsyncMap(String name, MapOptions options, Handler<AsyncResult<AsyncMap<K, V>>> resultHandler) {
    vertx.executeBlocking(() -> {
      IMap<K, V> map = hazelcast.getMap(name);
      return map;
    }, ar -> {
      if (ar.succeeded()) {
        resultHandler.handle(Future.completedFuture(new HazelcastAsyncMap<>(vertx, ar.result())));
      } else {
        resultHandler.handle(Future.completedFuture(ar.cause()));
      }
    });
  }

  @Override
  public <K, V> Map<K, V> getSyncMap(String name) {
    IMap<K, V> map = hazelcast.getMap(name);
    return map;
  }

  @Override
  public void getLockWithTimeout(String name, long timeout, Handler<AsyncResult<Lock>> resultHandler) {
    vertx.executeBlocking(() -> {
      ISemaphore iSemaphore = hazelcast.getSemaphore(LOCK_SEMAPHORE_PREFIX + name);
      boolean locked = false;
      try {
        locked = iSemaphore.tryAcquire(timeout, TimeUnit.MILLISECONDS);
      } catch (InterruptedException e) {
        // OK continue
      }
      if (locked) {
        return new HazelcastLock(iSemaphore);
      } else {
        throw new VertxException("Timed out waiting to get lock " + name);
      }
    }, resultHandler);
  }

  @Override
  public void getCounter(String name, Handler<AsyncResult<Counter>> resultHandler) {
    vertx.executeBlocking(() ->  new HazelcastCounter(hazelcast.getAtomicLong(name)), resultHandler);
  }

  public synchronized void leave(Handler<AsyncResult<Void>> resultHandler) {
    vertx.executeBlocking(() -> {
      if (!active) {
        return null;
      } else {
        try {
          active = false;
          boolean left = hazelcast.getCluster().removeMembershipListener(membershipListenerId);
          if (!left) {
            log.warn("No membership listener");
          }
          while (hazelcast.getLifecycleService().isRunning()) {
            try {
              // This can sometimes throw java.util.concurrent.RejectedExecutionException so we retry.
              hazelcast.getLifecycleService().shutdown();
            } catch (RejectedExecutionException ignore) {
              ignore.printStackTrace();
            }
            Thread.sleep(1);
          }
          return null;
        } catch (Throwable t) {
          t.printStackTrace();
          throw new RuntimeException(t.getMessage());
        }
      }
    }, resultHandler);
  }

  @Override
  public synchronized void memberAdded(MembershipEvent membershipEvent) {
    if (!active) {
      return;
    }
    try {
      if (nodeListener != null) {
        Member member = membershipEvent.getMember();
        nodeListener.nodeAdded(member.getUuid());
      }
    } catch (Throwable t) {
      log.error("Failed to handle memberAdded", t);
    }
  }

  @Override
  public synchronized void memberRemoved(MembershipEvent membershipEvent) {
    if (!active) {
      return;
    }
    try {
      if (nodeListener != null) {
        Member member = membershipEvent.getMember();
        nodeListener.nodeLeft(member.getUuid());
      }
    } catch (Throwable t) {
      log.error("Failed to handle memberRemoved", t);
    }
  }

  @Override
  public synchronized boolean isActive() {
    return active;
  }

  @Override
  public void memberAttributeChanged(MemberAttributeEvent memberAttributeEvent) {
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

  /**
   * Get the Hazelcast config
   * @return a config object
   */
  public Config getConfig() {
    return conf;
  }

  /**
   * Set the hazelcast config
   * @param config
   */
  public void setConfig(Config config) {
    this.conf = config;
  }

  public Config loadConfigFromClasspath() {
    Config cfg = null;
    try (InputStream is = getConfigStream();
         InputStream bis = new BufferedInputStream(is)) {
      if (is != null) {
        cfg = new XmlConfigBuilder(bis).build();
      }
    } catch (IOException ex) {
      log.error("Failed to read config", ex);
    }
    return cfg;
  }

  private class HazelcastCounter implements Counter {
    private IAtomicLong atomicLong;


    private HazelcastCounter(IAtomicLong atomicLong) {
      this.atomicLong = atomicLong;
    }

    @Override
    public void get(Handler<AsyncResult<Long>> resultHandler) {
      Objects.requireNonNull(resultHandler, "resultHandler");
      vertx.executeBlocking(atomicLong::get, resultHandler);
    }

    @Override
    public void incrementAndGet(Handler<AsyncResult<Long>> resultHandler) {
      Objects.requireNonNull(resultHandler, "resultHandler");
      vertx.executeBlocking(atomicLong::incrementAndGet, resultHandler);
    }

    @Override
    public void getAndIncrement(Handler<AsyncResult<Long>> resultHandler) {
      Objects.requireNonNull(resultHandler, "resultHandler");
      vertx.executeBlocking(atomicLong::getAndIncrement, resultHandler);
    }

    @Override
    public void decrementAndGet(Handler<AsyncResult<Long>> resultHandler) {
      Objects.requireNonNull(resultHandler, "resultHandler");
      vertx.executeBlocking(atomicLong::decrementAndGet, resultHandler);
    }

    @Override
    public void addAndGet(long value, Handler<AsyncResult<Long>> resultHandler) {
      Objects.requireNonNull(resultHandler, "resultHandler");
      vertx.executeBlocking(() ->  atomicLong.addAndGet(value), resultHandler);
    }

    @Override
    public void getAndAdd(long value, Handler<AsyncResult<Long>> resultHandler) {
      Objects.requireNonNull(resultHandler, "resultHandler");
      vertx.executeBlocking(() ->  atomicLong.getAndAdd(value), resultHandler);
    }

    @Override
    public void compareAndSet(long expected, long value, Handler<AsyncResult<Boolean>> resultHandler) {
      Objects.requireNonNull(resultHandler, "resultHandler");
      vertx.executeBlocking(() ->  atomicLong.compareAndSet(expected, value), resultHandler);
    }
  }

  private class HazelcastLock implements Lock {

    private ISemaphore semaphore;

    private HazelcastLock(ISemaphore semaphore) {
      this.semaphore = semaphore;
    }

    @Override
    public void release() {
      semaphore.release();
    }
  }

}
