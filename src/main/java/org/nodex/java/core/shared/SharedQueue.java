package org.nodex.java.core.shared;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * User: tim
 * Date: 17/08/11
 * Time: 19:13
 */
public class SharedQueue<T> {
  private final Queue<T> queue = new ConcurrentLinkedQueue<>();

  public void add(T t) {
    t = SharedUtils.checkObject(t);
    queue.add(t);
  }

  public void clear() {
    queue.clear();
  }

  public T poll() {
    return queue.poll();
  }

  public Object peek() {
    return queue.peek();
  }
}
