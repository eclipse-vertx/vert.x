/*
 * Copyright (c) 2011-2019 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */

package io.vertx.core.spi;

import io.vertx.core.*;

import java.util.concurrent.Callable;

/**
 *
 * Has responsibility for creating verticle instances.
 * <p>
 * Implementations of this are responsible for creating verticle written in various different JVM languages and
 * for other purposes.
 *
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public interface VerticleFactory {

  /**
   * Helper method to remove a prefix from an identifier string
   * @param identifer the identifier
   * @return  The identifier without the prefix (if it had any)
   */
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

  /**
   * The order of the factory. If there is more than one matching verticle they will be tried in ascending order.
   * @return  the order
   */
  default int order() {
    return 0;
  }

  /**
   * Initialise the factory
   * @param vertx  The Vert.x instance
   */
  default void init(Vertx vertx) {
  }

  /**
   * Close the factory. The implementation must release all resources.
   */
  default void close() {
  }

  /**
   * @return  The prefix for the factory, e.g. "java", or "js".
   */
  String prefix();

  /**
   * Create a verticle instance. If this method is likely to be slow then make sure it is run on a
   * worker thread by {@link Vertx#executeBlocking}.
   *
   * @param verticleName  The verticle name
   * @param classLoader  The class loader
   * @param promise the promise to complete with the result
   * @deprecated deprecated, instead implement {@link #createVerticle2(String, ClassLoader, Promise)}
   */
  @Deprecated
  default void createVerticle(String verticleName, ClassLoader classLoader, Promise<Callable<Verticle>> promise) {
    promise.fail("Should not be called, now deploys deployable");
  }

  /**
   * Create a verticle instance. If this method is likely to be slow then make sure it is run on a
   * worker thread by {@link Vertx#executeBlocking}.
   *
   * @param verticleName  The verticle name
   * @param classLoader  The class loader
   * @param promise the promise to complete with the result
   */
  default void createVerticle2(String verticleName, ClassLoader classLoader, Promise<Callable<? extends Deployable>> promise) {
    Promise<Callable<Verticle>> p = Promise.promise();
    createVerticle(verticleName, classLoader, p);
    Future<Callable<Verticle>> fut = p.future();
    Future<Callable<? extends Deployable>> f = fut.map(callable -> callable);
    fut.onComplete(promise);
  }
}
