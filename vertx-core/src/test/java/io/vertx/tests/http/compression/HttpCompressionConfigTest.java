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
package io.vertx.tests.http.compression;

import io.netty.handler.codec.compression.*;
import io.vertx.core.http.CompressionConfig;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class HttpCompressionConfigTest {

  private CompressionConfig cfg;

  @Before
  public void before() {
    cfg = new CompressionConfig();
  }

  @Test
  public void testBrotli() {
    cfg.addBrotli();
    assertEquals(StandardCompressionOptions.brotli());
    cfg.addBrotli(10);
    assertEquals(StandardCompressionOptions.brotli(10, 22, BrotliMode.TEXT));
  }

  @Test
  public void testDeflate() {
    DeflateOptions def = StandardCompressionOptions.deflate();
    cfg.addDeflate();
    assertEquals(def);
    cfg.addDeflate(8);
    assertEquals(StandardCompressionOptions.deflate(8, def.windowBits(), def.memLevel()));
    cfg.addDeflate(8, 10, 7);
    assertEquals(StandardCompressionOptions.deflate(8, 10, 7));
  }

  @Test
  public void testGzip() {
    GzipOptions def = StandardCompressionOptions.gzip();
    cfg.addGzip();
    assertEquals(StandardCompressionOptions.gzip());
    cfg.addGzip(8);
    assertEquals(StandardCompressionOptions.gzip(8, def.windowBits(), def.memLevel()));
    cfg.addGzip(8, 10, 7);
    assertEquals(StandardCompressionOptions.gzip(8, 10, 7));
  }

  @Test
  public void testSnappy() {
    cfg.addSnappy();
    assertEquals(StandardCompressionOptions.snappy());
  }

  @Test
  public void testZstd() {
    ZstdOptions def = StandardCompressionOptions.zstd();
    cfg.addZstd();
    assertEquals(def);
    cfg.addZstd(10);
    assertEquals(StandardCompressionOptions.zstd(10, def.blockSize(), def.maxEncodeSize()));
    cfg.addZstd(10, 11, 12);
    assertEquals(StandardCompressionOptions.zstd(10, 11, 12));
  }

  private void assertEquals(BrotliOptions expected) {
    BrotliOptions actual = (BrotliOptions) cfg.getCompressors().get(0);
    Assert.assertEquals(expected.parameters().lgwin(), actual.parameters().lgwin());
    Assert.assertEquals(expected.parameters().mode(), actual.parameters().mode());
    Assert.assertEquals(expected.parameters().quality(), actual.parameters().quality());
  }

  private void assertEquals(DeflateOptions expected) {
    DeflateOptions actual = (DeflateOptions) cfg.getCompressors().get(0);
    Assert.assertEquals(expected.compressionLevel(), actual.compressionLevel());
    Assert.assertEquals(expected.memLevel(), actual.memLevel());
    Assert.assertEquals(expected.windowBits(), actual.windowBits());
  }

  private void assertEquals(GzipOptions expected) {
    GzipOptions actual = (GzipOptions) cfg.getCompressors().get(0);
    Assert.assertEquals(expected.compressionLevel(), actual.compressionLevel());
    Assert.assertEquals(expected.memLevel(), actual.memLevel());
    Assert.assertEquals(expected.windowBits(), actual.windowBits());
  }

  private void assertEquals(ZstdOptions expected) {
    ZstdOptions actual = (ZstdOptions) cfg.getCompressors().get(0);
    Assert.assertEquals(expected.compressionLevel(), actual.compressionLevel());
    Assert.assertEquals(expected.maxEncodeSize(), actual.maxEncodeSize());
    Assert.assertEquals(expected.blockSize(), actual.blockSize());
  }

  private void assertEquals(SnappyOptions expected) {
    // No config at the moment
  }
}
