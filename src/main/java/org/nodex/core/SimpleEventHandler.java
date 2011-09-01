package org.nodex.core;

/**
 * User: tim
 * Date: 31/08/11
 * Time: 08:59
 */
public abstract class SimpleEventHandler implements EventHandler<Void> {

  public void onEvent(Void event) {
    onEvent();
  }

  protected abstract void onEvent();
}
