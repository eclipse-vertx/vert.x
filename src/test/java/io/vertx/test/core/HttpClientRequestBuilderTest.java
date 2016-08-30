package io.vertx.test.core;

import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.http.HttpClientRequestBuilder;
import io.vertx.core.http.HttpServerOptions;
import org.junit.Test;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class HttpClientRequestBuilderTest extends HttpTestBase {

  @Override
  public void setUp() throws Exception {
    super.setUp();
    client = vertx.createHttpClient(new HttpClientOptions());
    server = vertx.createHttpServer(new HttpServerOptions().setPort(DEFAULT_HTTP_PORT).setHost(DEFAULT_HTTP_HOST));
  }

  @Test
  public void testGet() throws Exception {
    waitFor(4);
    server.requestHandler(req -> {
      complete();
    });
    startServer();
    HttpClientRequestBuilder get = client.createGet(DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, "/somepath");
    get.request(onSuccess(resp -> {
      complete();
    }));
    get.request(onSuccess(resp -> {
      complete();
    }));
  }
}
