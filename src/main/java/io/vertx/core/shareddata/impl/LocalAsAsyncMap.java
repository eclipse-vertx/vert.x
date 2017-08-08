package io.vertx.core.shareddata.impl;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.shareddata.AsyncMap;
import io.vertx.core.shareddata.LocalMap;

/**
 * @author <a href="http://nlq.su">Anton Potsyus</a>
 */
final class LocalAsAsyncMap<K, V> implements AsyncMap<K, V> {
  private final LocalMap<K, V> map;

  public LocalAsAsyncMap(LocalMap<K, V> map) {
    this.map = map;
  }

  @Override
  public void get(K key, Handler<AsyncResult<V>> resultHandler) {
    resultHandler.handle(Future.succeededFuture(map.get(key)));
  }

  @Override
  public void put(K key, V value, Handler<AsyncResult<Void>> completionHandler) {
    map.put(key, value);
    completionHandler.handle(Future.succeededFuture());
  }

  @Override
  public void put(K key, V value, long ttl, Handler<AsyncResult<Void>> completionHandler) {
    throw new UnsupportedOperationException("TTL is not supported");
  }

  @Override
  public void putIfAbsent(K key, V value, Handler<AsyncResult<V>> completionHandler) {
    completionHandler.handle(Future.succeededFuture(map.putIfAbsent(key, value)));
  }

  @Override
  public void putIfAbsent(K key, V value, long ttl, Handler<AsyncResult<V>> completionHandler) {
    throw new UnsupportedOperationException("TTL is not supported");
  }

  @Override
  public void remove(K key, Handler<AsyncResult<V>> resultHandler) {
    resultHandler.handle(Future.succeededFuture(map.remove(key)));
  }

  @Override
  public void removeIfPresent(K key, V value, Handler<AsyncResult<Boolean>> resultHandler) {
    resultHandler.handle(Future.succeededFuture(map.removeIfPresent(key, value)));
  }

  @Override
  public void replace(K key, V value, Handler<AsyncResult<V>> resultHandler) {
    resultHandler.handle(Future.succeededFuture(map.replace(key, value)));
  }

  @Override
  public void replaceIfPresent(K key, V oldValue, V newValue, Handler<AsyncResult<Boolean>> resultHandler) {
    resultHandler.handle(Future.succeededFuture(map.replaceIfPresent(key, oldValue, newValue)));
  }

  @Override
  public void clear(Handler<AsyncResult<Void>> completionHandler) {
    map.clear();
    completionHandler.handle(Future.succeededFuture());
  }

  @Override
  public void size(Handler<AsyncResult<Integer>> resultHandler) {
    resultHandler.handle(Future.succeededFuture(map.size()));
  }
}
