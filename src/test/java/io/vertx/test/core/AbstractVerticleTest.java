/*
 * Copyright (c) 2011-2017 The original author or authors
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * and Apache License v2.0 which accompanies this distribution.
 *
 *     The Eclipse Public License is available at
 *     https://www.eclipse.org/legal/epl-2.0/
 *
 *     The Apache License v2.0 is available at
 *     http://www.apache.org/licenses/LICENSE-2.0
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
