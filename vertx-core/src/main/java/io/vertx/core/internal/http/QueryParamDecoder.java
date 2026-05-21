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
package io.vertx.core.internal.http;

import io.netty.handler.codec.http.QueryStringDecoder;
import io.vertx.core.MultiMap;
import io.vertx.core.http.QueryParamDecoderConfig;

import java.nio.charset.Charset;
import java.util.List;
import java.util.Map;

/**
 * Query parameter decoder, this object is immutable.
 *
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class QueryParamDecoder {

  private final Charset charset;
  private final boolean useSemiColonAsDelimiter;
  private final int maxSize;

  public QueryParamDecoder(QueryParamDecoderConfig config) {
    this.charset = config.getCharset();
    this.useSemiColonAsDelimiter = config.isUseSemicolonAsDelimiter();
    this.maxSize = config.getMaxSize();
  }

  /**
   * @return the actual charset
   */
  public Charset charset() {
    return charset;
  }

  /**
   * @return whether to treat semicolon as a delimiter or part of the parameter
   */
  public boolean isUseSemiColonAsDelimiter() {
    return useSemiColonAsDelimiter;
  }

  /**
   * @return the maximum number of parameters to decode
   */
  public int maxParams() {
    return maxSize;
  }

  public MultiMap decode(String uri) {
    MultiMap params = MultiMap.caseInsensitiveMultiMap();
    appendTo(uri, params);
    return params;
  }

  public void appendTo(String uri, MultiMap params) {
    QueryStringDecoder queryStringDecoder = new QueryStringDecoder(uri, charset, true, maxSize, !useSemiColonAsDelimiter);
    Map<String, List<String>> parameters = queryStringDecoder.parameters();
    if (!parameters.isEmpty()) {
      for (Map.Entry<String, List<String>> entry: parameters.entrySet()) {
        params.add(entry.getKey(), entry.getValue());
      }
    }
  }
}
