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
package io.vertx.tests.http;

import io.vertx.core.http.HttpServerOptions;
import org.junit.Ignore;
import org.junit.Test;

public class Http2MultiplexServerTest extends Http2ServerTest {

  @Override
  protected HttpServerOptions createBaseServerOptions() {
    return super.createBaseServerOptions().setHttp2MultiplexImplementation(true);
  }

  @Test
  @Ignore
  @Override
  public void testPromiseStreamError() throws Exception {
    // Ignore
  }

  @Test
  @Ignore
  @Override
  public void testPushPromiseClearText() throws Exception {
    // Ignore
  }

  @Test
  @Ignore
  @Override
  public void testPushPromise() throws Exception {
    // Ignore
  }

  @Test
  @Ignore
  @Override
  public void testPushPromiseHeaders() throws Exception {
    // Ignore
  }

  @Test
  @Ignore
  @Override
  public void testPushPromiseNoAuthority() throws Exception {
    // Ignore
  }

  @Test
  @Ignore
  @Override
  public void testPushPromiseOverrideAuthority() throws Exception {
    // Ignore
  }

  @Test
  @Ignore
  @Override
  public void testResetActivePushPromise() throws Exception {
    // Ignore
  }

  @Test
  @Ignore
  @Override
  public void testQueuePushPromise() throws Exception {
    // Ignore
  }

  @Test
  @Ignore
  @Override
  public void testResetPendingPushPromise() throws Exception {
    // Ignore
  }

  @Test
  @Ignore
  @Override
  public void testStreamPriority() throws Exception {
    // Ignore
  }

  @Test
  @Ignore
  @Override
  public void testStreamPriorityChange() throws Exception {
    // Ignore
  }

  @Test
  @Ignore
  @Override
  public void testStreamPriorityNoChange() throws Exception {
    // Ignore
  }

  @Test
  @Ignore
  @Override
  public void testResponseCompressionEnabledButResponseAlreadyCompressed() throws Exception {
    // This behavior cannot be really feasible without hinting the encoder using a specific pass-through response header
  }
}
