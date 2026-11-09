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
package io.vertx.tests.http.decoder;

import io.vertx.core.transport.Transport;
import io.vertx.test.core.VertxTestBase;
import io.vertx.tests.http.http3.Http3Configurator;
import org.junit.Assume;
import org.junit.Test;

public class Http3ContentDecoderTest extends ContentDecoderTest {

  private static final String IO_URING_RETENTION = "With io_uring, the server QUIC stream retains its last read buffer, " +
    "the chunks are slices of, until the connection closes";

  public Http3ContentDecoderTest() {
    super(Http3Configurator.INSTANCE);
  }

  @Test
  @Override
  public void testChunkReleasedAfterHandle() throws Exception {
    Assume.assumeFalse(IO_URING_RETENTION, VertxTestBase.TRANSPORT == Transport.IO_URING);
    super.testChunkReleasedAfterHandle();
  }

  @Test
  @Override
  public void testRetainedChunkOutlivesHandle() throws Exception {
    Assume.assumeFalse(IO_URING_RETENTION, VertxTestBase.TRANSPORT == Transport.IO_URING);
    super.testRetainedChunkOutlivesHandle();
  }
}
