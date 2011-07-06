package org.nodex.core.composition;

import org.nodex.core.NoArgCallback;

/**
 * User: tim
 * Date: 05/07/11
 * Time: 15:44
 */
public class Deferred extends Completion {

  private final NoArgCallback cb;

  public Deferred(NoArgCallback cb) {
    this.cb = cb;
  }

  @Override
  public void execute() {
    cb.onEvent();
    complete();
  }
}
