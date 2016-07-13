package io.vertx.test.core;

import java.util.concurrent.CountDownLatch;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.net.JksOptions;
import io.vertx.core.net.NetClient;
import io.vertx.core.net.NetClientOptions;
import io.vertx.core.net.NetSocket;

/**
 * @author <a href="http://oss.lehmann.cx/">Alexander Lehmann</a>
 *
 */
public class NetClientTLSConnectTest extends VertxTestBase {

  private HttpServer server;

  @Before
  public void startServer() throws InterruptedException {
    CountDownLatch latch = new CountDownLatch(1);

    HttpServerOptions serverOptions = new HttpServerOptions();
    serverOptions.setSsl(true);
    serverOptions.setKeyCertOptions(new JksOptions().setPath("tls/server-keystore-root-ca.jks").setPassword("wibble"));
    server = vertx.createHttpServer(serverOptions.setPort(4043));
    server.requestHandler(req -> {
      req.bodyHandler(buffer -> {
        req.response().end();
      });
    });
    server.listen(ar -> latch.countDown());
    latch.await();
  }

  @After
  public void stopServer() {
    server.close();
  }

  @Test
  public void testHostnameCheck() {
    NetClientOptions options = new NetClientOptions();
    options.setHostnameVerificationAlgorithm("HTTPS");

    options.setTrustStoreOptions(new JksOptions().setPath("tls/client-truststore.jks").setPassword("wibble"));

    NetClient client = vertx.createNetClient(options);

    client.connect(4043, "localhost", ar -> {
      if (ar.succeeded()) {
        NetSocket ns = ar.result();
        ns.exceptionHandler(th -> {
          fail(th);
        });
        ns.upgradeToSsl(v -> {
          testComplete();
        });
      } else {
        fail(ar.cause());
      }
    });

    await();
  }

}
