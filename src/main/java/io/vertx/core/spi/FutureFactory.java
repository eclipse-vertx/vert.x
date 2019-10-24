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

import io.vertx.core.Future;
import io.vertx.core.Promise;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public interface FutureFactory {

  <T> Promise<T> promise();

  <T> Future<T> succeededFuture();

  <T> Future<T> succeededFuture(T result);

  <T> Future<T> failedFuture(Throwable t);

  <T> Future<T> failureFuture(String failureMessage);
}
