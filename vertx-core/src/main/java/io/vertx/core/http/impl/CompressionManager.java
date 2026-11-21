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
package io.vertx.core.http.impl;

import io.netty.handler.codec.Headers;
import io.netty.handler.codec.compression.CompressionOptions;
import io.netty.handler.codec.http.HttpContentCompressor;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http2.Http2Headers;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.impl.headers.HttpRequestHeaders;

import static io.vertx.core.http.HttpHeaders.CONTENT_ENCODING;
import static io.vertx.core.http.HttpHeaders.IDENTITY;

/**
 *
 */
public class CompressionManager {

  private final EncodingDetector encodingDetector;
  private final CompressionOptions[] options;

  public CompressionManager(int contentSizeThreshold, CompressionOptions[] options) {
    this.options = options;
    this.encodingDetector = new EncodingDetector(contentSizeThreshold, options);
  }

  public CompressionOptions[] options() {
    return options;
  }

  public String determineEncoding(String acceptEncoding) {
    return encodingDetector.determineEncoding(acceptEncoding);
  }

  /**
   * Set the {@code responseHeaders} content-encoding header.
   *
   * @param requestHeaders
   * @param responseHeaders
   */
  public void setContentEncoding(HttpRequestHeaders requestHeaders, Headers<CharSequence, CharSequence, ?> responseHeaders) {
    String contentEncodingToApply = determineContentEncodingToApply(requestHeaders, responseHeaders);
    if (contentEncodingToApply == null || contentEncodingToApply.equalsIgnoreCase(IDENTITY.toString())) {
      if (responseHeaders.contains(CONTENT_ENCODING, IDENTITY)) {
        responseHeaders.remove(CONTENT_ENCODING);
      }
    } else {
      responseHeaders.set(CONTENT_ENCODING, contentEncodingToApply);
    }
  }

  public static boolean isContentAlwaysEmpty(HttpRequestHeaders requestHeaders, Headers<CharSequence, CharSequence, ?> responseHeaders) {
    int statusCode = responseHeaders.getInt(Http2Headers.PseudoHeaderName.STATUS.value(), 200);
    return statusCode < 200 || statusCode == 204 || statusCode == 205 || statusCode == 304 ||
      requestHeaders.method() == HttpMethod.HEAD;
  }

  private String determineContentEncodingToApply(HttpRequestHeaders requestHeaders, Headers<CharSequence, CharSequence, ?> responseHeaders) {
    if (responseHeaders.contains(CONTENT_ENCODING) || isContentAlwaysEmpty(requestHeaders, responseHeaders)) {
      return null;
    }
    return determineContentEncoding(requestHeaders.unwrap());
  }

  private String determineContentEncoding(Headers<CharSequence, CharSequence, ?> headers) {
    String acceptEncoding = headers.get(HttpHeaderNames.ACCEPT_ENCODING) != null ? headers.get(HttpHeaderNames.ACCEPT_ENCODING).toString() : null;
    if (acceptEncoding != null) {
      return determineEncoding(acceptEncoding);
    }
    return null;
  }

  private static class EncodingDetector extends HttpContentCompressor {

    private EncodingDetector(int contentSizeThreshold, CompressionOptions[] compressionOptions) {
      super(contentSizeThreshold, compressionOptions);
    }

    @Override
    protected String determineEncoding(String acceptEncoding) {
      return super.determineEncoding(acceptEncoding);
    }
  }
}
