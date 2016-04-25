/*
 * Copyright (c) 2011-2013 The original author or authors
 * ------------------------------------------------------
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * and Apache License v2.0 which accompanies this distribution.
 *
 *     The Eclipse Public License is available at
 *     http://www.eclipse.org/legal/epl-v10.html
 *
 *     The Apache License v2.0 is available at
 *     http://www.opensource.org/licenses/apache2.0.php
 *
 * You may elect to redistribute this code under either of these licenses.
 */
package io.vertx.core.http.impl;


import io.netty.handler.codec.compression.ZlibWrapper;
import io.netty.handler.codec.http.HttpContentCompressor;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http.QueryStringDecoder;
import io.netty.handler.codec.http2.Http2Headers;
import io.netty.handler.codec.http2.Http2Settings;
import io.vertx.core.MultiMap;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.CaseInsensitiveHeaders;
import io.vertx.core.http.HttpServerRequest;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import static io.vertx.core.http.Http2Settings.DEFAULT_ENABLE_PUSH;
import static io.vertx.core.http.Http2Settings.DEFAULT_HEADER_TABLE_SIZE;
import static io.vertx.core.http.Http2Settings.DEFAULT_INITIAL_WINDOW_SIZE;
import static io.vertx.core.http.Http2Settings.DEFAULT_MAX_CONCURRENT_STREAMS;
import static io.vertx.core.http.Http2Settings.DEFAULT_MAX_FRAME_SIZE;
import static io.vertx.core.http.Http2Settings.DEFAULT_MAX_HEADER_LIST_SIZE;

/**
 * Various http utils.
 *
 * @author <a href="mailto:nmaurer@redhat.com">Norman Maurer</a>
 */
public final class HttpUtils {

  private HttpUtils() {
  }

  /**
   * Extract the path out of the uri.
   */
  static String parsePath(String uri) {
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
    }
    return uri.substring(i, queryStart);
  }

  /**
   * Extract the query out of a uri or returns {@code null} if no query was found.
   */
  static String parseQuery(String uri) {
    int i = uri.indexOf('?');
    if (i == -1) {
      return null;
    } else {
      return uri.substring(i + 1 , uri.length());
    }
  }

  static String absoluteURI(String serverOrigin, HttpServerRequest req) throws URISyntaxException {
    String absoluteURI;
    URI uri = new URI(req.uri());
    String scheme = uri.getScheme();
    if (scheme != null && (scheme.equals("http") || scheme.equals("https"))) {
      absoluteURI = uri.toString();
    } else {
      String host = req.host();
      if (host != null) {
        absoluteURI = req.scheme() + "://" + host + uri;
      } else {
        // Fall back to the server origin
        absoluteURI = serverOrigin + uri;
      }
    }
    return absoluteURI;
  }

  static MultiMap params(String uri) {
    QueryStringDecoder queryStringDecoder = new QueryStringDecoder(uri);
    Map<String, List<String>> prms = queryStringDecoder.parameters();
    MultiMap params = new CaseInsensitiveHeaders();
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
        nettySettings.maxHeaderListSize((int)(long) vertxSettings.getMaxHeaderListSize());
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
    converted.headerTableSize((int)(long)settings.getHeaderTableSize());
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
    Integer maxHeaderListSize = settings.maxHeaderListSize();
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
      converted.setHeaderTableSize((int)(long) headerTableSize);
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
      Buffer buffer = Buffer.buffer(Base64.getDecoder().decode(base64Settings));
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

  private static class CustomCompressor extends HttpContentCompressor {
    @Override
    public ZlibWrapper determineWrapper(String acceptEncoding) {
      return super.determineWrapper(acceptEncoding);
    }
  }
  private static final CustomCompressor compressor = new CustomCompressor();

  static String determineContentEncoding(Http2Headers headers) {
    String acceptEncoding = headers.get(HttpHeaderNames.ACCEPT_ENCODING) != null ? headers.get(HttpHeaderNames.ACCEPT_ENCODING).toString() : null;
    if (acceptEncoding != null) {
      ZlibWrapper wrapper = compressor.determineWrapper(acceptEncoding);
      if (wrapper != null) {
        switch (wrapper) {
          case GZIP:
            return "gzip";
          case ZLIB:
            return "deflate";
        }
      }
    }
    return null;
  }

  static HttpMethod toNettyHttpMethod(io.vertx.core.http.HttpMethod method, String rawMethod) {
    switch (method) {
      case CONNECT: {
        return HttpMethod.CONNECT;
      }
      case GET: {
        return HttpMethod.GET;
      }
      case PUT: {
        return HttpMethod.PUT;
      }
      case POST: {
        return HttpMethod.POST;
      }
      case DELETE: {
        return HttpMethod.DELETE;
      }
      case HEAD: {
        return HttpMethod.HEAD;
      }
      case OPTIONS: {
        return HttpMethod.OPTIONS;
      }
      case TRACE: {
        return HttpMethod.TRACE;
      }
      case PATCH: {
        return HttpMethod.PATCH;
      }
      default: {
        return HttpMethod.valueOf(rawMethod);
      }
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
    try {
      return io.vertx.core.http.HttpMethod.valueOf(method);
    } catch (IllegalArgumentException e) {
      return io.vertx.core.http.HttpMethod.OTHER;
    }
  }
}
