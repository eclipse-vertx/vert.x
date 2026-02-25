/*
 * Copyright (c) 2011-2025 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package io.vertx.core.http;

import io.netty.handler.codec.compression.BrotliMode;
import io.netty.handler.codec.compression.CompressionOptions;
import io.netty.handler.codec.compression.DeflateOptions;
import io.netty.handler.codec.compression.GzipOptions;
import io.netty.handler.codec.compression.StandardCompressionOptions;
import io.netty.handler.codec.compression.ZstdOptions;
import io.vertx.codegen.annotations.DataObject;
import io.vertx.codegen.annotations.GenIgnore;
import io.vertx.core.impl.Arguments;

import java.util.ArrayList;
import java.util.List;

/**
 * HTTP compression configuration.
 */
@DataObject
public class CompressionConfig {

  private boolean compressionEnabled;
  private boolean decompressionEnabled;
  private int contentSizeThreshold;
  private List<CompressionOptions> compressors;

  public CompressionConfig() {
    this.compressionEnabled = HttpServerOptions.DEFAULT_COMPRESSION_SUPPORTED;
    this.decompressionEnabled = HttpServerOptions.DEFAULT_DECOMPRESSION_SUPPORTED;
    this.contentSizeThreshold = HttpServerOptions.DEFAULT_COMPRESSION_CONTENT_SIZE_THRESHOLD;
    this.compressors = null;
  }

  public CompressionConfig(CompressionConfig other) {
    this.compressionEnabled = other.compressionEnabled;
    this.decompressionEnabled = other.decompressionEnabled;
    this.contentSizeThreshold = other.contentSizeThreshold;
    this.compressors = other.compressors != null ? new ArrayList<>(other.compressors) : null;
  }

  /**
   * @return {@code true} if the server supports gzip/deflate compression
   */
  public boolean isCompressionEnabled() {
    return compressionEnabled;
  }

  /**
   * Set whether the server should support gzip/deflate compression
   * (serving compressed responses to clients advertising support for them with Accept-Encoding header)
   *
   * @param compressionEnabled {@code true} to enable compression support
   * @return a reference to this, so the API can be used fluently
   */
  public CompressionConfig setCompressionEnabled(boolean compressionEnabled) {
    this.compressionEnabled = compressionEnabled;
    return this;
  }

  /**
   * @return {@code true} if the server supports decompression
   */
  public boolean isDecompressionEnabled() {
    return decompressionEnabled;
  }

  /**
   * Set whether the server supports decompression
   *
   * @param decompressionEnabled {@code true} if decompression supported
   * @return a reference to this, so the API can be used fluently
   */
  public CompressionConfig setDecompressionEnabled(boolean decompressionEnabled) {
    this.decompressionEnabled = decompressionEnabled;
    return this;
  }

  /**
   * @return the compression content size threshold
   */
  public int getContentSizeThreshold() {
    return contentSizeThreshold;
  }

  /**
   * Set the compression content size threshold if compression is enabled. This is only applicable for HTTP/1.x response bodies.
   * If the response content size in bytes is greater than this threshold, then the response is compressed. Otherwise, it is not compressed.
   *
   * @param contentSizeThreshold integer greater than or equal to 0.
   * @return a reference to this, so the API can be used fluently
   */
  public CompressionConfig setContentSizeThreshold(int contentSizeThreshold) {
    Arguments.require(contentSizeThreshold >= 0, "compressionContentSizeThreshold must be >= 0");
    this.contentSizeThreshold = contentSizeThreshold;
    return this;
  }

  /**
   * @return the list of compressor to use
   */
  public List<CompressionOptions> getCompressors() {
    return compressors;
  }

  /**
   * Set the list of compressors.
   *
   * @param compressors the list of compressors
   * @return a reference to this, so the API can be used fluently
   */
  public CompressionConfig setCompressors(List<CompressionOptions> compressors) {
    this.compressors = compressors;
    return this;
  }

  /**
   * Add a compressor, if the compressor is already registered, the compressor is updated instead.
   *
   * @return a reference to this, so the API can be used fluently
   */
  public CompressionConfig addCompressor(CompressionOptions compressor) {
    if (compressors != null) {
      for (int i = 0;i < compressors.size();i++) {
        CompressionOptions c = compressors.get(i);
        if (c.getClass() == compressor.getClass()) {
          compressors.set(i, compressor);
          return this;
        }
      }
    } else {
      compressors = new ArrayList<>();
    }
    compressors.add(compressor);
    return this;
  }

  /**
   * Calls {@link #addCompressor(CompressionOptions)} with the gzip algorithm configured with default parameters.
   *
   * @return a reference to this, so the API can be used fluently
   */
  @GenIgnore
  public CompressionConfig addGzip() {
    return addCompressor(StandardCompressionOptions.gzip());
  }

  /**
   * Calls {@link #addCompressor(CompressionOptions)} with the gzip algorithm configured with the specified {@code compressionLevel}
   * , default window bits and default mem level.
   *
   * @param compressionLevel the compression level, ranged from 1 to 9
   * @return a reference to this, so the API can be used fluently
   */
  @GenIgnore
  public CompressionConfig addGzip(int compressionLevel) {
    GzipOptions def = StandardCompressionOptions.gzip();
    return addCompressor(StandardCompressionOptions.gzip(compressionLevel, def.windowBits(), def.memLevel()));
  }

  /**
   * Calls {@link #addCompressor(CompressionOptions)} with the gzip algorithm configured with the specified {@code compressionLevel}
   * , specified {@code windowBits} and specified {@code memLevel}.
   *
   * @param compressionLevel the compression level param, ranged from 1 to 9
   * @param windowBits the window bits param
   * @param memLevel the mem level param
   * @return a reference to this, so the API can be used fluently
   */
  @GenIgnore
  public CompressionConfig addGzip(int compressionLevel, int windowBits, int memLevel) {
    return addCompressor(StandardCompressionOptions.gzip(compressionLevel, windowBits, memLevel));
  }

  /**
   * Calls {@link #addCompressor(CompressionOptions)} with the deflate algorithm configured with default parameters.
   *
   * @return a reference to this, so the API can be used fluently
   */
  @GenIgnore
  public CompressionConfig addDeflate() {
    return addCompressor(StandardCompressionOptions.deflate());
  }

  /**
   * Calls {@link #addCompressor(CompressionOptions)} with the deflate algorithm configured with the specified {@code compressionLevel}
   * , default window bits and default mem level.
   *
   * @return a reference to this, so the API can be used fluently
   */
  @GenIgnore
  public CompressionConfig addDeflate(int compressionLevel) {
    DeflateOptions def = StandardCompressionOptions.deflate();
    return addCompressor(StandardCompressionOptions.deflate(compressionLevel, def.windowBits(), def.memLevel()));
  }

  /**
   * Calls {@link #addCompressor(CompressionOptions)} with the deflate algorithm configured with the specified {@code compressionLevel}
   * , specified {@code windowBits} and specified {@code memLevel}.
   *
   * @param compressionLevel the compression level param, ranged from 1 to 9
   * @param windowBits the window bits param
   * @param memLevel the mem level param
   * @return a reference to this, so the API can be used fluently
   */
  @GenIgnore
  public CompressionConfig addDeflate(int compressionLevel, int windowBits, int memLevel) {
    return addCompressor(StandardCompressionOptions.deflate(compressionLevel, windowBits, memLevel));
  }

  /**
   * Calls {@link #addCompressor(CompressionOptions)} with the snappy algorithm configured with default parameters.
   *
   * @return a reference to this, so the API can be used fluently
   */
  @GenIgnore
  public CompressionConfig addSnappy() {
    return addCompressor(StandardCompressionOptions.snappy());
  }

  /**
   * Calls {@link #addCompressor(CompressionOptions)} with the zstd algorithm configured with default parameters.
   *
   * @return a reference to this, so the API can be used fluently
   */
  @GenIgnore
  public CompressionConfig addZstd() {
    return addCompressor(StandardCompressionOptions.zstd());
  }

  /**
   * Calls {@link #addCompressor(CompressionOptions)} with the zstd algorithm configured with the specified {@code compressionLevel}
   * , default block size and default max encode size.
   *
   * @param compressionLevel the compression level param
   * @return a reference to this, so the API can be used fluently
   */
  @GenIgnore
  public CompressionConfig addZstd(int compressionLevel) {
    ZstdOptions def = StandardCompressionOptions.zstd();
    return addCompressor(StandardCompressionOptions.zstd(compressionLevel, def.blockSize(), def.maxEncodeSize()));
  }

  /**
   * Calls {@link #addCompressor(CompressionOptions)} with the zstd algorithm configured with the specified {@code compressionLevel}
   * , specified {@code blockSize} and specified {@code maxEncodeSize}.
   *
   * @param compressionLevel the compression level param
   * @param blockSize the block size param
   * @param maxEncodeSize the max encode size param
   * @return a reference to this, so the API can be used fluently
   */
  @GenIgnore
  public CompressionConfig addZstd(int compressionLevel, int blockSize, int maxEncodeSize) {
    ZstdOptions def = StandardCompressionOptions.zstd();
    return addCompressor(StandardCompressionOptions.zstd(compressionLevel, blockSize, maxEncodeSize));
  }

  /**
   * Calls {@link #addCompressor(CompressionOptions)} with the brotli algorithm configured with default parameters.
   *
   * @return a reference to this, so the API can be used fluently
   */
  @GenIgnore
  public CompressionConfig addBrotli() {
    return addCompressor(StandardCompressionOptions.brotli());
  }

  /**
   * Calls {@link #addCompressor(CompressionOptions)} with the brotli algorithm configured with the specified {@code quality}
   * , default window and default mode.
   *
   * @param quality the quality param
   * @return a reference to this, so the API can be used fluently
   */
  @GenIgnore
  public CompressionConfig addBrotli(int quality) {
    return addCompressor(StandardCompressionOptions.brotli(quality, 4, BrotliMode.TEXT));
  }
}
