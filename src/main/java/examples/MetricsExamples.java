/*
 * Copyright (c) 2011-2014 The original author or authors
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

package examples;

import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.core.http.HttpServer;
import io.vertx.core.json.JsonObject;
import io.vertx.core.metrics.MetricsOptions;

import java.util.Map;

/**
 * @author <a href="mailto:nscavell@redhat.com">Nick Scavelli</a>
 */
public class MetricsExamples {

  public void example1(Vertx vertx) {
    Map<String, JsonObject> metrics = vertx.metrics();
    metrics.forEach((name, metric) -> {
      System.out.println(name + " : " + metric.encodePrettily());
    });
  }

  public void example2() {
    Vertx vertx = Vertx.vertx(
        new VertxOptions().setMetricsOptions(
            new MetricsOptions().setEnabled(true))
    );
  }

  public void example3(Vertx vertx) {
    HttpServer server = vertx.createHttpServer();
    // set up server
    Map<String, JsonObject> metrics = server.metrics();
  }
}
