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
    vertx.deployVerticle("java:" + OtherSourceVerticle.class.getName().replace('.', '/') + ".java", new DeploymentOptions(), ar -> {
      if (ar.succeeded()) {
        startFuture.complete((Void) null);
      } else {
        ar.cause().printStackTrace();
      }
    });
  }
}
