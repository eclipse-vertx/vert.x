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

import io.vertx.test.core.Checkpoint;
import org.junit.Ignore;
import org.junit.Test;

public class Http2MultiplexTest extends Http2Test {

  public Http2MultiplexTest() {
    super(true);
  }

  @Test
  @Ignore
  @Override
  public void testStreamWeightAndDependency(Checkpoint checkpoint) throws Exception {
  }

  @Test
  @Ignore
  @Override
  public void testStreamWeightAndDependencyChange(Checkpoint checkpoint1, Checkpoint checkpoint2, Checkpoint checkpoint3, Checkpoint checkpoint4) throws Exception {
  }

  @Test
  @Ignore
  @Override
  public void testServerStreamPriorityNoChange(Checkpoint checkpoint1, Checkpoint checkpoint2) throws Exception {
  }

  @Test
  @Ignore
  @Override
  public void testClientStreamPriorityNoChange(Checkpoint checkpoint1, Checkpoint checkpoint2) throws Exception {
  }

  @Test
  @Ignore
  @Override
  public void testStreamWeightAndDependencyInheritance() throws Exception {
  }

  @Test
  @Ignore
  @Override
  public void testDefaultStreamWeightAndDependency() throws Exception {
  }

  @Test
  @Ignore
  @Override
  public void testStreamWeightAndDependencyPushPromise(Checkpoint checkpoint) throws Exception {
  }

  @Test
  @Ignore
  @Override
  public void testStreamWeightAndDependencyInheritancePushPromise(Checkpoint checkpoint) throws Exception {
  }

  @Test
  @Ignore
  @Override
  public void testConnectionCloseEvictsConnectionFromThePoolBeforeStreamsAreClosed(Checkpoint checkpoint) throws Exception {
  }
}
