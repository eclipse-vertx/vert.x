package io.vertx.core.http.impl.http2.multiplex;

import io.netty.handler.codec.http2.CompressorHttp2ConnectionEncoder;
import io.netty.handler.codec.http2.DelegatingDecompressorFrameListener;
import io.netty.handler.codec.http2.Http2Connection;
import io.netty.handler.codec.http2.Http2ConnectionDecoder;
import io.netty.handler.codec.http2.Http2ConnectionEncoder;
import io.netty.handler.codec.http2.Http2FrameCodec;
import io.netty.handler.codec.http2.Http2FrameCodecBuilder;
import io.netty.handler.codec.http2.Http2FrameListener;
import io.netty.handler.codec.http2.Http2FrameLogger;
import io.netty.handler.codec.http2.Http2Settings;
import io.netty.handler.logging.LogLevel;
import io.vertx.core.http.impl.CompressionManager;

class Http2CustomFrameCodecBuilder extends Http2FrameCodecBuilder {

  private final CompressionManager compressionManager;
  private final boolean decompressionSupported; // options.isDecompressionSupported()
  private boolean logEnabled;

  Http2CustomFrameCodecBuilder(CompressionManager compressionManager, boolean decompressionSupported) {
    this.compressionManager = compressionManager;
    this.decompressionSupported = decompressionSupported;
    gracefulShutdownTimeoutMillis(0);
  }

  @Override
  public Http2CustomFrameCodecBuilder initialSettings(Http2Settings settings) {
    return (Http2CustomFrameCodecBuilder) super.initialSettings(settings);
  }

  @Override
  public Http2CustomFrameCodecBuilder server(boolean isServer) {
    return (Http2CustomFrameCodecBuilder) super.server(isServer);
  }

  public Http2CustomFrameCodecBuilder logEnabled(boolean logEnabled) {
    this.logEnabled = logEnabled;
    return this;
  }

  @Override
  public Http2FrameCodec build() {
    if (logEnabled) {
      frameLogger(new Http2FrameLogger(LogLevel.DEBUG));
    }
    return super.build();
  }

  @Override
  protected Http2FrameCodec build(Http2ConnectionDecoder decoder, Http2ConnectionEncoder encoder, Http2Settings initialSettings) {
    Http2FrameCodec codec;
    if (isServer() && compressionManager != null) {
      encoder = new CompressorHttp2ConnectionEncoder(encoder, compressionManager.options());
    }
    codec = super.build(decoder, encoder, initialSettings);
    if (decompressionSupported) {
      decoder = codec.decoder();
      Http2Connection http2Connection = codec.connection();
      Http2FrameListener listener = decoder.frameListener();
      decoder.frameListener(new DelegatingDecompressorFrameListener(http2Connection, listener, 0));
    }
    return codec;
  }
}
