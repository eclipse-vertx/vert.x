/*
 *  Copyright (c) 2011-2015 The original author or authors
 *  ------------------------------------------------------
 *  All rights reserved. This program and the accompanying materials
 *  are made available under the terms of the Eclipse Public License v1.0
 *  and Apache License v2.0 which accompanies this distribution.
 *
 *       The Eclipse Public License is available at
 *       http://www.eclipse.org/legal/epl-v10.html
 *
 *       The Apache License v2.0 is available at
 *       http://www.opensource.org/licenses/apache2.0.php
 *
 *  You may elect to redistribute this code under either of these licenses.
 */

package io.vertx.test.spi;

import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.spi.NamedThreadPoolFactory;

import java.util.HashMap;
import java.util.Map;

/**
 * A dummy implementation of the {@link NamedThreadPoolFactory} SPI.
 *
 * @author <a href="http://escoffier.me">Clement Escoffier</a>
 */
public class NamedThreadPoolFactoryImpl implements NamedThreadPoolFactory {

  private Map<String, Integer> executors = new HashMap<>();


  @Override
  public void configure(Vertx vertx, JsonObject config) {
    JsonArray array = config.getJsonArray("pools");
    if (array != null && !array.isEmpty()) {
      for (int i = 0; i < array.size(); i++) {
        JsonObject poolConfiguration = array.getJsonObject(i);
        String name = poolConfiguration.getString("name");
        int size = poolConfiguration.getInteger("size", 25);
        executors.put(name, size);
      }
    }
  }

  @Override
  public Map<String, Integer> getNamedThreadPools() {
    return executors;
  }
}
