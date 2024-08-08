package io.vertx.core.http.impl.headers;

import io.netty.incubator.codec.http3.DefaultHttp3Headers;
import io.netty.incubator.codec.http3.Http3Headers;

public class VertxDefaultHttp3Headers implements VertxDefaultHttpHeaders<Http3Headers> {
  private final DefaultHttp3Headers headers;

  public VertxDefaultHttp3Headers() {
    this.headers = new DefaultHttp3Headers();
  }

  @Override
  public Http3Headers getHttpHeaders() {
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
