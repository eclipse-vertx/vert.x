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
package io.vertx.tests.http;

import io.netty.handler.codec.compression.CompressionOptions;
import io.netty.handler.codec.compression.DeflateOptions;
import io.netty.handler.codec.compression.GzipOptions;
import io.vertx.core.http.CompressionConfig;
import io.vertx.core.http.HttpServerConfig;
import io.vertx.core.http.HttpServerOptions;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.*;

public class HttpServerConfigTest {

  @Test
  public void testCompressionFromDefaultOptions() {
    HttpServerConfig config = new HttpServerConfig(new HttpServerOptions().setCompressionSupported(true));
    CompressionConfig compressionConfig = config.getCompressionConfig();
    List<CompressionOptions> compressors = compressionConfig.getCompressors();
    assertEquals(2, compressors.size());
    for (CompressionOptions compressor : compressors) {
      if (compressor instanceof GzipOptions) {
        GzipOptions gzipCompressor = (GzipOptions) compressor;
        assertEquals(HttpServerOptions.DEFAULT_COMPRESSION_LEVEL, gzipCompressor.compressionLevel());
      } else if (compressor instanceof DeflateOptions) {
        DeflateOptions deflateCompressor = (DeflateOptions) compressor;
        assertEquals(HttpServerOptions.DEFAULT_COMPRESSION_LEVEL, deflateCompressor.compressionLevel());
      } else {
        fail();
      }
    }
  }

  @Test
  public void testUseSemicolonAsQueryParamDelimiter() {
    HttpServerConfig cfg = new HttpServerConfig(new HttpServerOptions());
    assertNull(cfg.getQueryParamConfig());
    cfg = new HttpServerConfig(new HttpServerOptions().setUseSemicolonAsQueryParamDelimiter(true));
    assertNull(cfg.getQueryParamConfig());
    cfg = new HttpServerConfig(new HttpServerOptions().setUseSemicolonAsQueryParamDelimiter(false));
    assertNotNull(cfg.getQueryParamConfig());
    assertFalse(cfg.getQueryParamConfig().isUseSemicolonAsDelimiter());
  }
}
