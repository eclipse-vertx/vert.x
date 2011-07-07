package org.nodex.core.composition;

import org.nodex.core.DoneHandler;

/**
 * User: timfox
 * Date: 03/07/2011
 * Time: 17:15
 */
public class Completion {

  private DoneHandler onComplete;
  private boolean complete;

  public synchronized void onComplete(DoneHandler onComplete) {
    this.onComplete = onComplete;
    if (complete) {
      onComplete.onDone();
    }
  }

  public synchronized void complete() {
    if (onComplete != null) {
      onComplete.onDone();
    }
    complete = true;
  }

  public void execute() {

  }
}
