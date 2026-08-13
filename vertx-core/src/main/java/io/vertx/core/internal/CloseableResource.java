/*
 * Copyright (c) 2011-2026 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package io.vertx.core.internal;

import io.vertx.core.Future;

import java.time.Duration;

/**
 * Closeable resource.
 *
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public interface CloseableResource<R> extends Closeable {

  static <R extends Closeable> CloseableResource<R> of(R resource) {
    return new CloseableResource<R>() {
      @Override
      public R get() {
        return resource;
      }
      @Override
      public Future<Void> shutdown(Duration timeout) {
        return resource.shutdown(timeout);
      }
    };
  }

  /**
   * @return the actual resource
   */
  R get();

}
