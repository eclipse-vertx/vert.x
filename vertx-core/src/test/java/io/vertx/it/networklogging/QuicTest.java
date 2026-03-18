package io.vertx.it.networklogging;

import io.vertx.core.net.LogConfig;
import io.vertx.core.net.QuicClient;
import io.vertx.core.net.QuicClientConfig;
import io.vertx.core.net.QuicConnection;
import io.vertx.core.net.QuicServer;
import io.vertx.core.net.QuicServerConfig;
import io.vertx.core.net.QuicStream;
import io.vertx.core.net.SocketAddress;
import io.vertx.test.core.Repeat;
import io.vertx.test.core.TestUtils;
import io.vertx.test.core.VertxTestBase;
import io.vertx.test.netty.TestLoggerFactory;
import io.vertx.tests.net.quic.QuicClientTest;
import org.junit.Test;

import static io.vertx.tests.net.quic.QuicServerTest.SSL_OPTIONS;

public class QuicTest extends VertxTestBase {

  @Test
  public void testLogging() {
    TestUtils.testLogging(factory -> {
      try {
        QuicServer server = vertx.createQuicServer(
          new QuicServerConfig().setLogConfig(new LogConfig().setEnabled(true)),
          SSL_OPTIONS);
        server.handler(conn -> {
          conn.handler(stream -> {

          });
        });
        int port = server.listen().await().port();
        QuicClient client = vertx.createQuicClient(new QuicClientConfig(), QuicClientTest.SSL_OPTIONS);
        QuicConnection connection = client.connect(SocketAddress.inetSocketAddress(port, "localhost")).await();
        QuicStream stream = connection.openStream().await();
        stream.write("ping").await();
        connection.close().await();
      } catch (Exception e) {
        fail(e);
      }
      assertTrue(factory.hasName("io.netty.handler.logging.LoggingHandler"));
    });
  }
}
