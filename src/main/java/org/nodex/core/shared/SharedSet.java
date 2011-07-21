package org.nodex.core.shared;

import org.cliffc.high_scale_lib.NonBlockingHashSet;

import java.util.Collection;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * User: timfox
 * Date: 19/07/2011
 * Time: 09:59
 */
public class SharedSet<E> implements Set<E> {

  private static Map<String, Set<?>> refs = new WeakHashMap<String, Set<?>>();

  private final Set<E> set;

  public SharedSet(String name) {
    synchronized (refs) {
      Set<E> s = (Set<E>) refs.get(name);
      if (s == null) {
        s = new NonBlockingHashSet<E>();
        refs.put(name, s);
      }
      set = s;
    }
  }

  public int size() {
    return set.size();
  }

  public boolean isEmpty() {
    return set.isEmpty();
  }

  public boolean contains(Object o) {
    return set.contains(o);
  }

  public Iterator<E> iterator() {
    return set.iterator();
  }

  public Object[] toArray() {
    return set.toArray();
  }

  public <T> T[] toArray(T[] ts) {
    return set.toArray(ts);
  }

  public boolean add(E e) {
    return set.add(e);
  }

  public boolean remove(Object o) {
    return set.remove(o);
  }

  public boolean containsAll(Collection<?> objects) {
    return set.containsAll(objects);
  }

  public boolean addAll(Collection<? extends E> es) {
    return set.addAll(es);
  }

  public boolean retainAll(Collection<?> objects) {
    return set.retainAll(objects);
  }

  public boolean removeAll(Collection<?> objects) {
    return set.removeAll(objects);
  }

  public void clear() {
    set.clear();
  }

  @Override
  public boolean equals(Object o) {
    return set.equals(o);
  }

  @Override
  public int hashCode() {
    return set.hashCode();
  }
}
