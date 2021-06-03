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

package io.vertx.core;


import java.util.List;

/**
 * verticle group is a list of verticle that share the same context.
 * Usage example:
 * <pre>
 * {@code
 *  public class CompositeVerticleGroup extends AbstractVerticleGroup {
 *   @Override
 *   public List<Verticle> verticles() {
 *     return Arrays.asList(new VerticleA(), new VerticleB(), new VerticleC());
 *   }
 * }
 * }
 * </pre>
 * and then vertx.deploy(CompositeVerticleGroup).
 * <p>
 * Most of the time you don't use it directly, you should use {@link AbstractVerticleGroup}
 * <p>
 * methods are executed in the order:
 *  <ol>
 *   <li>VerticleGroup {@link #init(Vertx, Context)}</li>
 *   <li>VerticleGroup {@link #start(Promise)}</li>
 *   <li>VerticleGroup {@link #verticles()} executed in the {@link #start(Promise)}</li>
 *   <li>{@link #init(Vertx, Context)} and {@link #start(Promise)} that executed each verticle in the {@link #verticles()} sequentially</li>
 *   <li>VerticleGroup {@link #stop(Promise)}</li>
 *   <li>{@link #stop(Promise)} that executed each verticle in the {@link #verticles()} sequentially</li>
 * </ol>
 *
 * @author <a href="https://wang007.github.io">wang007</a>
 * @see Verticle
 * @see AbstractVerticleGroup
 */
public interface VerticleGroup extends Verticle {

  /**
   * Get list verticle to deployed.
   * The order of verticle to deployed is based on the order of list
   * This method is called by VerticleGroup impl when the instance is deployed. You do not call it yourself.
   *
   * @return list verticle to deployed
   */
  List<Verticle> verticles();

  /**
   * Returns ture that async deployed all {@link #verticles()}
   * and otherwise to deployed {@link #verticles()} one by one on complete
   *
   * @return async deployed all vertilces?
   */
  default boolean asyncDeploy() {
    return true;
  }

  /**
   * Returns ture that async undeploy all {@link #verticles()}
   * and otherwise to undeploy {@link #verticles()} one by one on complete
   *
   * @return async undeploy all vertilces?
   */
  default boolean asyncUndeploy() {
    return true;
  }
}
