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

import io.netty.buffer.ByteBuf;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.buffer.Unpooled;
import io.vertx.core.internal.buffer.BufferInternal;
import io.vertx.test.core.PooledByteBufChecker;
import io.vertx.test.core.TestUtils;
import io.vertx.test.http.HttpConfigurator;
import io.vertx.test.http.SimpleHttpTest2;

/**
 * Base for stream outbound content tests, among which the ByteBuf ownership contract: a buffer
 * passed to {@link io.vertx.core.internal.http.ContentEncoder} is released by the stream whatsoever
 * happens. Any buffer a test allocates with {@link #allocateBuffer(int)} is checked for release
 * when the test ends.
 */
public abstract class ContentEncoderTestBase extends SimpleHttpTest2 {

  protected static final BufferInternal EMPTY_BUFFER = BufferInternal.buffer(Unpooled.EMPTY_BUFFER);

  private final PooledByteBufChecker buffers = new PooledByteBufChecker();

  public ContentEncoderTestBase(HttpConfigurator config) {
    super(config);
  }

  @Override
  protected void tearDown() throws Exception {
    try {
      if (!buffers.isEmpty()) {
        buffers.awaitOnlyPinnedAndUnpin();
      }
    } finally {
      buffers.unpin();
      super.tearDown();
    }
  }

  protected ByteBuf allocateBuffer(int size) {
    ByteBuf buffer = PooledByteBufAllocator.DEFAULT.directBuffer();
    buffer.writeBytes(TestUtils.randomByteArray(size));
    return trackBuffer(buffer);
  }

  /**
   * Track {@code buffer} for the end of test release check, see {@link PooledByteBufChecker}. A buffer added after the test
   * finished is not tracked.
   */
  protected ByteBuf trackBuffer(ByteBuf buffer) {
    return buffers.pin(buffer);
  }
}
