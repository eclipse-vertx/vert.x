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
package io.vertx.tests.http;

import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.http.HttpClientConfig;
import org.junit.Test;

import java.util.List;

import static io.vertx.core.http.HttpVersion.*;
import static org.junit.Assert.*;

public class HttpClientConfigTest {

  @Test
  public void testDefaultConfig() {
    HttpClientConfig config = new HttpClientConfig();
    assertNull(config.getHttp1Config());
    assertNull(config.getHttp2Config());
    assertNull(config.getHttp3Config());
    assertFalse(config.isSsl());
    assertEquals(List.of(HTTP_1_1, HTTP_2), config.getVersions());
  }

  @Test
  public void testFromDefaultOptions() {
    HttpClientConfig config = new HttpClientConfig(new HttpClientOptions());
    assertNotNull(config.getHttp1Config());
    assertNotNull(config.getHttp2Config());
    assertNotNull(config.getHttp3Config());
    assertFalse(config.isSsl());
    assertEquals(List.of(HTTP_1_1, HTTP_2), config.getVersions());
  }

  @Test
  public void testFromHttp2Options() {
    HttpClientConfig config = new HttpClientConfig(new HttpClientOptions().setProtocolVersion(HTTP_2));
    assertNotNull(config.getHttp1Config());
    assertNotNull(config.getHttp2Config());
    assertNotNull(config.getHttp3Config());
    assertFalse(config.isSsl());
    assertEquals(List.of(HTTP_2, HTTP_1_1), config.getVersions());
  }

  @Test
  public void testFromHttp1_0Options() {
    HttpClientConfig config = new HttpClientConfig(new HttpClientOptions().setProtocolVersion(HTTP_1_0));
    assertNotNull(config.getHttp1Config());
    assertNotNull(config.getHttp2Config());
    assertNotNull(config.getHttp3Config());
    assertFalse(config.isSsl());
    assertEquals(List.of(HTTP_1_0), config.getVersions());
  }
}
