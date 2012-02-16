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

import org.vertx.java.core.json.JsonObject;
import org.vertx.java.core.logging.Logger;

/**
 *
 *
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public interface Vertx {

  static Vertx instance = new VertxImpl();

  /**
   * Set a one-shot timer to fire after {@code delay} milliseconds, at which point {@code handler} will be called with
   * the id of the timer.
   * @return the unique ID of the timer
   */
  long setTimer(long delay, Handler<Long> handler);

  /**
   * Set a periodic timer to fire every {@code delay} milliseconds, at which point {@code handler} will be called with
   * the id of the timer.
   * @return the unique ID of the timer
   */
  long setPeriodic(long delay, Handler<Long> handler);

  /**
   * Cancel the timer with the specified {@code id}. Returns {@code} true if the timer was successfully cancelled, or
   * {@code false} if the timer does not exist.
   */
  boolean cancelTimer(long id);

  /**
   * Call the specified event handler asynchronously on the next "tick" of the event loop.
   */
  void nextTick(Handler<Void> handler);

  /**
   * Is the current thread an event loop thread?
   * @return true if current thread is an event loop thread
   */
  boolean isEventLoop();


  String deployWorkerVerticle(String main);

  String deployWorkerVerticle(String main, int instances);

  String deployWorkerVerticle(String main, JsonObject config);

  String deployWorkerVerticle(String main, JsonObject config, int instances);

  String deployWorkerVerticle(String main, JsonObject config, int instances, Handler<Void> doneHandler);


  String deployVerticle(String main);

  String deployVerticle(String main, int instances);

  String deployVerticle(String main, JsonObject config);

  String deployVerticle(String main, JsonObject config, int instances, Handler<Void> doneHandler);

  void undeployVerticle(String deploymentID);

  void undeployVerticle(String deploymentID, Handler<Void> doneHandler);

  void exit();

  JsonObject getConfig();

  Logger getLogger();
}
