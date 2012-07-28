package org.vertx.java.core.impl;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class LowerCaseKeyMap<T> implements Map<String, T> {

  private Map<String, T> delegate = new HashMap<>();

  @Override
  public int size() {
    return delegate.size();
  }

  @Override
  public boolean isEmpty() {
    return delegate.isEmpty();
  }

  @Override
  public boolean containsKey(Object key) {
    if (key instanceof String) {
      return delegate.containsKey(((String)key).toLowerCase());
    } else {
      return false;
    }
  }

  @Override
  public boolean containsValue(Object value) {
    return delegate.containsValue(value);
  }

  @Override
  public T get(Object key) {
    if (key instanceof String) {
      return delegate.get(((String)key).toLowerCase());
    } else {
      return null;
    }
  }

  @Override
  public T put(String key, T value) {
    return delegate.put(key.toLowerCase(), value);
  }

  @Override
  public T remove(Object key) {
    if (key instanceof String) {
      return delegate.remove(((String)key).toLowerCase());
    } else {
      return null;
    }
  }

  @Override
  public void putAll(Map<? extends String, ? extends T> m) {
    for (Map.Entry<? extends String, ? extends T> entry: m.entrySet()) {
      delegate.put(entry.getKey().toLowerCase(), entry.getValue());
    }
  }

  @Override
  public void clear() {
    delegate.clear();
  }

  @Override
  public Set<String> keySet() {
    return delegate.keySet();
  }

  @Override
  public Collection<T> values() {
    return delegate.values();
  }

  @Override
  public Set<Entry<String, T>> entrySet() {
    return delegate.entrySet();
  }

  @Override
  public boolean equals(Object o) {
    return delegate.equals(o);
  }

  @Override
  public int hashCode() {
    return delegate.hashCode();
  }

  public Object putRaw(String key, T value) {
    return delegate.put(key, value);
  }

  public T getRaw(Object key) {
    return delegate.get(key);
  }
}
