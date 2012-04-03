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

import org.vertx.java.core.eventbus.EventBus;
import org.vertx.java.core.eventbus.impl.DefaultEventBus;
import org.vertx.java.core.file.FileSystem;
import org.vertx.java.core.http.HttpClient;
import org.vertx.java.core.http.HttpServer;
import org.vertx.java.core.impl.DefaultVertx;
import org.vertx.java.core.impl.VertxInternal;
import org.vertx.java.core.json.JsonObject;
import org.vertx.java.core.net.NetClient;
import org.vertx.java.core.net.NetServer;
import org.vertx.java.core.net.impl.DefaultNetServer;
import org.vertx.java.core.sockjs.AppConfig;
import org.vertx.java.core.sockjs.SockJSServer;

import java.util.List;

/**
 * A singleton instance of Vertx is available to all verticles.
 * <p>
 * It contains operations to set and cancel timers, and deploy and undeploy
 * verticles, amongst other things.
 * <p>
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public abstract class Vertx {

  public static Vertx newVertx() {
    return new DefaultVertx();
  }

  public static Vertx newVertx(String hostname) {
    return new DefaultVertx(hostname);
  }

  public static Vertx newVertx(int port, String hostname) {
    return new DefaultVertx(port, hostname);
  }

  public abstract NetServer createNetServer();

  public abstract NetClient createNetClient();

  public abstract HttpServer createHttpServer();

  public abstract HttpClient createHttpClient();

  public abstract SockJSServer createSockJSServer(HttpServer httpServer);

  public abstract FileSystem fileSystem();

  public abstract EventBus eventBus();

  /**
   * Set a one-shot timer to fire after {@code delay} milliseconds, at which point {@code handler} will be called with
   * the id of the timer.
   * @return the unique ID of the timer
   */
  public abstract long setTimer(long delay, Handler<Long> handler);

  /**
   * Set a periodic timer to fire every {@code delay} milliseconds, at which point {@code handler} will be called with
   * the id of the timer.
   * @return the unique ID of the timer
   */
  public abstract long setPeriodic(long delay, Handler<Long> handler);

  /**
   * Cancel the timer with the specified {@code id}. Returns {@code} true if the timer was successfully cancelled, or
   * {@code false} if the timer does not exist.
   */
  public abstract boolean cancelTimer(long id);

  /**
   * Put the handler on the event queue for this loop so it will be run asynchronously ASAP after this event has
   * been processed
   */
  public abstract void runOnLoop(Handler<Void> handler);

  /**
   * Is the current thread an event loop thread?
   * @return true if current thread is an event loop thread
   */
  public abstract boolean isEventLoop();

  /**
   * Is the current thread an worker thread?
   * @return true if current thread is an worker thread
   */
  public abstract boolean isWorker();
}
