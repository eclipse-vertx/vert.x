package org.nodex.core.composition;

import org.nodex.core.NoArgCallback;

/**
 * User: timfox
 * Date: 03/07/2011
 * Time: 17:15
 */
public class Completion {

  private NoArgCallback onComplete;
  private boolean complete;

  public synchronized void onComplete(NoArgCallback onComplete) {
    this.onComplete = onComplete;
    if (complete) {
      onComplete.onEvent();
    }
  }

  public synchronized void complete() {
    if (onComplete != null) {
      onComplete.onEvent();
    }
    complete = true;
  }

  public void execute() {

  }
}
