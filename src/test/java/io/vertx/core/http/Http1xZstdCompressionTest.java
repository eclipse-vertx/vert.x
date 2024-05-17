package io.vertx.core.http;

import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.MessageToByteEncoder;
import io.netty.handler.codec.compression.StandardCompressionOptions;
import io.netty.handler.codec.compression.Zstd;
import io.netty.handler.codec.compression.ZstdEncoder;
import org.junit.Assert;
import org.junit.BeforeClass;


public class Http1xZstdCompressionTest extends HttpCompressionTestBase {

  @BeforeClass
  public static void zstdLibraryIsAvailable() {
    Assert.assertTrue(Zstd.isAvailable());
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
  protected MessageToByteEncoder<ByteBuf> encoder() {
    return new ZstdEncoder();
  }

  @Override
  protected String encoding() {
    return "zstd";
  }

  @Override
  protected void configureServerCompression(HttpServerOptions options) {
    options.setCompressionSupported(true).addCompressor(StandardCompressionOptions.zstd());
  }
}
