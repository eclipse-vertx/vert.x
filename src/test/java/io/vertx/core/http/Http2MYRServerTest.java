/*
 * Copyright (c) 2011-2019 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package io.vertx.core.http;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http2.AbstractHttp2ConnectionHandlerBuilder;
import io.netty.handler.codec.http2.DefaultHttp2Connection;
import io.netty.handler.codec.http2.DefaultHttp2ConnectionDecoder;
import io.netty.handler.codec.http2.DefaultHttp2ConnectionEncoder;
import io.netty.handler.codec.http2.DefaultHttp2FrameReader;
import io.netty.handler.codec.http2.DefaultHttp2FrameWriter;
import io.netty.handler.codec.http2.DefaultHttp2Headers;
import io.netty.handler.codec.http2.DefaultHttp2HeadersDecoder;
import io.netty.handler.codec.http2.Http2Connection;
import io.netty.handler.codec.http2.Http2ConnectionDecoder;
import io.netty.handler.codec.http2.Http2ConnectionEncoder;
import io.netty.handler.codec.http2.Http2ConnectionHandler;
import io.netty.handler.codec.http2.Http2EventAdapter;
import io.netty.handler.codec.http2.Http2Exception;
import io.netty.handler.codec.http2.Http2FrameListener;
import io.netty.handler.codec.http2.Http2FrameListenerDecorator;
import io.netty.handler.codec.http2.Http2FrameReader;
import io.netty.handler.codec.http2.Http2FrameWriter;
import io.netty.handler.codec.http2.Http2Headers;
import io.netty.handler.codec.http2.Http2Settings;
import org.junit.Test;

import java.net.InetSocketAddress;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;

import static io.netty.handler.codec.http2.Http2CodecUtil.DEFAULT_HEADER_LIST_SIZE;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class Http2MYRServerTest extends Http2TestBase {

  @Test
  public void testMYR() throws Exception {

    int maxConcurrentStreams = 10;
    int maxRstFramePerWindow = 15;

    HttpServer server = vertx.createHttpServer(new HttpServerOptions()
      .setHttp2RstFloodMaxRstFramePerWindow(maxRstFramePerWindow)
      .setInitialSettings(new io.vertx.core.http.Http2Settings().setMaxConcurrentStreams(maxConcurrentStreams))
      .setHttp2ClearTextEnabled(true));


    AtomicInteger inflightRequests = new AtomicInteger();
    AtomicInteger maxInflightRequests = new AtomicInteger();
    AtomicInteger receivedRstFrames = new AtomicInteger();
    CompletableFuture<Void> goAway = new CompletableFuture<>();

    server.requestHandler(req -> {
      int val = inflightRequests.incrementAndGet();
      if (val > maxInflightRequests.get()) {
        maxInflightRequests.set(val);
      }
      req.exceptionHandler(err -> {
        inflightRequests.decrementAndGet();
      });
    });

    server.listen(8080, "localhost").toCompletionStage().toCompletableFuture().get(10, TimeUnit.SECONDS);

    class Http2Bootstrap {

      protected ChannelInitializer<?> channelInitializer(BiConsumer<ChannelHandlerContext, Http2ConnectionHandler> handler) {
        return new ChannelInitializer<Channel>() {
          @Override
          protected void initChannel(Channel ch) {
            class Builder extends AbstractHttp2ConnectionHandlerBuilder<Http2ConnectionHandler, Builder> {

              private Http2ConnectionHandler connectionHandler;

              @Override
              protected Http2ConnectionHandler build(Http2ConnectionDecoder decoder, Http2ConnectionEncoder encoder, Http2Settings initialSettings) throws Exception {
                connectionHandler = new Http2ConnectionHandler(decoder, encoder, initialSettings) {
                };
                return connectionHandler;
              }

              public Http2ConnectionHandler build() {

                Http2FrameReader reader = new DefaultHttp2FrameReader(new DefaultHttp2HeadersDecoder(isValidateHeaders(), DEFAULT_HEADER_LIST_SIZE, -1)) {
                  @Override
                  public void readFrame(ChannelHandlerContext ctx, ByteBuf input, Http2FrameListener listener) throws Http2Exception {
                    super.readFrame(ctx, input, new Http2FrameListenerDecorator(listener) {
                      @Override
                      public void onRstStreamRead(ChannelHandlerContext ctx, int streamId, long errorCode) throws Http2Exception {
                        receivedRstFrames.incrementAndGet();
                        try {
                          super.onRstStreamRead(ctx, streamId, errorCode);
                        } catch (Http2Exception ignore) {
                          // Reset frames sent by the server are invalid because the stream is already ended
                          // we need to intercept the exception in order to avoid the frame reader closing the connection
                        }
                      }
                    });
                  }
                };
                Http2FrameWriter writer = new DefaultHttp2FrameWriter(headerSensitivityDetector());

                Http2Connection connection = new DefaultHttp2Connection(false, maxConcurrentStreams);
                Http2ConnectionEncoder encoder = new DefaultHttp2ConnectionEncoder(connection, writer);
                Http2ConnectionDecoder decoder = new DefaultHttp2ConnectionDecoder(connection, encoder, reader, promisedRequestVerifier(), isAutoAckSettingsFrame(), isAutoAckPingFrame(), isValidateHeaders());

                codec(decoder, encoder);

                frameListener(new Http2EventAdapter() {

                  private boolean initialSettings = true;

                  @Override
                  public void onSettingsRead(ChannelHandlerContext ctx, Http2Settings settings) throws Http2Exception {
                    if (initialSettings) {
                      initialSettings = false;
                      handler.accept(ctx, connectionHandler);
                    }
                  }

                  @Override
                  public void onGoAwayRead(ChannelHandlerContext ctx, int lastStreamId, long errorCode, ByteBuf debugData) throws Http2Exception {
                    goAway.complete(null);
                  }
                });
                return super.build();
              }
            }

            Builder clientHandlerBuilder = new Builder();
            Http2ConnectionHandler clientHandler = clientHandlerBuilder.build();
            ch.pipeline().addLast(clientHandler);
          }
        };
      }

      public ChannelFuture connect(int port, String host, BiConsumer<ChannelHandlerContext, Http2ConnectionHandler> handler) {
        Bootstrap bootstrap = new Bootstrap();
        NioEventLoopGroup eventLoopGroup = new NioEventLoopGroup();
        eventLoopGroups.add(eventLoopGroup);
        bootstrap.channel(NioSocketChannel.class);
        bootstrap.group(eventLoopGroup);
        bootstrap.handler(channelInitializer(handler));
        return bootstrap.connect(new InetSocketAddress(host, port));
      }
    }

    Http2Bootstrap bootstrap = new Http2Bootstrap();
    bootstrap.connect(8080, "localhost", (chctx, connectionHandler) -> {
      int numStreams = maxConcurrentStreams + 100;
      Http2Headers headers = new DefaultHttp2Headers().method("GET").scheme("http").path("/").authority("localhost:8080");
      Http2FrameWriter frameWriter = connectionHandler.encoder().frameWriter();
      for (int i = 0; i < numStreams; i++) {
        int id = connectionHandler.connection().local().incrementAndGetNextStreamId();
        frameWriter.writeHeaders(chctx, id, headers, 0, true, chctx.newPromise());
        frameWriter.writeWindowUpdate(chctx, id, 0, chctx.newPromise());
      }
      chctx.flush();
    }).sync();

    goAway.get(10, TimeUnit.SECONDS);

    // Check the number of rst frame received before getting a go away
    assertEquals(receivedRstFrames.get(), maxRstFramePerWindow + 1);
    assertEquals(maxInflightRequests.get(), maxRstFramePerWindow + 1);
  }
}
