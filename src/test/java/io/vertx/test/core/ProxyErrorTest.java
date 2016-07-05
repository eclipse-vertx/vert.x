package io.vertx.test.core;

import java.util.concurrent.CountDownLatch;

import org.junit.Test;

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

  private ConnectHttpProxy proxy = null;

  // we don't start a https server, due to the error, it will not be queried 

  private void startProxy(int error, String username) throws InterruptedException {
    CountDownLatch latch = new CountDownLatch(1);
    proxy = new ConnectHttpProxy(username);
    proxy.setError(error);
    proxy.start(vertx, v -> latch.countDown());
    latch.await();
  }

  @Override
  public void tearDown() throws Exception {
    super.tearDown();
    if (proxy!=null) {
      proxy.stop();
    }
  }

  @Test
  public void testProxyError() throws Exception {
    startProxy(403, null);

    CountDownLatch latch = new CountDownLatch(1);

    final HttpClientOptions options = new HttpClientOptions()
        .setProxyOptions(new ProxyOptions()
            .setType(ProxyType.HTTP)
            .setHost("localhost")
            .setPort(proxy.getPort()));
    HttpClient client = vertx.createHttpClient(options);

    client.getAbs("https://localhost/", resp -> {
      log.info("this request is supposed to fail");
      fail();
    })
    .exceptionHandler(e -> {
      log.warn("Exception", e);
      latch.countDown();
    })
    .end();

    latch.await();
  }

  @Test
  public void testProxyAuthFail() throws Exception {
    startProxy(0, "user");

    CountDownLatch latch = new CountDownLatch(1);

    final HttpClientOptions options = new HttpClientOptions()
        .setProxyOptions(new ProxyOptions()
            .setType(ProxyType.HTTP)
            .setHost("localhost")
            .setPort(proxy.getPort()));
    HttpClient client = vertx.createHttpClient(options);

    client.getAbs("https://localhost/", resp -> {
      log.info("this request is supposed to fail");
      fail();
    })
    .exceptionHandler(e -> {
      log.warn("Exception", e);
      latch.countDown();
    })
    .end();

    latch.await();
  }

  @Test
  public void testProxyHostUnknown() throws Exception {
    startProxy(0, null);

    CountDownLatch latch = new CountDownLatch(1);

    final HttpClientOptions options = new HttpClientOptions()
        .setProxyOptions(new ProxyOptions()
            .setType(ProxyType.HTTP)
            .setHost("localhost")
            .setPort(proxy.getPort()));
    HttpClient client = vertx.createHttpClient(options);

    client.getAbs("https://unknown.hostname/", resp -> {
      log.info("this request is supposed to fail");
      fail();
    })
    .exceptionHandler(e -> {
      log.warn("Exception", e);
      latch.countDown();
    })
    .end();

    latch.await();
  }

}
