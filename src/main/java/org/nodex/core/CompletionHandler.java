package org.nodex.core;

/**
 * User: tim
 * Date: 01/08/11
 * Time: 16:18
 */
public interface CompletionHandler {

  void onCompletion();

  void onException(Exception e);
}
