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
 * Represents a result that may not have occurred yet.
 *
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public interface AsyncResult<T> {

  /**
   * The result of the operation. This will be null if the operation failed.
   */
  T result();

  /**
   * An exception describing failure. This will be null if the operation succeeded.
   */
  Throwable cause();

  /**
   * Did it succeed?
   */
  boolean succeeded();

  /**
   * Did it fail?
   */
  boolean failed();

}
