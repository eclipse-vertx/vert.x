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

package org.vertx.java.core.eventbus.spi.hazelcast;

import org.vertx.java.core.BlockingAction;
import org.vertx.java.core.CompletionHandler;
import org.vertx.java.core.Deferred;
import org.vertx.java.core.eventbus.spi.AsyncMultiMap;

import java.util.Collection;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class HazelcastAsyncMultiMap<K, V> implements AsyncMultiMap<K, V> {

  private final com.hazelcast.core.MultiMap<K, V> map;

  public HazelcastAsyncMultiMap(com.hazelcast.core.MultiMap<K, V> map) {
    this.map = map;
  }

  @Override
  public void put(final K k, final V v, CompletionHandler<Void> completionHandler) {

    Deferred<Void> action = new BlockingAction<Void>() {
      public Void action() throws Exception {
        map.put(k, v);
        return null;
      }
    };
    action.handler(completionHandler);
    action.execute();
  }

  @Override
  public void get(final K k, CompletionHandler<Collection<V>> completionHandler) {
    Deferred<Collection<V>> action = new BlockingAction<Collection<V>>() {
      public Collection<V> action() throws Exception {
        return map.get(k);
      }
    };
    action.handler(completionHandler);
    action.execute();
  }

  @Override
  public void remove(final K k, final V v, CompletionHandler<Boolean> completionHandler) {
    Deferred<Boolean> action = new BlockingAction<Boolean>() {
      public Boolean action() throws Exception {
        return map.remove(k, v);
      }
    };
    action.handler(completionHandler);
    action.execute();
  }


}
