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
package io.vertx.core;

import java.util.ServiceLoader;

/**
 * Factory for creating Vertx instances.<p>
 * Use this to create Vertx instances when embedding Vert.x core directly.<p>
 *
 * @author pidster
 *
 */
public abstract class VertxFactory {

  public static Vertx newVertx() {
    return loadFactory().createVertx();
  }

  public static Vertx newVertx(VertxOptions options) {
    return loadFactory().createVertx(options);
  }

  public static void newVertx(VertxOptions options, Handler<AsyncResult<Vertx>> resultHandler) {
    loadFactory().createVertx(options, resultHandler);
  }

  private static VertxFactory loadFactory() {
    ServiceLoader<VertxFactory> factories = ServiceLoader.load(VertxFactory.class);
    return factories.iterator().next();
  }

  protected Vertx createVertx() {
    return null;
  }

  protected Vertx createVertx(VertxOptions options) {
    return null;
  }

  protected void createVertx(VertxOptions options, Handler<AsyncResult<Vertx>> resultHandler) {
  }

}
