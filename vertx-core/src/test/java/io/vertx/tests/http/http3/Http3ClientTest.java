package io.vertx.tests.http.http3;

import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.util.NetUtil;
import io.vertx.core.Future;
import io.vertx.core.MultiMap;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.*;
import io.vertx.core.net.QuicClient;
import io.vertx.core.net.SocketAddress;
import io.vertx.test.core.LinuxOrOsx;
import io.vertx.test.core.VertxTestBase;
import io.vertx.test.tls.Cert;
import io.vertx.test.tls.Trust;
import io.vertx.tests.net.quic.QuicClientTest;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.net.InetSocketAddress;

@RunWith(LinuxOrOsx.class)
public class Http3ClientTest extends VertxTestBase {

  private HttpServer server;
  private Http3ClientOptions clientOptions;
  private HttpClientAgent client;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    Http3ServerOptions serverOptions = new Http3ServerOptions();
    serverOptions.getSslOptions().setKeyCertOptions(Cert.SERVER_JKS.get());
    clientOptions = new Http3ClientOptions();
    clientOptions.getSslOptions().setTrustOptions(Trust.SERVER_JKS.get());
    clientOptions.getSslOptions().setHostnameVerificationAlgorithm("");
    server = vertx.createHttpServer(serverOptions);
    client = vertx.createHttpClient(clientOptions);
  }

  @Override
  protected void tearDown() throws Exception {
    server.close().await();
    client.close().await();
    super.tearDown();
  }

  @Test
  public void testConnect() {
    server.requestHandler(req -> {
      req.response().end("Hello World");
    });
    server.listen(8443, "localhost").await();

    HttpClientConnection connection = client.connect(new HttpConnectOptions()
      .setHost("localhost")
      .setPort(8443)).await();

    Buffer response = connection.request(HttpMethod.GET, 8443, "localhost", "/")
      .compose(request -> request
        .send()
        .expecting(HttpResponseExpectation.SC_OK)
        .compose(HttpClientResponse::body))
      .await();

    assertEquals("Hello World", response.toString());
  }

  @Test
  public void testResponseTrailers() throws Exception {
    server.requestHandler(req -> {
      req.response()
        .putTrailer("trailer_key", "trailer_value")
        .end();
    });
    server.listen(8443, "localhost").await();

    MultiMap trailers = client.request(HttpMethod.GET, 8443, "localhost", "/")
      .compose(request -> request
        .send()
        .expecting(HttpResponseExpectation.SC_OK)
        .compose(resp -> resp.end().map(v -> resp.trailers())))
      .await();

    assertEquals(1, trailers.size());
    assertEquals("trailer_value", trailers.get("trailer_key"));
  }
}
