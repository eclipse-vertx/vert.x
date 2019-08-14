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

package io.vertx.core.eventbus;

import io.vertx.core.*;
import io.vertx.core.spi.cluster.AsyncMultiMap;
import io.vertx.core.spi.cluster.ChoosableIterable;
import io.vertx.core.spi.cluster.ClusterManager;
import io.vertx.test.core.VertxTestBase;
import io.vertx.test.fakecluster.FakeClusterManager;
import org.junit.Test;

import java.util.function.Predicate;

/**
 * @author Julien Viet
 */
public final class WriteHandlerLookupFailureTest extends VertxTestBase {

  @Test
  public void test() {
    Throwable cause = new Throwable();
    ClusterManager cm = new FakeClusterManager() {
      @Override
      public <K, V> void getAsyncMultiMap(String name, Handler<AsyncResult<AsyncMultiMap<K, V>>> handler) {
        if ("__vertx.subs".equals(name)) {
          super.<K, V>getAsyncMultiMap(name, ar -> {
            handler.handle(ar.map(map -> {
              return new AsyncMultiMap<K, V>() {
                @Override
                public void add(K k, V v, Handler<AsyncResult<Void>> completionHandler) {
                  map.add(k, v, completionHandler);
                }

                @Override
                public void get(K k, Handler<AsyncResult<ChoosableIterable<V>>> completionHandler) {
                  completionHandler.handle(Future.failedFuture(cause));
                }

                @Override
                public void remove(K k, V v, Handler<AsyncResult<Boolean>> completionHandler) {
                  map.remove(k, v, completionHandler);
                }

                @Override
                public void removeAllForValue(V v, Handler<AsyncResult<Void>> completionHandler) {
                  map.removeAllForValue(v, completionHandler);
                }

                @Override
                public void removeAllMatching(Predicate<V> p, Handler<AsyncResult<Void>> completionHandler) {
                  map.removeAllMatching(p, completionHandler);
                }
              };
            }));
          });
        } else {
          super.getAsyncMultiMap(name, handler);
        }
      }
    };
    VertxOptions options = new VertxOptions().setClusterManager(cm);
    options.getEventBusOptions().setHost("localhost").setPort(0).setClustered(true);
    vertices = new Vertx[1];
    clusteredVertx(options, onSuccess(node -> {
      vertices[0] = node;
    }));
    assertWaitUntil(() -> vertices[0] != null);
    MessageProducer<String> sender = vertices[0].eventBus().sender("foo");
    sender.write("the_string", onFailure(err -> {
      assertSame(cause, err);
      testComplete();
    }));
    await();
  }
}
