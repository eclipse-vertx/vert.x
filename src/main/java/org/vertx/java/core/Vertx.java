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

import org.vertx.java.core.impl.VertxImpl;
import org.vertx.java.core.json.JsonObject;
import org.vertx.java.core.logging.Logger;

/**
 * A singleton instance of Vertx is available to all verticles.
 * <p>
 * It contains operations to set and cancel timers, and deploy and undeploy
 * verticles, amongst other things.
 * <p>
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

  /**
   * Deploy a worker verticle programmatically
   * @param main The main of the verticle
   * @return Unique deployment id
   */
  String deployWorkerVerticle(String main);

  /**
   * Deploy a worker verticle programmatically
   * @param main The main of the verticle
   * @param instances The number of instances to deploy (defaults to 1)
   * @return Unique deployment id
   */
  String deployWorkerVerticle(String main, int instances);

  /**
   * Deploy a worker verticle programmatically
   * @param main The main of the verticle
   * @param config JSON config to provide to the verticle
   * @return Unique deployment id
   */
  String deployWorkerVerticle(String main, JsonObject config);

  /**
   * Deploy a worker verticle programmatically
   * @param main The main of the verticle
   * @param config JSON config to provide to the verticle
   * @param instances The number of instances to deploy (defaults to 1)
   * @return Unique deployment id
   */
  String deployWorkerVerticle(String main, JsonObject config, int instances);

  /**
   * Deploy a worker verticle programmatically
   * @param main The main of the verticle
   * @param config JSON config to provide to the verticle
   * @param instances The number of instances to deploy (defaults to 1)
   * @param doneHandler The handler will be called when deployment is complete
   * @return Unique deployment id
   */
  String deployWorkerVerticle(String main, JsonObject config, int instances, Handler<Void> doneHandler);

  /**
   * Deploy a worker verticle programmatically
   * @param main The main of the verticle
   * @return Unique deployment id
   */
  String deployVerticle(String main);

  /**
   * Deploy a verticle programmatically
   * @param main The main of the verticle
   * @param instances The number of instances to deploy (defaults to 1)
   * @return Unique deployment id
   */
  String deployVerticle(String main, int instances);

  /**
   * Deploy a verticle programmatically
   * @param main The main of the verticle
   * @param config JSON config to provide to the verticle
   * @return Unique deployment id
   */
  String deployVerticle(String main, JsonObject config);

  /**
   * Deploy a verticle programmatically
   * @param main The main of the verticle
   * @param config JSON config to provide to the verticle
   * @param instances The number of instances to deploy (defaults to 1)
   * @param doneHandler The handler will be called when deployment is complete
   * @return Unique deployment id
   */
  String deployVerticle(String main, JsonObject config, int instances, Handler<Void> doneHandler);

  /**
   * Undeploy a verticle
   * @param deploymentID The deployment ID
   */
  void undeployVerticle(String deploymentID);

  /**
   * Undeploy a verticle
   * @param deploymentID The deployment ID
   * @param doneHandler The handler will be called when undeployment is complete
   */
  void undeployVerticle(String deploymentID, Handler<Void> doneHandler);

  /**
   * Get the verticle configuration
   * @return a JSON object representing the configuration
   */
  JsonObject getConfig();

  /**
   * Get the verticle logger
   * @return The logger
   */
  Logger getLogger();
}
