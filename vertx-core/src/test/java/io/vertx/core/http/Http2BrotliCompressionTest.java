package io.vertx.core.http;

import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.MessageToByteEncoder;
import io.netty.handler.codec.compression.BrotliEncoder;
import io.netty.handler.codec.compression.StandardCompressionOptions;

public class Http2BrotliCompressionTest extends HttpCompressionTestBase {

  @Override
  protected HttpServerOptions createBaseServerOptions() {
    return Http2TestBase.createHttp2ServerOptions(HttpTestBase.DEFAULT_HTTPS_PORT, HttpTestBase.DEFAULT_HTTPS_HOST);
  }

  @Override
  protected HttpClientOptions createBaseClientOptions() {
    return Http2TestBase.createHttp2ClientOptions().setDefaultPort(DEFAULT_HTTPS_PORT).setDefaultHost(DEFAULT_HTTPS_HOST);
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
