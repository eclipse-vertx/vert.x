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

  long setTimer(long delay, Closure handler) {
    jVertex.setTimer(delay, handler as Handler)
  }

  long setPeriodic(long delay, Closure handler) {
    jVertex.setPeriodic(delay, handler as Handler)
  }

  void cancelTimer(long timerID) {
    jVertex.cancelTimer(timerID)
  }

  void runOnLoop(Closure handler) {
    jVertex.runOnLoop(handler as Handler)
  }

  boolean isEventLoop() {
    jVertex.isEventLoop()
  }

  boolean isWorker() {
    jVertex.isWorker()
  }

}
