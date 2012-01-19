/*
 * Copyright 2011 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.vertx.java.core;

import org.vertx.java.core.logging.Logger;

/**
 * <p>Internal class used to run specific blocking actions on the background pool.</p>
 *
 * <p>This class shouldn't be used directlty from user applications.</p>
 *
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public abstract class BlockingAction<T> extends SynchronousAction<T> {

  private static final Logger log = Logger.getLogger(BlockingAction.class);

  /**
   * Run the blocking action using a thread from the background pool.
   */
  protected void run() {
    final long contextID = Vertx.instance.getContextID();
    Runnable runner = new Runnable() {
      public void run() {
        try {
          final T result = action();
          VertxInternal.instance.executeOnContext(contextID, new Runnable() {
            public void run() {
              VertxInternal.instance.setContextID(contextID);
              setResult(result);
            }
          });
        } catch (final Exception e) {
          VertxInternal.instance.executeOnContext(contextID, new Runnable() {
            public void run() {
              VertxInternal.instance.setContextID(contextID);
              setException(e);
            }
          });
        } catch (Throwable t) {
          //Not much we can do, just log it
          log.error(t);
        }
      }
    };

    VertxInternal.instance.getBackgroundPool().execute(runner);
  }

}
