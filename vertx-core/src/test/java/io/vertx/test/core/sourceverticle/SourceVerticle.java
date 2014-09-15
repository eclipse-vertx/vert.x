/*
 * Copyright 2014 Red Hat, Inc.
 *
 *   Red Hat licenses this file to you under the Apache License, version 2.0
 *   (the "License"); you may not use this file except in compliance with the
 *   License.  You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 *   WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 *   License for the specific language governing permissions and limitations
 *   under the License.
 */

package io.vertx.test.core.sourceverticle;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Future;
import io.vertx.test.core.sourceverticle.somepackage.OtherSourceVerticle;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class SourceVerticle extends AbstractVerticle {


  @Override
  public void start(Future<Void> startFuture) throws Exception {
    vertx.deployVerticle("java:" + OtherSourceVerticle.class.getName().replace('.', '/') + ".java", DeploymentOptions.options(), ar -> {
      if (ar.succeeded()) {
        startFuture.complete((Void) null);
      } else {
        ar.cause().printStackTrace();
      }
    });
  }
}
