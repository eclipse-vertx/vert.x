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
  private boolean useSemiColonAsDelimiter;
  private MultiMap params;

  public HttpServerRequestBase(QueryParamDecoder queryParamDecoder) {
    this.queryParamDecoder = queryParamDecoder;
    this.useSemiColonAsDelimiter = queryParamDecoder.isUseSemiColonAsDelimiter();
  }

  @Override
  public final HttpServerRequest setParamsCharset(String charset) {
    Objects.requireNonNull(charset, "Charset must not be null");
    Charset cs = Charset.forName(charset);
    if (!queryParamDecoder.charset().equals(cs)) {
      queryParamDecoder = new QueryParamDecoder(new QueryParamDecoderConfig()
        .setMaxSize(queryParamDecoder.maxParams())
        .setUseSemicolonAsDelimiter(useSemiColonAsDelimiter)
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
  public MultiMap params() {
    return params(!useSemiColonAsDelimiter);
  }

  @Override
  public final MultiMap params(boolean semicolonIsNormalChar) {
    QueryParamDecoder decoder = queryParamDecoder(semicolonIsNormalChar);
    if (decoder != queryParamDecoder) {
      queryParamDecoder = decoder;
      params = null;
    }
    if (params == null) {
      params = queryParamDecoder.decode(uri());
    }
    return params;
  }

  private QueryParamDecoder queryParamDecoder(boolean semicolonIsNormalChar) {
    QueryParamDecoder decoder = queryParamDecoder;
    if (decoder.isUseSemiColonAsDelimiter() == semicolonIsNormalChar) {
      decoder = new QueryParamDecoder(new QueryParamDecoderConfig()
        .setCharset(decoder.charset())
        .setMaxSize(decoder.maxParams())
        .setUseSemicolonAsDelimiter(!semicolonIsNormalChar)
      );
    }
    return decoder;
  }

  @Override
  public final QueryParamDecoder queryParamDecoder() {
    return queryParamDecoder(!useSemiColonAsDelimiter);
  }
}
