package org.nodex.core.concurrent;

import org.cliffc.high_scale_lib.ConcurrentAutoTable;

/**
 * User: timfox
 * Date: 02/07/2011
 * Time: 18:25
 * <p/>
 * Highly concurrent counter
 */
public class SafeCounter {
  private ConcurrentAutoTable table = new ConcurrentAutoTable();

  public void add(long delta) {
    table.add(delta);
  }

  public void increment() {
    table.increment();
  }

  public void decrement() {
    table.decrement();
  }

  public void set(long value) {
    table.set(value);
  }

  public long get() {
    return table.get();
  }

}
