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
 */package org.vertx.java.tests.deploy;

import org.vertx.java.core.json.JsonObject;
import org.vertx.java.deploy.Verticle;
import org.vertx.java.deploy.impl.ModuleClassLoader;
import org.vertx.java.deploy.impl.VerticleFactory;
import org.vertx.java.deploy.impl.VerticleManager;

/**
 * @author swilliams
 *
 */
public class FooLangVerticleFactory implements VerticleFactory {

  private VerticleManager manager;

  @SuppressWarnings("unused")
  private ModuleClassLoader mcl;

  @Override
  public void init(VerticleManager manager, ModuleClassLoader mcl) {
    this.manager = manager;
    this.mcl = mcl;
  }

  @Override
  public Verticle createVerticle(final String main) throws Exception {

    return new Verticle() {

      @Override
      public void start() throws Exception {
        JsonObject config = manager.getConfig();
        String foo = config.getString("foo", "bar");
        if (foo.equalsIgnoreCase("bar")) {
          throw new Exception("foo must not be bar!");
        }
        if (!(main.startsWith("foo:") || main.endsWith("foo"))) {
          throw new Exception("main must either start or end with foo!");
        }
      }};
  }

  @Override
  public void reportException(Throwable t) {
    t.printStackTrace();
  }

  public void close() {
  }

}
