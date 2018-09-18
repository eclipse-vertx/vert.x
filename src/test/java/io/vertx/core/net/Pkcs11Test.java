package io.vertx.core.net;

import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.test.core.VertxTestBase;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.security.Provider;
import java.security.Security;
import java.util.concurrent.CountDownLatch;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.hamcrest.core.Is.is;

public class Pkcs11Test extends VertxTestBase {

  private final static int PORT = 8443;
  private final static String CONTENT_VALUE = "foo";

  private CountDownLatch latch;

  private HttpClient client;
  private HttpServer server;

  public void setUp() throws Exception {
    super.setUp();
    latch = new CountDownLatch(1);

    // Add PKCS11 security provider
    String PKCS11Cfg = "name = SoftHSM2\n" +
      "library = " + System.getenv("SOFTHSM2_LIB") + "\n" +
      "slotListIndex = 0\n";

    ByteArrayInputStream config = new ByteArrayInputStream(PKCS11Cfg.getBytes(UTF_8));
    Provider provider = new sun.security.pkcs11.SunPKCS11(config);
    Security.addProvider(provider);

    client = vertx.createHttpClient(
      new HttpClientOptions()
        .setSsl(true)
        .setTrustAll(true));
  }

  protected void tearDown() throws Exception {
    client.close();
    server.close();
    super.tearDown();
  }

  @Test
  public void should_test_pkcs11_keystore() throws InterruptedException {
    HttpServerOptions options = new HttpServerOptions().setSsl(true);
    options.setPkcs11KeyOptions(new Pkcs11Options().setPassword("vertx"));

    server = vertx.createHttpServer(options);

    server.requestHandler(req -> req.response()
      .end(CONTENT_VALUE)).listen(PORT, ready ->
      client.getNow(PORT, "localhost", "/", resp -> resp.bodyHandler(body -> {
        String content = body.toString();
        latch.countDown();
        assertThat(content, is(CONTENT_VALUE));
      })));

    latch.await();
  }
}
