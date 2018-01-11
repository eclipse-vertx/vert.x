/*
 * Copyright (c) 2014 Red Hat, Inc. and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */

package io.vertx.core.spi;

import io.vertx.core.DeploymentOptions;
import io.vertx.core.Future;
import io.vertx.core.Verticle;
import io.vertx.core.Vertx;

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
   * Does the factory require resolution? See {@link #resolve(String, DeploymentOptions, ClassLoader, Future)} for more
   * information.
   * @return true if yes
   */
  default boolean requiresResolve() {
    return false;
  }

  /**
   * If the {@link #createVerticle(String, ClassLoader)} method might be slow Vert.x will call it using a worker
   * thread instead of an event loop if this returns true
   * @return true if {@link #createVerticle(String, ClassLoader)} should be called on a worker thread.
   */
  default boolean blockingCreate() {
    return false;
  }

  /**
   * Some verticle factories can "resolve" the identifer to another identifier which is then used to look up the real
   * verticle factory. An Example is the Vert.x service factory which takes an identifier of form `service:com.mycompany.clever-db-service"
   * then looks for a JSON file which it loads to get the real identifier (main verticle).
   *
   * @param identifier  The identifier
   * @param deploymentOptions  The deployment options - these can be changed inside the resolve method (e.g. to add an extra classpath)
   * @param classLoader  The classloader
   * @param resolution  A future which will receive the result of the resolution.
   */
  default void resolve(String identifier, DeploymentOptions deploymentOptions, ClassLoader classLoader, Future<String> resolution) {
    resolution.complete(identifier);
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
   * Create a verticle instance. If this method is likely to be slow (e.g. Ruby or JS verticles which might have to
   * start up a language engine) then make sure it is run on a worker thread by returning `true` from
   * {@link #blockingCreate()}.
   *
   * @param verticleName  The verticle name
   * @param classLoader  The class loader
   * @return  The instance
   * @throws Exception
   */
  Verticle createVerticle(String verticleName, ClassLoader classLoader) throws Exception;

}
