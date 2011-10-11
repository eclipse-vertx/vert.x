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

package org.vertx.java.core.stdio;

import org.vertx.java.core.Handler;
import org.vertx.java.core.Vertx;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
class StreamBase {

  protected final long contextID;
  protected final Thread th;
  protected Handler<Exception> exceptionHandler;

  protected StreamBase() {
    Long contextID = Vertx.instance.getContextID();
    if (contextID == null) {
      throw new IllegalStateException("Can only be used inside an event loop");
    }
    this.contextID = contextID;
    this.th = Thread.currentThread();
  }

  protected void checkThread() {
    // All ops must always be invoked on same thread
    if (Thread.currentThread() != th) {
      throw new IllegalStateException("Invoked with wrong thread, actual: " + Thread.currentThread() + " expected: " + th);
    }
  }

  public void exceptionHandler(Handler<Exception> handler) {
    checkThread();
    this.exceptionHandler = handler;
  }
}
