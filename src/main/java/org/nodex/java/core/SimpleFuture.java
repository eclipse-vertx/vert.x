/*
 * Copyright 2011 VMware, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.nodex.java.core;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class SimpleFuture<T> implements Future<T> {

  private T result;
  private Exception exception;
  private CompletionHandler<T> completionHandler;
  protected boolean complete;

  /**
   * {@inheritDoc}
   */
  public T result() {
    return result;
  }

  /**
   * {@inheritDoc}
   */
  public Exception exception() {
    return exception;
  }

  /**
   * {@inheritDoc}
   */
  public boolean complete() {
    return complete;
  }

  /**
   * {@inheritDoc}
   */
  public boolean succeeded() {
    return complete && exception == null;
  }

  /**
   * {@inheritDoc}
   */
  public boolean failed() {
    return complete && exception != null;
  }

  /**
   * {@inheritDoc}
   */
  public Future<T> handler(CompletionHandler<T> completionHandler) {
    this.completionHandler = completionHandler;
    if (complete) {
      callHandler();
    }
    return this;
  }

  /**
   * Call this method with the result of the action when it is complete.
   */
  public void setResult(T result) {
    complete = true;
    this.result = result;
    callHandler();
  }

  /**
   * Call this method with an Exception if the action failed
   */
  public void setException(Exception e) {
    complete = true;
    this.exception = e;
    callHandler();
  }

  private void callHandler() {
    if (completionHandler != null) {
      completionHandler.handle(this);
    }
  }
}
