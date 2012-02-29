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

import org.vertx.java.core.CompletionHandler;

/**
 * <p>DeferredAction is useful when you want to create your own Deferred actions.</p>
 *
 * <p>Normally, instances of Deferred are returned from vert.x modules to represent operations such as getting a key from
 * a Redis server, or copying a file. However if you wish to create your own instances you can do this by subclassing this
 * class and implementing the {@link #run} method.</p>
 *
 * <p>When the operation is complete be sure to call {@link #setResult} or {@link #setException}</p>
 *
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public abstract class DeferredAction<T> extends SimpleFuture<T> implements Deferred<T> {

  protected boolean executed;

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

  /**
   * {@inheritDoc}
   */
  public Deferred<T> handler(CompletionHandler<T> completionHandler) {
    super.handler(completionHandler);
    return this;
  }

  /**
   * Override this method to implement the deferred operation.
   * When the operation is complete be sure to call {@link #setResult} or {@link #setException}
   */
  protected abstract void run();

}
