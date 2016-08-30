package io.vertx.test.core;

import io.vertx.core.buffer.Buffer;
import io.vertx.core.file.AsyncFile;
import io.vertx.core.file.OpenOptions;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.http.HttpClientRequestBuilder;
import io.vertx.core.http.HttpServerOptions;
import org.junit.Test;

import java.io.File;
import java.nio.file.Files;

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
      req.response().end();
    });
    startServer();
    HttpClientRequestBuilder get = client.createGet(DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, "/somepath");
    get.request(onSuccess(resp -> {
      complete();
    }));
    get.request(onSuccess(resp -> {
      complete();
    }));
    await();
  }

  @Test
  public void testPost() throws Exception {
    String expected = TestUtils.randomAlphaString(1024 * 1024);
    File f = File.createTempFile("vertx", ".data");
    f.deleteOnExit();
    Files.write(f.toPath(), expected.getBytes());
    waitFor(2);
    server.requestHandler(req -> {
      req.bodyHandler(buff -> {
        assertEquals(Buffer.buffer(expected), buff);
        complete();
        req.response().end();
      });
    });
    startServer();
    vertx.runOnContext(v -> {
      AsyncFile asyncFile = vertx.fileSystem().openBlocking(f.getAbsolutePath(), new OpenOptions());
      HttpClientRequestBuilder post = client.createGet(DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, "/somepath");
      post.putHeader("Content-Length", "" + expected.length())
          .request(asyncFile, onSuccess(resp -> {
            assertEquals(200, resp.statusCode());
      complete();
          }));
    });
    await();
  }
}
