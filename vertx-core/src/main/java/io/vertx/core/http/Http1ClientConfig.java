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
package io.vertx.core.http;

import io.vertx.core.impl.Arguments;

/**
 * HTTP/1.x client configuration.
 *
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
class Http1ClientConfig {

  private boolean keepAlive;
  private int keepAliveTimeout;
  private int pipeliningLimit;
  private boolean pipelining;
  private int maxChunkSize;
  private int maxInitialLineLength;
  private int maxHeaderSize;
  private int decoderInitialBufferSize;

  Http1ClientConfig() {
    keepAlive = HttpClientOptions.DEFAULT_KEEP_ALIVE;
    keepAliveTimeout = HttpClientOptions.DEFAULT_KEEP_ALIVE_TIMEOUT;
    pipelining = HttpClientOptions.DEFAULT_PIPELINING;
    pipeliningLimit = HttpClientOptions.DEFAULT_PIPELINING_LIMIT;
    maxChunkSize = HttpClientOptions.DEFAULT_MAX_CHUNK_SIZE;
    maxInitialLineLength = HttpClientOptions.DEFAULT_MAX_INITIAL_LINE_LENGTH;
    maxHeaderSize = HttpClientOptions.DEFAULT_MAX_HEADER_SIZE;
    decoderInitialBufferSize = HttpClientOptions.DEFAULT_DECODER_INITIAL_BUFFER_SIZE;
  }

  Http1ClientConfig(Http1ClientConfig other) {
    this.keepAlive = other.isKeepAlive();
    this.keepAliveTimeout = other.getKeepAliveTimeout();
    this.pipelining = other.isPipelining();
    this.pipeliningLimit = other.getPipeliningLimit();
    this.maxChunkSize = other.maxChunkSize;
    this.maxInitialLineLength = other.getMaxInitialLineLength();
    this.maxHeaderSize = other.getMaxHeaderSize();
    this.decoderInitialBufferSize = other.getDecoderInitialBufferSize();
  }


  /**
   * Is keep alive enabled on the client?
   *
   * @return {@code true} if enabled
   */
  public boolean isKeepAlive() {
    return keepAlive;
  }

  /**
   * Set whether keep alive is enabled on the client
   *
   * @param keepAlive {@code true} if enabled
   * @return a reference to this, so the API can be used fluently
   */
  public Http1ClientConfig setKeepAlive(boolean keepAlive) {
    this.keepAlive = keepAlive;
    return this;
  }

  /**
   * @return the keep alive timeout value in seconds for HTTP/1.x connections
   */
  public int getKeepAliveTimeout() {
    return keepAliveTimeout;
  }

  /**
   * Set the keep alive timeout for HTTP/1.x, in seconds.
   * <p/>
   * This value determines how long a connection remains unused in the pool before being evicted and closed.
   * <p/>
   * A timeout of {@code 0} means there is no timeout.
   *
   * @param keepAliveTimeout the timeout, in seconds
   * @return a reference to this, so the API can be used fluently
   */
  public Http1ClientConfig setKeepAliveTimeout(int keepAliveTimeout) {
    if (keepAliveTimeout < 0) {
      throw new IllegalArgumentException("keepAliveTimeout must be >= 0");
    }
    this.keepAliveTimeout = keepAliveTimeout;
    return this;
  }

  /**
   * Is pipe-lining enabled on the client
   *
   * @return {@code true} if pipe-lining is enabled
   */
  public boolean isPipelining() {
    return pipelining;
  }

  /**
   * Set whether pipe-lining is enabled on the client
   *
   * @param pipelining {@code true} if enabled
   * @return a reference to this, so the API can be used fluently
   */
  public Http1ClientConfig setPipelining(boolean pipelining) {
    this.pipelining = pipelining;
    return this;
  }

  /**
   * @return the limit of pending requests a pipe-lined HTTP/1 connection can send
   */
  public int getPipeliningLimit() {
    return pipeliningLimit;
  }

  /**
   * Set the limit of pending requests a pipe-lined HTTP/1 connection can send.
   *
   * @param limit the limit of pending requests
   * @return a reference to this, so the API can be used fluently
   */
  public Http1ClientConfig setPipeliningLimit(int limit) {
    if (limit < 1) {
      throw new IllegalArgumentException("pipeliningLimit must be > 0");
    }
    this.pipeliningLimit = limit;
    return this;
  }

  /**
   * Set the maximum HTTP chunk size
   * @param maxChunkSize the maximum chunk size
   * @return a reference to this, so the API can be used fluently
   */
  public Http1ClientConfig setMaxChunkSize(int maxChunkSize) {
    this.maxChunkSize = maxChunkSize;
    return this;
  }

  /**
   * Returns the maximum HTTP chunk size
   * @return the maximum HTTP chunk size
   */
  public int getMaxChunkSize() {
    return maxChunkSize;
  }

  /**
   * @return the maximum length of the initial line for HTTP/1.x (e.g. {@code "GET / HTTP/1.0"})
   */
  public int getMaxInitialLineLength() {
    return maxInitialLineLength;
  }

  /**
   * Set the maximum length of the initial line for HTTP/1.x (e.g. {@code "HTTP/1.1 200 OK"})
   *
   * @param maxInitialLineLength the new maximum initial length
   * @return a reference to this, so the API can be used fluently
   */
  public Http1ClientConfig setMaxInitialLineLength(int maxInitialLineLength) {
    this.maxInitialLineLength = maxInitialLineLength;
    return this;
  }

  /**
   * @return Returns the maximum length of all headers for HTTP/1.x
   */
  public int getMaxHeaderSize() {
    return maxHeaderSize;
  }

  /**
   * Set the maximum length of all headers for HTTP/1.x .
   *
   * @param maxHeaderSize the new maximum length
   * @return a reference to this, so the API can be used fluently
   */
  public Http1ClientConfig setMaxHeaderSize(int maxHeaderSize) {
    this.maxHeaderSize = maxHeaderSize;
    return this;
  }

  /**
   * @return the initial buffer size for the HTTP decoder
   */
  public int getDecoderInitialBufferSize() { return decoderInitialBufferSize; }

  /**
   * set to {@code initialBufferSizeHttpDecoder} the initial buffer of the HttpDecoder.
   *
   * @param decoderInitialBufferSize the initial buffer size
   * @return a reference to this, so the API can be used fluently
   */
  public Http1ClientConfig setDecoderInitialBufferSize(int decoderInitialBufferSize) {
    Arguments.require(decoderInitialBufferSize > 0, "initialBufferSizeHttpDecoder must be > 0");
    this.decoderInitialBufferSize = decoderInitialBufferSize;
    return this;
  }
}
