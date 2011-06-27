package org.nodex.core;

/**
 * Created by IntelliJ IDEA.
 * User: timfox
 * Date: 25/06/2011
 * Time: 09:06
 * To change this template use File | Settings | File Templates.
 */
public abstract class Callback<T> {
  public abstract void onEvent(T t);
}
