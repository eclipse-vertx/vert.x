/*
 * Copyright (c) 2011-2025 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package io.vertx.tests.net.quic;

import io.vertx.core.internal.VertxInternal;
import io.vertx.core.net.PemKeyCertOptions;
import io.vertx.core.net.impl.quic.TokenManager;
import io.vertx.test.core.Repeat;
import io.vertx.test.core.VertxTestBase;
import io.vertx.test.tls.Cert;
import org.junit.Test;

import java.time.Duration;

public class TokenManagerTest extends VertxTestBase {

  private PemKeyCertOptions pemConf;

  @Override
  public void setUp() throws Exception {
    pemConf = Cert.SERVER_PEM.get();
    super.setUp();
  }

  private TokenManager tokenManager(Duration timeWindow) throws Exception {
    TokenManager tokenManager = new TokenManager((VertxInternal) vertx, timeWindow);
    tokenManager.init(pemConf);
    assertNotNull(tokenManager.signingAlgorithm());
    return tokenManager;
  }

  @Test
  public void testValidation() throws Exception {
    Duration timeWindow = Duration.ofMillis(200);
    TokenManager tokenManager = tokenManager(timeWindow);
    int retries = 0;
    while (true) {
      assertTrue(retries < 3);
      long now = System.currentTimeMillis();
      byte[] token = tokenManager.generateToken("hello".getBytes());
      long target = now + (timeWindow.toMillis() * 80) / 100;
      while (System.currentTimeMillis() < target) {
        Thread.yield();
      }
      if (tokenManager.verify(token)) {
        break;
      } else {
        if ((System.currentTimeMillis() - now) < timeWindow.toMillis()) {
          fail();
        } else {
          retries++;
        }
      }
    }
  }

  @Test
  public void testExpiration() throws Exception {
    Duration timeWindow = Duration.ofMillis(20);
    TokenManager tokenManager = tokenManager(timeWindow);
    byte[] token = tokenManager.generateToken("hello".getBytes());
    Thread.sleep(timeWindow.toMillis() * 2 + 1);
    assertFalse(tokenManager.verify(token));
  }

  @Test
  public void testAlteration() throws Exception {
    Duration timeWindow = Duration.ofSeconds(30);
    TokenManager tokenManager = tokenManager(timeWindow);
    byte[] token = tokenManager.generateToken("hello".getBytes());
    for (int i = 0;i < token.length;i += 2) {
      token[i] = 0;
    }
    assertFalse(tokenManager.verify(token));
  }
}
