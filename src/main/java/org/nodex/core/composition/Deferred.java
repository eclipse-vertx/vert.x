package org.nodex.core.composition;

import org.nodex.core.DoneHandler;

/**
 * User: tim
 * Date: 05/07/11
 * Time: 15:44
 */
public class Deferred extends Completion {

  private final DoneHandler cb;

  public Deferred(DoneHandler cb) {
    this.cb = cb;
  }

  @Override
  public void execute() {
    cb.onDone();
    complete();
  }
}
