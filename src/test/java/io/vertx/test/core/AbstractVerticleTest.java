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

package io.vertx.test.core;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.json.JsonObject;
import org.junit.Test;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class AbstractVerticleTest extends VertxTestBase {

  MyAbstractVerticle verticle = new MyAbstractVerticle();

  @Test
  public void testFieldsSet() {
    JsonObject config = new JsonObject().put("foo", "bar");
    vertx.deployVerticle(verticle, new DeploymentOptions().setConfig(config), onSuccess(res -> {
      assertEquals(res, verticle.getDeploymentID());
      assertEquals(config, verticle.getConfig());
      testComplete();
    }));
    await();
  }

  class MyAbstractVerticle extends AbstractVerticle {

    public void start() {

    }

    public String getDeploymentID() {
      return deploymentID();
    }

    public JsonObject getConfig() {
      return config();
    }
  }
}
