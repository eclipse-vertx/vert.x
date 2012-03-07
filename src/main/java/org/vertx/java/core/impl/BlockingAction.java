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

import org.vertx.java.core.logging.Logger;
import org.vertx.java.core.logging.impl.LoggerFactory;

/**
 * <p>Internal class used to run specific blocking actions on the worker pool.</p>
 *
 * <p>This class shouldn't be used directlty from user applications.</p>
 *
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public abstract class BlockingAction<T> extends SynchronousAction<T> {

  private static final Logger log = LoggerFactory.getLogger(BlockingAction.class);

  protected Context context;

  /**
   * Run the blocking action using a thread from the worker pool.
   */
  protected void run() {
    context = VertxInternal.instance.getOrAssignContext();

    Runnable runner = new Runnable() {
      public void run() {
        try {
          final T result = action();
          context.execute(new Runnable() {
            public void run() {
              setResult(result);
            }
          });
        } catch (final Exception e) {
          context.execute(new Runnable() {
            public void run() {
              setException(e);
            }
          });
        } catch (Throwable t) {
          VertxInternal.instance.reportException(t);
        }
      }
    };

    VertxInternal.instance.getBackgroundPool().execute(runner);
  }

}
