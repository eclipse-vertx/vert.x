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
import org.vertx.groovy.core.net.NetClient
import org.vertx.groovy.core.net.NetServer
import org.vertx.groovy.core.sockjs.SockJSServer
import org.vertx.java.core.Handler
import org.vertx.java.core.impl.DefaultVertx
import org.vertx.java.core.impl.VertxInternal
import org.vertx.java.core.shareddata.SharedData

/**
 *
 * @author Peter Ledbrook
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
class Vertx {

  private final VertxInternal jVertex;
  private final EventBus eventBus;
  private final org.vertx.groovy.core.file.FileSystem fileSystem;

  public Vertx(VertxInternal jVertex) {
    this.jVertex = jVertex;
    this.eventBus = new EventBus(jVertex.eventBus());
    this.fileSystem = new org.vertx.groovy.core.file.FileSystem(jVertex.fileSystem());
  }

  public static Vertx newVertx() {
    return new Vertx(new DefaultVertx());
  }

  public static Vertx newVertx(String hostname) {
    return new Vertx(new DefaultVertx(hostname));
  }

  public static Vertx newVertx(int port, String hostname) {
    return new Vertx(new DefaultVertx(port, hostname));
  }

  public NetServer createNetServer() {
    return new NetServer(jVertex);
  }

  public NetClient createNetClient() {
    return new NetClient(jVertex);
  }

  public HttpServer createHttpServer() {
    return new HttpServer(jVertex);
  }

  public HttpClient createHttpClient(Map props) {
    return new HttpClient(jVertex, props);
  }

  public SockJSServer createSockJSServer(HttpServer httpServer) {
    return new SockJSServer(httpServer);
  }

  public org.vertx.groovy.core.file.FileSystem fileSystem() {
    return fileSystem;
  }

  public SharedData sharedData() {
    return jVertex.sharedData();
  }

  public EventBus eventBus() {
    return eventBus;
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

  org.vertx.java.core.Vertx toJavaVertx() {
    return jVertex;
  }

}
