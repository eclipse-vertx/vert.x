package io.vertx.core.http.impl.headers;

import io.netty.handler.codec.Headers;
import io.vertx.core.MultiMap;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.net.HostAndPort;


/**
 * @author <a href="mailto:zolfaghari19@gmail.com">Iman Zolfaghari</a>
 */
public interface VertxHttpHeaders extends MultiMap {

  <T extends Headers<CharSequence, CharSequence, T>> T getHeaders();

  VertxHttpHeaders method(HttpMethod value);

  VertxHttpHeaders authority(HostAndPort authority);

  HostAndPort authority();

  void path(CharSequence value);

  void scheme(CharSequence value);

  CharSequence scheme();

  CharSequence path();

  HttpMethod method();

  Integer status();

  VertxHttpHeaders status(CharSequence status);

  boolean contains(CharSequence name, CharSequence value);

  VertxHttpHeaders prepare();

  Headers<CharSequence, CharSequence, ?> unwrap();
}
