package io.vertx.core.http.headers;

import io.netty.incubator.codec.http3.DefaultHttp3Headers;
import io.vertx.core.MultiMap;
import io.vertx.core.http.impl.headers.VertxHttp3Headers;
import io.vertx.core.http.impl.headers.VertxHttpHeadersBase;

/**
 * @author <a href="mailto:zolfaghari19@gmail.com">Iman Zolfaghari</a>
 */
public class VertxHttp3HeadersTest extends VertxHttpHeadersTestBase {
  @Override
  protected MultiMap newMultiMap() {
    return new VertxHttp3Headers(new DefaultHttp3Headers()).toHeaderAdapter();
  }

  @Override
  protected VertxHttpHeadersBase newVertxHttpHeaders() {
    return new VertxHttp3Headers(new DefaultHttp3Headers());
  }
}
