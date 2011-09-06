package org.nodex.java.core;

/**
 * User: tim
 * Date: 01/09/11
 * Time: 17:31
 */
public class Completion<T> {

  public final T result;
  public final Exception exception;

  public static final Completion<Void> VOID_SUCCESSFUL_COMPLETION = new Completion<Void>((Void) null);

  public Completion(T result) {
    this.result = result;
    this.exception = null;
  }

  public Completion(Exception exception) {
    this.result = null;
    this.exception = exception;
  }

  public boolean succeeded() {
    return exception == null;
  }

  public boolean failed() {
    return exception != null;
  }

}

