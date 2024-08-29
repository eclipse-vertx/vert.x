package io.vertx.core.http.impl.headers;

import io.netty.handler.codec.Headers;
import io.netty.handler.codec.http.HttpHeaders;
import io.vertx.core.MultiMap;

import java.util.Map;

/**
 * @author <a href="mailto:zolfaghari19@gmail.com">Iman Zolfaghari</a>
 */
public interface VertxHttpHeaders extends MultiMap {

  <T extends Headers<CharSequence, CharSequence, T>> T getHeaders();

  Iterable<Map.Entry<CharSequence, CharSequence>> getIterable();

  void method(String value);

  void authority(String authority);

  String authority();

  void path(String value);

  void scheme(String value);

  CharSequence scheme();

  String path();

  String method();

  String status();

  void status(CharSequence status);

  MultiMap toHeaderAdapter();

  HttpHeaders toHttpHeaders();

  boolean contains(String name, String value);

}
