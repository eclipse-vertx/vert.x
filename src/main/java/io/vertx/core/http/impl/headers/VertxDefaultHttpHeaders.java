package io.vertx.core.http.impl.headers;

import io.netty.handler.codec.Headers;
import io.netty.handler.codec.http.HttpHeaders;
import io.vertx.core.MultiMap;

import java.util.Map;

public interface VertxDefaultHttpHeaders {

  <T extends Headers<CharSequence, CharSequence, T>> T getHeaders();

  Iterable<Map.Entry<CharSequence, CharSequence>> getIterable();

  void method(String value);

  void authority(String authority);

  CharSequence authority();

  void path(String value);

  void scheme(String value);

  void add(CharSequence name, String value);

  CharSequence get(CharSequence name);

  void set(CharSequence name, CharSequence value);

  CharSequence path();

  CharSequence method();

  VertxDefaultHttpHeaders add(String name, String value);

  CharSequence status();

  MultiMap toHeaderAdapter();

  HttpHeaders toHttpHeaders();

  boolean contains(CharSequence name, CharSequence value);

  void remove(CharSequence name);
}
