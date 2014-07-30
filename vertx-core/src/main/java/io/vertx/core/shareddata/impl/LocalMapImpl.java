package io.vertx.core.shareddata.impl;

import io.vertx.core.shareddata.LocalMap;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import static io.vertx.core.shareddata.impl.Checker.*;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
class LocalMapImpl<K, V> implements LocalMap<K, V> {


  private final ConcurrentMap<Object, LocalMap<?, ?>> maps;
  private final String name;
  private final ConcurrentMap<K, V> map = new ConcurrentHashMap<>();

  LocalMapImpl(String name, ConcurrentMap<Object, LocalMap<?, ?>> maps) {
    this.name = name;
    this.maps = maps;
  }

  @Override
  public V get(K key) {
    return copyIfRequired(map.get(key));
  }

  @Override
  public V put(K key, V value) {
    checkType(key);
    checkType(value);
    return map.put(key, value);
  }

  @Override
  public V remove(K key) {
    return copyIfRequired(map.remove(key));
  }

  @Override
  public void clear() {
    map.clear();
  }

  @Override
  public int size() {
    return map.size();
  }

  @Override
  public boolean isEmpty() {
    return map.isEmpty();
  }

  @Override
  public V putIfAbsent(K key, V value) {
    checkType(key);
    checkType(value);
    return copyIfRequired(map.putIfAbsent(key, value));
  }

  @Override
  public boolean removeIfPresent(K key, V value) {
    return map.remove(key, value);
  }

  @Override
  public boolean replaceIfPresent(K key, V oldValue, V newValue) {
    checkType(key);
    checkType(oldValue);
    checkType(newValue);
    return map.replace(key, oldValue, newValue);
  }

  @Override
  public V replace(K key, V value) {
    checkType(key);
    checkType(value);
    return copyIfRequired(map.replace(key, value));
  }

  @Override
  public void close() {
    maps.remove(name);
  }
}
