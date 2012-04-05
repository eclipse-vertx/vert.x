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
  public final T result;

  /**
   * An exception describing failure. This will be null if the operation succeeded.
   */
  public final Exception exception;

  /**
   * Did it succeeed?
   */
  public boolean succeeded() {
    return exception == null;
  }

  /**
   * Did it fail?
   */
  public boolean failed() {
    return exception != null;
  }

  /**
   * Create a successful AsyncResult
   * @param result The result
   */
  public AsyncResult(T result) {
    this.result = result;
    this.exception = null;
  }

  /**
   * Create a failed AsyncResult
   * @param exception The exception
   */
  public AsyncResult(Exception exception) {
    this.exception = exception;
    this.result = null;
  }
}
