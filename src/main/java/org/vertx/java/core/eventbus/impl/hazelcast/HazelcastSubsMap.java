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
import org.vertx.java.core.impl.ConcurrentHashSet;
import org.vertx.java.core.impl.VertxInternal;
import org.vertx.java.core.logging.Logger;
import org.vertx.java.core.logging.impl.LoggerFactory;
import org.vertx.java.core.net.impl.ServerID;

import java.util.Collection;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class HazelcastSubsMap implements SubsMap, EntryListener<String, HazelcastServerID> {

  private static final Logger log = LoggerFactory.getLogger(HazelcastSubsMap.class);

  private final VertxInternal vertx;
  private final com.hazelcast.core.MultiMap<String, HazelcastServerID> map;

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
  private ConcurrentMap<String, ServerIDs> cache = new ConcurrentHashMap<>();

  public HazelcastSubsMap(VertxInternal vertx, com.hazelcast.core.MultiMap<String, HazelcastServerID> map) {
    this.vertx = vertx;
    this.map = map;
    map.addEntryListener(this, true);
  }

  @Override
  public void put(final String subName, final ServerID serverID, final AsyncResultHandler<Void> completionHandler) {
    new BlockingAction<Void>(vertx, completionHandler) {
      public Void action() throws Exception {
        map.put(subName, new HazelcastServerID(serverID));
        return null;
      }
    }.run();
  }

  @Override
  public void get(final String subName, final AsyncResultHandler<Collection<ServerID>> completionHandler) {
    ServerIDs entries = cache.get(subName);
    if (entries != null && entries.initialised) {
      completionHandler.handle(new AsyncResult<>(entries.ids));
    } else {
      new BlockingAction<Collection<HazelcastServerID>>(vertx, new AsyncResultHandler<Collection<HazelcastServerID>>() {
        public void handle(AsyncResult<Collection<HazelcastServerID>> result) {
          AsyncResult<Collection<ServerID>> sresult;
          if (result.succeeded()) {
            Collection<HazelcastServerID> entries = result.result;
            ServerIDs sids;
            if (entries != null) {
              sids = new ServerIDs(entries.size());
              for (HazelcastServerID hid: entries) {
                sids.ids.add(hid.serverID);
              }
              ServerIDs prev = cache.putIfAbsent(subName, sids);
              if (prev != null) {
                // Merge them
                prev.ids.addAll(sids.ids);
                sids = prev;
              }
              sids.initialised = true;
            } else {
              sids = null;
            }
            sresult = new AsyncResult<>(sids == null ? null : sids.ids);
          } else {
            sresult = new AsyncResult<>(result.exception);
          }
          completionHandler.handle(sresult);
        }
      }) {
        public Collection<HazelcastServerID> action() throws Exception {
          return map.get(subName);
        }
      }.run();
    }
  }

  @Override
  public void remove(final String subName, final ServerID serverID, final AsyncResultHandler<Boolean> completionHandler) {
    new BlockingAction<Boolean>(vertx, completionHandler) {
      public Boolean action() throws Exception {
        return map.remove(subName, new HazelcastServerID(serverID));
      }
    }.run();
  }

  @Override
  public void entryAdded(EntryEvent<String, HazelcastServerID> entry) {
     addEntry(entry.getKey(), entry.getValue().serverID);
  }

  private void addEntry(String key, ServerID value) {
     ServerIDs entries = cache.get(key);
    if (entries == null) {
      entries = new ServerIDs();
      ServerIDs prev = cache.putIfAbsent(key, entries);
      if (prev != null) {
        entries = prev;
      }
    }
    entries.ids.add(value);
  }

  @Override
  public void entryRemoved(EntryEvent<String, HazelcastServerID> entry) {
    removeEntry(entry.getKey(), entry.getValue().serverID);
  }

  private void removeEntry(String key, ServerID value) {
    ServerIDs entries = cache.get(key);
    if (entries != null) {
      entries.ids.remove(value);
      if (entries.ids.isEmpty()) {
        cache.remove(key);
      }
    }
  }

  @Override
  public void entryUpdated(EntryEvent<String, HazelcastServerID> entry) {
    String key = entry.getKey();
    ServerIDs entries = cache.get(key);
    if (entries != null) {
      entries.ids.add(entry.getValue().serverID);
    }
  }

  @Override
  public void entryEvicted(EntryEvent<String, HazelcastServerID> entry) {
    entryRemoved(entry);
  }

  private static class ServerIDs {
    final Collection<ServerID> ids;
    ServerIDs(int initialSize) {
      ids = new ConcurrentHashSet<>(initialSize);
    }
    ServerIDs() {
      ids = new ConcurrentHashSet<>();
    }
    volatile boolean initialised;
  }

}
