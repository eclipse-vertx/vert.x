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

import io.vertx.codegen.annotations.DataObject;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import io.vertx.codegen.annotations.Unstable;

/**
 * Query parameter decoder configuration.
 *
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
@DataObject
@Unstable
public class QueryParamDecoderConfig {

  /**
   * The default max number of query parameter to decode : {@code 1024}
   */
  public static final int DEFAULT_MAX_SIZE = 1024;

  /**
   * The default behavior of the semicolon as a delimiter : {@code true}
   */
  public static final boolean DEFAULT_USE_SEMICOLON_AS_DELIMITER = true;

  /**
   * The default charset for decoding query parameters : {@link StandardCharsets#UTF_8}
   */
  public static final Charset DEFAULT_CHARSET = StandardCharsets.UTF_8;

  private int maxSize;
  private boolean useSemicolonAsDelimiter;
  private Charset charset;

  public QueryParamDecoderConfig() {
    this.maxSize = DEFAULT_MAX_SIZE;
    this.useSemicolonAsDelimiter = DEFAULT_USE_SEMICOLON_AS_DELIMITER;
    this.charset = DEFAULT_CHARSET;
  }

  public QueryParamDecoderConfig(QueryParamDecoderConfig other) {
    this.maxSize = other.maxSize;
    this.useSemicolonAsDelimiter = other.useSemicolonAsDelimiter;
    this.charset = other.charset;
  }

  /**
   * @return the maximum number of parameters to decode
   */
  public int getMaxSize() {
    return maxSize;
  }

  /**
   * Set the maximum number of parameters to decode.
   *
   * @param maxSize the max size
   * @return a reference to this, so the API can be used fluently
   */
  public QueryParamDecoderConfig setMaxSize(int maxSize) {
    if (maxSize < 1) {
      throw new IllegalArgumentException("maxSize must be > 0");
    }
    this.maxSize = maxSize;
    return this;
  }

  /**
   * @return whether to use the semicolon char {@code ;} as a delimiter for query string parameters.
   */
  public boolean isUseSemicolonAsDelimiter() {
    return useSemicolonAsDelimiter;
  }

  /**
   * Configure whether to use the semicolon char {@code ;} as a delimiter for query string parameters.
   *
   * @param useSemicolonAsDelimiter whether to allow semicolon to be used as a delimiter or it is a an actual parameter name or value
   * @return a reference to this, so the API can be used fluently
   */
  public QueryParamDecoderConfig setUseSemicolonAsDelimiter(boolean useSemicolonAsDelimiter) {
    this.useSemicolonAsDelimiter = useSemicolonAsDelimiter;
    return this;
  }

  /**
   * @return the charset to decode query parameters
   */
  public Charset getCharset() {
    return charset;
  }

  /**
   * Set the charset to decode query parameters.
   *
   * @param charset the charset
   * @return a reference to this, so the API can be used fluently
   */
  public QueryParamDecoderConfig setCharset(Charset charset) {
    this.charset = Objects.requireNonNull(charset);
    return this;
  }
}
