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
import io.vertx.core.Future;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class RedeployVerticle2 extends AbstractVerticle {

  @Override
  public void start(Future<Void> startFuture) throws Exception {

    // The redeploy setting should be ignored on this as its not a top level verticle
    vertx.deployVerticle(RedeployVerticle.class.getName(), new DeploymentOptions().setRedeploy(true), res -> {
      if (res.succeeded()) {
        startFuture.complete();
      } else {
        startFuture.fail(res.cause());
      }
    });
  }
}
