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
import io.vertx.core.shareddata.LocalMap;
import io.vertx.core.shareddata.Shareable;

import java.util.function.Function;

class SharedClientHolder<C> implements Shareable {

  static <C> C createSharedClient(Vertx vertx, String clientKey, String clientName, CloseFuture closeFuture, Function<CloseFuture, C> supplier) {
    LocalMap<String, SharedClientHolder<C>> localMap = vertx.sharedData().getLocalMap(clientKey);
    SharedClientHolder<C> v = localMap.compute(clientName, (key, value) -> {
      if (value == null) {
        Hook<C> hook = new Hook<>(vertx, clientKey, clientName);
        C client = supplier.apply(hook.closeFuture);
        return new SharedClientHolder<>(hook, 1, client);
      } else {
        return new SharedClientHolder<>(value.hook, value.count + 1, value.client);
      }
    });
    C client = v.client;
    closeFuture.add(v.hook);
    return client;
  }

  final Hook<C> hook;
  final int count;
  final C client;

  SharedClientHolder(Hook<C> hook, int count, C client) {
    this.hook = hook;
    this.count = count;
    this.client = client;
  }

  private static class Hook<C> implements Closeable {

    private final Vertx vertx;
    private final CloseFuture closeFuture;
    private final String clientKey;
    private final String clientName;

    private Hook(Vertx vertx, String clientKey, String clientName) {
      this.vertx = vertx;
      this.closeFuture = new CloseFuture();
      this.clientKey = clientKey;
      this.clientName = clientName;
    }

    @Override
    public void close(Promise<Void> completion) {
      LocalMap<String, SharedClientHolder<C>> localMap1 = vertx.sharedData().getLocalMap(clientKey);
      SharedClientHolder<C> res = localMap1.compute(clientName, (key, value) -> {
        if (value == null) {
          return null; // Should never happen unless bug
        } else if (value.count == 1) {
          return null;
        } else {
          return new SharedClientHolder<>(this, value.count - 1, value.client);
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
