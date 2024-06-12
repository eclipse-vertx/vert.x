package io.vertx.core.http;

import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.MessageToByteEncoder;
import io.netty.handler.codec.compression.BrotliEncoder;
import io.netty.handler.codec.compression.StandardCompressionOptions;

public class Http1xBrotliCompressionTest extends HttpCompressionTestBase {

  @Override
  protected HttpServerOptions createBaseServerOptions() {
    return new HttpServerOptions().setPort(DEFAULT_HTTP_PORT).setHost(DEFAULT_HTTP_HOST);
  }

  @Override
  protected HttpClientOptions createBaseClientOptions() {
    return new HttpClientOptions().setDefaultPort(DEFAULT_HTTP_PORT).setDefaultHost(DEFAULT_HTTP_HOST);
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
