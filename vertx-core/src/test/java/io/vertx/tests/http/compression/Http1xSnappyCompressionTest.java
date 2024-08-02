package io.vertx.tests.http.compression;

import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.MessageToByteEncoder;
import io.netty.handler.codec.compression.SnappyFrameEncoder;
import io.netty.handler.codec.compression.StandardCompressionOptions;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.http.HttpServerOptions;

public class Http1xSnappyCompressionTest extends HttpCompressionTest {

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
    return new SnappyFrameEncoder();
  }

  @Override
  protected String encoding() {
    return "snappy";
  }

  @Override
  protected void configureServerCompression(HttpServerOptions options) {
    options.setCompressionSupported(true).addCompressor(StandardCompressionOptions.snappy());
  }
}
