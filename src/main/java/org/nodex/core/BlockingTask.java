package org.nodex.core;

/**
 * User: tim
 * Date: 02/08/11
 * Time: 12:23
 */
public abstract class BlockingTask extends BlockingTaskWithResult<Object> {

  public BlockingTask(final CompletionHandler completionHandler) {
    super(new CompletionHandlerWithResult<Object>() {
      public void onCompletion(Object result) {
        completionHandler.onCompletion();
      }
      public void onException(Exception e) {
        completionHandler.onException(e);
      }
    });
  }
}
