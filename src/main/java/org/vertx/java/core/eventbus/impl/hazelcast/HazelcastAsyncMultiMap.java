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

package org.vertx.java.core.eventbus.impl.hazelcast;

import com.hazelcast.core.EntryEvent;
import com.hazelcast.core.EntryListener;
import org.vertx.java.core.AsyncResult;
import org.vertx.java.core.AsyncResultHandler;
import org.vertx.java.core.eventbus.impl.AsyncMultiMap;
import org.vertx.java.core.impl.BlockingAction;
import org.vertx.java.core.impl.CompletionHandler;
import org.vertx.java.core.impl.Deferred;
import org.vertx.java.core.impl.Future;
import org.vertx.java.core.logging.Logger;
import org.vertx.java.core.logging.impl.LoggerFactory;

import java.util.Collection;
import java.util.HashSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class HazelcastAsyncMultiMap<K, V> implements AsyncMultiMap<K, V>, EntryListener<K, V> {

  private static final Logger log = LoggerFactory.getLogger(HazelcastAsyncMultiMap.class);

  private final com.hazelcast.core.MultiMap<K, V> map;

  // The Hazelcast near cache is very slow so we use our own one
  private ConcurrentMap<K, Collection<V>> cache = new ConcurrentHashMap<>();

  public HazelcastAsyncMultiMap(com.hazelcast.core.MultiMap<K, V> map) {
    this.map = map;
    map.addEntryListener(this, true);
  }

  @Override
  public void put(final K k, final V v, final AsyncResultHandler<Void> completionHandler) {
    Deferred<Void> action = new BlockingAction<Void>() {
      public Void action() throws Exception {
        map.put(k, v);
        return null;
      }
    };
    action.handler(new CompletionHandler<Void>() {
      public void handle(Future<Void> event) {
        AsyncResult<Void> result;
        if (event.succeeded()) {
          result = new AsyncResult<>(event.result());
        } else {
          result = new AsyncResult<>(event.exception());
        }
        completionHandler.handle(result);
      }
    });
    action.execute();
  }

  @Override
  public void get(final K k, final AsyncResultHandler<Collection<V>> completionHandler) {
    Collection<V> entries = cache.get(k);
    if (entries != null) {
      completionHandler.handle(new AsyncResult<>(entries));
    } else {
      Deferred<Collection<V>> action = new BlockingAction<Collection<V>>() {
        public Collection<V> action() throws Exception {
          return map.get(k);
        }
      };
      action.handler(new CompletionHandler<Collection<V>>() {
        public void handle(Future<Collection<V>> event) {
          AsyncResult<Collection<V>> result;
          if (event.succeeded()) {
            Collection<V> entries = event.result();
            if (entries != null) {
              cache.put(k, new HashSet<>(entries));
            }
            result = new AsyncResult<>(event.result());
          } else {
            result = new AsyncResult<>(event.exception());
          }
          completionHandler.handle(result);
        }
      });
      action.execute();
    }
  }

  @Override
  public void remove(final K k, final V v, final AsyncResultHandler<Boolean> completionHandler) {
    Deferred<Boolean> action = new BlockingAction<Boolean>() {
      public Boolean action() throws Exception {
        return map.remove(k, v);
      }
    };
    action.handler(new CompletionHandler<Boolean>() {
      public void handle(Future<Boolean> event) {
        AsyncResult<Boolean> result;
        if (event.succeeded()) {
          result = new AsyncResult<>(event.result());
        } else {
          result = new AsyncResult<>(event.exception());
        }
        completionHandler.handle(result);
      }
    });
    action.execute();
  }

  @Override
  public void entryAdded(EntryEvent<K, V> entry) {
     addEntry(entry.getKey(), entry.getValue());
  }

  private void addEntry(K key, V value) {
    Collection<V> entries = cache.get(key);
    if (entries == null) {
      entries = new HashSet<>();
      Collection<V> prev = cache.putIfAbsent(key, entries);
      if (prev != null) {
        entries = prev;
      }
    }
    entries.add(value);
  }

  @Override
  public void entryRemoved(EntryEvent<K, V> entry) {
    removeEntry(entry.getKey(), entry.getValue());
  }

  private void removeEntry(K key, V value) {
    Collection<V> entries = cache.get(key);
    if (entries != null) {
      entries.remove(value);
      if (entries.isEmpty()) {
        cache.remove(key);
      }
    }
  }

  @Override
  public void entryUpdated(EntryEvent<K, V> entry) {
    K key = entry.getKey();
    Collection<V> entries = cache.get(key);
    if (entries != null) {
      entries.add(entry.getValue());
    }
  }

  @Override
  public void entryEvicted(EntryEvent<K, V> entry) {
    entryRemoved(entry);
  }
}
