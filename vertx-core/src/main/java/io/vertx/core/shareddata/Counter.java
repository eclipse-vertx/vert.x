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

import io.vertx.codegen.annotations.VertxGen;
import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
@VertxGen
public interface Counter {

  void get(Handler<AsyncResult<Long>> resultHandler);

  void incrementAndGet(Handler<AsyncResult<Long>> resultHandler);

  void getAndIncrement(Handler<AsyncResult<Long>> resultHandler);

  void decrementAndGet(Handler<AsyncResult<Long>> resultHandler);

  void addAndGet(long value, Handler<AsyncResult<Long>> resultHandler);

  void getAndAdd(long value, Handler<AsyncResult<Long>> resultHandler);

  void compareAndSet(long expected, long value, Handler<AsyncResult<Boolean>> resultHandler);
}
