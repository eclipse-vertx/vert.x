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
package io.vertx.it.buffer;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.UnpooledByteBufAllocator;
import io.netty.util.ReferenceCountUtil;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.buffer.impl.BufferImpl;
import io.vertx.core.impl.buffer.VertxByteBufAllocator;
import io.vertx.core.internal.net.NetSocketInternal;
import io.vertx.core.net.NetClient;
import io.vertx.core.net.NetServer;
import io.vertx.core.net.NetSocket;
import io.vertx.test.core.VertxTestBase;
import org.junit.Test;

public class TcpAllocationTest extends VertxTestBase {

  @Test
  public void testByteBufOriginateFromDefaultByteBufAllocator() {
    NetServer server = vertx.createNetServer();
    server.connectHandler(so -> {
      NetSocketInternal soi = (NetSocketInternal) so;
      soi.messageHandler(msg -> {
        try {
          ByteBuf bbuf = (ByteBuf) msg;
          assertSame(VertxByteBufAllocator.POOLED_ALLOCATOR, bbuf.alloc());
        } finally {
          ReferenceCountUtil.release(msg);
        }
        testComplete();
      });
    });
    server.listen(1234, "localhost").await();
    NetClient client = vertx.createNetClient();
    NetSocket so = client.connect(1234, "localhost").await();
    so.write(Buffer.buffer("ping"));
    await();
  }

  @Test
  public void testByteBufCopyAndRelease() {
    NetServer server = vertx.createNetServer();
    server.connectHandler(so -> {
      so.handler(buff -> {
        ByteBuf byteBuf = ((BufferImpl)buff).byteBuf();
        assertFalse(byteBuf.isDirect());
        assertFalse(byteBuf.alloc().isDirectBufferPooled());
        testComplete();
      });
    });
    server.listen(1234, "localhost").await();
    NetClient client = vertx.createNetClient();
    NetSocket so = client.connect(1234, "localhost").await();
    so.write(Buffer.buffer("ping"));
    await();
  }
}
