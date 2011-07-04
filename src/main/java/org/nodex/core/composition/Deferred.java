package org.nodex.core.composition;

import org.nodex.core.NoArgCallback;

/**
 * User: timfox
 * Date: 03/07/2011
 * Time: 17:15
 */
public abstract class Deferred {

  protected abstract void perform();

  private NoArgCallback onComplete;

  public synchronized void onComplete(NoArgCallback onComplete) {
    this.onComplete = onComplete;
    if (complete) {
      onComplete.onEvent();
    }
  }

  public void execute() {
    perform();
  }

  private boolean complete;

  protected synchronized void complete() {
    if (onComplete != null) {
      onComplete.onEvent();
    }
    complete = true;
  }
}
