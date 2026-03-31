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

import io.vertx.core.dns.DnsClient;
import io.vertx.core.dns.DnsClientOptions;
import io.vertx.test.core.TestUtils;
import io.vertx.test.core.VertxTestBase;
import io.vertx.test.fakedns.MockDnsServer;
import io.vertx.test.netty.TestLoggerFactory;
import org.junit.Test;

public class DnsTest extends VertxTestBase {

  @Test
  public void testDoNotLogActivity() throws Exception {
    MockDnsServer mockDnsServer = new MockDnsServer(vertx);
    mockDnsServer.start();
    try {
      DnsClient client = vertx.createDnsClient(new DnsClientOptions(new DnsClientOptions().setLogActivity(false))
        .setPort(MockDnsServer.PORT)
        .setHost(MockDnsServer.IP_ADDRESS));

      final String ip = "10.0.0.1";
      mockDnsServer.testResolveA(ip);
      TestUtils.testLogging(factory -> {
        try {
          client
            .resolveA(ip)
            .onComplete(fut -> testComplete());
          await();
        } catch (Exception e) {
          fail(e);
        }
        assertFalse(factory.hasName("io.netty.handler.logging.LoggingHandler"));
      });
    } finally {
      mockDnsServer.stop();
    }
  }
}
