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

package org.vertx.java.core.impl;

/**
 * <p>Represents an operation that may or may not have completed yet.</p>
 * <p>It contains methods to determine it it has completed, failed or succeded, and allows a handler to
 * be set which will be called when the operation completes, or fails.</p>
 *
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public interface Future<T> {

  /**
   * Returns the result of the operation. If the operation has not yet completed, or if it failed it will return null.
   */
  T result();

  /**
   * Returns the exception. An exception is always set if the operation failed.
   * If the operation has not yet completed, or if it succeeded it will return null.
   */
  Exception exception();

  /**
   * This will return true if the operation has completed, or failed.
   */
  boolean complete();

  /**
   * Has the operation succeeded?
   */
  boolean succeeded();

  /**
   * Did the operation fail?
   */
  boolean failed();

  /**
   * Set a handler on the Future. If the operation has already completed it will be called immediately, otherwise
   * it will be called when the operation completes or fails, passing in a reference to this.
   */
  Future<T> handler(CompletionHandler<T> handler);
}
