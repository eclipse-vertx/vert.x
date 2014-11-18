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
import io.vertx.core.Context;
import io.vertx.core.Future;

import java.util.HashSet;
import java.util.Set;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class TestVerticle2 extends AbstractVerticle {

  private static Set<Context> contexts = new HashSet<>();

  @Override
  public void start() throws Exception {
    synchronized (contexts) {
      if (contexts.contains(context)) {
        throw new IllegalStateException("Same context!");
      } else {
        contexts.add(context);
        vertx.eventBus().send("tvstarted", "started");
      }
    }
  }

  @Override
  public void stop(Future<Void> stopFuture) throws Exception {
    vertx.eventBus().send("tvstopped", "stopped", reply -> {
      stopFuture.complete(null);
    });
  }
}
