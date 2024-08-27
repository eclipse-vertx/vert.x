package io.vertx.core.http.impl.headers;

import io.netty.handler.codec.Headers;
import io.netty.incubator.codec.http3.DefaultHttp3Headers;
import io.netty.incubator.codec.http3.Http3Headers;

public class VertxDefaultHttp3Headers implements VertxDefaultHttpHeaders {
  private final Http3Headers headers;

  public VertxDefaultHttp3Headers() {
    this.headers = new DefaultHttp3Headers();
  }

  public VertxDefaultHttp3Headers(Http3Headers headers) {
    this.headers = headers;
  }

  @Override
  public <T extends Headers<CharSequence, CharSequence, T>> T getHeaders() {
    return (T) headers;
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

  @Override
  public VertxDefaultHttp3Headers add(String name, String value) {
    this.headers.add(name, value);
    return this;
  }

  @Override
  public CharSequence status() {
    return this.headers.status();
  }
}
