/*
 * Copyright (c) 2011-2013 The original author or authors
 *  ------------------------------------------------------
 *  All rights reserved. This program and the accompanying materials
 *  are made available under the terms of the Eclipse Public License v1.0
 *  and Apache License v2.0 which accompanies this distribution.
 *
 *      The Eclipse Public License is available at
 *      http://www.eclipse.org/legal/epl-v10.html
 *
 *      The Apache License v2.0 is available at
 *      http://www.opensource.org/licenses/apache2.0.php
 *
 *  You may elect to redistribute this code under either of these licenses.
 */

package io.vertx.core.http.impl;

import io.netty.channel.Channel;
import io.netty.handler.codec.http2.AbstractHttp2ConnectionHandlerBuilder;
import io.netty.handler.codec.http2.CompressorHttp2ConnectionEncoder;
import io.netty.handler.codec.http2.DefaultHttp2LocalFlowController;
import io.netty.handler.codec.http2.DelegatingDecompressorFrameListener;
import io.netty.handler.codec.http2.Http2ConnectionDecoder;
import io.netty.handler.codec.http2.Http2ConnectionEncoder;
import io.netty.handler.codec.http2.Http2FrameLogger;
import io.netty.handler.codec.http2.Http2LocalFlowController;
import io.netty.handler.codec.http2.Http2Settings;
import io.netty.handler.logging.LogLevel;
import io.vertx.core.http.HttpServerOptions;

import java.util.Map;
import java.util.function.Function;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
class VertxHttp2ConnectionHandlerBuilder<C extends Http2ConnectionBase> extends AbstractHttp2ConnectionHandlerBuilder<VertxHttp2ConnectionHandler<C>, VertxHttp2ConnectionHandlerBuilder<C>> {

  private Map<Channel, ? super C> connectionMap;
  private boolean useCompression;
  private boolean useDecompression;
  private int compressionLevel = HttpServerOptions.DEFAULT_COMPRESSION_LEVEL;
  private io.vertx.core.http.Http2Settings initialSettings;
  private Function<VertxHttp2ConnectionHandler<C>, C> connectionFactory;
  private boolean logEnabled;

  VertxHttp2ConnectionHandlerBuilder() {
  }

  protected VertxHttp2ConnectionHandlerBuilder<C> server(boolean isServer) {
    return super.server(isServer);
  }

  VertxHttp2ConnectionHandlerBuilder<C> connectionMap(Map<Channel, ? super C> connectionMap) {
    this.connectionMap = connectionMap;
    return this;
  }

  VertxHttp2ConnectionHandlerBuilder<C> initialSettings(io.vertx.core.http.Http2Settings settings) {
    this.initialSettings = settings;
    return this;
  }

  VertxHttp2ConnectionHandlerBuilder<C> useCompression(boolean useCompression) {
    this.useCompression = useCompression;
    return this;
  }
  
  /** 
   * This method allows to set the compression level to be used in the http/2 connection encoder 
   * (for data sent to client) when compression support is turned on (@see useCompression) and 
   * the client advertises to support deflate/gizip compression in the Accept-Encoding header
   * 
   * default value is : 6 (Netty legacy)
   * 
   * While one can think that best value is always the maximum compression ratio, 
   * there's a trade-off to consider: the most compressed level requires the most computatinal work to compress/decompress, 
   * E.g. you have it set fairly high on a high-volume website, you may experience performance degradation 
   * and latency on resource serving due to CPU overload, and however - as the comptational work is required also client side 
   * while decompressing - setting an higher compression level can result in an overall higher page load time
   * especially nowadays when many clients are handled mobile devices with a low CPU profile.
   * 
   * see also: http://www.gzip.org/algorithm.txt
   * 
   * @param compressionLevel integer 1-9, 1 means use fastest algorithm, 9 slower algorithm but better compression ratio 
   * @return a reference to this instance for fulent API coding style
   */
  VertxHttp2ConnectionHandlerBuilder<C> compressionLevel(int compressionLevel) {
    this.compressionLevel = compressionLevel;
    return this;
  }

  VertxHttp2ConnectionHandlerBuilder<C> useDecompression(boolean useDecompression) {
    this.useDecompression = useDecompression;
    return this;
  }
  
  VertxHttp2ConnectionHandlerBuilder<C> connectionFactory(Function<VertxHttp2ConnectionHandler<C>, C> connectionFactory) {
    this.connectionFactory = connectionFactory;
    return this;
  }

  VertxHttp2ConnectionHandlerBuilder<C> logEnabled(boolean logEnabled) {
    this.logEnabled = logEnabled;
    return this;
  }

  @Override
  protected VertxHttp2ConnectionHandler<C> build() {
    if (initialSettings != null) {
      HttpUtils.fromVertxInitialSettings(isServer(), initialSettings, initialSettings());
    }
    if (logEnabled) {
      frameLogger(new Http2FrameLogger(LogLevel.DEBUG));
    }
    return super.build();
  }

  @Override
  protected VertxHttp2ConnectionHandler<C> build(Http2ConnectionDecoder decoder, Http2ConnectionEncoder encoder, Http2Settings initialSettings) throws Exception {
    if (isServer()) {
      if (useCompression) {
        encoder = new CompressorHttp2ConnectionEncoder(encoder,compressionLevel,CompressorHttp2ConnectionEncoder.DEFAULT_WINDOW_BITS,CompressorHttp2ConnectionEncoder.DEFAULT_MEM_LEVEL);
      }
      VertxHttp2ConnectionHandler<C> handler = new VertxHttp2ConnectionHandler<>(connectionMap, decoder, encoder, initialSettings, connectionFactory);
      if (useDecompression) {
        frameListener(new DelegatingDecompressorFrameListener(decoder.connection(), handler.connection));
      } else {
        frameListener(handler.connection);
      }
      return handler;
    } else {
      VertxHttp2ConnectionHandler<C> handler = new VertxHttp2ConnectionHandler<>(connectionMap, decoder, encoder, initialSettings, connectionFactory);
      if (useCompression) {
        frameListener(new DelegatingDecompressorFrameListener(decoder.connection(), handler.connection));
      } else {
        frameListener(handler.connection);
      }
      return handler;
    }
  }
}
