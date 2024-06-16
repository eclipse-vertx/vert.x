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
import io.netty.handler.codec.MessageToByteEncoder;
import io.netty.handler.codec.compression.JdkZlibEncoder;
import io.netty.handler.codec.compression.ZlibWrapper;

import io.vertx.core.http.HttpServerOptions;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.Arrays;
import java.util.Collection;

@RunWith(Parameterized.class)
public abstract class GzipHttpCompressionTestBase extends HttpCompressionTest {

  @Parameterized.Parameters(name = "{index}: compressionLevel = {0}")
  public static Collection<Object[]> data() {
    return Arrays.asList(new Object[][] {
      { 1 }, { 6 }, { 9 }
    });
  }

  private int compressionLevel;

  public GzipHttpCompressionTestBase(int compressionLevel) {
    this.compressionLevel = compressionLevel;
  }

  @Override
  protected String encoding() {
    return "gzip";
  }

  protected MessageToByteEncoder<ByteBuf> encoder() {
    return new JdkZlibEncoder(ZlibWrapper.GZIP, compressionLevel);
  }

  @Override
  protected void configureServerCompression(HttpServerOptions options) {
    options.setCompressionSupported(true).setCompressionLevel(compressionLevel);
  }
}
