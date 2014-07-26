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

package io.vertx.core.shareddata;

import io.vertx.codegen.annotations.GenIgnore;
import io.vertx.codegen.annotations.VertxGen;
import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;

import java.util.Set;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
@VertxGen
public interface SharedData {

  <K, V> void getClusterWideMap(String name, Handler<AsyncResult<AsyncMap<K, V>>> resultHandler);

  void getLock(String name, Handler<AsyncResult<Lock>> resultHandler);

  void getLockWithTimeout(String name, long timeout, Handler<AsyncResult<Lock>> resultHandler);

  void getCounter(String name, Handler<AsyncResult<Counter>> resultHandler);

  /**
   * Return a {@code Map} with the specific {@code name}. All invocations of this method with the same value of {@code name}
   * are guaranteed to return the same {@code Map} instance. <p>
   */
  @GenIgnore
  <K, V> ConcurrentSharedMap<K, V> getLocalMap(String name);

  /**
   * Return a {@code Set} with the specific {@code name}. All invocations of this method with the same value of {@code name}
   * are guaranteed to return the same {@code Set} instance. <p>
   */
  @GenIgnore
  <E> Set<E> getLocalSet(String name);

  /**
   * Remove the {@code Map} with the specific {@code name}.
   */
  @GenIgnore
  boolean removeLocalMap(Object name);

  /**
   * Remove the {@code Set} with the specific {@code name}.
   */
  @GenIgnore
  boolean removeLocalSet(Object name);

}
