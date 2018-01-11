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

package io.vertx.test.core;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.json.JsonObject;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class TestVerticle extends AbstractVerticle {

  public static AtomicInteger instanceCount = new AtomicInteger();
  public static List<String> processArgs;
  public static JsonObject conf;

  public TestVerticle() {
  }

  @Override
  public void start() throws Exception {
    processArgs = context.processArgs();
    conf = context.config();
//    if (Thread.currentThread().getContextClassLoader() != getClass().getClassLoader()) {
//      throw new IllegalStateException("Wrong tccl!");
//    }
    vertx.eventBus().send("testcounts",
      new JsonObject().put("deploymentID", context.deploymentID()).put("count", instanceCount.incrementAndGet()));
  }

  @Override
  public void stop() throws Exception {
  }

}
