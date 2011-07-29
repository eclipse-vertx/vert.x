package org.nodex.core;

/**
 * User: timfox
 * Date: 28/07/2011
 * Time: 14:35
 */
public interface Actor<T> {
  void onMessage(T message);
}
