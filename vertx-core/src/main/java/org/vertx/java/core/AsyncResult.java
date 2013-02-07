/*
 * Copyright 2011-2012 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.vertx.java.core;

/**
 * Represents a result that is returned asynchronously from an operation.<p>
 *
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class AsyncResult<T> {

  /**
   * The result of the operation. This will be null if the operation failed.
   */
  public T result;

  /**
   * An exception describing failure. This will be null if the operation succeeded.
   */
  public Exception exception;

  private boolean failed;
  private boolean succeeded;

  /**
   * Did it succeeed?
   */
  public boolean succeeded() {
    return succeeded;
  }

  /**
   * Did it fail?
   */
  public boolean failed() {
    return failed;
  }

  public boolean complete() {
    return failed || succeeded;
  }

  private AsyncResultHandler<T> handler;

  public void setHandler(AsyncResultHandler<T> handler) {
    this.handler = handler;
    checkCallHandler();
  }

  public void setResult(T result) {
    this.result = result;
    succeeded = true;
    checkCallHandler();
  }

  public void setFailure(Exception exception) {
    this.exception = exception;
    failed = true;
    checkCallHandler();
  }

  private void checkCallHandler() {
    if (handler != null && complete()) {
      handler.handle(this);
    }
  }

   TODO What we should do is refactor all the core and platform APIs and create two forms of each method - one
   using the old style - pass in a handler, the other using the "promise" style API
}
