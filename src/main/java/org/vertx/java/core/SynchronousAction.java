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
 * <p>A subclass of {@link DeferredAction} which can be used to implement custom synchronous actions.</p>
 *
 * <p>If you wish to create an instance of Deferred which computes a result immediately without blocking, then
 * this class can be subclassed and the action method implemented.</p>
 *
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public abstract class SynchronousAction<T> extends DeferredAction<T> {

  @Override
  protected void run() {
    try {
      setResult(action());
    } catch (Exception e) {
      setException(e);
    }
  }

  /**
   * Implement this method in a subclass to implement the non blocking synchronous action and return the result as the
   * return value of the method.
   * <b>Do not</b> use this method to implement long running, blocking operations. Use {@link BlockingAction} for that.
   */
  public abstract T action() throws Exception;
}
