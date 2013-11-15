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
package org.vertx.java.tests.platform;

import org.vertx.java.core.Vertx;
import org.vertx.java.core.json.JsonObject;
import org.vertx.java.core.logging.Logger;
import org.vertx.java.platform.Container;
import org.vertx.java.platform.Verticle;
import org.vertx.java.platform.VerticleFactory;

/**
 * @author swilliams
 *
 */
public class FooLangVerticleFactory implements VerticleFactory {

  @SuppressWarnings("unused")
  private ClassLoader cl;

  @Override
  public void init(Vertx vertx, Container container, ClassLoader cl) {
    this.cl = cl;
  }

  @Override
  public Verticle createVerticle(final String main) throws Exception {

    return new Verticle() {

      @Override
      public void start() {
        JsonObject config = container.config();
        String foo = config.getString("foo", "bar");
        if (foo.equalsIgnoreCase("bar")) {
          throw new RuntimeException("foo must not be bar!");
        }
        if (!(main.endsWith("foo"))) {
          throw new RuntimeException("main must end with foo!");
        }
      }};
  }

  @Override
  public void reportException(Logger logger, Throwable t) {
    logger.error("Exception in Foo verticle!", t);
  }

  public void close() {
  }

}
