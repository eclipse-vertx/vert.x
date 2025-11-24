package io.vertx.tests.http.compression;

import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.MessageToByteEncoder;
import io.netty.handler.codec.compression.*;

import java.util.function.Supplier;

public class CompressionConfig {

  public static CompressionConfig gzip(int level) {
    return new CompressionConfig("gzip", () -> new JdkZlibEncoder(ZlibWrapper.GZIP, level),
      StandardCompressionOptions.gzip(level, StandardCompressionOptions.gzip().windowBits(), StandardCompressionOptions.gzip().memLevel())) {
      @Override
      public String toString() {
        return "(gzip level:" + level + ")";
      }
    };
  }

  public static CompressionConfig brotli() {
    return new CompressionConfig("br", BrotliEncoder::new, StandardCompressionOptions.brotli()) {
      @Override
      public String toString() {
        return "(brotli)";
      }
    };
  }

  public static CompressionConfig snappy() {
    return new CompressionConfig("snappy", SnappyFrameEncoder::new, StandardCompressionOptions.snappy()) {
      @Override
      public String toString() {
        return "(snappy)";
      }
    };
  }

  public static CompressionConfig zstd() {
    return new CompressionConfig("zstd", ZstdEncoder::new, StandardCompressionOptions.zstd()) {
      @Override
      public String toString() {
        return "(zstd)";
      }
    };
  }

  public final String encoding;
  public final Supplier<MessageToByteEncoder<ByteBuf>> encoder;
  public final CompressionOptions compressor;

  public CompressionConfig(String encoding, Supplier<MessageToByteEncoder<ByteBuf>> encoder, CompressionOptions compressor) {
    this.encoding = encoding;
    this.encoder = encoder;
    this.compressor = compressor;
  }
}
