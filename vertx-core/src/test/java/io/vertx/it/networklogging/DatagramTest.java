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

public class DatagramTest extends VertxTestBase {

  private TestLoggerFactory testLogging(DatagramSocketOptions sendOptions, DatagramSocketOptions listenOptions) throws Exception {
    return TestUtils.testLogging(() -> {
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
    });
  }

  @Test
  public void testNoLogging() throws Exception {
    TestLoggerFactory factory = testLogging(new DatagramSocketOptions(), new DatagramSocketOptions());
    assertFalse(factory.hasName("io.netty.handler.logging.LoggingHandler"));
  }

  @Test
  public void testSendLogging() throws Exception {
    TestLoggerFactory factory = testLogging(new DatagramSocketOptions().setLogActivity(true), new DatagramSocketOptions());
    assertTrue(factory.hasName("io.netty.handler.logging.LoggingHandler"));
  }

  @Test
  public void testListenLogging() throws Exception {
    TestLoggerFactory factory = testLogging(new DatagramSocketOptions(), new DatagramSocketOptions().setLogActivity(true));
    assertTrue(factory.hasName("io.netty.handler.logging.LoggingHandler"));
  }

}
