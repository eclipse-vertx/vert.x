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
package io.vertx.tests.http.encoder;

import io.vertx.test.core.Checkpoint;
import io.vertx.tests.http.http3.Http3Configurator;
import org.junit.Ignore;
import org.junit.Test;

public class Http3ServerContentEncoderTest extends ServerContentEncoderTest {

  public Http3ServerContentEncoderTest() {
    super(Http3Configurator.INSTANCE);
  }

  @Test
  @Ignore("Write after end throws UnsupportedOperationException: handle me")
  @Override
  public void testWriteAfterEndReleasesBuffers(Checkpoint checkpoint1, Checkpoint checkpoint2) throws Exception {
    super.testWriteAfterEndReleasesBuffers(checkpoint1, checkpoint2);
  }

  @Test
  @Ignore("The server stream does not tear down its output on the STOP_SENDING triggered by the client reset: " +
    "the queued writes remain parked behind the exhausted flow control window")
  @Override
  public void testRemoteResetReleasesQueuedBuffers() throws Exception {
    super.testRemoteResetReleasesQueuedBuffers();
  }
}
