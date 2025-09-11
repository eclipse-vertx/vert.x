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

package io.vertx.tests.net;

import io.vertx.core.json.JsonObject;
import io.vertx.core.net.ProxyOptions;
import io.vertx.core.net.ProxyType;
import io.vertx.test.core.TestUtils;
import io.vertx.test.core.VertxTestBase;
import org.junit.Test;

import java.time.Duration;

import static io.vertx.test.core.TestUtils.assertIllegalArgumentException;
import static io.vertx.test.core.TestUtils.assertNullPointerException;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class ProxyOptionsTest extends VertxTestBase {

  ProxyType randType;
  String randHost;
  int randPort;
  String randUsername;
  String randPassword;
  long randConnectTimeout;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    randType = TestUtils.randomElement(ProxyType.values());
    randHost = TestUtils.randomAlphaString(10);
    randPort = TestUtils.randomPortInt();
    randUsername = TestUtils.randomAlphaString(10);
    randPassword = TestUtils.randomAlphaString(10);
    randConnectTimeout = TestUtils.randomPositiveLong();
  }

  @Test
  public void testProxyOptions() {
    ProxyOptions options = new ProxyOptions();

    assertEquals(ProxyOptions.DEFAULT_TYPE, options.getType());
    assertEquals(options, options.setType(randType));
    assertEquals(randType, options.getType());
    assertNullPointerException(() -> options.setType(null));

    assertEquals(ProxyOptions.DEFAULT_HOST, options.getHost());
    assertEquals(options, options.setHost(randHost));
    assertEquals(randHost, options.getHost());
    assertNullPointerException(() -> options.setHost(null));

    assertEquals(ProxyOptions.DEFAULT_CONNECT_TIMEOUT, options.getConnectTimeout());
    assertEquals(options, options.setConnectTimeout(Duration.ofMillis(randConnectTimeout)));
    assertEquals(randConnectTimeout, options.getConnectTimeout());
    assertIllegalArgumentException(() -> options.setConnectTimeout(Duration.ofMillis(-1)));

    assertEquals(ProxyOptions.DEFAULT_PORT, options.getPort());
    assertEquals(options, options.setPort(randPort));
    assertEquals(randPort, options.getPort());
    assertIllegalArgumentException(() -> options.setPort(-1));
    assertIllegalArgumentException(() -> options.setPort(65536));

    assertEquals(null, options.getUsername());
    assertEquals(options, options.setUsername(randUsername));
    assertEquals(randUsername, options.getUsername());

    assertEquals(null, options.getPassword());
    assertEquals(options, options.setPassword(randPassword));
    assertEquals(randPassword, options.getPassword());
  }

  @Test
  public void testCopyProxyOptions() {
    ProxyOptions options = new ProxyOptions();
    options.setType(randType);
    options.setHost(randHost);
    options.setPort(randPort);
    options.setUsername(randUsername);
    options.setPassword(randPassword);
    options.setConnectTimeout(Duration.ofMillis(randConnectTimeout));

    ProxyOptions copy = new ProxyOptions(options);
    assertEquals(randType, copy.getType());
    assertEquals(randPort, copy.getPort());
    assertEquals(randHost, copy.getHost());
    assertEquals(randUsername, copy.getUsername());
    assertEquals(randPassword, copy.getPassword());
    assertEquals(randConnectTimeout, copy.getConnectTimeout());
  }

  @Test
  public void testDefaultOptionsJson() {
    ProxyOptions def = new ProxyOptions();
    ProxyOptions options = new ProxyOptions(new JsonObject());
    assertEquals(def.getType(), options.getType());
    assertEquals(def.getPort(), options.getPort());
    assertEquals(def.getHost(), options.getHost());
    assertEquals(def.getUsername(), options.getUsername());
    assertEquals(def.getPassword(), options.getPassword());
    assertEquals(def.getConnectTimeout(), options.getConnectTimeout());
  }

  @Test
  public void testOptionsJson() {
    JsonObject json = new JsonObject();
    json.put("type", randType.toString())
        .put("host", randHost)
        .put("port", randPort)
        .put("username", randUsername)
        .put("password", randPassword)
        .put("connectTimeout", randConnectTimeout)
    ;
    ProxyOptions options = new ProxyOptions(json);
    assertEquals(randType, options.getType());
    assertEquals(randPort, options.getPort());
    assertEquals(randHost, options.getHost());
    assertEquals(randUsername, options.getUsername());
    assertEquals(randPassword, options.getPassword());
    assertEquals(randConnectTimeout, options.getConnectTimeout());
  }
}
