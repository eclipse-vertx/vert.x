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
package io.vertx.it.networklogging;

import io.vertx.core.buffer.Buffer;
import io.vertx.core.datagram.DatagramSocket;
import io.vertx.core.datagram.DatagramSocketOptions;
import io.vertx.test.core.TestUtils;
import io.vertx.test.core.VertxTestBase;
import io.vertx.test.netty.TestLoggerFactory;
import org.junit.Test;

import java.util.function.Predicate;

public class DatagramTest extends VertxTestBase {

  private void testLogging(DatagramSocketOptions sendOptions,
                           DatagramSocketOptions listenOptions,
                           Predicate<TestLoggerFactory> checker) throws Exception {
    TestUtils.testLogging(factory -> {
      DatagramSocket peer1 = vertx.createDatagramSocket(sendOptions);
      DatagramSocket peer2 = vertx.createDatagramSocket(listenOptions);
      try {
        peer2.exceptionHandler(t -> fail(t.getMessage()));
        peer2.listen(1234, "127.0.0.1").await();
        Buffer buffer = TestUtils.randomBuffer(128);
        peer2.handler(packet -> {
          assertEquals(buffer, packet.data());
          testComplete();
        });
        peer1.send(buffer, 1234, "127.0.0.1").await();
        await();
      } finally {
        peer1.close().await();
        peer2.close().await();
      }
      assertTrue(checker.test(factory));
    });
  }

  @Test
  public void testNoLogging() throws Exception {
    testLogging(new DatagramSocketOptions(), new DatagramSocketOptions(), factory -> !factory.hasName("io.netty.handler.logging.LoggingHandler"));
  }

  @Test
  public void testSendLogging() throws Exception {
    testLogging(new DatagramSocketOptions().setLogActivity(true), new DatagramSocketOptions(), factory -> factory.hasName("io.netty.handler.logging.LoggingHandler"));
  }

  @Test
  public void testListenLogging() throws Exception {
    testLogging(new DatagramSocketOptions(), new DatagramSocketOptions().setLogActivity(true), factory -> factory.hasName("io.netty.handler.logging.LoggingHandler"));
  }

}
