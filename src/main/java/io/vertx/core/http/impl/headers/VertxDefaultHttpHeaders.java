package io.vertx.core.http.impl.headers;

import io.netty.handler.codec.Headers;

public interface VertxDefaultHttpHeaders {

  <T extends Headers<CharSequence, CharSequence, T>> T getHeaders();

  void method(String value);

  void authority(String authority);

  CharSequence authority();

  void path(String value);

  void scheme(String value);

  void add(CharSequence name, String value);

  Object get(CharSequence name);

  void set(CharSequence name, CharSequence value);

  CharSequence path();

  CharSequence method();

  VertxDefaultHttpHeaders add(String name, String value);
}
