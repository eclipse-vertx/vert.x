package io.vertx.core.shareddata.impl;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.shareddata.AsyncMap;
import io.vertx.core.shareddata.LocalMap;

import java.util.*;

/**
 * @author <a href="http://nlq.su">Anton Potsyus</a>
 */
final class LocalAsAsyncMap<K, V> implements AsyncMap<K, V> {
  private final Vertx vertx;
  private final LocalMap<K, V> map;

  public LocalAsAsyncMap(Vertx vertx, LocalMap<K, V> map) {
    this.vertx = vertx;
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
    put(key, value, handlerWithTTL(key, value, ttl, completionHandler));
  }

  @Override
  public void putIfAbsent(K key, V value, Handler<AsyncResult<V>> completionHandler) {
    completionHandler.handle(Future.succeededFuture(map.putIfAbsent(key, value)));
  }

  @Override
  public void putIfAbsent(K key, V value, long ttl, Handler<AsyncResult<V>> completionHandler) {
    putIfAbsent(key, value, handlerWithTTL(key, value, ttl, completionHandler));
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

  @Override
  public void keys(Handler<AsyncResult<Set<K>>> resultHandler) {
    resultHandler.handle(Future.succeededFuture(new HashSet<>(map.keySet())));
  }

  @Override
  public void values(Handler<AsyncResult<List<V>>> resultHandler) {
    resultHandler.handle(Future.succeededFuture(new ArrayList<>(map.values())));
  }

  @Override
  public void entries(Handler<AsyncResult<Map<K, V>>> resultHandler) {
    resultHandler.handle(Future.succeededFuture(new HashMap<>(map)));
  }

  private <T> Handler<AsyncResult<T>> handlerWithTTL(K key, V value, long ttl, Handler<AsyncResult<T>> completionHandler) {
    return event -> {
      if (event.succeeded()) {
        vertx.setTimer(ttl, timer -> map.remove(key, value));
      }
      completionHandler.handle(event);
    };
  }
}
