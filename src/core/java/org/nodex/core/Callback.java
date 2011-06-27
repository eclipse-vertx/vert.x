package org.nodex.core;

/**
 * Author: timfox
 */
public abstract class Callback<T> {
  public abstract void onEvent(T t);
}
