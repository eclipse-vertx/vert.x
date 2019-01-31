/*
 * Copyright (c) 2011-2017 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */

package io.vertx.core;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.stream.Collectors;

import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import io.vertx.core.impl.VertxImpl;
import io.vertx.core.net.impl.transport.Transport;

public class VertxNameTest {
  // some test cases did not close vertx instance properly, and leak some vertx thread
  // eg: io.vertx.core.eventbus.LocalEventBusTest.testMessageConsumerCloseHookIsClosedCorrectly
  static List<String> leakedThreadNames;

  @BeforeClass
  public static void setup(){
    leakedThreadNames = Thread.getAllStackTraces().keySet().stream()
      .map(t->t.getName())
      .filter(name->name.startsWith("vert.x-"))
      .sorted()
      .collect(Collectors.toList());
  }

  @Test
  public void defaultName() throws Exception {
    testThreadName(Vertx.vertx(), VertxOptions.DEFAULT_VERTX_NAME);
  }

  @Test
  public void specialName() throws Exception {
    String vertxName = "special-vert.x";
    VertxOptions options = new VertxOptions().setName(vertxName);
    VertxImpl vertx = new VertxImpl(options, Transport.transport(options.getPreferNativeTransport()));
    vertx.init();

    testThreadName(vertx, vertxName);
  }

  private void testThreadName(Vertx vertx, String vertxName) throws Exception {
    String expectThreadNames = String.format("[%s-blocked-thread-checker, %s-eventloop-thread-0, %s-worker-thread-0]", vertxName, vertxName, vertxName);
    vertx.runOnContext(v->{});
    vertx.executeBlocking(v-> {}, f->{});

    List<String> threadNames = Thread.getAllStackTraces().keySet().stream()
      .map(t->t.getName())
      .filter(name->name.startsWith(vertxName))
      .sorted()
      .collect(Collectors.toList());

    leakedThreadNames.forEach(name->threadNames.remove(name));

    Assert.assertEquals(vertxName, vertx.getName());
    Assert.assertEquals(expectThreadNames, threadNames.toString());

    CountDownLatch latch = new CountDownLatch(1);
    vertx.close(ar -> latch.countDown());
    latch.await();
  }
}
