package org.nodex.core;

/**
 * User: tim
 * Date: 19/08/11
 * Time: 11:36
 */
public interface EventHandler<E> {
  void onEvent(E event);
}
