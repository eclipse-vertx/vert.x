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

import io.vertx.core.Closeable;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.internal.CloseFuture;
import io.vertx.core.shareddata.LocalMap;
import io.vertx.core.shareddata.Shareable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

class SharedResourceHolder<C> implements Shareable {

  static <C> List<C> clearSharedResource(Vertx vertx, String resourceKey) {
    LocalMap<String, SharedResourceHolder<C>> localMap = vertx.sharedData().getLocalMap(resourceKey);
    ArrayList<SharedResourceHolder<C>> values = new ArrayList<>(localMap.values());
    localMap.clear();
    return values.stream().map(sc -> sc.resource).collect(Collectors.toList());
  }

  static <R> R createSharedResource(Vertx vertx, String resourceKey, String resourceName, CloseFuture closeFuture, Function<CloseFuture, R> supplier) {
    LocalMap<String, SharedResourceHolder<R>> localMap = vertx.sharedData().getLocalMap(resourceKey);
    SharedResourceHolder<R> v = localMap.compute(resourceName, (key, value) -> {
      if (value == null) {
        Hook<R> hook = new Hook<>(vertx, resourceKey, resourceName);
        R resource = supplier.apply(hook.closeFuture);
        return new SharedResourceHolder<>(hook, 1, resource);
      } else {
        return new SharedResourceHolder<>(value.hook, value.count + 1, value.resource);
      }
    });
    R resource = v.resource;
    closeFuture.add(v.hook);
    return resource;
  }

  final Hook<C> hook;
  final int count;
  final C resource;

  SharedResourceHolder(Hook<C> hook, int count, C resource) {
    this.hook = hook;
    this.count = count;
    this.resource = resource;
  }

  private static class Hook<C> implements Closeable {

    private final Vertx vertx;
    private final CloseFuture closeFuture;
    private final String resourceKey;
    private final String resourceName;

    private Hook(Vertx vertx, String resourceKey, String resourceName) {
      this.vertx = vertx;
      this.closeFuture = new CloseFuture();
      this.resourceKey = resourceKey;
      this.resourceName = resourceName;
    }

    @Override
    public void close(Promise<Void> completion) {
      LocalMap<String, SharedResourceHolder<C>> localMap1 = vertx.sharedData().getLocalMap(resourceKey);
      SharedResourceHolder<C> res = localMap1.compute(resourceName, (key, value) -> {
        if (value == null) {
          return null; // Should never happen unless bug
        } else if (value.count == 1) {
          return null;
        } else {
          return new SharedResourceHolder<>(this, value.count - 1, value.resource);
        }
      });
      if (res == null) {
        closeFuture.close(completion);
      } else {
        completion.complete();
      }
    }
  }
}
