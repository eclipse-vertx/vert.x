package io.vertx.tests.http.compression;

import io.vertx.test.http.HttpConfigurator;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class Http2MultiplexCompressionTest extends HttpCompressionTest {

  public Http2MultiplexCompressionTest(CompressionConfig config) {
    super(HttpConfigurator.H2.MULTIPLEX, config);
  }
}
