package org.nodex.core.composition;

/**
 * User: timfox
 * Date: 03/07/2011
 * Time: 17:15
 */
public class Completion {

  private Runnable onComplete;
  private boolean complete;

  public synchronized void onComplete(Runnable onComplete) {
    this.onComplete = onComplete;
    if (complete) {
      onComplete.run();
    }
  }

  public synchronized void complete() {
    if (onComplete != null) {
      onComplete.run();
    }
    complete = true;
  }

  public void execute() {
  }
}
