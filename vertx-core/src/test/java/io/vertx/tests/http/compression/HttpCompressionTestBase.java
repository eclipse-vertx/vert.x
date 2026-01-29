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

package io.vertx.tests.http.compression;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.MessageToByteEncoder;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.*;
import io.vertx.core.internal.buffer.BufferInternal;
import io.vertx.test.http.HttpConfig;
import io.vertx.test.http.SimpleHttpTest;

import java.util.Optional;
import java.util.Queue;

public abstract class HttpCompressionTestBase extends SimpleHttpTest {

  protected static final String COMPRESS_TEST_STRING = "/*\n" +
      " * Copyright (c) 2011-2016 The original author or authors\n" +
      " * ------------------------------------------------------\n" +
      " * All rights reserved. This program and the accompanying materials\n" +
      " * are made available under the terms of the Eclipse Public License v1.0\n" +
      " * and Apache License v2.0 which accompanies this distribution.\n" +
      " *\n" +
      " *     The Eclipse Public License is available at\n" +
      " *     http://www.eclipse.org/legal/epl-v10.html\n" +
      " *\n" +
      " *     The Apache License v2.0 is available at\n" +
      " *     http://www.opensource.org/licenses/apache2.0.php\n" +
      " *\n" +
      " * You may elect to redistribute this code under either of these licenses.\n" +
      " */";

  protected Buffer compressedTestString;

  public HttpCompressionTestBase(HttpConfig config) {
    super(config);
  }

  protected abstract String encoding();

  protected abstract MessageToByteEncoder<ByteBuf> encoder();

  /**
   * @return the default configuration
   */
  protected Optional<HttpCompressionConfig> serverCompressionConfig() {
    throw new UnsupportedOperationException();
  }

  protected Buffer compress(Buffer src) {
    EmbeddedChannel channel = new EmbeddedChannel();
    channel.pipeline().addFirst(encoder());
    channel.writeAndFlush(Unpooled.copiedBuffer(((BufferInternal)src).getByteBuf()));
    channel.close();
    Queue<Object> messages = channel.outboundMessages();
    Buffer dst = Buffer.buffer();
    ByteBuf buf;
    while ((buf = (ByteBuf) messages.poll()) != null) {
      byte[] tmp = new byte[buf.readableBytes()];
      buf.readBytes(tmp);
      buf.release();
      dst.appendBytes(tmp);
    }
    return dst;
  }

  @Override
  public void setUp() throws Exception {
    super.setUp();
    compressedTestString = compress(Buffer.buffer(COMPRESS_TEST_STRING));
  }
}
