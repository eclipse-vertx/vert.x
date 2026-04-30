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
package io.vertx.tests.http.http3;

import io.vertx.test.core.Checkpoint;
import io.vertx.tests.http.HttpTest;
import org.junit.Ignore;
import org.junit.Test;

public class Http3Test extends HttpTest {

  public Http3Test() {
    super(Http3Config.INSTANCE);
  }

  @Ignore("Introduce stream cancellation")
  @Test
  @Override
  public void testResetClientRequestAwaitingResponse(Checkpoint checkpoint) {
  }

  @Ignore("Implement compression")
  @Test
  @Override
  public void testClientDecompressionError() {
  }

  @Ignore("Requires fixe of stream cancellation")
  @Test
  @Override
  public void testFollowRedirectPropagatesTimeout(Checkpoint checkpoint) {
  }

  @Ignore()
  @Test
  @Override
  public void testListenInvalidPort() {
  }

  @Ignore()
  @Test
  @Override
  public void testListenInvalidHost() {
  }

  @Ignore("Does it make sense for HTTP/3 ?")
  @Test
  @Override
  public void testCloseMulti() {
  }

  @Ignore("Is this test valid ?")
  @Test
  @Override
  public void testResetClientRequestResponseInProgress(Checkpoint checkpoint) throws Exception {
  }

  @Ignore("Requires to implement client local address")
  @Test
  @Override
  public void testClientLocalAddress() {
  }

  @Ignore("Missing feature")
  @Test
  @Override
  public void testDisableIdleTimeoutInPool(Checkpoint checkpoint) {
  }

  @Ignore("Cannot pass because stream channel does not detect the write failure")
  @Test
  @Override
  public void testCancelPartialClientRequest(Checkpoint checkpoint) throws Exception {
  }

  @Ignore("Cannot pass because stream channel does not detect the write failure")
  @Test
  @Override
  public void testCancelPartialServerResponse(Checkpoint checkpoint1, Checkpoint checkpoint2) throws Exception {
  }
}
