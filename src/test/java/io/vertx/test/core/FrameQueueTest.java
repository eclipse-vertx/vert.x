/*
 * Copyright (c) 2011-2013 The original author or authors
 *  ------------------------------------------------------
 *  All rights reserved. This program and the accompanying materials
 *  are made available under the terms of the Eclipse Public License v1.0
 *  and Apache License v2.0 which accompanies this distribution.
 *
 *      The Eclipse Public License is available at
 *      http://www.eclipse.org/legal/epl-v10.html
 *
 *      The Apache License v2.0 is available at
 *      http://www.opensource.org/licenses/apache2.0.php
 *
 *  You may elect to redistribute this code under either of these licenses.
 */

package io.vertx.test.core;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpFrame;
import io.vertx.core.http.impl.FrameQueue;
import io.vertx.core.http.impl.HttpFrameImpl;
import org.junit.Test;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class FrameQueueTest extends VertxTestBase {

  private static final Charset cs = StandardCharsets.UTF_8;

  @Test
  public void testSingleBuffer() {
    FrameQueue list = new FrameQueue();
    ByteBuf buf = Unpooled.copiedBuffer("hello", cs);
    list.append(buf);
    assertSame(buf, list.next());
    assertNull(list.next());
  }

  @Test
  public void testSingleFrame() {
    FrameQueue list = new FrameQueue();
    HttpFrame frame = new HttpFrameImpl(0, 0, Buffer.buffer());
    list.append(frame);
    assertSame(frame, list.next());
    assertNull(list.next());
  }

  @Test
  public void testMultipleFrames() {
    FrameQueue list = new FrameQueue();
    HttpFrame frame1 = new HttpFrameImpl(0, 0, Buffer.buffer());
    HttpFrame frame2 = new HttpFrameImpl(0, 0, Buffer.buffer());
    HttpFrame frame3 = new HttpFrameImpl(0, 0, Buffer.buffer());
    list.append(frame1);
    list.append(frame2);
    list.append(frame3);
    assertSame(frame1, list.next());
    assertSame(frame2, list.next());
    assertSame(frame3, list.next());
    assertNull(list.next());
  }

  @Test
  public void testMergeBuffers() {
    FrameQueue list = new FrameQueue();
    ByteBuf hello = Unpooled.copiedBuffer("hello", cs);
    ByteBuf world = Unpooled.copiedBuffer("world", cs);
    list.append(hello);
    list.append(world);
    ByteBuf buf = (ByteBuf) list.next();
    assertEquals("helloworld", buf.toString(cs));
    assertNull(list.next());
  }

  @Test
  public void testMergeLastBuffers() {
    FrameQueue list = new FrameQueue();
    HttpFrame frame = new HttpFrameImpl(0, 0, Buffer.buffer());
    list.append(frame);
    ByteBuf hello = Unpooled.copiedBuffer("hello", cs);
    ByteBuf world = Unpooled.copiedBuffer("world", cs);
    list.append(hello);
    list.append(world);
    assertSame(frame, list.next());
    ByteBuf buf = (ByteBuf) list.next();
    assertEquals("helloworld", buf.toString(cs));
    assertNull(list.next());
  }

  @Test
  public void testInterleave() {
    FrameQueue list = new FrameQueue();
    HttpFrame frame1 = new HttpFrameImpl(0, 0, Buffer.buffer());
    HttpFrame frame2 = new HttpFrameImpl(0, 0, Buffer.buffer());
    ByteBuf hello = Unpooled.copiedBuffer("hello", cs);
    ByteBuf world = Unpooled.copiedBuffer("world", cs);
    list.append(frame1);
    list.append(hello);
    list.append(frame2);
    list.append(world);
    assertSame(frame1, list.next());
    assertSame(hello, list.next());
    assertSame(frame2, list.next());
    assertSame(world, list.next());
    assertNull(list.next());
  }
}
