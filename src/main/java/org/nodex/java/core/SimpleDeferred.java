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

import org.nodex.java.core.CompletionHandler;
import org.nodex.java.core.Deferred;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public abstract class SimpleDeferred<T> implements Deferred<T> {

  private T result;
  private Exception exception;
  private CompletionHandler<T> completionHandler;
  protected boolean executed;
  protected boolean complete;

  public T result() {
    return result;
  }

  public Exception exception() {
    return exception;
  }

  public boolean complete() {
    return complete;
  }

  public boolean succeeded() {
    return complete && exception == null;
  }

  public boolean failed() {
    return complete && exception != null;
  }

  public void handler(CompletionHandler<T> completionHandler) {
    this.completionHandler = completionHandler;
    if (complete) {
      callHandler();
    }
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

  public Deferred<T> execute() {
    if (!executed) {
      run();
      executed = true;
    }
    return this;
  }

  protected abstract void run();

  private void callHandler() {
    if (completionHandler != null) {
      completionHandler.handle(this);
    }
  }
}
