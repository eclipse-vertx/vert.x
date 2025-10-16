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
package io.vertx.core.http.impl.http1x;

import io.netty.handler.codec.http.*;
import io.netty.util.AsciiString;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.http.impl.headers.HeadersMultiMap;
import io.vertx.core.impl.SysProps;
import io.vertx.core.internal.http.HttpHeadersInternal;

import java.nio.charset.StandardCharsets;

/**
 * A request decoder using {@link HeadersMultiMap} which is faster than {@code DefaultHttpHeaders} used by the super class.
 */
public class VertxHttpRequestDecoder extends HttpRequestDecoder {

  private static final int HOST_AS_INT = 'o' << 8 | 's' << 16 | 't' << 24 ;

  private static final long CONNECTION_AS_LONG_0 = 'o' << 8 | 'n' << 16 | 'n' << 24 |
    (long) 'e' << 32 | (long) 'c' << 40 | (long) 't' << 48 | (long) 'i' << 56;

  private static final short CONNECTION_AS_SHORT_1 = 'o' | 'n' << 8;

  private static final long CONTENT_AS_LONG = 'o' << 8 | 'n' << 16 | 't' << 24 |
    (long) 'e' << 32 | (long) 'n' << 40 | (long) 't' << 48 | (long) '-' << 56;

  private static final int TYPE_AS_INT = 'y' << 8 | 'p' << 16 | 'e' << 24;

  private static final long LENGTH_AS_LONG = 'e' << 8 | 'n' << 16 | 'g' << 24 |
    (long) 't' << 32 | (long) 'h' << 40;

  private static final long ACCEPT_AS_LONG = 'c' << 8 | 'c' << 16 | 'e' << 24 |
    (long) 'p' << 32 | (long) 't' << 40;

  private final AsciiString _Host;
  private final AsciiString _Connection;
  private final AsciiString _Content_Type;
  private final AsciiString _Content_Length;
  private final AsciiString _Accept;

  public VertxHttpRequestDecoder(HttpServerOptions options) {
    super(
      options.getMaxInitialLineLength(),
      options.getMaxHeaderSize(),
      options.getMaxChunkSize(),
      !HttpHeadersInternal.DISABLE_HTTP_HEADERS_VALIDATION,
      options.getDecoderInitialBufferSize());

    boolean internToLowerCase = SysProps.INTERN_COMMON_HTTP_REQUEST_HEADERS_TO_LOWER_CASE.getBoolean();

    // Get headers from super class
    _Host = internToLowerCase ? HttpHeaderNames.HOST : intern("Host");
    _Connection = internToLowerCase ? HttpHeaderNames.CONNECTION : intern("Connection");
    _Content_Type = internToLowerCase ? HttpHeaderNames.CONTENT_TYPE : intern("Content-Type");
    _Content_Length = internToLowerCase ? HttpHeaderNames.CONTENT_LENGTH : intern("Content-Length");
    _Accept = internToLowerCase ? HttpHeaderNames.ACCEPT : intern("Accept");
  }

  private AsciiString intern(String name) {
    byte[] bytes = name.getBytes(StandardCharsets.UTF_8);
    return super.splitHeaderName(bytes, 0, bytes.length);
  }

  @Override
  protected AsciiString splitHeaderName(byte[] sb, int start, int length) {
    final byte firstChar = sb[start];
    if (firstChar == 'H' || firstChar == 'h') {
      if (length == 4 && isHost(sb, start)) {
        return firstChar == 'H' ? _Host : HttpHeaderNames.HOST;
      }
    } else if (firstChar == 'A' || firstChar == 'a') {
      if (length == 6 && isAccept(sb, start)) {
        return firstChar == 'A' ? _Accept : HttpHeaderNames.ACCEPT;
      }
    } else if (firstChar == 'C' || firstChar == 'c') {
      if (length == 10) {
        if (isConnection(sb, start)) {
          return firstChar == 'C' ? _Connection : HttpHeaderNames.CONNECTION;
        }
      } else if (length == 12) {
        if (isContentType(sb, start)) {
          return firstChar == 'C' ? _Content_Type : HttpHeaderNames.CONTENT_TYPE;
        }
      } else if (length == 14) {
        if (isContentLength(sb, start)) {
          return firstChar == 'C' ? _Content_Length : HttpHeaderNames.CONTENT_LENGTH;
        }
      }
    }
    return new AsciiString(sb, start, length, true);
  }

  private static boolean isAccept(byte[] sb, int start) {
    final long maybeAccept = sb[start + 1] << 8 |
      sb[start + 2] << 16 |
      sb[start + 3] << 24 |
      (long) sb[start + 4] << 32 |
      (long) sb[start + 5] << 40;
    return maybeAccept == ACCEPT_AS_LONG;
  }

  private static boolean isHost(byte[] sb, int start) {
    final int maybeHost = sb[start + 1] << 8 |
      sb[start + 2] << 16 |
      sb[start + 3] << 24;
    int a = HOST_AS_INT;
    return maybeHost == HOST_AS_INT;
  }

  private static boolean isConnection(byte[] sb, int start) {
    final long maybeConnecti = sb[start + 1] << 8 |
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

  private static boolean isContentType(byte[] sb, int start) {
    final long maybeContent = sb[start + 1] << 8 |
      sb[start + 2] << 16 |
      sb[start + 3] << 24 |
      (long) sb[start + 4] << 32 |
      (long) sb[start + 5] << 40 |
      (long) sb[start + 6] << 48 |
      (long) sb[start + 7] << 56;
    if (maybeContent != CONTENT_AS_LONG) {
      return false;
    }
    final int maybeType = sb[start + 9] << 8 |
      sb[start + 10] << 16 |
      sb[start + 11] << 24;
    return maybeType == TYPE_AS_INT;
  }

  private static boolean isContentLength(byte[] sb, int start) {
    final long maybeContent = sb[start + 1] << 8 |
      sb[start + 2] << 16 |
      sb[start + 3] << 24 |
      (long) sb[start + 4] << 32 |
      (long) sb[start + 5] << 40 |
      (long) sb[start + 6] << 48 |
      (long) sb[start + 7] << 56;
    if (maybeContent != CONTENT_AS_LONG) {
      return false;
    }
    final long maybeLength = sb[start + 9] << 8 |
      sb[start + 10] << 16 |
      sb[start + 11] << 24 |
      (long) sb[start + 12] << 32 |
      (long) sb[start + 13] << 40;
    return maybeLength == LENGTH_AS_LONG;
  }

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
