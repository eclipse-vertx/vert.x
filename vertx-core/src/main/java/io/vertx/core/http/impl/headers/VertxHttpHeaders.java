package io.vertx.core.http.impl.headers;

import io.netty.handler.codec.Headers;
import io.vertx.core.MultiMap;


/**
 * @author <a href="mailto:zolfaghari19@gmail.com">Iman Zolfaghari</a>
 */
public interface VertxHttpHeaders extends MultiMap {

  <T extends Headers<CharSequence, CharSequence, T>> T getHeaders();

  void method(CharSequence value);

  void authority(CharSequence authority);

  CharSequence authority();

  void path(CharSequence value);

  void scheme(CharSequence value);

  CharSequence scheme();

  CharSequence path();

  CharSequence method();

  CharSequence status();

  void status(CharSequence status);

  boolean contains(CharSequence name, CharSequence value);

}
