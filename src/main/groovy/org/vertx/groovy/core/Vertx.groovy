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

package org.vertx.groovy.core

import org.vertx.java.core.Handler

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
class Vertx {

  static Vertx instance = new Vertx()

  private org.vertx.java.core.Vertx jVertex = org.vertx.java.core.Vertx.instance

  private Vertx() {
  }

  /**
   * Set a one-shot timer to fire after {@code delay} milliseconds, at which point {@code handler} will be called with
   * the id of the timer.
   * @return the unique ID of the timer
   */
  long setTimer(long delay, Closure handler) {
    jVertex.setTimer(delay, handler as Handler)
  }

  /**
   * Set a periodic timer to fire every {@code delay} milliseconds, at which point {@code handler} will be called with
   * the id of the timer.
   * @return the unique ID of the timer
   */
  long setPeriodic(long delay, Closure handler) {
    jVertex.setPeriodic(delay, handler as Handler)
  }

  /**
   * Cancel the timer with the specified {@code id}. Returns {@code} true if the timer was successfully cancelled, or
   * {@code false} if the timer does not exist.
   */
  void cancelTimer(long timerID) {
    jVertex.cancelTimer(timerID)
  }

  /**
   * Put the handler on the event queue for this loop so it will be run asynchronously ASAP after this event has
   * been processed
   */
  void runOnLoop(Closure handler) {
    jVertex.runOnLoop(handler as Handler)
  }

  /**
   * Is the current thread an event loop thread?
   * @return true if current thread is an event loop thread
   */
  boolean isEventLoop() {
    jVertex.isEventLoop()
  }

  /**
   * Is the current thread an worker thread?
   * @return true if current thread is an worker thread
   */
  boolean isWorker() {
    jVertex.isWorker()
  }

}
