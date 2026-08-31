/*
 * Copyright (c) 2011-2021 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */

package io.vertx.core.impl;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.internal.Closeable;
import io.vertx.core.internal.CloseableResource;
import io.vertx.core.shareddata.LocalMap;
import io.vertx.core.shareddata.Shareable;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;
import java.util.stream.Collectors;

class SharedResourceHolder<R> implements Shareable {

  static <C extends Closeable> List<C> clearSharedResource(Vertx vertx, String resourceKey) {
    LocalMap<String, SharedResourceHolder<C>> localMap = vertx.sharedData().getLocalMap(resourceKey);
    ArrayList<SharedResourceHolder<C>> values = new ArrayList<>(localMap.values());
    localMap.clear();
    return values.stream().map(sc -> sc.resource).collect(Collectors.toList());
  }

  static <R extends Closeable> CloseableResource<R> createSharedResource(Vertx vertx, String resourceKey, String resourceName, Supplier<R> supplier) {
    LocalMap<String, SharedResourceHolder<R>> localMap = vertx.sharedData().getLocalMap(resourceKey);
    SharedResourceHolder<R> v = localMap.compute(resourceName, (key, value) -> {
      if (value == null) {
        R resource = supplier.get();
        return new SharedResourceHolder<>(1, resource);
      } else {
        return new SharedResourceHolder<>(value.count + 1, value.resource);
      }
    });
    R resource = v.resource;
    return new CloseableResource<>() {
      final AtomicBoolean shutdown = new AtomicBoolean();
      @Override
      public R get() {
        return resource;
      }
      @Override
      public Future<Void> shutdown(Duration timeout) {
        if ( (shutdown.compareAndSet(false, true))) {
          LocalMap<String, SharedResourceHolder<R>> localMap1 = vertx.sharedData().getLocalMap(resourceKey);
          SharedResourceHolder<R> res = localMap1.compute(resourceName, (key, value) -> {
            if (value == null) {
              return null; // Should never happen unless bug
            } else if (value.count == 1) {
              return null;
            } else {
              return new SharedResourceHolder<>(value.count - 1, value.resource);
            }
          });
          if (res == null) {
            return resource.shutdown(timeout);
          } else {
            return Future.succeededFuture();
          }
        } else {
          return Future.succeededFuture();
        }
      }
    };
  }

  final int count;
  final R resource;

  SharedResourceHolder(int count, R resource) {
    this.count = count;
    this.resource = resource;
  }
}
