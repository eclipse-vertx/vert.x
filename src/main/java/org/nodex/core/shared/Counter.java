package org.nodex.core.shared;

/**
 * User: timfox
 * Date: 02/07/2011
 * Time: 18:25
 */
public interface Counter {

  void add(long amount);

  void subtract(long amount);

  void increment();

  void decrement();

  void set(long value);

  long get();

}
