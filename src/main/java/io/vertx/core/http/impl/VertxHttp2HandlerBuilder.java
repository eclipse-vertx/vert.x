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

import io.netty.handler.codec.http2.AbstractHttp2ConnectionHandlerBuilder;
import io.netty.handler.codec.http2.Http2ConnectionDecoder;
import io.netty.handler.codec.http2.Http2ConnectionEncoder;
import io.netty.handler.codec.http2.Http2Settings;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServerRequest;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class VertxHttp2HandlerBuilder extends AbstractHttp2ConnectionHandlerBuilder<VertxHttp2Handler, VertxHttp2HandlerBuilder> {

  private final Vertx vertx;
  private final Handler<HttpServerRequest> handler;

  public VertxHttp2HandlerBuilder(Vertx vertx, Handler<HttpServerRequest> handler) {
    this.vertx = vertx;
    this.handler = handler;
  }

  @Override
  protected VertxHttp2Handler build() {
/*
    if (encoder() != null) {
      assert decoder() != null;
      return buildFromCodec(decoder(), encoder());
    }

    Http2Connection connection = this.connection();
    if (connection == null) {
      connection = new DefaultHttp2Connection(isServer());
    }

    return buildFromConnection(connection);
*/
    return super.build();
  }

  @Override
  protected VertxHttp2Handler build(Http2ConnectionDecoder decoder, Http2ConnectionEncoder encoder, Http2Settings initialSettings) throws Exception {
    VertxHttp2Handler vertxHttp2Handler = new VertxHttp2Handler(vertx, decoder, encoder, initialSettings, handler);
    frameListener(vertxHttp2Handler);
    return vertxHttp2Handler;
  }

/*
  private VertxHttp2Handler buildFromCodec(Http2ConnectionDecoder decoder, Http2ConnectionEncoder encoder) {
    final VertxHttp2Handler handler;
    try {
      // Call the abstract build method
      handler = build(decoder, encoder, initialSettings());
    } catch (Throwable t) {
      encoder.close();
      decoder.close();
      throw new IllegalStateException("failed to build a Http2ConnectionHandler", t);
    }

    // Setup post build options
    handler.gracefulShutdownTimeoutMillis(gracefulShutdownTimeoutMillis());
    if (handler.decoder().frameListener() == null) {
      handler.decoder().frameListener(frameListener());
    }
    return handler;
  }
*/

/*
  private VertxHttp2Handler buildFromConnection(Http2Connection connection) {
    Http2FrameReader reader = new DefaultHttp2FrameReader(isValidateHeaders());
    Http2FrameWriter writer = new DefaultHttp2FrameWriter(headerSensitivityDetector()) {
      @Override
      public ChannelFuture writeWindowUpdate(ChannelHandlerContext ctx, int streamId, int windowSizeIncrement, ChannelPromise promise) {
        new Exception().printStackTrace();
        ChannelFuture channelFuture = super.writeWindowUpdate(ctx, streamId, windowSizeIncrement, promise);
        channelFuture.addListener(l -> {
        });
        return channelFuture;
      }
    };

    if (frameLogger() != null) {
      reader = new Http2InboundFrameLogger(reader, frameLogger());
      writer = new Http2OutboundFrameLogger(writer, frameLogger());
    }

    Http2ConnectionEncoder encoder = new DefaultHttp2ConnectionEncoder(connection, writer);
    boolean encoderEnforceMaxConcurrentStreams = encoderEnforceMaxConcurrentStreams();

    if (encoderEnforceMaxConcurrentStreams) {
      if (connection.isServer()) {
        encoder.close();
        reader.close();
        throw new IllegalArgumentException(
            "encoderEnforceMaxConcurrentStreams: " + encoderEnforceMaxConcurrentStreams +
                " not supported for server");
      }
      encoder = new StreamBufferingEncoder(encoder);
    }

    Http2ConnectionDecoder decoder = new DefaultHttp2ConnectionDecoder(connection, encoder, reader);
    return buildFromCodec(decoder, encoder);
  }
*/
}
