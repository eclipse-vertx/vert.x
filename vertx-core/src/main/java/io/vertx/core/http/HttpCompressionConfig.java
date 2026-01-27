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

import io.netty.handler.codec.compression.CompressionOptions;
import io.vertx.codegen.annotations.DataObject;
import io.vertx.core.impl.Arguments;

import java.util.ArrayList;
import java.util.List;

/**
 * HTTP compression configuration.
 */
@DataObject
public class HttpCompressionConfig {

  private boolean compressionEnabled;
  private boolean decompressionEnabled;
  private int contentSizeThreshold;
  private List<CompressionOptions> compressors;

  public HttpCompressionConfig() {
    this.compressionEnabled = HttpServerOptions.DEFAULT_COMPRESSION_SUPPORTED;
    this.decompressionEnabled = HttpServerOptions.DEFAULT_DECOMPRESSION_SUPPORTED;
    this.contentSizeThreshold = HttpServerOptions.DEFAULT_COMPRESSION_CONTENT_SIZE_THRESHOLD;
    this.compressors = null;
  }

  public HttpCompressionConfig(HttpCompressionConfig other) {
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
  public HttpCompressionConfig setCompressionEnabled(boolean compressionEnabled) {
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
  public HttpCompressionConfig setDecompressionEnabled(boolean decompressionEnabled) {
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
  public HttpCompressionConfig setContentSizeThreshold(int contentSizeThreshold) {
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
   * Add a compressor.
   *
   * @see #setCompressors(List)
   * @return a reference to this, so the API can be used fluently
   */
  public HttpCompressionConfig addCompressor(CompressionOptions compressor) {
    if (compressors == null) {
      compressors = new ArrayList<>();
    }
    compressors.add(compressor);
    return this;
  }

  /**
   * @param compressors the list of compressors
   * @return a reference to this, so the API can be used fluently
   */
  public HttpCompressionConfig setCompressors(List<CompressionOptions> compressors) {
    this.compressors = compressors;
    return this;
  }
}
