/*
 * Copyright 2013 the original author or authors.
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
package org.vertx.java.tests.container;

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
