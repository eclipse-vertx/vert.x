package org.nodex.core;

/**
 * User: tfox
 * Date: 29/06/11
 * Time: 14:21
 */
public class FutureAction extends NoArgCallback {

  private NoArgCallback callback;
  private boolean done;

  public synchronized void onComplete(NoArgCallback callback) {
    this.callback = callback;
    if (done) {
      callback.onEvent();
    }
  }

  public synchronized void onEvent() {
    if (callback != null) {
      callback.onEvent();
    }
    done = true;
  }
}
