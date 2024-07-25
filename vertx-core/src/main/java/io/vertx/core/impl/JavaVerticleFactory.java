/*
 * Copyright (c) 2011-2019 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */

package io.vertx.core.impl;

import io.vertx.core.Promise;
import io.vertx.core.Verticle;
import io.vertx.core.impl.verticle.CompilingClassLoader;
import io.vertx.core.spi.VerticleFactory;

import java.util.concurrent.Callable;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class JavaVerticleFactory implements VerticleFactory {

  @Override
  public String prefix() {
    return "java";
  }

  @Override
  public void createVerticle(String verticleName, ClassLoader classLoader, Promise<Callable<Verticle>> promise) {
    verticleName = VerticleFactory.removePrefix(verticleName);
    Class<Verticle> clazz;
    try {
      if (verticleName.endsWith(".java")) {
        CompilingClassLoader compilingLoader = new CompilingClassLoader(classLoader, verticleName);
        String className = compilingLoader.resolveMainClassName();
        clazz = (Class<Verticle>) compilingLoader.loadClass(className);
      } else {
        clazz = (Class<Verticle>) classLoader.loadClass(verticleName);
      }
    } catch (ClassNotFoundException e) {
      promise.fail(e);
      return;
    }
    promise.complete(() -> clazz.getDeclaredConstructor().newInstance());
  }

}
