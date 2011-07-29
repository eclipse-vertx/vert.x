package org.nodex.core.composition;

/**
 * User: tim
 * Date: 05/07/11
 * Time: 15:44
 */
public class Deferred extends Completion {

  private final Runnable cb;

  public Deferred(Runnable cb) {
    this.cb = cb;
  }

  @Override
  public void execute() {
    cb.run();
    complete();
  }
}
