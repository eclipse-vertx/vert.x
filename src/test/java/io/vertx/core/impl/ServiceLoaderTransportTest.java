package io.vertx.core.impl;

import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.core.spi.transport.Transport;
import io.vertx.test.core.AsyncTestBase;
import io.vertx.test.core.TestUtils;
import io.vertx.test.faketransport.FakeTransport;
import org.junit.Test;

public class ServiceLoaderTransportTest extends AsyncTestBase {

  @Test
  public void testServiceLoaderTransportNotAvailable() {
    Vertx vertx = TestUtils.runWithServiceLoader(Transport.class, FakeTransport.class, () ->
      Vertx.vertx(new VertxOptions().setPreferNativeTransport(true)));
    assertNotSame(FakeTransport.class, ((VertxInternal)vertx).transport());
  }
}
