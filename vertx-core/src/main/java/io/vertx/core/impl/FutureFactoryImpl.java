/*
 * Copyright 2014 Red Hat, Inc.
 *
 *   Red Hat licenses this file to you under the Apache License, version 2.0
 *   (the "License"); you may not use this file except in compliance with the
 *   License.  You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 *   WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 *   License for the specific language governing permissions and limitations
 *   under the License.
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
}
