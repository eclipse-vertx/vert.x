package io.vertx.test.it;

import io.netty.handler.ssl.JdkSslContext;
import io.netty.handler.ssl.OpenSslContext;
import io.netty.handler.ssl.SslContext;
import io.vertx.core.VertxException;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.http.HttpVersion;
import io.vertx.core.http.impl.HttpServerImpl;
import io.vertx.core.impl.VertxInternal;
import io.vertx.core.net.JdkSSLEngineOptions;
import io.vertx.core.net.OpenSSLEngineOptions;
import io.vertx.core.net.SSLEngineOptions;
import io.vertx.core.net.impl.SSLHelper;
import io.vertx.test.core.HttpTestBase;
import io.vertx.test.core.TLSCert;
import org.junit.Test;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class AlpnTest extends HttpTestBase {

  private static final boolean JDK = Boolean.getBoolean("vertx-test-alpn-jdk");
  private static boolean OPEN_SSL = Boolean.getBoolean("vertx-test-alpn-openssl");
  private static final String EXPECTED_SSL_CONTEXT = System.getProperty("vertx-test-sslcontext");

  public AlpnTest() {
  }

  @Test
  public void testDefaultEngine() throws Exception {
    doTest(null, JDK | OPEN_SSL, EXPECTED_SSL_CONTEXT);
  }

  @Test
  public void testJdkEngine() throws Exception {
    doTest(new JdkSSLEngineOptions(), JDK, "jdk");
  }

  @Test
  public void testOpenSSLEngine() throws Exception {
    doTest(new OpenSSLEngineOptions(), OPEN_SSL, "openssl");
  }

  private void doTest(SSLEngineOptions engine, boolean pass, String expectedSslContext) {
    server.close();
    server = vertx.createHttpServer(new HttpServerOptions()
        .setSslEngineOptions(engine)
        .setPort(DEFAULT_HTTP_PORT)
        .setHost(DEFAULT_HTTP_HOST)
        .setKeyCertOptions(TLSCert.PEM.getServerKeyCertOptions())
        .setSsl(true)
        .setUseAlpn(true)
    );
    server.requestHandler(req -> {
      req.response().end();
    });
    try {
      server.listen(onSuccess(s -> {
        HttpServerImpl impl = (HttpServerImpl) s;
        SSLHelper sslHelper = impl.getSslHelper();
        SslContext ctx = sslHelper.getContext((VertxInternal) vertx);
        switch (expectedSslContext) {
          case "jdk":
            assertTrue(ctx instanceof JdkSslContext);
            break;
          case "openssl":
            assertTrue(ctx instanceof OpenSslContext);
            break;
        }
        client = vertx.createHttpClient(new HttpClientOptions()
            .setSslEngineOptions(engine)
            .setSsl(true)
            .setUseAlpn(true)
            .setTrustAll(true)
            .setProtocolVersion(HttpVersion.HTTP_2));
        client.getNow(DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, "/somepath", resp -> {
          assertEquals(200, resp.statusCode());
          testComplete();
        });
      }));
    } catch (VertxException e) {
      if (pass) {
        fail(e);
      } else {
        assertEquals("ALPN is not available", e.getMessage());
      }
      return;
    }
    await();
  }
}
