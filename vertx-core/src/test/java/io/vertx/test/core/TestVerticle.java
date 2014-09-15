/*
 * Copyright 2014 Red Hat, Inc.
 *
 * Red Hat licenses this file to you under the Apache License, version 2.0
 * (the "License"); you may not use this file except in compliance with the
 * License.  You may obtain a copy of the License at:
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the specific language governing permissions and limitations
 * under the License.
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

  public TestVerticle() {
  }

  @Override
  public void start() throws Exception {
    processArgs = vertx.context().processArgs();
    vertx.eventBus().send("testcounts",
      new JsonObject().putString("deploymentID", vertx.context().deploymentID()).putNumber("count", instanceCount.incrementAndGet()));
  }

  @Override
  public void stop() throws Exception {
  }
}
