package io.vertx.core.http.impl.headers;

import io.netty.handler.codec.Headers;

import java.util.Map;

abstract class VertxHttpHeadersBase<H extends Headers<CharSequence, CharSequence, H>> implements VertxHttpHeaders {
  protected H headers;

  public VertxHttpHeadersBase(H headers) {
    this.headers = headers;
  }

  @Override
  public H getHeaders() {
    return headers;
  }

  @Override
  public void add(CharSequence name, String value) {
    this.headers.add(name, value);
  }

  @Override
  public CharSequence get(CharSequence name) {
    return this.headers.get(name);
  }

  @Override
  public void set(CharSequence name, CharSequence value) {
    this.headers.set(name, value);
  }

  @Override
  public VertxHttpHeadersBase<H> add(String name, String value) {
    this.headers.add(name, value);
    return this;
  }

  @Override
  public boolean contains(CharSequence name, CharSequence value) {
    return headers.contains(name, value);
  }

  @Override
  public void remove(CharSequence name) {
    headers.remove(name);
  }

  @Override
  public Iterable<Map.Entry<CharSequence, CharSequence>> getIterable() {
    return headers;
  }

}
