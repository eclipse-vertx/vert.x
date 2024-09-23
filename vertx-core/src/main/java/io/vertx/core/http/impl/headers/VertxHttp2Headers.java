package io.vertx.core.http.impl.headers;

import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http2.DefaultHttp2Headers;
import io.netty.handler.codec.http2.Http2Headers;
import io.vertx.core.MultiMap;

import java.util.Map;
import java.util.Objects;

/**
 * @author <a href="mailto:zolfaghari19@gmail.com">Iman Zolfaghari</a>
 */
public class VertxHttp2Headers extends VertxHttpHeadersBase<Http2Headers> implements VertxHttpHeaders {

  public VertxHttp2Headers() {
    this(new DefaultHttp2Headers());
  }

  public VertxHttp2Headers(Http2Headers headers) {
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
  public String authority() {
    return String.valueOf(this.headers.authority());
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
  public String path() {
    return String.valueOf(this.headers.path());
  }

  @Override
  public String method() {
    return String.valueOf(this.headers.method());
  }

  @Override
  public String status() {
    return String.valueOf(this.headers.status());
  }

  @Override
  public void status(CharSequence status) {
    this.headers.status(status);
  }

  @Override
  public CharSequence scheme() {
    return this.headers.scheme();
  }

  @Override
  public MultiMap toHeaderAdapter() {
    return new Http2HeadersAdaptor(headers);
  }

  @Override
  public HttpHeaders toHttpHeaders() {
    HeadersMultiMap headers = HeadersMultiMap.httpHeaders();
    for (Map.Entry<CharSequence, CharSequence> header : this.headers) {
      CharSequence name = Objects.requireNonNull(Http2Headers.PseudoHeaderName.getPseudoHeader(header.getKey())).name();
      headers.add(name, header.getValue());
    }
    return headers;
  }

}
