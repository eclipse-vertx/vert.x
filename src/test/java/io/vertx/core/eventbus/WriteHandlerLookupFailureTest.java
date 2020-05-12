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

import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.core.impl.VertxFactory;
import io.vertx.core.spi.cluster.NodeSelector;
import io.vertx.core.spi.cluster.impl.DefaultNodeSelector;
import io.vertx.test.core.VertxTestBase;
import org.junit.Test;

/**
 * @author Julien Viet
 */
public final class WriteHandlerLookupFailureTest extends VertxTestBase {

  @Test
  public void test() {
    Throwable cause = new Throwable();
    VertxOptions options = new VertxOptions();
    options.getEventBusOptions()
      .setHost("localhost")
      .setPort(0);
    vertices = new Vertx[1];
    NodeSelector nodeSelector = new DefaultNodeSelector() {
      @Override
      public void selectForSend(Message<?> message, Promise<String> promise) {
        promise.fail(cause);
      }

      @Override
      public void selectForPublish(Message<?> message, Promise<Iterable<String>> promise) {
        promise.fail("Not implemented");
      }
    };
    new VertxFactory(options).clusterNodeSelector(nodeSelector).clusteredVertx(onSuccess(node -> {
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
