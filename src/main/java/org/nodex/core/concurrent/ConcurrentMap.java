package org.nodex.core.concurrent;

import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentSkipListMap;

/**
 * User: timfox
 * Date: 29/06/2011
 * Time: 21:20
 *
 */
public class ConcurrentMap<K, V> implements Map<K, V> {

  private Map<K, V> map = new ConcurrentSkipListMap<K, V>();

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
