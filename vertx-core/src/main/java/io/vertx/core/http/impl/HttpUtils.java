/*
 * Copyright (c) 2011-2023 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */

package io.vertx.core.http.impl;


import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.handler.codec.http.*;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http2.Http2Settings;
import io.netty.incubator.codec.quic.QuicStreamPriority;
import io.netty.util.AsciiString;
import io.netty.util.CharsetUtil;
import io.vertx.core.Future;
import io.vertx.core.MultiMap;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.file.AsyncFile;
import io.vertx.core.file.FileSystem;
import io.vertx.core.file.OpenOptions;
import io.vertx.core.http.*;
import io.vertx.core.internal.ContextInternal;
import io.vertx.core.internal.VertxInternal;
import io.vertx.core.net.HostAndPort;
import io.vertx.core.net.impl.HostAndPortImpl;
import io.vertx.core.spi.tracing.TagExtractor;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import static io.netty.handler.codec.http.HttpHeaderValues.APPLICATION_X_WWW_FORM_URLENCODED;
import static io.netty.handler.codec.http.HttpHeaderValues.MULTIPART_FORM_DATA;
import static io.netty.handler.codec.http.HttpResponseStatus.METHOD_NOT_ALLOWED;
import static io.netty.handler.codec.http.HttpVersion.HTTP_1_1;
import static io.vertx.core.http.Http2Settings.*;

/**
 * Various http utils.
 *
 * @author <a href="mailto:nmaurer@redhat.com">Norman Maurer</a>
 */
public final class HttpUtils {

  static final HttpClosedException CONNECTION_CLOSED_EXCEPTION = new HttpClosedException("Connection was closed");
  static final HttpClosedException STREAM_CLOSED_EXCEPTION = new HttpClosedException("Stream was closed");
  static final int SC_SWITCHING_PROTOCOLS = 101;
  static final int SC_BAD_GATEWAY = 502;

  static final TagExtractor<HttpServerRequest> SERVER_REQUEST_TAG_EXTRACTOR = new TagExtractor<HttpServerRequest>() {
    @Override
    public int len(HttpServerRequest req) {
      return req.query() == null ? 4 : 5;
    }
    @Override
    public String name(HttpServerRequest req, int index) {
      switch (index) {
        case 0:
          return "http.url";
        case 1:
          return "http.method";
        case 2:
          return "http.scheme";
        case 3:
          return "http.path";
        case 4:
          return "http.query";
      }
      throw new IndexOutOfBoundsException("Invalid tag index " + index);
    }
    @Override
    public String value(HttpServerRequest req, int index) {
      switch (index) {
        case 0:
          return req.absoluteURI();
        case 1:
          return req.method().name();
        case 2:
          return req.scheme();
        case 3:
          return req.path();
        case 4:
          return req.query();
      }
      throw new IndexOutOfBoundsException("Invalid tag index " + index);
    }
  };

  static final TagExtractor<HttpServerResponse> SERVER_RESPONSE_TAG_EXTRACTOR = new TagExtractor<HttpServerResponse>() {
    @Override
    public int len(HttpServerResponse resp) {
      return 1;
    }
    @Override
    public String name(HttpServerResponse resp, int index) {
      if (index == 0) {
        return "http.status_code";
      }
      throw new IndexOutOfBoundsException("Invalid tag index " + index);
    }
    @Override
    public String value(HttpServerResponse resp, int index) {
      if (index == 0) {
        return "" + resp.getStatusCode();
      }
      throw new IndexOutOfBoundsException("Invalid tag index " + index);
    }
  };

  static final TagExtractor<HttpRequestHead> CLIENT_HTTP_REQUEST_TAG_EXTRACTOR = new TagExtractor<HttpRequestHead>() {
    @Override
    public int len(HttpRequestHead req) {
      return 2;
    }
    @Override
    public String name(HttpRequestHead req, int index) {
      switch (index) {
        case 0:
          return "http.url";
        case 1:
          return "http.method";
      }
      throw new IndexOutOfBoundsException("Invalid tag index " + index);
    }
    @Override
    public String value(HttpRequestHead req, int index) {
      switch (index) {
        case 0:
          return req.absoluteURI;
        case 1:
          return req.method.name();
      }
      throw new IndexOutOfBoundsException("Invalid tag index " + index);
    }
  };

  static final TagExtractor<HttpResponseHead> CLIENT_RESPONSE_TAG_EXTRACTOR = new TagExtractor<HttpResponseHead>() {
    @Override
    public int len(HttpResponseHead resp) {
      return 1;
    }
    @Override
    public String name(HttpResponseHead resp, int index) {
      if (index == 0) {
        return "http.status_code";
      }
      throw new IndexOutOfBoundsException("Invalid tag index " + index);
    }
    @Override
    public String value(HttpResponseHead resp, int index) {
      if (index == 0) {
        return "" + resp.statusCode;
      }
      throw new IndexOutOfBoundsException("Invalid tag index " + index);
    }
  };

  static final StreamPriorityBase DEFAULT_STREAM_PRIORITY = new Http2StreamPriority(new StreamPriority() {
    @Override
    public StreamPriority setWeight(short weight) {
      throw new UnsupportedOperationException("Unmodifiable stream priority");
    }

    @Override
    public StreamPriority setDependency(int dependency) {
      throw new UnsupportedOperationException("Unmodifiable stream priority");
    }

    @Override
    public StreamPriority setExclusive(boolean exclusive) {
      throw new UnsupportedOperationException("Unmodifiable stream priority");
    }
  });

  static final StreamPriorityBase DEFAULT_QUIC_STREAM_PRIORITY = new Http3StreamPriority(new QuicStreamPriority(0, true));

  private HttpUtils() {
  }

  private static int indexOfSlash(CharSequence str, int start) {
    for (int i = start; i < str.length(); i++) {
      if (str.charAt(i) == '/') {
        return i;
      }
    }

    return -1;
  }

  private static boolean matches(CharSequence path, int start, String what) {
    return matches(path, start, what, false);
  }

  private static boolean matches(CharSequence path, int start, String what, boolean exact) {
    if (exact) {
      if (path.length() - start != what.length()) {
        return false;
      }
    }

    if (path.length() - start >= what.length()) {
      for (int i = 0; i < what.length(); i++) {
        if (path.charAt(start + i) != what.charAt(i)) {
          return false;
        }
      }
      return true;
    }

    return false;
  }

  /**
   * Normalizes a path as per <a href="http://tools.ietf.org/html/rfc3986#section-5.2.4>rfc3986</a>.
   *
   * There are 2 extra transformations that are not part of the spec but kept for backwards compatibility:
   *
   * double slash // will be converted to single slash and the path will always start with slash.
   *
   * Null paths are not normalized as nothing can be said about them.
   *
   * @param pathname raw path
   * @return normalized path
   */
  public static String normalizePath(String pathname) {
    if (pathname == null) {
      return null;
    }

    // add trailing slash if not set
    if (pathname.isEmpty()) {
      return "/";
    }

    int indexOfFirstPercent = pathname.indexOf('%');
    if (indexOfFirstPercent == -1) {
      // no need to removeDots nor replace double slashes
      if (pathname.indexOf('.') == -1 && pathname.indexOf("//") == -1) {
        if (pathname.charAt(0) == '/') {
          return pathname;
        }
        // See https://bugs.openjdk.org/browse/JDK-8085796
        return "/" + pathname;
      }
    }
    return normalizePathSlow(pathname, indexOfFirstPercent);
  }

  private static String normalizePathSlow(String pathname, int indexOfFirstPercent) {
    final StringBuilder ibuf;
    // Not standard!!!
    if (pathname.charAt(0) != '/') {
      ibuf = new StringBuilder(pathname.length() + 1);
      ibuf.append('/');
      if (indexOfFirstPercent != -1) {
        indexOfFirstPercent++;
      }
    } else {
      ibuf = new StringBuilder(pathname.length());
    }
    ibuf.append(pathname);
    if (indexOfFirstPercent != -1) {
      decodeUnreservedChars(ibuf, indexOfFirstPercent);
    }
    // remove dots as described in
    // http://tools.ietf.org/html/rfc3986#section-5.2.4
    return removeDots(ibuf);
  }

  private static void decodeUnreservedChars(StringBuilder path, int start) {
    while (start < path.length()) {
      // decode unreserved chars described in
      // http://tools.ietf.org/html/rfc3986#section-2.4
      if (path.charAt(start) == '%') {
        decodeUnreserved(path, start);
      }

      start++;
    }
  }

  private static void decodeUnreserved(StringBuilder path, int start) {
    if (start + 3 <= path.length()) {
      // these are latin chars so there is no danger of falling into some special unicode char that requires more
      // than 1 byte
      final String escapeSequence = path.substring(start + 1, start + 3);
      int unescaped;
      try {
        unescaped = Integer.parseInt(escapeSequence, 16);
        if (unescaped < 0) {
          throw new IllegalArgumentException("Invalid escape sequence: %" + escapeSequence);
        }
      } catch (NumberFormatException e) {
        throw new IllegalArgumentException("Invalid escape sequence: %" + escapeSequence);
      }
      // validate if the octet is within the allowed ranges
      if (
        // ALPHA
        (unescaped >= 0x41 && unescaped <= 0x5A) ||
          (unescaped >= 0x61 && unescaped <= 0x7A) ||
          // DIGIT
          (unescaped >= 0x30 && unescaped <= 0x39) ||
          // HYPHEN
          (unescaped == 0x2D) ||
          // PERIOD
          (unescaped == 0x2E) ||
          // UNDERSCORE
          (unescaped == 0x5F) ||
          // TILDE
          (unescaped == 0x7E)) {

        path.setCharAt(start, (char) unescaped);
        path.delete(start + 1, start + 3);
      }
    } else {
      throw new IllegalArgumentException("Invalid position for escape character: " + start);
    }
  }

  /**
   * Removed dots as per <a href="http://tools.ietf.org/html/rfc3986#section-5.2.4>rfc3986</a>.
   *
   * There is 1 extra transformation that are not part of the spec but kept for backwards compatibility:
   *
   * double slash // will be converted to single slash.
   *
   * @param path raw path
   * @return normalized path
   */
  public static String removeDots(CharSequence path) {

    if (path == null) {
      return null;
    }

    final StringBuilder obuf = new StringBuilder(path.length());

    int i = 0;
    while (i < path.length()) {
      // remove dots as described in
      // http://tools.ietf.org/html/rfc3986#section-5.2.4
      if (matches(path, i, "./")) {
        i += 2;
      } else if (matches(path, i, "../")) {
        i += 3;
      } else if (matches(path, i, "/./")) {
        // preserve last slash
        i += 2;
      } else if (matches(path, i,"/.", true)) {
        path = "/";
        i = 0;
      } else if (matches(path, i, "/../")) {
        // preserve last slash
        i += 3;
        int pos = obuf.lastIndexOf("/");
        if (pos != -1) {
          obuf.delete(pos, obuf.length());
        }
      } else if (matches(path, i, "/..", true)) {
        path = "/";
        i = 0;
        int pos = obuf.lastIndexOf("/");
        if (pos != -1) {
          obuf.delete(pos, obuf.length());
        }
      } else if (matches(path, i, ".", true) || matches(path, i, "..", true)) {
        break;
      } else {
        if (path.charAt(i) == '/') {
          i++;
          // Not standard!!!
          // but common // -> /
          if (obuf.length() == 0 || obuf.charAt(obuf.length() - 1) != '/') {
            obuf.append('/');
          }
        }
        int pos = indexOfSlash(path, i);
        if (pos != -1) {
          obuf.append(path, i, pos);
          i = pos;
        } else {
          obuf.append(path, i, path.length());
          break;
        }
      }
    }

    return obuf.toString();
  }

  /**
   * Resolve an URI reference as per <a href="http://tools.ietf.org/html/rfc3986#section-5.2.4>rfc3986</a>
   */
  public static URI resolveURIReference(String base, String ref) throws URISyntaxException {
    return resolveURIReference(URI.create(base), ref);
  }

  /**
   * Resolve an URI reference as per <a href="http://tools.ietf.org/html/rfc3986#section-5.2.4>rfc3986</a>
   */
  public static URI resolveURIReference(URI base, String ref) throws URISyntaxException {
    URI _ref = URI.create(ref);
    String scheme;
    String authority;
    String path;
    String query;
    if (_ref.getScheme() != null) {
      scheme = _ref.getScheme();
      authority = _ref.getAuthority();
      path = removeDots(_ref.getRawPath());
      query = _ref.getRawQuery();
    } else {
      if (_ref.getAuthority() != null) {
        authority = _ref.getAuthority();
        path = _ref.getRawPath();
        query = _ref.getRawQuery();
      } else {
        if (_ref.getRawPath().length() == 0) {
          path = base.getRawPath();
          if (_ref.getRawQuery() != null) {
            query = _ref.getRawQuery();
          } else {
            query = base.getRawQuery();
          }
        } else {
          if (_ref.getRawPath().startsWith("/")) {
            path = removeDots(_ref.getRawPath());
          } else {
            // Merge paths
            String mergedPath;
            String basePath = base.getRawPath();
            if (base.getAuthority() != null && basePath.length() == 0) {
              mergedPath = "/" + _ref.getRawPath();
            } else {
              int index = basePath.lastIndexOf('/');
              if (index > -1) {
                mergedPath = basePath.substring(0, index + 1) + _ref.getRawPath();
              } else {
                mergedPath = _ref.getRawPath();
              }
            }
            path = removeDots(mergedPath);
          }
          query = _ref.getRawQuery();
        }
        authority = base.getAuthority();
      }
      scheme = base.getScheme();
    }
    return new URI(scheme, authority, path, query, _ref.getFragment());
  }

  /**
   * Extract the path out of the uri.
   */
  static String parsePath(String uri) {
    if (uri.length() == 0) {
      return "";
    }
    int i;
    if (uri.charAt(0) == '/') {
      i = 0;
    } else {
      i = uri.indexOf("://");
      if (i == -1) {
        i = 0;
      } else {
        i = uri.indexOf('/', i + 3);
        if (i == -1) {
          // contains no /
          return "/";
        }
      }
    }

    int queryStart = uri.indexOf('?', i);
    if (queryStart == -1) {
      queryStart = uri.length();
      if (i == 0) {
        return uri;
      }
    }
    return uri.substring(i, queryStart);
  }

  /**
   * Extract the query out of a uri or returns the empty string if no query was found.
   */
  static String parseQuery(String uri) {
    int i = uri.indexOf('?');
    if (i == -1) {
      return null;
    } else {
      return uri.substring(i + 1);
    }
  }

  static String absoluteURI(String serverOrigin, HttpServerRequest req) {
    String uri = req.uri();
    if ("*".equals(uri)) {
      return null;
    }
    if (uri.startsWith("https://") || uri.startsWith("http://")) {
      return uri;
    }
    String absoluteURI;
    boolean ssl = req.isSSL();
    HostAndPort authority = req.authority();
    if (authority != null) {
      StringBuilder sb = new StringBuilder(req.scheme()).append("://").append(authority.host());
      if (authority.port() > 0 && (ssl && authority.port() != 443) || (!ssl && authority.port() != 80)) {
        sb.append(':').append(authority.port());
      }
      sb.append(uri);
      absoluteURI = sb.toString();
    } else {
      // Fall back to the server origin
      absoluteURI = serverOrigin + uri;
    }
    return absoluteURI;
  }

  public static MultiMap params(String uri, Charset charset, boolean semicolonIsNormalChar) {
    QueryStringDecoder queryStringDecoder = new QueryStringDecoder(uri, charset, true, 1024, semicolonIsNormalChar);
    Map<String, List<String>> prms = queryStringDecoder.parameters();
    MultiMap params = MultiMap.caseInsensitiveMultiMap();
    if (!prms.isEmpty()) {
      for (Map.Entry<String, List<String>> entry: prms.entrySet()) {
        params.add(entry.getKey(), entry.getValue());
      }
    }
    return params;
  }

  public static void fromVertxInitialSettings(boolean server, io.vertx.core.http.Http2Settings vertxSettings, Http2Settings nettySettings) {
    if (vertxSettings != null) {
      if (!server && vertxSettings.isPushEnabled() != DEFAULT_ENABLE_PUSH) {
        nettySettings.pushEnabled(vertxSettings.isPushEnabled());
      }
      if (vertxSettings.getHeaderTableSize() != DEFAULT_HEADER_TABLE_SIZE) {
        nettySettings.put('\u0001', (Long)vertxSettings.getHeaderTableSize());
      }
      if (vertxSettings.getInitialWindowSize() != DEFAULT_INITIAL_WINDOW_SIZE) {
        nettySettings.initialWindowSize(vertxSettings.getInitialWindowSize());
      }
      if (vertxSettings.getMaxConcurrentStreams() != DEFAULT_MAX_CONCURRENT_STREAMS) {
        nettySettings.maxConcurrentStreams(vertxSettings.getMaxConcurrentStreams());
      }
      if (vertxSettings.getMaxFrameSize() != DEFAULT_MAX_FRAME_SIZE) {
        nettySettings.maxFrameSize(vertxSettings.getMaxFrameSize());
      }
      if (vertxSettings.getMaxHeaderListSize() != DEFAULT_MAX_HEADER_LIST_SIZE) {
        nettySettings.maxHeaderListSize(vertxSettings.getMaxHeaderListSize());
      }
      Map<Integer, Long> extraSettings = vertxSettings.getExtraSettings();
      if (extraSettings != null) {
        extraSettings.forEach((code, setting) -> {
          nettySettings.put((char)(int)code, setting);
        });
      }
    }
  }

  public static Http2Settings fromVertxSettings(io.vertx.core.http.Http2Settings settings) {
    Http2Settings converted = new Http2Settings();
    converted.pushEnabled(settings.isPushEnabled());
    converted.maxFrameSize(settings.getMaxFrameSize());
    converted.initialWindowSize(settings.getInitialWindowSize());
    converted.headerTableSize(settings.getHeaderTableSize());
    converted.maxConcurrentStreams(settings.getMaxConcurrentStreams());
    converted.maxHeaderListSize(settings.getMaxHeaderListSize());
    if (settings.getExtraSettings() != null) {
      settings.getExtraSettings().forEach((key, value) -> {
        converted.put((char)(int)key, value);
      });
    }
    return converted;
  }

  public static io.vertx.core.http.Http2Settings toVertxSettings(Http2Settings settings) {
    io.vertx.core.http.Http2Settings converted = new io.vertx.core.http.Http2Settings();
    Boolean pushEnabled = settings.pushEnabled();
    if (pushEnabled != null) {
      converted.setPushEnabled(pushEnabled);
    }
    Long maxConcurrentStreams = settings.maxConcurrentStreams();
    if (maxConcurrentStreams != null) {
      converted.setMaxConcurrentStreams(maxConcurrentStreams);
    }
    Long maxHeaderListSize = settings.maxHeaderListSize();
    if (maxHeaderListSize != null) {
      converted.setMaxHeaderListSize(maxHeaderListSize);
    }
    Integer maxFrameSize = settings.maxFrameSize();
    if (maxFrameSize != null) {
      converted.setMaxFrameSize(maxFrameSize);
    }
    Integer initialWindowSize = settings.initialWindowSize();
    if (initialWindowSize != null) {
      converted.setInitialWindowSize(initialWindowSize);
    }
    Long headerTableSize = settings.headerTableSize();
    if (headerTableSize != null) {
      converted.setHeaderTableSize(headerTableSize);
    }
    settings.forEach((key, value) -> {
      if (key > 6) {
        converted.set(key, value);
      }
    });
    return converted;
  }

  static Http2Settings decodeSettings(String base64Settings) {
    try {
      Http2Settings settings = new Http2Settings();
      Buffer buffer = Buffer.buffer(Base64.getUrlDecoder().decode(base64Settings));
      int pos = 0;
      int len = buffer.length();
      while (pos < len) {
        int i = buffer.getUnsignedShort(pos);
        pos += 2;
        long j = buffer.getUnsignedInt(pos);
        pos += 4;
        settings.put((char)i, (Long)j);
      }
      return settings;
    } catch (Exception ignore) {
    }
    return null;
  }

  public static String encodeSettings(io.vertx.core.http.Http2Settings settings) {
    Buffer buffer = Buffer.buffer();
    fromVertxSettings(settings).forEach((c, l) -> {
      buffer.appendUnsignedShort(c);
      buffer.appendUnsignedInt(l);
    });
    return Base64.getUrlEncoder().encodeToString(buffer.getBytes());
  }

  public static ByteBuf generateWSCloseFrameByteBuf(short statusCode, String reason) {
    if (reason != null)
      return Unpooled.copiedBuffer(
        Unpooled.copyShort(statusCode), // First two bytes are reserved for status code
        Unpooled.copiedBuffer(reason, StandardCharsets.UTF_8)
      );
    else
      return Unpooled.copyShort(statusCode);
  }

  static void sendError(Channel ch, HttpResponseStatus status) {
    sendError(ch, status, status.reasonPhrase());
  }

  static void sendError(Channel ch, HttpResponseStatus status, CharSequence err) {
    FullHttpResponse resp = new DefaultFullHttpResponse(HTTP_1_1, status);
    if (status.code() == METHOD_NOT_ALLOWED.code()) {
      // SockJS requires this
      resp.headers().set(io.vertx.core.http.HttpHeaders.ALLOW, io.vertx.core.http.HttpHeaders.GET);
    }
    if (err != null) {
      resp.content().writeBytes(err.toString().getBytes(CharsetUtil.UTF_8));
      HttpUtil.setContentLength(resp, err.length());
    } else {
      HttpUtil.setContentLength(resp, 0);
    }
    ch.writeAndFlush(resp);
  }

  static String getWebSocketLocation(HttpServerRequest req, boolean ssl) throws Exception {
    String prefix;
    if (ssl) {
      prefix = "wss://";
    } else {
      prefix = "ws://";
    }
    URI uri = new URI(req.uri());
    String path = uri.getRawPath();
    String loc = prefix + req.headers().get(HttpHeaderNames.HOST) + path;
    String query = uri.getRawQuery();
    if (query != null) {
      loc += "?" + query;
    }
    return loc;
  }

  /**
   * @return convert the {@code sequence} to a lower case instance
   */
  public static CharSequence toLowerCase(CharSequence sequence) {
    StringBuilder buffer = null;
    int len = sequence.length();
    for (int index = 0; index < len; index++) {
      char c = sequence.charAt(index);
      if (c >= 'A' && c <= 'Z') {
        if (buffer == null) {
          buffer = new StringBuilder(sequence);
        }
        buffer.setCharAt(index, (char)(c + ('a' - 'A')));
      }
    }
    if (buffer != null) {
      return buffer.toString();
    } else {
      return sequence;
    }
  }

  static HttpVersion toNettyHttpVersion(io.vertx.core.http.HttpVersion version) {
    switch (version) {
      case HTTP_1_0: {
        return HttpVersion.HTTP_1_0;
      }
      case HTTP_1_1: {
        return HttpVersion.HTTP_1_1;
      }
      default:
        throw new IllegalArgumentException("Unsupported HTTP version: " + version);
    }
  }

  static io.vertx.core.http.HttpMethod toVertxMethod(String method) {
    return io.vertx.core.http.HttpMethod.valueOf(method);
  }

  private static final AsciiString TIMEOUT_EQ = AsciiString.of("timeout=");

  public static int parseKeepAliveHeaderTimeout(CharSequence value) {
    int len = value.length();
    int pos = 0;
    while (pos < len) {
      int idx = AsciiString.indexOf(value, ',', pos);
      int next;
      if (idx == -1) {
        idx = next = len;
      } else {
        next = idx + 1;
      }
      while (pos < idx && value.charAt(pos) == ' ') {
        pos++;
      }
      int to = idx;
      while (to > pos && value.charAt(to -1) == ' ') {
        to--;
      }
      if (AsciiString.regionMatches(value, true, pos, TIMEOUT_EQ, 0, TIMEOUT_EQ.length())) {
        pos += TIMEOUT_EQ.length();
        if (pos < to) {
          int ret = 0;
          while (pos < to) {
            int ch = value.charAt(pos++);
            if (ch >= '0' && ch < '9') {
              ret = ret * 10 + (ch - '0');
            } else {
              ret = -1;
              break;
            }
          }
          if (ret > -1) {
            return ret;
          }
        }
      }
      pos = next;
    }
    return -1;
  }

  private static final Consumer<CharSequence> HEADER_VALUE_VALIDATOR = HttpUtils::validateHeaderValue;

  public static void validateHeader(CharSequence name, CharSequence value) {
    validateHeaderName(name);
    if (value != null) {
      validateHeaderValue(value);
    }
  }

  public static void validateHeader(CharSequence name, Iterable<? extends CharSequence> values) {
    validateHeaderName(name);
    values.forEach(value -> {
      if (value != null) {
        HEADER_VALUE_VALIDATOR.accept(value);
      }
    });
  }

  public static void validateHeaderValue(CharSequence seq) {

    int state = 0;
    // Start looping through each of the character
    for (int index = 0; index < seq.length(); index++) {
      state = validateValueChar(seq, state, seq.charAt(index));
    }

    if (state != 0) {
      throw new IllegalArgumentException("a header value must not end with '\\r' or '\\n':" + seq);
    }
  }

  private static final int HIGHEST_INVALID_VALUE_CHAR_MASK = ~0x1F;

  private static int validateValueChar(CharSequence seq, int state, char character) {
    /*
     * State:
     * 0: Previous character was neither CR nor LF
     * 1: The previous character was CR
     * 2: The previous character was LF
     */
    if ((character & HIGHEST_INVALID_VALUE_CHAR_MASK) == 0 || character == 0x7F) { // 0x7F is "DEL".
      // The only characters allowed in the range 0x00-0x1F are : HTAB, LF and CR
      switch (character) {
        case 0x09: // Horizontal tab - HTAB
        case 0x0a: // Line feed - LF
        case 0x0d: // Carriage return - CR
          break;
        default:
          throw new IllegalArgumentException("a header value contains a prohibited character '" + (int) character + "': " + seq);
      }
    }

    // Check the CRLF (HT | SP) pattern
    switch (state) {
      case 0:
        switch (character) {
          case '\r':
            return 1;
          case '\n':
            return 2;
        }
        break;
      case 1:
        switch (character) {
          case '\n':
            return 2;
          default:
            throw new IllegalArgumentException("only '\\n' is allowed after '\\r': " + seq);
        }
      case 2:
        switch (character) {
          case '\t':
          case ' ':
            return 0;
          default:
            throw new IllegalArgumentException("only ' ' and '\\t' are allowed after '\\n': " + seq);
        }
    }
    return state;
  }

  private static final boolean[] VALID_H_NAME_ASCII_CHARS;

  static {
    VALID_H_NAME_ASCII_CHARS = new boolean[Byte.MAX_VALUE + 1];
    Arrays.fill(VALID_H_NAME_ASCII_CHARS, true);
    VALID_H_NAME_ASCII_CHARS[' '] = false;
    VALID_H_NAME_ASCII_CHARS['"'] = false;
    VALID_H_NAME_ASCII_CHARS['('] = false;
    VALID_H_NAME_ASCII_CHARS[')'] = false;
    VALID_H_NAME_ASCII_CHARS[','] = false;
    VALID_H_NAME_ASCII_CHARS['/'] = false;
    VALID_H_NAME_ASCII_CHARS[':'] = false;
    VALID_H_NAME_ASCII_CHARS[';'] = false;
    VALID_H_NAME_ASCII_CHARS['<'] = false;
    VALID_H_NAME_ASCII_CHARS['>'] = false;
    VALID_H_NAME_ASCII_CHARS['='] = false;
    VALID_H_NAME_ASCII_CHARS['?'] = false;
    VALID_H_NAME_ASCII_CHARS['@'] = false;
    VALID_H_NAME_ASCII_CHARS['['] = false;
    VALID_H_NAME_ASCII_CHARS[']'] = false;
    VALID_H_NAME_ASCII_CHARS['\\'] = false;
    VALID_H_NAME_ASCII_CHARS['{'] = false;
    VALID_H_NAME_ASCII_CHARS['}'] = false;
    VALID_H_NAME_ASCII_CHARS[0x7f] = false;
    // control characters are not valid
    for (int i = 0; i < 0x20; i++) {
      VALID_H_NAME_ASCII_CHARS[i] = false;
    }
  }

  public static void validateHeaderName(CharSequence value) {
    if (value instanceof AsciiString) {
      // no need to check for ASCII-ness anymore
      validateHeaderName((AsciiString) value);
    } else {
      validateHeaderName0(value);
    }
  }

  private static void validateHeaderName(AsciiString value) {
    final int len = value.length();
    final int off = value.arrayOffset();
    final byte[] asciiChars = value.array();
    for (int i = 0; i < len; i++) {
      // Check to see if the character is not an ASCII character, or invalid
      byte c = asciiChars[off + i];
      if (c < 0) {
        throw new IllegalArgumentException("a header name cannot contain non-ASCII character: " + value);
      }
      if (!VALID_H_NAME_ASCII_CHARS[c & 0x7F]) {
        throw new IllegalArgumentException("a header name cannot contain some prohibited characters, such as : " + value);
      }
    }
  }

  private static void validateHeaderName0(CharSequence value) {
    for (int i = 0; i < value.length(); i++) {
      final char c = value.charAt(i);
      // Check to see if the character is not an ASCII character, or invalid
      if (c > 0x7f) {
        throw new IllegalArgumentException("a header name cannot contain non-ASCII character: " + value);
      }
      if (!VALID_H_NAME_ASCII_CHARS[c & 0x7F]) {
        throw new IllegalArgumentException("a header name cannot contain some prohibited characters, such as : " + value);
      }
    }
  }

  public static boolean isValidMultipartContentType(String contentType) {
    return MULTIPART_FORM_DATA.regionMatches(true, 0, contentType, 0, MULTIPART_FORM_DATA.length())
      || APPLICATION_X_WWW_FORM_URLENCODED.regionMatches(true, 0, contentType, 0, APPLICATION_X_WWW_FORM_URLENCODED.length());
  }

  public static boolean isValidMultipartMethod(HttpMethod method) {
    return method.equals(HttpMethod.POST) || method.equals(HttpMethod.PUT) || method.equals(HttpMethod.PATCH)
      || method.equals(HttpMethod.DELETE);
  }

  static Future<AsyncFile> resolveFile(ContextInternal context, String filename, long offset, long length) {
    VertxInternal vertx = context.owner();
    File file_ = vertx.resolveFile(filename);
    if (!file_.exists()) {
      return context.failedFuture(new FileNotFoundException());
    }

    //We open the fileName using a RandomAccessFile to make sure that this is an actual file that can be read.
    //i.e is not a directory
    try (RandomAccessFile raf = new RandomAccessFile(file_, "r")) {
      FileSystem fs = vertx.fileSystem();
      return fs.open(filename, new OpenOptions().setCreate(false).setWrite(false))
        .transform(ar -> {
          if (ar.succeeded()) {
            AsyncFile file = ar.result();
            long contentLength = Math.min(length, file_.length() - offset);
            if (contentLength < 0) {
              file.close();
              return context.failedFuture("offset : " + offset + " is larger than the requested file length : " + file_.length());
            }
            file.setReadPos(offset);
            file.setReadLength(contentLength);
          }
          return (Future) ar;
        });
    } catch (IOException e) {
      return context.failedFuture(e);
    }
  }

  static boolean isConnectOrUpgrade(io.vertx.core.http.HttpMethod method, MultiMap headers) {
    if (method == io.vertx.core.http.HttpMethod.CONNECT) {
      return true;
    }
    if (method == io.vertx.core.http.HttpMethod.GET) {
      for (String connection : headers.getAll(io.vertx.core.http.HttpHeaders.CONNECTION)) {
        // Firefox doesn't send a normal 'Connection: Upgrade' header for WebSockets.
        // Instead, it sends: 'Connection: keep-alive, Upgrade'.
        if (AsciiString.containsIgnoreCase(connection, io.vertx.core.http.HttpHeaders.UPGRADE)) {
          return true;
        }
      }
    }
    return false;
  }

  static boolean isKeepAlive(HttpRequest request) {
    HttpVersion version = request.protocolVersion();
    return (version == HttpVersion.HTTP_1_1 && !request.headers().contains(io.vertx.core.http.HttpHeaders.CONNECTION, io.vertx.core.http.HttpHeaders.CLOSE, true))
      || (version == HttpVersion.HTTP_1_0 && request.headers().contains(io.vertx.core.http.HttpHeaders.CONNECTION, io.vertx.core.http.HttpHeaders.KEEP_ALIVE, true));
  }

  public static boolean isValidHostAuthority(String host) {
    int len = host.length();
    return HostAndPortImpl.parseHost(host, 0, len) == len;
  }

  public static boolean canUpgradeToWebSocket(HttpServerRequest req) {
    if (req.version() != io.vertx.core.http.HttpVersion.HTTP_1_1) {
      return false;
    }
    if (req.method() != io.vertx.core.http.HttpMethod.GET) {
      return false;
    }
    MultiMap headers = req.headers();
    for (String connection : headers.getAll(io.vertx.core.http.HttpHeaders.CONNECTION)) {
      if (AsciiString.containsIgnoreCase(connection, io.vertx.core.http.HttpHeaders.UPGRADE)) {
        for (String upgrade : headers.getAll(io.vertx.core.http.HttpHeaders.UPGRADE)) {
          if (AsciiString.containsIgnoreCase(upgrade, io.vertx.core.http.HttpHeaders.WEBSOCKET)) {
            return true;
          }
        }
      }
    }
    return false;
  }
}
