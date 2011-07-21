package org.nodex.core.shared;

import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * User: timfox
 * Date: 19/07/2011
 * Time: 09:59
 */
public class SharedList<E> implements List<E> {

  private static Map<String, List<?>> refs = new WeakHashMap<String, List<?>>();

  private final List<E> list;

  public SharedList(String name) {
    synchronized (refs) {
      List<E> l = (List<E>)refs.get(name);
      if (l == null) {
        l = new CopyOnWriteArrayList<E>();
        refs.put(name, l);
      }
      list = l;
    }
  }

  public int size() {
    return list.size();
  }

  public boolean isEmpty() {
    return list.isEmpty();
  }

  public boolean contains(Object o) {
    return list.contains(o);
  }

  public Iterator<E> iterator() {
    return list.iterator();
  }

  public Object[] toArray() {
    return list.toArray();
  }

  public <T> T[] toArray(T[] ts) {
    return list.toArray(ts);
  }

  public boolean add(E e) {
    return list.add(e);
  }

  public boolean remove(Object o) {
    return list.remove(o);
  }

  public boolean containsAll(Collection<?> objects) {
    return list.containsAll(objects);
  }

  public boolean addAll(Collection<? extends E> es) {
    return list.addAll(es);
  }

  public boolean addAll(int i, Collection<? extends E> es) {
    return list.addAll(i, es);
  }

  public boolean removeAll(Collection<?> objects) {
    return list.removeAll(objects);
  }

  public boolean retainAll(Collection<?> objects) {
    return list.retainAll(objects);
  }

  public void clear() {
    list.clear();
  }

  @Override
  public boolean equals(Object o) {
    return list.equals(o);
  }

  @Override
  public int hashCode() {
    return list.hashCode();
  }

  public E get(int i) {
    return list.get(i);
  }

  public E set(int i, E e) {
    return list.set(i, e);
  }

  public void add(int i, E e) {
    list.add(i, e);
  }

  public E remove(int i) {
    return list.remove(i);
  }

  public int indexOf(Object o) {
    return list.indexOf(o);
  }

  public int lastIndexOf(Object o) {
    return list.lastIndexOf(o);
  }

  public ListIterator<E> listIterator() {
    return list.listIterator();
  }

  public ListIterator<E> listIterator(int i) {
    return list.listIterator(i);
  }

  public List<E> subList(int i, int i1) {
    return list.subList(i, i1);
  }
}
