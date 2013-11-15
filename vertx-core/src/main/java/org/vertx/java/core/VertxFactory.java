/*
 * Copyright (c) 2011-2013 The original author or authors
 * ------------------------------------------------------
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * and Apache License v2.0 which accompanies this distribution.
 *
 *     The Eclipse Public License is available at
 *     http://www.eclipse.org/legal/epl-v10.html
 *
 *     The Apache License v2.0 is available at
 *     http://www.opensource.org/licenses/apache2.0.php
 *
 * You may elect to redistribute this code under either of these licenses.
 */
package org.vertx.java.core;

import java.util.ServiceLoader;

/**
 * Factory for creating Vertx instances.<p>
 * Use this to create Vertx instances when embedding Vert.x core directly.<p>
 *
 * @author pidster
 *
 */
public abstract class VertxFactory {

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
   * Create a clustered Vertx instance.
   * Note that the event bus might not be listening until some time after this method has returned
   * @param port The port to listen for cluster connections
   * @param hostname The hostname or ip address to listen for cluster connections
   */
  public static Vertx newVertx(int port, String hostname) {
    return loadFactory().createVertx(port, hostname);
  }

  /**
   * Create a clustered Vertx instance returning the instance asynchronously in the resultHandler
   * when the event bus is ready and listening
   * @param port The port to listen for cluster connections
   * @param hostname The hostname or ip address to listen for cluster connections
   */
  public static void newVertx(int port, String hostname, Handler<AsyncResult<Vertx>> resultHandler) {
    loadFactory().createVertx(port, hostname, resultHandler);
  }

  private static VertxFactory loadFactory() {
    ServiceLoader<VertxFactory> factories = ServiceLoader.load(VertxFactory.class);
    return factories.iterator().next();
  }

  protected Vertx createVertx() {
    return null;
  }

  protected Vertx createVertx(String hostname) {
    return null;
  }

  protected Vertx createVertx(int port, String hostname) {
    return null;
  }

  protected void createVertx(int port, String hostname, Handler<AsyncResult<Vertx>> resultHandler) {
  }
}
