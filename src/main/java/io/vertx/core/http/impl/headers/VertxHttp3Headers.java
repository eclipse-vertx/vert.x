package io.vertx.core.http.impl.headers;

import io.netty.handler.codec.http.HttpHeaders;
import io.netty.incubator.codec.http3.DefaultHttp3Headers;
import io.netty.incubator.codec.http3.Http3Headers;
import io.vertx.core.MultiMap;

import java.util.Map;
import java.util.Objects;

public class VertxHttp3Headers extends VertxHttpHeadersBase<Http3Headers> implements VertxHttpHeaders {

  public VertxHttp3Headers() {
    this(new DefaultHttp3Headers());
  }

  public VertxHttp3Headers(Http3Headers headers) {
    super(headers);
  }

  @Override
  public void method(String value) {
    this.headers.method(value);
  }

  @Override
  public void authority(String authority) {
    this.headers.authority(authority);
  }

  @Override
  public CharSequence authority() {
    return this.headers.authority();
  }

  @Override
  public void path(String value) {
    this.headers.path(value);
  }

  @Override
  public void scheme(String value) {
    this.headers.scheme(value);
  }

  @Override
  public CharSequence path() {
    return this.headers.path();
  }

  @Override
  public CharSequence method() {
    return this.headers.method();
  }

  @Override
  public CharSequence status() {
    return this.headers.status();
  }

  @Override
  public MultiMap toHeaderAdapter() {
    return new Http3HeadersAdaptor(headers);
  }

  @Override
  public HttpHeaders toHttpHeaders() {
    HeadersMultiMap headers = HeadersMultiMap.httpHeaders();
    for (Map.Entry<CharSequence, CharSequence> header : this.headers) {
      CharSequence name = Objects.requireNonNull(Http3Headers.PseudoHeaderName.getPseudoHeader(header.getKey())).name();
      headers.add(name, header.getValue());
    }
    return headers;
  }
}
