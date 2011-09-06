package org.nodex.java.core;

/**
 * User: tim
 * Date: 31/08/11
 * Time: 08:57
 */
public interface EventHandler<E> {
  void onEvent(E event);
}
