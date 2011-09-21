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
 * <p>SimpleDeferred is useful when you want to create your own Deferred instances.</p>
 *
 * <p>Normally, instances of Deferred are returned from node.x modules to represent operations such as getting a key from
 * a Redis server, or copying a file. However if you wish to create your own instances you can do this by subclassing this
 * class and implementing the {@link #run} method.</p>
 *
 * <p>When the operation is complete be sure to call {@link #setResult} or {@link #setException}</p>
 *
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public abstract class SimpleDeferred<T> implements Deferred<T> {

  private T result;
  private Exception exception;
  private CompletionHandler<T> completionHandler;
  protected boolean executed;
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
  public void handler(CompletionHandler<T> completionHandler) {
    this.completionHandler = completionHandler;
    if (complete) {
      callHandler();
    }
  }

  /**
   * {@inheritDoc}
   */
  public Deferred<T> execute() {
    if (!executed) {
      run();
      executed = true;
    }
    return this;
  }

  protected void setResult(T result) {
    complete = true;
    this.result = result;
    callHandler();
  }

  protected void setException(Exception e) {
    complete = true;
    this.exception = e;
    callHandler();
  }


  /**
   * Override this method to implement the deferred operation.
   * When the operation is complete be sure to call {@link #setResult} or {@link #setException}
   */
  protected abstract void run();

  private void callHandler() {
    if (completionHandler != null) {
      completionHandler.handle(this);
    }
  }
}
