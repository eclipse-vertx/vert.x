/*
 * Copyright (c) 2011-2024 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */

package io.vertx.it;

import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.PooledByteBufAllocator;
import io.vertx.core.buffer.impl.VertxByteBufAllocator;
import org.junit.Assert;
import org.junit.Test;

public class ReuseNettyAllocatorsTest {

  @Test
  public void testVertxAllocatorsReuseNettyAllocators() {
    Assert.assertEquals("true", System.getProperty("vertx.reuseNettyAllocators"));
    Assert.assertSame(PooledByteBufAllocator.DEFAULT, VertxByteBufAllocator.POOLED_ALLOCATOR);
    Assert.assertSame(ByteBufAllocator.DEFAULT, VertxByteBufAllocator.POOLED_ALLOCATOR);
    Assert.assertSame(ByteBufAllocator.DEFAULT, PooledByteBufAllocator.DEFAULT);
  }
}
