package io.vertx.test.core;

import java.util.concurrent.CountDownLatch;

import org.junit.Test;

import static org.junit.Assert.*;

import io.netty.util.internal.logging.InternalLoggerFactory;
import io.netty.util.internal.logging.Log4JLoggerFactory;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.core.net.ProxyOptions;
import io.vertx.core.net.ProxyType;
import io.vertx.test.core.ConnectHttpProxy;
import io.vertx.test.core.VertxTestBase;

/**
 * @author <a href="http://oss.lehmann.cx/">Alexander Lehmann</a>
 *
 */
public class ProxyErrorTest extends VertxTestBase {

  private static final Logger log = LoggerFactory.getLogger(ProxyErrorTest.class);

  private ConnectHttpProxy proxy;

  // we don't start a https server, due to the error, it will not be queried 
  @Override
  public void setUp() throws Exception {
//    InternalLoggerFactory.setDefaultFactory(Log4JLoggerFactory.INSTANCE);
    super.setUp();
    CountDownLatch latch = new CountDownLatch(1);
    proxy = new ConnectHttpProxy(null);
    proxy.setError(403);
    proxy.start(vertx, v -> latch.countDown());
    latch.await();
    log.info("proxy");
  }

  @Override
  public void tearDown() throws Exception {
    super.tearDown();
    proxy.stop();
  }

  @Test
  public void testProxyError() throws Exception {
    CountDownLatch latch = new CountDownLatch(1);

    final HttpClientOptions options = new HttpClientOptions()
        .setProxyOptions(new ProxyOptions()
            .setType(ProxyType.HTTP)
            .setHost("localhost")
            .setPort(13128));
    HttpClient client = vertx.createHttpClient(options);

    log.info("starting request");
    client.getAbs("https://localhost/", resp -> {
      log.info("this request is supposed to fail");
      fail();
    })
    .exceptionHandler(e -> {
      log.warn("Exception", e);
      latch.countDown();
    })
    .end();
    log.info("request.end");

    latch.await();
  }

}
