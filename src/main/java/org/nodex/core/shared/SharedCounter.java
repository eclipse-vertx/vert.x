package org.nodex.core.shared;

import org.cliffc.high_scale_lib.ConcurrentAutoTable;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * User: timfox
 * Date: 19/07/2011
 * Time: 09:59
 */
public class SharedCounter implements Counter {

  private static Map<String, ConcurrentAutoTable> refs = new WeakHashMap<String, ConcurrentAutoTable>();

  private final ConcurrentAutoTable counter;

  public SharedCounter(String name) {
    synchronized (refs) {
      ConcurrentAutoTable c = (ConcurrentAutoTable) refs.get(name);
      if (c == null) {
        c = new ConcurrentAutoTable();
        refs.put(name, c);
      }
      counter = c;
    }
  }

  public void add(long amount) {
    counter.add(amount);
  }

  public void subtract(long amount) {
    counter.add(-amount);
  }

  public void increment() {
    counter.increment();
  }

  public void decrement() {
    counter.decrement();
  }

  public void set(long value) {
    counter.set(value);
  }

  public long get() {
    return counter.get();
  }
}
