package io.vertx.core.shareddata;

import io.vertx.codegen.annotations.VertxGen;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
@VertxGen
public interface LocalMap<K, V> {

  V get(K key);

  V put(K key, V value);

  V remove(K key);

  void clear();

  int size();

  boolean isEmpty();

  V putIfAbsent(K key, V value);

  boolean removeIfPresent(K key, V value);

  boolean replaceIfPresent(K key, V oldValue, V newValue);

  V replace(K key, V value);

  void close();

}
