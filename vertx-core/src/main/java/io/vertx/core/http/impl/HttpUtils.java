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
import io.netty.handler.codec.http2.Http2Settings;
import io.netty.incubator.codec.http3.DefaultHttp3SettingsFrame;
import io.netty.incubator.codec.http3.Http3SettingsFrame;
import io.netty.incubator.codec.quic.QuicStreamPriority;
import io.netty.util.AsciiString;
import io.netty.util.CharsetUtil;
import io.vertx.core.Future;
import io.vertx.core.MultiMap;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.file.AsyncFile;
import io.vertx.core.file.FileSystem;
import io.vertx.core.file.OpenOptions;
import io.vertx.core.http.Http2StreamPriority;
import io.vertx.core.http.Http3Settings;
import io.vertx.core.http.Http3StreamPriority;
import io.vertx.core.http.HttpClosedException;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.http.StreamPriority;
import io.vertx.core.http.StreamPriorityBase;
import io.vertx.core.internal.ContextInternal;
import io.vertx.core.internal.VertxInternal;
import io.vertx.core.internal.net.RFC3986;
import io.vertx.core.net.HostAndPort;
import io.vertx.core.net.impl.HostAndPortImpl;
import io.vertx.core.spi.tracing.TagExtractor;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashMap;
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

  public static final HttpClosedException CONNECTION_CLOSED_EXCEPTION = new HttpClosedException("Connection was closed");
  public static final HttpClosedException STREAM_CLOSED_EXCEPTION = new HttpClosedException("Stream was closed");
  public static final int SC_SWITCHING_PROTOCOLS = 101;
  public static final int SC_BAD_GATEWAY = 502;

  public static final TagExtractor<HttpServerRequest> SERVER_REQUEST_TAG_EXTRACTOR = new TagExtractor<>() {
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
          return "http.request.method";
        case 2:
          return "url.scheme";
        case 3:
          return "url.path";
        case 4:
          return "url.query";
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

  public static final TagExtractor<io.vertx.core.spi.observability.HttpResponse> SERVER_RESPONSE_TAG_EXTRACTOR = new TagExtractor<>() {
    @Override
    public int len(io.vertx.core.spi.observability.HttpResponse resp) {
      return 1;
    }

    @Override
    public String name(io.vertx.core.spi.observability.HttpResponse resp, int index) {
      if (index == 0) {
        return "http.response.status_code";
      }
      throw new IndexOutOfBoundsException("Invalid tag index " + index);
    }

    @Override
    public String value(io.vertx.core.spi.observability.HttpResponse resp, int index) {
      if (index == 0) {
        return Integer.toString(resp.statusCode());
      }
      throw new IndexOutOfBoundsException("Invalid tag index " + index);
    }
  };

  public static final TagExtractor<HttpRequestHead> CLIENT_HTTP_REQUEST_TAG_EXTRACTOR = new TagExtractor<>() {
    @Override
    public int len(HttpRequestHead req) {
      return 2;
    }

    @Override
    public String name(HttpRequestHead req, int index) {
      switch (index) {
        case 0:
          return "url.full";
        case 1:
          return "http.request.method";
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

  public static final TagExtractor<HttpResponseHead> CLIENT_RESPONSE_TAG_EXTRACTOR = new TagExtractor<>() {
    @Override
    public int len(HttpResponseHead resp) {
      return 1;
    }

    @Override
    public String name(HttpResponseHead resp, int index) {
      if (index == 0) {
        return "http.response.status_code";
      }
      throw new IndexOutOfBoundsException("Invalid tag index " + index);
    }

    @Override
    public String value(HttpResponseHead resp, int index) {
      if (index == 0) {
        return Integer.toString(resp.statusCode);
      }
      throw new IndexOutOfBoundsException("Invalid tag index " + index);
    }
  };

  public static final StreamPriorityBase DEFAULT_STREAM_PRIORITY = new Http2StreamPriority() {
    @Override
    public Http2StreamPriority setWeight(short weight) {
      throw new UnsupportedOperationException("Unmodifiable stream priority");
    }

    @Override
    public Http2StreamPriority setDependency(int dependency) {
      throw new UnsupportedOperationException("Unmodifiable stream priority");
    }

    @Override
    public Http2StreamPriority setExclusive(boolean exclusive) {
      throw new UnsupportedOperationException("Unmodifiable stream priority");
    }
  };

  public static final StreamPriorityBase DEFAULT_QUIC_STREAM_PRIORITY = new Http3StreamPriority(new QuicStreamPriority(0, true));

  private HttpUtils() {
  }

  public static StreamPriorityBase getDefaultStreamPriority(io.vertx.core.http.HttpVersion version){
    return version == io.vertx.core.http.HttpVersion.HTTP_3 ? DEFAULT_QUIC_STREAM_PRIORITY : DEFAULT_STREAM_PRIORITY;
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
      path = RFC3986.removeDotSegments(_ref.getRawPath());
      query = _ref.getRawQuery();
    } else {
      if (_ref.getAuthority() != null) {
        authority = _ref.getAuthority();
        path = _ref.getRawPath();
        query = _ref.getRawQuery();
      } else {
        if (_ref.getRawPath().isEmpty()) {
          path = base.getRawPath();
          if (_ref.getRawQuery() != null) {
            query = _ref.getRawQuery();
          } else {
            query = base.getRawQuery();
          }
        } else {
          if (_ref.getRawPath().startsWith("/")) {
            path = RFC3986.removeDotSegments(_ref.getRawPath());
          } else {
            // Merge paths
            String mergedPath;
            String basePath = base.getRawPath();
            if (base.getAuthority() != null && basePath.isEmpty()) {
              mergedPath = "/" + _ref.getRawPath();
            } else {
              int index = basePath.lastIndexOf('/');
              if (index > -1) {
                mergedPath = basePath.substring(0, index + 1) + _ref.getRawPath();
              } else {
                mergedPath = _ref.getRawPath();
              }
            }
            path = RFC3986.removeDotSegments(mergedPath);
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
  public static String parsePath(String uri) {
    if (uri.isEmpty()) {
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
  public static String parseQuery(String uri) {
    int i = uri.indexOf('?');
    if (i == -1) {
      return null;
    } else {
      return uri.substring(i + 1);
    }
  }

  public static String absoluteURI(String serverOrigin, HttpServerRequest req) {
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

  public static Http2Settings fromVertxInitialSettings(boolean server, io.vertx.core.http.Http2Settings vertxSettings) {
    Http2Settings nettySettings = new Http2Settings();
    fromVertxInitialSettings(server, vertxSettings, nettySettings);
    return nettySettings;
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
  public static Http3SettingsFrame fromVertxSettings(io.vertx.core.http.Http3Settings settings) {
    Http3SettingsFrame converted = new DefaultHttp3SettingsFrame();
    converted.put(Http3SettingsFrame.HTTP3_SETTINGS_QPACK_MAX_TABLE_CAPACITY, settings.getQpackMaxTableCapacity());
    converted.put(Http3SettingsFrame.HTTP3_SETTINGS_MAX_FIELD_SECTION_SIZE, settings.getMaxFieldSectionSize());
    converted.put(Http3SettingsFrame.HTTP3_SETTINGS_QPACK_BLOCKED_STREAMS, settings.getQpackMaxBlockedStreams());
    converted.put(Http3Settings.HTTP3_SETTINGS_ENABLE_CONNECT_PROTOCOL, settings.getEnableConnectProtocol());
    converted.put(Http3Settings.HTTP3_SETTINGS_H3_DATAGRAM, settings.getH3Datagram());
    converted.put(Http3Settings.HTTP3_SETTINGS_ENABLE_METADATA, settings.getEnableMetadata());
    if (settings.getExtraSettings() != null) {
      settings.getExtraSettings().forEach((key, value) -> {
        if (Http3Settings.VALID_H3_SETTINGS_KEYS.contains(key)) {
          converted.put(key, value);
        }
      });
    }
    return converted;
  }

  public static io.vertx.core.http.Http3Settings toVertxSettings(Http3SettingsFrame settings) {
    Http3Settings http3Settings = new Http3Settings();
    http3Settings.setQpackMaxTableCapacity(
      settings.getOrDefault(Http3SettingsFrame.HTTP3_SETTINGS_QPACK_MAX_TABLE_CAPACITY,
        Http3Settings.DEFAULT_QPACK_MAX_TABLE_CAPACITY));
    http3Settings.setMaxFieldSectionSize(settings.getOrDefault(Http3SettingsFrame.HTTP3_SETTINGS_MAX_FIELD_SECTION_SIZE,
      Http3Settings.DEFAULT_MAX_FIELD_SECTION_SIZE));
    http3Settings.setQpackMaxBlockedStreams(
      Math.toIntExact(settings.getOrDefault(Http3SettingsFrame.HTTP3_SETTINGS_QPACK_BLOCKED_STREAMS,
        Http3Settings.DEFAULT_QPACK_BLOCKED_STREAMS)));
    http3Settings.setEnableConnectProtocol(settings.getOrDefault(Http3Settings.HTTP3_SETTINGS_ENABLE_CONNECT_PROTOCOL,
      Http3Settings.DEFAULT_ENABLE_CONNECT_PROTOCOL));
    http3Settings.setH3Datagram(settings.getOrDefault(Http3Settings.HTTP3_SETTINGS_H3_DATAGRAM,
      Http3Settings.DEFAULT_H3_DATAGRAM));
    http3Settings.setEnableMetadata(settings.getOrDefault(Http3Settings.HTTP3_SETTINGS_ENABLE_METADATA,
      Http3Settings.DEFAULT_ENABLE_METADATA));

    http3Settings.setExtraSettings(Http3Settings.DEFAULT_EXTRA_SETTINGS);

    settings.forEach(entry -> {
      if (!Http3Settings.SETTING_KEYS.contains(entry.getKey())) {
        if (http3Settings.getExtraSettings() == null) {
          http3Settings.setExtraSettings(new HashMap<>());
        }
        http3Settings.getExtraSettings().put(entry.getKey(), entry.getValue());
      }
    });
    return http3Settings;
  }

  public static Http2Settings decodeSettings(String base64Settings) {
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

  public static void validateHeaderValue(CharSequence value) {
    if (value instanceof AsciiString) {
      validateAsciiHeaderValue((AsciiString) value);
    } else if (value instanceof String) {
      validateStringHeaderValue((String) value);
    } else {
      validateSequenceHeaderValue(value);
    }
  }

  private static void validateAsciiHeaderValue(AsciiString value) {
    final int length = value.length();
    if (length == 0) {
      return;
    }
    byte[] asciiChars = value.array();
    int off = value.arrayOffset();
    if (off == 0 && length == asciiChars.length) {
      for (int index = 0; index < asciiChars.length; index++) {
        int latinChar = asciiChars[index] & 0xFF;
        if (latinChar == 0x7F) {
          throw new IllegalArgumentException("a header value contains a prohibited character '127': " + value);
        }
        // non-printable chars are rare so let's make it a fall-back method, whilst still accepting HTAB
        if (latinChar < 32 && latinChar != 0x09) {
          validateSequenceHeaderValue(value, index - off);
          break;
        }
      }
    } else {
      validateAsciiRangeHeaderValue(value, off, length, asciiChars);
    }
  }

  /**
   * This method is the slow-path generic version of {@link #validateAsciiHeaderValue(AsciiString)} which
   * is optimized for {@link AsciiString} instances which are backed by a 0-offset full-blown byte array.
   */
  private static void validateAsciiRangeHeaderValue(AsciiString value, int off, int length, byte[] asciiChars) {
    int end = off + length;
    for (int index = off; index < end; index++) {
      int latinChar = asciiChars[index] & 0xFF;
      if (latinChar == 0x7F) {
        throw new IllegalArgumentException("a header value contains a prohibited character '127': " + value);
      }
      // non-printable chars are rare so let's make it a fall-back method, whilst still accepting HTAB
      if (latinChar < 32 && latinChar != 0x09) {
        validateSequenceHeaderValue(value, index - off);
        break;
      }
    }
  }

  private static void validateStringHeaderValue(String value) {
    final int length = value.length();
    if (length == 0) {
      return;
    }

    for (int index = 0; index < length; index++) {
      char latinChar = value.charAt(index);
      if (latinChar == 0x7F) {
        throw new IllegalArgumentException("a header value contains a prohibited character '127': " + value);
      }
      // non-printable chars are rare so let's make it a fall-back method, whilst still accepting HTAB
      if (latinChar < 32 && latinChar != 0x09) {
        validateSequenceHeaderValue(value, index);
        break;
      }
    }
  }

  private static void validateSequenceHeaderValue(CharSequence value) {
    final int length = value.length();
    if (length == 0) {
      return;
    }

    for (int index = 0; index < length; index++) {
      char latinChar = value.charAt(index);
      if (latinChar == 0x7F) {
        throw new IllegalArgumentException("a header value contains a prohibited character '127': " + value);
      }
      // non-printable chars are rare so let's make it a fall-back method, whilst still accepting HTAB
      if (latinChar < 32 && latinChar != 0x09) {
        validateSequenceHeaderValue(value, index);
        break;
      }
    }
  }

  private static final int HIGHEST_INVALID_VALUE_CHAR_MASK = ~0x1F;
  private static final int NO_CR_LF_STATE = 0;
  private static final int CR_STATE = 1;
  private static final int LF_STATE = 2;

  /**
   * This method is taken as we need to validate the header value for the non-printable characters.
   */
  private static void validateSequenceHeaderValue(CharSequence seq, int index) {
      // we already expect the very-first character to be non-printable
      int state = validateValueChar(seq, NO_CR_LF_STATE, seq.charAt(index));
      for (int i = index + 1; i < seq.length(); i++) {
        state = validateValueChar(seq, state, seq.charAt(i));
      }
      if (state != NO_CR_LF_STATE) {
        throw new IllegalArgumentException("a header value must not end with '\\r' or '\\n':" + seq);
      }
  }

  private static int validateValueChar(CharSequence seq, int state, char ch) {
    /*
     * State:
     * 0: Previous character was neither CR nor LF
     * 1: The previous character was CR
     * 2: The previous character was LF
     */
    if (ch == 0x7F) {
      throw new IllegalArgumentException("a header value contains a prohibited character '127': " + seq);
    }
    if ((ch & HIGHEST_INVALID_VALUE_CHAR_MASK) == 0) {
      // this is a rare scenario
      validateNonPrintableCtrlChar(seq, ch);
      // this can include LF and CR as they are non-printable characters
      if (state == NO_CR_LF_STATE) {
        // Check the CRLF (HT | SP) pattern
        switch (ch) {
          case '\r':
            return CR_STATE;
          case '\n':
            return LF_STATE;
        }
        return NO_CR_LF_STATE;
      }
    }
    if (state != NO_CR_LF_STATE) {
      // this is a rare scenario
      return validateCrLfChar(seq, state, ch);
    } else {
      return NO_CR_LF_STATE;
    }
  }

  private static int validateCrLfChar(CharSequence seq, int state, char ch) {
    switch (state) {
      case CR_STATE:
        if (ch == '\n') {
          return LF_STATE;
        }
        throw new IllegalArgumentException("only '\\n' is allowed after '\\r': " + seq);
      case LF_STATE:
        switch (ch) {
          case '\t':
          case ' ':
            // return to the normal state
            return NO_CR_LF_STATE;
          default:
            throw new IllegalArgumentException("only ' ' and '\\t' are allowed after '\\n': " + seq);
        }
      default:
        // this should never happen
        throw new AssertionError();
    }
  }

  private static void validateNonPrintableCtrlChar(CharSequence seq, int ch) {
    // The only characters allowed in the range 0x00-0x1F are : HTAB, LF and CR
    switch (ch) {
      case 0x09: // Horizontal tab - HTAB
      case 0x0a: // Line feed - LF
      case 0x0d: // Carriage return - CR
        break;
      default:
        throw new IllegalArgumentException("a header value contains a prohibited character '" + (int) ch + "': " + seq);
    }
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
      validateAsciiHeaderName((AsciiString) value);
    } else if(value instanceof String) {
      validateStringHeaderName((String) value);
    } else {
      validateSequenceHeaderName(value);
    }
  }

  private static void validateAsciiHeaderName(AsciiString value) {
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

  private static void validateStringHeaderName(String value) {
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

  private static void validateSequenceHeaderName(CharSequence value) {
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

  public static Future<AsyncFile> resolveFile(ContextInternal context, String filename, long offset, long length) {
    VertxInternal vertx = context.owner();
    File file_ = vertx.fileResolver().resolve(filename);
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

  /**
   * Convert a {@link SocketAddress} to a {@link HostAndPort}.
   * If the socket address is an {@link InetSocketAddress}, the hostString and port are used.
   * Otherwise {@code null} is returned.
   *
   * @param socketAddress The socket address to convert
   * @return The converted instance or {@code null} if not applicable.
   */
  public static HostAndPort socketAddressToHostAndPort(SocketAddress socketAddress) {
    if (socketAddress instanceof InetSocketAddress) {
      InetSocketAddress inetSocketAddress = (InetSocketAddress) socketAddress;
      return new HostAndPortImpl(inetSocketAddress.getHostString(), inetSocketAddress.getPort());
    }
    return null;
  }

  private static final String[] SMALL_POSITIVE_LONGS = new String[256];

  /**
   * This try hard to cache the first 256 positive longs as strings [0, 255] to avoid the cost of creating a new
   * string for each of them.<br>
   * The size/capacity of the cache is subject to change but this method is expected to be used for hot and frequent code paths.
   */
  public static String positiveLongToString(long value) {
    if (value < 0) {
      throw new IllegalArgumentException("contentLength must be >= 0");
    }
    if (value >= SMALL_POSITIVE_LONGS.length) {
      return Long.toString(value);
    }
    final int index = (int) value;
    String str = SMALL_POSITIVE_LONGS[index];
    if (str == null) {
      // it's ok to be racy here, String is immutable hence it benefits from safe publication!
      str = Long.toString(value);
      SMALL_POSITIVE_LONGS[index] = str;
    }
    return str;
  }
}
