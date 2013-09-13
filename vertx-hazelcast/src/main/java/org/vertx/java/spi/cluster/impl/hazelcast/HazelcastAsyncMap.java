package org.vertx.java.spi.cluster.impl.hazelcast;

import com.hazelcast.core.IMap;
import org.vertx.java.core.AsyncResult;
import org.vertx.java.core.Handler;
import org.vertx.java.core.spi.Action;
import org.vertx.java.core.spi.VertxSPI;
import org.vertx.java.core.spi.cluster.AsyncMap;

/*
 * Copyright 2013 Red Hat, Inc.
 *
 * Red Hat licenses this file to you under the Apache License, version 2.0
 * (the "License"); you may not use this file except in compliance with the
 * License.  You may obtain a copy of the License at:
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the specific language governing permissions and limitations
 * under the License.
 *
 */
class HazelcastAsyncMap<K, V> implements AsyncMap<K, V> {

  private final VertxSPI vertx;
  private final IMap<K, V> map;

  public HazelcastAsyncMap(VertxSPI vertx, IMap<K, V> map) {
    this.vertx = vertx;
    this.map = map;
  }

  @Override
  public void get(final K k, Handler<AsyncResult<V>> asyncResultHandler) {
    vertx.executeBlocking(new Action<V>() {
      public V perform() {
        return map.get(k);
      }
    }, asyncResultHandler);
  }

  @Override
  public void put(final K k, final V v, Handler<AsyncResult<Void>> completionHandler) {
    vertx.executeBlocking(new Action<Void>() {
      public Void perform() {
        map.put(k, HazelcastServerID.convertServerID(v));
        return null;
      }
    }, completionHandler);
  }

  @Override
  public void remove(final K k, Handler<AsyncResult<Void>> completionHandler) {
    vertx.executeBlocking(new Action<Void>() {
      public Void perform() {
        map.remove(k);
        return null;
      }
    }, completionHandler);
  }
}
