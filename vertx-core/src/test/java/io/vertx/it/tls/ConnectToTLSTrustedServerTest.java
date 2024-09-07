package io.vertx.it.tls;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.*;
import io.vertx.test.tls.Cert;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class ConnectToTLSTrustedServerTest {

  @Test
  public void testHTTP1x() throws Exception {
    String val = testHTTP(
      new HttpServerOptions().setSsl(true).setKeyCertOptions(Cert.SERVER_JKS_ROOT_CA.get()),
      new HttpClientOptions().setProtocolVersion(HttpVersion.HTTP_1_1)
    );
    assertEquals("true/HTTP_1_1", val);
  }

  @Test
  public void testHTTP2() throws Exception {
    String val = testHTTP(
      new HttpServerOptions().setUseAlpn(true).setSsl(true).setKeyCertOptions(Cert.SERVER_JKS_ROOT_CA.get()),
      new HttpClientOptions().setUseAlpn(true).setProtocolVersion(HttpVersion.HTTP_2)
    );
    assertEquals("true/HTTP_2", val);
  }

  public String testHTTP(HttpServerOptions serverOptions, HttpClientOptions clientOptions) throws Exception {
    Vertx vertx = Vertx.vertx();
    try {
      HttpServer server = vertx.createHttpServer(serverOptions)
        .requestHandler(req -> req.response().end(req.isSSL() + "/" + req.version()));
      server.listen(8443, "localhost").await();
      HttpClient client = vertx.createHttpClient(new HttpClientOptions().setUseAlpn(true).setProtocolVersion(HttpVersion.HTTP_2));
      Future<HttpClientRequest> fut = client.request(new RequestOptions().setAbsoluteURI("https://localhost:8443"));
      Future<Buffer> buff = fut.compose(req -> req.send().compose(HttpClientResponse::body));
      return buff.await().toString();
    } finally {
      vertx.close();
    }
  }
}
