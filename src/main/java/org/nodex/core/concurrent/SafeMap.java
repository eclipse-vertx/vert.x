package org.nodex.core.concurrent;

import org.cliffc.high_scale_lib.NonBlockingHashMap;

import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentMap;

/**
 * User: timfox
 * Date: 29/06/2011
 * Time: 21:20
 * <p/>
 * We wrap Cliff Click's super scalable concurrent map implementation
 */
public class SafeMap<K, V> implements ConcurrentMap<K, V> {

  private final ConcurrentMap<K, V> map;

  public SafeMap() {
    map = new NonBlockingHashMap<K, V>();
  }

  public SafeMap(Map<? extends K, ? extends V> m) {
    this();
    map.putAll(m);
  }

  public V putIfAbsent(K k, V v) {
    return map.putIfAbsent(k, v);
  }

  public boolean remove(Object o, Object o1) {
    return map.remove(o, o1);
  }

  public boolean replace(K k, V v, V v1) {
    return map.replace(k, v, v1);
  }

  public V replace(K k, V v) {
    return map.replace(k, v);
  }

  public int size() {
    return map.size();
  }

  public boolean isEmpty() {
    return map.isEmpty();
  }

  public boolean containsKey(Object o) {
    return map.containsKey(o);
  }

  public boolean containsValue(Object o) {
    return map.containsValue(o);
  }

  public V get(Object o) {
    return map.get(o);
  }

  public V put(K k, V v) {
    return map.put(k, v);
  }

  public V remove(Object o) {
    return map.remove(o);
  }

  public void putAll(Map<? extends K, ? extends V> map) {
    this.map.putAll(map);
  }

  public void clear() {
    map.clear();
  }

  public Set<K> keySet() {
    return map.keySet();
  }

  public Collection<V> values() {
    return map.values();
  }

  public Set<Entry<K, V>> entrySet() {
    return map.entrySet();
  }

  @Override
  public boolean equals(Object o) {
    return map.equals(o);
  }

  @Override
  public int hashCode() {
    return map.hashCode();
  }
}
