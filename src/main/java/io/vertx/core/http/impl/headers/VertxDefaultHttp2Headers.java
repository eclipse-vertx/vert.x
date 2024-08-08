package io.vertx.core.http.impl.headers;

import io.netty.handler.codec.http2.DefaultHttp2Headers;
import io.netty.handler.codec.http2.Http2Headers;

public class VertxDefaultHttp2Headers implements VertxDefaultHttpHeaders<Http2Headers> {
  private final DefaultHttp2Headers headers;

  public VertxDefaultHttp2Headers() {
    this.headers = new DefaultHttp2Headers();
  }

  @Override
  public Http2Headers getHttpHeaders() {
    return this.headers;
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
  public void path(String value) {
    this.headers.path(value);
  }

  @Override
  public void scheme(String value) {
    this.headers.scheme(value);
  }

  @Override
  public void add(CharSequence name, String value) {
    this.headers.add(name, value);
  }

  @Override
  public Object get(CharSequence name) {
    return this.headers.get(name);
  }

  @Override
  public void set(CharSequence name, CharSequence value) {
    this.headers.set(name, value);
  }

  @Override
  public CharSequence path() {
    return this.headers.path();
  }

  @Override
  public CharSequence method() {
    return this.headers.method();
  }
}
