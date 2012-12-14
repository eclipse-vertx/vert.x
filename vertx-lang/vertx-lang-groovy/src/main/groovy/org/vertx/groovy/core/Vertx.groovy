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

import org.vertx.groovy.core.eventbus.EventBus
import org.vertx.groovy.core.http.HttpClient
import org.vertx.groovy.core.http.HttpServer
import org.vertx.groovy.core.http.impl.DefaultHttpClient
import org.vertx.groovy.core.http.impl.DefaultHttpServer
import org.vertx.groovy.core.net.NetClient
import org.vertx.groovy.core.net.NetServer
import org.vertx.groovy.core.net.impl.DefaultNetClient
import org.vertx.groovy.core.net.impl.DefaultNetServer
import org.vertx.groovy.core.sockjs.SockJSServer
import org.vertx.groovy.core.sockjs.impl.DefaultSockJSServer
import org.vertx.java.core.Handler
import org.vertx.java.core.impl.DefaultVertx
import org.vertx.java.core.impl.VertxInternal
import org.vertx.java.core.shareddata.SharedData

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
class Vertx {

  private final VertxInternal jVertex
  private final EventBus eventBus
  private final org.vertx.groovy.core.file.FileSystem fileSystem

  Vertx(VertxInternal jVertex) {
    this.jVertex = jVertex
    this.eventBus = new EventBus(jVertex.eventBus())
    this.fileSystem = new org.vertx.groovy.core.file.FileSystem(jVertex.fileSystem)
  }

  /**
   * Create a non clustered Vertx instance
   */
  static Vertx newVertx() {
    return new Vertx(new DefaultVertx())
  }

  /**
   * Create a clustered Vertx instance listening for cluster connections on the default port 25500
   * @param hostname The hostname or ip address to listen for cluster connections
   */
  static Vertx newVertx(String hostname) {
    return new Vertx(new DefaultVertx(hostname))
  }

  /**
   * Create a clustered Vertx instance
   * @param port The port to listen for cluster connections
   * @param hostname The hostname or ip address to listen for cluster connections
   */
  static Vertx newVertx(int port, String hostname) {
    return new Vertx(new DefaultVertx(port, hostname))
  }

  /**
   * Create a TCP/SSL server
   */
  NetServer createNetServer(Map props = null) {
    return new DefaultNetServer(jVertex, props)
  }

  /**
   * Create a TCP/SSL client
   */
  NetClient createNetClient(Map props = null) {
    return new DefaultNetClient(jVertex, props)
  }

  /*
   * Create an HTTP/HTTPS server
   */
  HttpServer createHttpServer(Map props = null) {
    return new DefaultHttpServer(jVertex, props)
  }

  /**
   * Create a HTTP/HTTPS client
   */
  HttpClient createHttpClient(Map props = null) {
    return new DefaultHttpClient(jVertex, props)
  }

  /**
   * Create a SockJS server that wraps an HTTP server
   */
  SockJSServer createSockJSServer(HttpServer httpServer) {
    return new DefaultSockJSServer(jVertex, httpServer)
  }

  /**
   * The File system object
   */
  org.vertx.groovy.core.file.FileSystem getFileSystem() {
    return fileSystem
  }

  /**
   * The event bus
   */
  EventBus getEventBus() {
    return eventBus
  }

  /**
   * The shared data object
   */
  SharedData getSharedData() {
    return jVertex.sharedData
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
  boolean cancelTimer(long timerID) {
    return jVertex.cancelTimer(timerID)
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

  org.vertx.java.core.Vertx toJavaVertx() {
    return jVertex
  }

}
