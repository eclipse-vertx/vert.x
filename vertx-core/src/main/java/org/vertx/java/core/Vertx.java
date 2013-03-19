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
import org.vertx.java.core.file.FileSystem;
import org.vertx.java.core.http.HttpClient;
import org.vertx.java.core.http.HttpServer;
import org.vertx.java.core.net.NetClient;
import org.vertx.java.core.net.NetServer;
import org.vertx.java.core.shareddata.SharedData;
import org.vertx.java.core.sockjs.SockJSServer;

import java.util.ServiceLoader;

/**
 * The control centre of vert.x<p>
 * You should normally only use a single instance of this class throughout your application. If you are running in the
 * vert.x container an instance will be provided to you.<p>
 * If you are using vert.x embedded, you can create an instance using one of the static {@code newVertx} methods.<p>
 * This class acts as a factory for TCP/SSL and HTTP/HTTPS servers and clients, SockJS servers, and provides an
 * instance of the event bus, file system and shared data classes, as well as methods for setting and cancelling
 * timers.
 *
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public abstract class Vertx {

  private static VertxFactory loadFactory() {
	  ServiceLoader<VertxFactory> factories = ServiceLoader.load(VertxFactory.class);
	  return factories.iterator().next();
  }

  /**
   * Create a non clustered Vertx instance
   */
  public static Vertx newVertx() {
  	return loadFactory().createVertx();
  }

  /**
   * Create a clustered Vertx instance listening for cluster connections on the default port 25500
   * @param hostname The hostname or ip address to listen for cluster connections
   */
  public static Vertx newVertx(String hostname) {
	  return loadFactory().createVertx(hostname);
  }

  /**
   * Create a clustered Vertx instance
   * @param port The port to listen for cluster connections
   * @param hostname The hostname or ip address to listen for cluster connections
   */
  public static Vertx newVertx(int port, String hostname) {
	  return loadFactory().createVertx(port, hostname);
  }

  /**
   * Create a TCP/SSL server
   */
  public abstract NetServer createNetServer();

  /**
   * Create a TCP/SSL client
   */
  public abstract NetClient createNetClient();

  /**
   * Create an HTTP/HTTPS server
   */
  public abstract HttpServer createHttpServer();

  /**
   * Create a HTTP/HTTPS client
   */
  public abstract HttpClient createHttpClient();

  /**
   * Create a SockJS server that wraps an HTTP server
   */
  public abstract SockJSServer createSockJSServer(HttpServer httpServer);

  /**
   * The File system object
   */
  public abstract FileSystem fileSystem();

  /**
   * The event bus
   */
  public abstract EventBus eventBus();

  /**
   * The shared data object
   */
  public abstract SharedData sharedData();

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

  /**
	 * Stop the eventbus and any resource managed by the eventbus.
	 */
	public abstract void stop();
}
