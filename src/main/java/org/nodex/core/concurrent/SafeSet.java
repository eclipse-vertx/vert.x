package org.nodex.core.concurrent;

import org.cliffc.high_scale_lib.NonBlockingHashSet;

import java.util.Collection;
import java.util.Iterator;
import java.util.Set;

/**
 * User: timfox
 * Date: 02/07/2011
 * Time: 18:22
 */
public class SafeSet<E> implements Set<E> {

  private final Set<E> set;

  public SafeSet() {
    set = new NonBlockingHashSet<E>();
  }

  public SafeSet(Collection<? extends E> c) {
    this();
    set.addAll(c);
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
