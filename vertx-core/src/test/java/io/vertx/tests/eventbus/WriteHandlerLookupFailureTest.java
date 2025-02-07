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

package io.vertx.tests.eventbus;

import io.vertx.core.Completable;
import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.core.eventbus.MessageProducer;
import io.vertx.core.impl.VertxBootstrapImpl;
import io.vertx.core.internal.VertxBootstrap;
import io.vertx.core.eventbus.impl.clustered.DefaultNodeSelector;
import io.vertx.core.eventbus.impl.clustered.NodeSelector;
import io.vertx.test.core.VertxTestBase;
import io.vertx.test.fakecluster.FakeClusterManager;
import org.junit.Test;

import java.util.Collections;

/**
 * @author Julien Viet
 */
public final class WriteHandlerLookupFailureTest extends VertxTestBase {

  private Vertx vertx;

  @Test
  public void test() {
    Throwable cause = new Throwable();
    VertxOptions options = new VertxOptions();
    options.getEventBusOptions()
      .setHost("localhost")
      .setPort(0);
    NodeSelector nodeSelector = new DefaultNodeSelector() {
      @Override
      public void selectForSend(String address, Completable<String> promise) {
        promise.fail(cause);
      }

      @Override
      public void selectForPublish(String address, Completable<Iterable<String>> promise) {
        promise.fail("Not implemented");
      }
    };
    ((VertxBootstrapImpl)VertxBootstrap.create().options(options).init())
      .clusterManager(new FakeClusterManager())
      .clusterNodeSelector(nodeSelector)
      .clusteredVertx().onComplete(onSuccess(node -> {
      vertx = node;
      MessageProducer<String> sender = vertx.eventBus().sender("foo");
      sender.write("the_string").onComplete(onFailure(err -> {
        assertSame(cause, err);
        testComplete();
      }));
    }));
    await();
  }

  @Override
  protected void tearDown() throws Exception {
    try {
      if (vertx != null) {
        close(Collections.singletonList(vertx));
      }
    } finally {
      super.tearDown();
    }
  }
}
