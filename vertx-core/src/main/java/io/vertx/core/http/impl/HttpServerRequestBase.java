package io.vertx.core.http.impl;

import io.vertx.core.MultiMap;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.QueryParamDecoderConfig;
import io.vertx.core.internal.http.HttpServerRequestInternal;
import io.vertx.core.internal.http.QueryParamDecoder;

import java.nio.charset.Charset;
import java.util.Objects;

/**
 * Common state / methods between HTTP/1.x implementation and the stream based implementation.
 */
public abstract class HttpServerRequestBase extends HttpServerRequestInternal {

  private QueryParamDecoder queryParamDecoder;
  private MultiMap params;

  public HttpServerRequestBase(QueryParamDecoder queryParamDecoder) {
    this.queryParamDecoder = queryParamDecoder;
  }

  @Override
  public final HttpServerRequest setParamsCharset(String charset) {
    Objects.requireNonNull(charset, "Charset must not be null");
    Charset cs = Charset.forName(charset);
    if (!queryParamDecoder.charset().equals(cs)) {
      queryParamDecoder = new QueryParamDecoder(new QueryParamDecoderConfig()
        .setMaxSize(queryParamDecoder.maxParams())
        .setUseSemicolonAsDelimiter(queryParamDecoder.isUseSemiColonAsDelimiter())
        .setCharset(cs));
      params = null;
    }
    return this;
  }

  @Override
  public final String getParamsCharset() {
    return queryParamDecoder.charset().name();
  }

  @Override
  public final MultiMap params(boolean semicolonIsNormalChar) {
    if (queryParamDecoder.isUseSemiColonAsDelimiter() == semicolonIsNormalChar) {
      queryParamDecoder = new QueryParamDecoder(new QueryParamDecoderConfig()
        .setCharset(queryParamDecoder.charset())
        .setMaxSize(queryParamDecoder.maxParams())
        .setUseSemicolonAsDelimiter(!semicolonIsNormalChar)
      );
      params = null;
    }
    if (params == null) {
      params = queryParamDecoder.decode(uri());
    }
    return params;
  }

  @Override
  public final QueryParamDecoder queryParamDecoder() {
    return queryParamDecoder;
  }
}
