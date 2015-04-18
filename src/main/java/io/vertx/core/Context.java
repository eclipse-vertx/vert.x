/*
 * Copyright (c) 2011-2013 The original author or authors
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

package io.vertx.core;

import io.vertx.codegen.annotations.VertxGen;
import io.vertx.core.json.JsonObject;

import java.util.List;

/**
 * The execution context of a {@link io.vertx.core.Handler} execution.
 * <p>
 * When Vert.x provides an event to a handler or calls the start or stop methods of a {@link io.vertx.core.Verticle},
 * the execution is associated with a {@code Context}.
 * <p>
 * Usually a context is an *event-loop context* and is tied to a specific event loop thread. So executions for that
 * context always occur on that exact same event loop thread.
 * <p>
 * In the case of worker verticles and running inline blocking code a worker context will be associated with the execution
 * which will use a thread from the worker thread pool.
 * <p>
 * When a handler is set by a thread associated with a specific context, the Vert.x will guarantee that when that handler
 * is executed, that execution will be associated with the same context.
 * <p>
 * If a handler is set by a thread not associated with a context (i.e. a non Vert.x thread). Then a new context will
 * be created for that handler.
 * <p>
 * In other words, a context is propagated.
 * <p>
 * This means that when a verticle is deployed, any handlers it sets will be associated with the same context - the context
 * of the verticle.
 * <p>
 * This means (in the case of a standard verticle) that the verticle code will always be executed with the exact same
 * thread, so you don't have to worry about multi-threaded acccess to the verticle state and you can code your application
 * as single threaded.
 * <p>
 * This class also allows arbitrary data to be {@link #put} and {@link #get} on the context so it can be shared easily
 * amongst different handlers of, for example, a verticle instance.
 * <p>
 * This class also provides {@link #runOnContext} which allows an action to be executed asynchronously using the same context.
 *
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
@VertxGen
public interface Context {

  /**
   * Run the specified action asynchronously on the same context, some time after the current execution has completed.
   *
   * @param action  the action to run
   */
  void runOnContext(Handler<Void> action);

  /**
   * If the context is associated with a Verticle deployment, this returns the deployment ID of that deployment.
   *
   * @return the deployment ID of the deployment or null if not a Verticle deployment
   */
  String deploymentID();

  /**
   * If the context is associated with a Verticle deployment, this returns the configuration that was specified when
   * the verticle was deployed.
   *
   * @return the configuration of the deployment or null if not a Verticle deployment
   */
  JsonObject config();

  /**
   * The process args
   */
  List<String> processArgs();

  /**
   * @return true if this is an event loop context, false otherwise
   */
  boolean isEventLoopContext();

  /**
   * @return true if this is an worker context, false otherwise
   */
  boolean isWorker();

  /**
   * @return true if this is a multi-threaded worker context, false otherwise
   */
  boolean isMultiThreaded();

  /**
   * Get some data from the context.
   *
   * @param key  the key of the data
   * @param <T>  the type of the data
   * @return the data
   */
  <T> T get(String key);

  /**
   * Put some data in the context.
   * <p>
   * This can be used to share data between different handlers that share a context
   *
   * @param key  the key of the data
   * @param value  the data
   */
  void put(String key, Object value);

  /**
   * Remove some data from the context.
   *
   * @param key  the key to remove
   * @return true if removed successfully, false otherwise
   */
  boolean remove(String key);

  /**
   * Get the number of instances of the verticle that were started
   */
  int getInstanceCount();

}
