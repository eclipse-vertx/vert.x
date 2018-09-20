/*
 * Copyright (c) 2011-2017 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */

package io.vertx.core;

import io.vertx.core.json.JsonObject;
import io.vertx.test.core.VertxTestBase;
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
