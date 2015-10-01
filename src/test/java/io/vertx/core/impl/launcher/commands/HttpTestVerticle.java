/*
 *  Copyright (c) 2011-2013 The original author or authors
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
package io.vertx.core.impl.launcher.commands;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.json.JsonObject;


public class HttpTestVerticle extends AbstractVerticle {


  @Override
  public void start() throws Exception {
    long time = System.nanoTime();
    vertx.createHttpServer().requestHandler(request -> {
      JsonObject json = new JsonObject();
      json
          .put("clustered", vertx.isClustered())
          .put("metrics", vertx.isMetricsEnabled())
          .put("id", System.getProperty("vertx.id", "no id"))
          .put("conf", config())
          .put("startTime", time);

      if (System.getProperty("foo") != null) {
        json.put("foo", System.getProperty("foo"));
      }

      if (System.getProperty("baz") != null) {
        json.put("baz", System.getProperty("baz"));
      }

      request.response().putHeader("content-type", "application/json").end(json.encodePrettily());
    }).listen(8080);
  }
}
