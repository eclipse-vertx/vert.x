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

package org.vertx.java.spi.cluster.impl.hazelcast;

import com.hazelcast.core.EntryEvent;
import com.hazelcast.core.EntryListener;
import org.vertx.java.core.AsyncResult;
import org.vertx.java.core.AsyncResultHandler;
import org.vertx.java.core.Handler;
import org.vertx.java.core.impl.DefaultFutureResult;
import org.vertx.java.core.logging.Logger;
import org.vertx.java.core.logging.impl.LoggerFactory;
import org.vertx.java.core.spi.Action;
import org.vertx.java.core.spi.VertxSPI;
import org.vertx.java.core.spi.cluster.AsyncMultiMap;
import org.vertx.java.core.spi.cluster.ChoosableIterable;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
class HazelcastAsyncMultiMap<K, V> implements AsyncMultiMap<K, V>, EntryListener<K, V> {

  private static final Logger log = LoggerFactory.getLogger(HazelcastAsyncMultiMap.class);

  private final VertxSPI vertx;
  private final com.hazelcast.core.MultiMap<K, V> map;

  /*
   The Hazelcast near cache is very slow so we use our own one.
   Keeping it in sync is a little tricky. As entries are added or removed the EntryListener will be called
   but when the node joins the cluster it isn't provided the initial state via the EntryListener
   Therefore the first time get is called for a subscription we *always* get the subs from
   Hazelcast (this is what the initialised flag is for), then consider that the initial state.
   While the get is in progress the entry listener may be being called, so we merge any
   pre-existing entries so we don't lose any. Hazelcast doesn't seem to have any consistent
   way to get an initial state plus a stream of updates.
    */
  private ConcurrentMap<K, ChoosableSet<V>> cache = new ConcurrentHashMap<>();

  public HazelcastAsyncMultiMap(VertxSPI vertx, com.hazelcast.core.MultiMap<K, V> map) {
    this.vertx = vertx;
    this.map = map;
    map.addEntryListener(this, true);
  }

  @Override
  public void removeAllForValue(final V val, final Handler<AsyncResult<Void>> completionHandler) {
    vertx.executeBlocking(new Action<Void>() {
      public Void perform() {
        for (Map.Entry<K, V> entry : map.entrySet()) {
          V v = entry.getValue();
          if (val.equals(v)) {
            map.remove(entry.getKey(), v);
          }
        }
        return null;
      }
    }, completionHandler);
  }

  @Override
  public void add(final K k, final V v, final Handler<AsyncResult<Void>> completionHandler) {
    vertx.executeBlocking(new Action<Void>() {
      public Void perform() {
        map.put(k, HazelcastServerID.convertServerID(v));
        return null;
      }
    }, completionHandler);
  }

  @Override
  public void get(final K k, final Handler<AsyncResult<ChoosableIterable<V>>> resultHandler) {
    ChoosableSet<V> entries = cache.get(k);
    DefaultFutureResult<ChoosableIterable<V>> result = new DefaultFutureResult<>();
    if (entries != null && entries.isInitialised()) {
      result.setResult(entries).setHandler(resultHandler);
    } else {
      vertx.executeBlocking(new Action<Collection<V>>() {
          public Collection<V> perform() {
            return map.get(k);
          }
        }, new AsyncResultHandler<Collection<V>>() {
          public void handle(AsyncResult<Collection<V>> result) {
            DefaultFutureResult<ChoosableIterable<V>> sresult = new DefaultFutureResult<>();
            if (result.succeeded()) {
              Collection<V> entries = result.result();
              ChoosableSet<V> sids;
              if (entries != null) {
                sids = new ChoosableSet<>(entries.size());
                for (V hid : entries) {
                  sids.add(hid);
                }
              } else {
                sids = new ChoosableSet<>(0);
              }
              ChoosableSet<V> prev = cache.putIfAbsent(k, sids);
              if (prev != null) {
                // Merge them
                prev.merge(sids);
                sids = prev;
              }
              sids.setInitialised();
              sresult.setResult(sids);
            } else {
              sresult.setFailure(result.cause());
            }
            sresult.setHandler(resultHandler);
          }
        }
      );
    }
  }

  @Override
  public void remove(final K k, final V v, final Handler<AsyncResult<Void>> completionHandler) {

    vertx.executeBlocking(new Action<Void>() {
      public Void perform() {
        map.remove(k, HazelcastServerID.convertServerID(v));
        return null;
      }
    }, completionHandler);
  }

  @Override
  public void entryAdded(EntryEvent<K, V> entry) {
    addEntry(entry.getKey(), entry.getValue());
  }

  private void addEntry(K k, V v) {
    ChoosableSet<V> entries = cache.get(k);
    if (entries == null) {
      entries = new ChoosableSet<>(1);
      ChoosableSet<V> prev = cache.putIfAbsent(k, entries);
      if (prev != null) {
        entries = prev;
      }
    }
    entries.add(v);
  }

  @Override
  public void entryRemoved(EntryEvent<K, V> entry) {
    removeEntry(entry.getKey(), entry.getValue());
  }

  private void removeEntry(K k, V v) {
    ChoosableSet<V> entries = cache.get(k);
    if (entries != null) {
      entries.remove(v);
      if (entries.isEmpty()) {
        cache.remove(k);
      }
    }
  }

  @Override
  public void entryUpdated(EntryEvent<K, V> entry) {
    K k = entry.getKey();
    ChoosableSet<V> entries = cache.get(k);
    if (entries != null) {
      entries.add(entry.getValue());
    }
  }

  @Override
  public void entryEvicted(EntryEvent<K, V> entry) {
    entryRemoved(entry);
  }



}
