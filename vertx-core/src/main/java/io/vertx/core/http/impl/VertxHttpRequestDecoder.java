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
package io.vertx.core.http.impl;

import io.netty.handler.codec.http.*;
import io.netty.util.AsciiString;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.http.impl.headers.HeadersMultiMap;
import io.vertx.core.internal.http.HttpHeadersInternal;

/**
 * A request decoder using {@link HeadersMultiMap} which is faster than {@code DefaultHttpHeaders} used by the super class.
 */
public class VertxHttpRequestDecoder extends HttpRequestDecoder {

  public VertxHttpRequestDecoder(HttpServerOptions options) {
    super(
      options.getMaxInitialLineLength(),
      options.getMaxHeaderSize(),
      options.getMaxChunkSize(),
      !HttpHeadersInternal.DISABLE_HTTP_HEADERS_VALIDATION,
      options.getDecoderInitialBufferSize());
  }

  @Override
  protected AsciiString splitHeaderName(byte[] sb, int start, int length) {
    // Copied from super class
    final byte firstChar = sb[start];
    if (firstChar == 'C') {
      if (length == 10) {
        if (isConnection(sb, start)) {
          // Note this change the semantic and should be made configurable
          return HttpHeaderNames.CONNECTION;
        }
      }
    }
    return super.splitHeaderName(sb, start, length);
  }

  // Copied from super class
  private static boolean isConnection(byte[] sb, int start) {
    final long maybeConnecti = sb[start] |
      sb[start + 1] << 8 |
      sb[start + 2] << 16 |
      sb[start + 3] << 24 |
      (long) sb[start + 4] << 32 |
      (long) sb[start + 5] << 40 |
      (long) sb[start + 6] << 48 |
      (long) sb[start + 7] << 56;
    if (maybeConnecti != CONNECTION_AS_LONG_0) {
      return false;
    }
    final short maybeOn = (short) (sb[start + 8] | sb[start + 9] << 8);
    return maybeOn == CONNECTION_AS_SHORT_1;
  }

  private static final long CONNECTION_AS_LONG_0 = 'C' | 'o' << 8 | 'n' << 16 | 'n' << 24 |
    (long) 'e' << 32 | (long) 'c' << 40 | (long) 't' << 48 | (long) 'i' << 56;

  private static final short CONNECTION_AS_SHORT_1 = 'o' | 'n' << 8;

  @Override
  protected HttpMessage createMessage(String[] initialLine) {
    return new DefaultHttpRequest(
      HttpVersion.valueOf(initialLine[2]),
      HttpMethod.valueOf(initialLine[0]),
      initialLine[1],
      HeadersMultiMap.httpHeaders());
  }

  @Override
  protected boolean isContentAlwaysEmpty(HttpMessage msg) {
    if (msg == null) {
      return false;
    }
    // we are forced to perform exact type check here because
    // users can override createMessage with a DefaultHttpRequest implements HttpResponse
    // and we have to enforce
    // if (msg instance HttpResponse) return super.isContentAlwaysEmpty(msg)
    if (msg.getClass() == DefaultHttpRequest.class) {
      return false;
    }
    return super.isContentAlwaysEmpty(msg);
  }
}
