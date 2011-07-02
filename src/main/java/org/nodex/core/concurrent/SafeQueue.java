package org.nodex.core.concurrent;

import java.util.Collection;
import java.util.Iterator;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * User: timfox
 * Date: 02/07/2011
 * Time: 18:13
 *
 * We wrap ConcurrentLinkedQueue
 */
public class SafeQueue<E> implements Queue<E> {

  private final Queue<E> queue;

  public SafeQueue() {
    queue = new ConcurrentLinkedQueue<E>();
  }

  public SafeQueue(Collection<? extends E> c) {
    queue = new ConcurrentLinkedQueue<E>(c);
  }

  public boolean add(E e) {
    return queue.add(e);
  }

  public boolean offer(E e) {
    return queue.offer(e);
  }

  public E remove() {
    return queue.remove();
  }

  public E poll() {
    return queue.poll();
  }

  public E element() {
    return queue.element();
  }

  public E peek() {
    return queue.peek();
  }

  public int size() {
    return queue.size();
  }

  public boolean isEmpty() {
    return queue.isEmpty();
  }

  public boolean contains(Object o) {
    return queue.contains(o);
  }

  public Iterator<E> iterator() {
    return queue.iterator();
  }

  public Object[] toArray() {
    return queue.toArray();
  }

  public <T> T[] toArray(T[] ts) {
    return queue.toArray(ts);
  }

  public boolean remove(Object o) {
    return queue.remove(o);
  }

  public boolean containsAll(Collection<?> objects) {
    return queue.containsAll(objects);
  }

  public boolean addAll(Collection<? extends E> es) {
    return queue.addAll(es);
  }

  public boolean removeAll(Collection<?> objects) {
    return queue.removeAll(objects);
  }

  public boolean retainAll(Collection<?> objects) {
    return queue.retainAll(objects);
  }

  public void clear() {
    queue.clear();
  }

  @Override
  public boolean equals(Object o) {
    return queue.equals(o);
  }

  @Override
  public int hashCode() {
    return queue.hashCode();
  }
}
