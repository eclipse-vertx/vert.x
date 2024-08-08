package io.vertx.core.http.impl.headers;

import io.netty.handler.codec.Headers;

public interface VertxDefaultHttpHeaders<H extends Headers<CharSequence, CharSequence, H>> {

  H getHttpHeaders();

  void method(String value);

  void authority(String authority);

  void path(String value);

  void scheme(String value);

  void add(CharSequence name, String value);

  Object get(CharSequence name);

  void set(CharSequence name, CharSequence value);

  CharSequence path();

  CharSequence method();
}
