package io.vertx.tests.http.compression;

import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.MessageToByteEncoder;
import io.netty.handler.codec.compression.BrotliEncoder;
import io.netty.handler.codec.compression.StandardCompressionOptions;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.test.http.HttpTestBase;
import io.vertx.tests.http.HttpOptionsFactory;

public class Http2BrotliCompressionTest extends HttpCompressionTest {

  @Override
  protected HttpServerOptions createBaseServerOptions() {
    return HttpOptionsFactory.createHttp2ServerOptions(HttpTestBase.DEFAULT_HTTPS_PORT, HttpTestBase.DEFAULT_HTTPS_HOST);
  }

  @Override
  protected HttpClientOptions createBaseClientOptions() {
    return HttpOptionsFactory.createHttp2ClientOptions().setDefaultPort(DEFAULT_HTTPS_PORT).setDefaultHost(DEFAULT_HTTPS_HOST);
  }

  @Override
  protected MessageToByteEncoder<ByteBuf> encoder() {
    return new BrotliEncoder();
  }

  @Override
  protected String encoding() {
    return "br";
  }

  @Override
  protected void configureServerCompression(HttpServerOptions options) {
    options.setCompressionSupported(true).addCompressor(StandardCompressionOptions.brotli());
  }
}
