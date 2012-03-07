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
import org.vertx.java.core.eventbus.impl.SubsMap;
import org.vertx.java.core.impl.BlockingAction;
import org.vertx.java.core.impl.CompletionHandler;
import org.vertx.java.core.impl.Deferred;
import org.vertx.java.core.impl.Future;
import org.vertx.java.core.logging.Logger;
import org.vertx.java.core.logging.impl.LoggerFactory;
import org.vertx.java.core.net.impl.ServerID;

import java.util.Collection;
import java.util.HashSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class HazelcastSubsMap implements SubsMap, EntryListener<String, HazelcastServerID> {

  private static final Logger log = LoggerFactory.getLogger(HazelcastSubsMap.class);

  private final com.hazelcast.core.MultiMap<String, HazelcastServerID> map;

  // The Hazelcast near cache is very slow so we use our own one
  private ConcurrentMap<String, Collection<ServerID>> cache = new ConcurrentHashMap<>();

  public HazelcastSubsMap(com.hazelcast.core.MultiMap<String, HazelcastServerID> map) {
    this.map = map;
    map.addEntryListener(this, true);
  }

  @Override
  public void put(final String subName, final ServerID serverID, final AsyncResultHandler<Void> completionHandler) {
    Deferred<Void> action = new BlockingAction<Void>() {
      public Void action() throws Exception {
        map.put(subName, new HazelcastServerID(serverID));
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
  public void get(final String subName, final AsyncResultHandler<Collection<ServerID>> completionHandler) {
    Collection<ServerID> entries = cache.get(subName);
    if (entries != null) {
      completionHandler.handle(new AsyncResult<>(entries));
    } else {
      Deferred<Collection<HazelcastServerID>> action = new BlockingAction<Collection<HazelcastServerID>>() {
        public Collection<HazelcastServerID> action() throws Exception {
          return map.get(subName);
        }
      };
      action.handler(new CompletionHandler<Collection<HazelcastServerID>>() {
        public void handle(Future<Collection<HazelcastServerID>> event) {
          AsyncResult<Collection<ServerID>> sresult;
          if (event.succeeded()) {
            Collection<HazelcastServerID> entries = event.result();
            Collection<ServerID> sids;
            if (entries != null) {
              sids = new HashSet<>(entries.size());
              for (HazelcastServerID hid: entries) {
                sids.add(hid.serverID);
              }
              cache.put(subName, sids);
            } else {
              sids = null;
            }
            sresult = new AsyncResult<>(sids);
          } else {
            sresult = new AsyncResult<>(event.exception());
          }
          completionHandler.handle(sresult);
        }
      });
      action.execute();
    }
  }

  @Override
  public void remove(final String subName, final ServerID serverID, final AsyncResultHandler<Boolean> completionHandler) {
    Deferred<Boolean> action = new BlockingAction<Boolean>() {
      public Boolean action() throws Exception {
        return map.remove(subName, new HazelcastServerID(serverID));
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
  public void entryAdded(EntryEvent<String, HazelcastServerID> entry) {
     addEntry(entry.getKey(), entry.getValue().serverID);
  }

  private void addEntry(String key, ServerID value) {
    Collection<ServerID> entries = cache.get(key);
    if (entries == null) {
      entries = new HashSet<>();
      Collection<ServerID> prev = cache.putIfAbsent(key, entries);
      if (prev != null) {
        entries = prev;
      }
    }
    entries.add(value);
  }

  @Override
  public void entryRemoved(EntryEvent<String, HazelcastServerID> entry) {
    removeEntry(entry.getKey(), entry.getValue().serverID);
  }

  private void removeEntry(String key, ServerID value) {
    Collection<ServerID> entries = cache.get(key);
    if (entries != null) {
      entries.remove(value);
      if (entries.isEmpty()) {
        cache.remove(key);
      }
    }
  }

  @Override
  public void entryUpdated(EntryEvent<String, HazelcastServerID> entry) {
    String key = entry.getKey();
    Collection<ServerID> entries = cache.get(key);
    if (entries != null) {
      entries.add(entry.getValue().serverID);
    }
  }

  @Override
  public void entryEvicted(EntryEvent<String, HazelcastServerID> entry) {
    entryRemoved(entry);
  }
}
