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

import com.hazelcast.core.IMap;
import com.hazelcast.nio.ObjectDataInput;
import com.hazelcast.nio.ObjectDataOutput;
import com.hazelcast.nio.serialization.DataSerializable;
import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;

import io.vertx.core.buffer.Buffer;
import io.vertx.core.shareddata.AsyncMap;
import io.vertx.core.shareddata.impl.ClusterSerializable;
import io.vertx.core.spi.cluster.VertxSPI;

import java.io.IOException;

class HazelcastAsyncMap<K, V> implements AsyncMap<K, V> {

  private final VertxSPI vertx;
  private final IMap<K, V> map;

  public HazelcastAsyncMap(VertxSPI vertx, IMap<K, V> map) {
    this.vertx = vertx;
    this.map = map;
  }

  @Override
  public void get(final K k, Handler<AsyncResult<V>> asyncResultHandler) {
    vertx.executeBlocking(() -> map.get(k), asyncResultHandler);
  }

  @Override
  public void put(K k, V v, Handler<AsyncResult<Void>> completionHandler) {
    final K kk = convertObject(k);
    final V vv = convertObject(v);
    vertx.executeBlocking(() -> {
      map.put(kk, HazelcastServerID.convertServerID(vv));
      return null;
    }, completionHandler);
  }

  @Override
  public void putIfAbsent(K k, V v, Handler<AsyncResult<V>> resultHandler) {
    final K kk = convertObject(k);
    final V vv = convertObject(v);
    vertx.executeBlocking(() ->  map.putIfAbsent(kk, HazelcastServerID.convertServerID(vv)), resultHandler);
  }

  @Override
  public void remove(final K k, Handler<AsyncResult<V>> resultHandler) {
    vertx.executeBlocking(() -> map.remove(k), resultHandler);
  }

  @Override
  public void removeIfPresent(K k, V v, Handler<AsyncResult<Boolean>> resultHandler) {
    vertx.executeBlocking(() -> map.remove(k, v), resultHandler);
  }

  @Override
  public void replace(K k, V v, Handler<AsyncResult<V>> resultHandler) {
    vertx.executeBlocking(() -> map.replace(k, v), resultHandler);
  }

  @Override
  public void replaceIfPresent(K k, V oldValue, V newValue, Handler<AsyncResult<Boolean>> resultHandler) {
    vertx.executeBlocking(() -> map.replace(k, oldValue, newValue), resultHandler);
  }

  @Override
  public void clear(Handler<AsyncResult<Void>> resultHandler) {
    vertx.executeBlocking(() -> {
      map.clear();
      return null;
    }, resultHandler);
  }

  private <T> T convertObject(T obj) {
    if (obj instanceof ClusterSerializable) {
      ClusterSerializable cobj = (ClusterSerializable)obj;
      return (T)(new DataSerializableHolder(cobj));
    } else {
      return obj;
    }
  }

  private static final class DataSerializableHolder implements DataSerializable {

    private ClusterSerializable clusterSerializable;

    private DataSerializableHolder(ClusterSerializable clusterSerializable) {
      this.clusterSerializable = clusterSerializable;
    }

    @Override
    public void writeData(ObjectDataOutput objectDataOutput) throws IOException {
      objectDataOutput.writeUTF(clusterSerializable.getClass().getName());
      byte[] bytes = clusterSerializable.writeToBuffer().getBytes();
      objectDataOutput.writeInt(bytes.length);
      objectDataOutput.write(bytes);
    }

    @Override
    public void readData(ObjectDataInput objectDataInput) throws IOException {
      String className = objectDataInput.readUTF();
      int length = objectDataInput.readInt();
      byte[] bytes = new byte[length];
      objectDataInput.readFully(bytes);
      try {
        Class<?> clazz = Thread.currentThread().getContextClassLoader().loadClass(className);
        clusterSerializable = (ClusterSerializable)clazz.newInstance();
        clusterSerializable.readFromBuffer(Buffer.buffer(bytes));
      } catch (Exception e) {
        throw new IllegalStateException("Failed to load class " + e.getMessage(), e);
      }
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (!(o instanceof DataSerializableHolder)) return false;
      DataSerializableHolder that = (DataSerializableHolder) o;
      if (clusterSerializable != null ? !clusterSerializable.equals(that.clusterSerializable) : that.clusterSerializable != null) {
        return false;
      }
      return true;
    }

    @Override
    public int hashCode() {
      return clusterSerializable != null ? clusterSerializable.hashCode() : 0;
    }
  }


}
