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

package org.vertx.java.core.eventbus.impl;

import org.vertx.java.core.impl.ConcurrentHashSet;
import org.vertx.java.core.net.impl.ServerID;

import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Set;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class ServerIDs implements Iterable<ServerID> {

  private volatile boolean initialised;
  private final Set<ServerID> ids;
  private volatile Iterator<ServerID> iter;

  public ServerIDs(int initialSize) {
    ids = new ConcurrentHashSet<>(initialSize);
  }

  public int size() {
    return ids.size();
  }

  public boolean isInitialised() {
    return initialised;
  }

  public void setInitialised() {
    this.initialised = true;
  }

  public void add(ServerID id) {
    ids.add(id);
  }

  public void remove(ServerID id) {
    ids.remove(id);
  }

  public void merge(ServerIDs toMerge) {
    ids.addAll(toMerge.ids);
  }

  public boolean isEmpty() {
    return ids.isEmpty();
  }

  @Override
  public Iterator<ServerID> iterator() {
    return ids.iterator();
  }

  public synchronized ServerID choose() {
    if (!ids.isEmpty()) {
      if (iter == null || !iter.hasNext()) {
        iter = ids.iterator();
      }
      try {
        return iter.next();
      } catch (NoSuchElementException e) {
        return null;
      }
    } else {
      return null;
    }
  }
}
