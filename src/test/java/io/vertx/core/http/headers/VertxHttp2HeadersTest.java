package io.vertx.core.http.headers;

import io.netty.handler.codec.http2.DefaultHttp2Headers;
import io.netty.handler.codec.http2.Http2Headers;
import io.vertx.core.MultiMap;
import io.vertx.core.http.impl.headers.VertxHttp2Headers;


/**
 * @author <a href="mailto:zolfaghari19@gmail.com">Iman Zolfaghari</a>
 */
public class VertxHttp2HeadersTest extends VertxHttpHeadersTestBase<Http2Headers> {
  @Override
  protected MultiMap newMultiMap() {
    return new VertxHttp2Headers(new DefaultHttp2Headers()).toHeaderAdapter();
  }

  @Override
  protected VertxHttp2Headers newVertxHttpHeaders() {
    return new VertxHttp2Headers(new DefaultHttp2Headers());
  }
}
