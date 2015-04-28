/*
 * Copyright 2014 Red Hat, Inc.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * and Apache License v2.0 which accompanies this distribution.
 *
 * The Eclipse Public License is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * The Apache License v2.0 is available at
 * http://www.opensource.org/licenses/apache2.0.php
 *
 * You may elect to redistribute this code under either of these licenses.
 */

package io.vertx.core.spi;

import io.vertx.core.AsyncResult;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Verticle;
import io.vertx.core.Vertx;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public interface VerticleFactory {

  static String removePrefix(String identifer) {
    int pos = identifer.indexOf(':');
    if (pos != -1) {
      if (pos == identifer.length() - 1) {
        throw new IllegalArgumentException("Invalid identifier: " + identifer);
      }
      return identifer.substring(pos + 1);
    } else {
      return identifer;
    }
  }

  default int order() {
    return 0;
  }

  default boolean requiresResolve() {
    return false;
  }

  default void resolve(String identifier, DeploymentOptions deploymentOptions, ClassLoader classLoader, Future<String> resolution) {
    resolution.complete(identifier);
  }

  default void init(Vertx vertx) {
  }

  default void close() {
  }

  String prefix();

  Verticle createVerticle(String verticleName, ClassLoader classLoader) throws Exception;

}
