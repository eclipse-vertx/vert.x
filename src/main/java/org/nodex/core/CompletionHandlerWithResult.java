package org.nodex.core;

/**
 * User: tim
 * Date: 02/08/11
 * Time: 12:00
 */
public interface CompletionHandlerWithResult<T> {

  void onCompletion(T result);

  void onException(Exception e);
}
