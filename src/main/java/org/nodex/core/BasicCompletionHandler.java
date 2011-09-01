package org.nodex.core;

/**
 * User: tim
 * Date: 31/08/11
 * Time: 08:45
 */
public abstract class BasicCompletionHandler<T> implements CompletionHandler<T> {

  protected T result;
  protected Exception exception;

  public void onCompletion(T result) {
    this.result = result;
    onCompletion();
  }

  public void onException(Exception exception) {
    this.exception = exception;
    onCompletion();
  }

  public boolean failed() {
    return exception != null;
  }

  public boolean succeeded() {
    return result != null;
  }

  protected abstract void onCompletion();
}
