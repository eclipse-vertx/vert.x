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

package io.vertx.core.impl;

import io.vertx.core.Future;
import io.vertx.core.spi.FutureFactory;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class FutureFactoryImpl implements FutureFactory {

  @Override
  public <T> Future<T> future() {
    return new FutureImpl<T>();
  }

  // TODO - for completed futures with null values we could maybe reuse a static instance to save allocation
  @Override
  public <T> Future<T> completedFuture() {
    return new FutureImpl<T>((T)null);
  }

  @Override
  public <T> Future<T> completedFuture(T result) {
    return new FutureImpl<T>(result);
  }

  @Override
  public <T> Future<T> completedFuture(Throwable t) {
    return new FutureImpl<T>(t);
  }

  @Override
  public <T> Future<T> completedFuture(String failureMessage, boolean failed) {
    return new FutureImpl<>(failureMessage, true);
  }
}
