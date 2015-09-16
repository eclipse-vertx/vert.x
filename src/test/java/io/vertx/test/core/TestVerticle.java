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
