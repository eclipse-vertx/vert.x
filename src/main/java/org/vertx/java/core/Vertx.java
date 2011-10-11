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

/**
 *
 * <p>An instance of {@code Vertx} is available to all event loops in a running application.</p>
 *
 * <p>It handles such things as setting and cancelling timers, global event handlers, amongst other things.</p>
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
   * Register a global handler with the system. The handler can be invoked by calling the {@link #sendToHandler}
   * method from any event loop. The handler will always be called on the event loop that invoked the {@code
   * registerHandler} method.
   * @return the unique ID of the handler. This is required when calling {@link #sendToHandler}.
   */
  <T> long registerHandler(Handler<T> handler);

  /**
   * Unregister the handler with the specified {@code handlerID}. This must be called from the same event loop that
   * registered the handler.
   * @return true if the handler was successfully unregistered, otherwise false if the handler cannot be found.
   */
  boolean unregisterHandler(long handlerID);

  /**
   * Send a message to the handler with the specified {@code actorID}. This can be called from any event loop.
   * @return true of the message was successfully sent, or false if no such handler exists.
   */
  <T> boolean sendToHandler(long actorID, T message);

  /**
   * Returns the context ID for the current event loop. The context ID uniquely identifies the event loop.
   */
  Long getContextID();

  /**
   * Call the specified event handler asynchronously on the next "tick" of the event loop.
   */
  void nextTick(Handler<Void> handler);

  /**
   * Run the specified Runnable inside an event loop. An event loop will be picked by the system from all available
   * loops.
   */
  void go(Runnable runnable);
}
