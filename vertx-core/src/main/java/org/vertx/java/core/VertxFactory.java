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

import java.util.ServiceLoader;

/**
 * Factory for creating Vertx instances.<p>
 * Use this to create Vertx instances when embedding Vert.x core directly.<p>
 *
 * @author pidster
 *
 */
public class VertxFactory {

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
}
