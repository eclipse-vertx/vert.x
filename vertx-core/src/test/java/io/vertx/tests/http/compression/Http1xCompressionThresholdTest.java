package io.vertx.tests.http.compression;

import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.MessageToByteEncoder;
import io.netty.handler.codec.compression.JdkZlibEncoder;
import io.netty.handler.codec.compression.ZlibWrapper;
import io.netty.util.CharsetUtil;
import io.netty.util.internal.StringUtil;
import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.*;
import org.junit.Test;

public class Http1xCompressionThresholdTest extends HttpCompressionTest {

  @Override
  protected String encoding() {
    return "gzip";
  }

  @Override
  protected MessageToByteEncoder<ByteBuf> encoder() {
    return new JdkZlibEncoder(ZlibWrapper.GZIP);
  }

  @Override
  protected HttpServerOptions createBaseServerOptions() {
    return new HttpServerOptions().setPort(DEFAULT_HTTP_PORT).setHost(DEFAULT_HTTP_HOST);
  }

  @Override
  protected HttpClientOptions createBaseClientOptions() {
    return new HttpClientOptions().setDefaultPort(DEFAULT_HTTP_PORT).setDefaultHost(DEFAULT_HTTP_HOST);
  }

  @Override
  protected void configureServerCompression(HttpServerOptions options) {
    options.setCompressionSupported(true);
  }

  @Test
  public void testServerCompressionBelowThreshold() throws Exception {
    // set compression threshold to be greater than the content string size so it WILL NOT be compressed
    HttpServerOptions httpServerOptions = createBaseServerOptions();
    configureServerCompression(httpServerOptions);
    httpServerOptions.setCompressionContentSizeThreshold(COMPRESS_TEST_STRING.length() * 2);

    doTest(httpServerOptions, onSuccess(resp -> {
      // check content encoding header is not set
      assertNull(resp.getHeader(HttpHeaders.CONTENT_ENCODING));

      resp.body().onComplete(onSuccess(responseBuffer -> {
        // check that the response body bytes is itself
        String responseBody = responseBuffer.toString(CharsetUtil.UTF_8);
        assertEquals(COMPRESS_TEST_STRING, responseBody);
        testComplete();
      }));
    }));
  }

  @Test
  public void testServerCompressionAboveThreshold() throws Exception {
    // set compression threshold to be less than the content string size so it WILL be compressed
    HttpServerOptions httpServerOptions = createBaseServerOptions();
    configureServerCompression(httpServerOptions);
    httpServerOptions.setCompressionContentSizeThreshold(COMPRESS_TEST_STRING.length() / 2);

    doTest(httpServerOptions, onSuccess(resp -> {
      // check content encoding header is set
      assertEquals(encoding(), resp.getHeader(HttpHeaders.CONTENT_ENCODING));

      resp.body().onComplete(onSuccess(responseBuffer -> {
        // check that response body bytes is compressed
        assertEquals(StringUtil.toHexString(compressedTestString.getBytes()), StringUtil.toHexString(responseBuffer.getBytes()));
        testComplete();
      }));
    }));
  }

  private void doTest(HttpServerOptions httpServerOptions, Handler<AsyncResult<HttpClientResponse>> handler) throws Exception {
    server.close();
    server = vertx.createHttpServer(httpServerOptions);
    server.requestHandler(req -> {
      assertNotNull(req.headers().get(HttpHeaders.ACCEPT_ENCODING));
      req.response()
        .end(Buffer.buffer(COMPRESS_TEST_STRING).toString(CharsetUtil.UTF_8));
    });
    startServer();
    client.request(new RequestOptions())
      .onComplete(onSuccess(req -> {
        req.putHeader(HttpHeaders.ACCEPT_ENCODING, encoding());
        req.send().onComplete(handler);
      }));
    await();
  }
}
