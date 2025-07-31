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
package io.vertx.tests.http;

import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.http.HttpServerOptions;
import org.junit.Ignore;
import org.junit.Test;

public class Http2MultiplexTest extends Http2Test {

  @Override
  protected HttpServerOptions createBaseServerOptions() {
    return super.createBaseServerOptions().setHttp2MultiplexImplementation(true);
  }

  @Override
  protected HttpClientOptions createBaseClientOptions() {
    return super.createBaseClientOptions().setHttp2MultiplexImplementation(true);
  }

  @Test
  @Ignore
  @Override
  public void testStreamPriority() throws Exception {
    super.testStreamPriority();
  }

  @Test
  @Ignore
  @Override
  public void testStreamPriorityChange() throws Exception {
    super.testStreamPriorityChange();
  }

  @Test
  @Ignore
  @Override
  public void testServerStreamPriorityNoChange() throws Exception {
    super.testServerStreamPriorityNoChange();
  }

  @Test
  @Ignore
  @Override
  public void testClientStreamPriorityNoChange() throws Exception {
    super.testClientStreamPriorityNoChange();
  }

  @Test
  @Ignore
  @Override
  public void testStreamPriorityInheritance() throws Exception {
    super.testStreamPriorityInheritance();
  }

  @Test
  @Ignore
  @Override
  public void testDefaultPriority() throws Exception {
    super.testDefaultPriority();
  }

  @Test
  @Ignore
  @Override
  public void testStreamPriorityPushPromise() throws Exception {
    super.testStreamPriorityPushPromise();
  }

  @Test
  @Ignore
  @Override
  public void testStreamPriorityInheritancePushPromise() throws Exception {
    super.testStreamPriorityInheritancePushPromise();
  }

  @Test
  @Ignore
  @Override
  public void testConnectionCloseEvictsConnectionFromThePoolBeforeStreamsAreClosed() throws Exception {
    super.testConnectionCloseEvictsConnectionFromThePoolBeforeStreamsAreClosed();
  }
}
