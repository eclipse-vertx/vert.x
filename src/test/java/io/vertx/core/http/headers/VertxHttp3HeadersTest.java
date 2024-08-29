package io.vertx.core.http.headers;

import io.netty.incubator.codec.http3.DefaultHttp3Headers;
import io.netty.incubator.codec.http3.Http3Headers;
import io.vertx.core.MultiMap;
import io.vertx.core.http.impl.headers.VertxHttp3Headers;

/**
 * @author <a href="mailto:zolfaghari19@gmail.com">Iman Zolfaghari</a>
 */
public class VertxHttp3HeadersTest extends VertxHttpHeadersTestBase<Http3Headers> {
  @Override
  protected MultiMap newMultiMap() {
    return new VertxHttp3Headers(new DefaultHttp3Headers()).toHeaderAdapter();
  }

  @Override
  protected VertxHttp3Headers newVertxHttpHeaders() {
    return new VertxHttp3Headers(new DefaultHttp3Headers());
  }
}
