package org.nodex.core;

/**
 * User: tim
 * Date: 02/08/11
 * Time: 12:23
 */
public abstract class BackgroundTask extends BackgroundTaskWithResult<Object> {

  public BackgroundTask(final Completion completion) {
    super(new CompletionWithResult<Object>() {
      public void onCompletion(Object result) {
        completion.onCompletion();
      }
      public void onException(Exception e) {
        completion.onException(e);
      }
    });
  }
}
