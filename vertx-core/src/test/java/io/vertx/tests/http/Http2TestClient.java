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
package io.vertx.tests.http;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.Headers;
import io.netty.handler.codec.http2.*;
import io.netty.handler.codec.quic.QuicStreamChannel;
import io.netty.handler.ssl.*;
import io.netty.util.concurrent.Future;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpVersion;
import io.vertx.core.http.impl.HttpUtils;
import io.vertx.core.http.impl.http2.Http2HeadersMultiMap;
import io.vertx.test.tls.Trust;

import java.net.InetSocketAddress;
import java.util.List;

/**
 * @author <a href="mailto:zolfaghari19@gmail.com">Iman Zolfaghari</a>
 */
class Http2TestClient {

  protected Vertx vertx;
  protected List<EventLoopGroup> eventLoopGroups;
  protected RequestHandler requestHandler;
  final Http2Settings settings = new Http2Settings();

  public Http2TestClient(Vertx vertx, List<EventLoopGroup> eventLoopGroups, RequestHandler requestHandler) {
    this.vertx = vertx;
    this.eventLoopGroups = eventLoopGroups;
    this.requestHandler = requestHandler;
  }

  public static class Connection {
    public final Channel channel;
    public final ChannelHandlerContext context;
    public final Http2Connection connection;
    public final Http2ConnectionEncoder encoder;
    public final Http2ConnectionDecoder decoder;

    public Connection(ChannelHandlerContext context, Http2Connection connection, Http2ConnectionEncoder encoder, Http2ConnectionDecoder decoder) {
      this.channel = context.channel();
      this.context = context;
      this.connection = connection;
      this.encoder = encoder;
      this.decoder = decoder;
    }

    public int nextStreamId() {
      return connection.local().incrementAndGetNextStreamId();
    }

  }


  interface RequestHandler {
    void setConnection(Connection request);

    int nextStreamId();

    void writeSettings(io.vertx.core.http.Http2Settings updatedSettings);

    void writeHeaders(ChannelHandlerContext ctx, int streamId, Http2HeadersMultiMap headers, int padding, boolean endStream, ChannelPromise promise);

    void writeHeaders(ChannelHandlerContext ctx, int streamId, Headers<CharSequence, CharSequence, ?> headers, int padding, boolean endStream, ChannelPromise promise);

    void writeData(ChannelHandlerContext ctx, int streamId, ByteBuf data, int padding, boolean endStream, ChannelPromise promise);

    void writeRstStream(ChannelHandlerContext ctx, int streamId, long errorCode, ChannelPromise promise);

    void writePriority(ChannelHandlerContext ctx, int streamId, int streamDependency,
                       short weight, boolean exclusive, ChannelPromise promise);

    void flush();

    void writeGoAway(ChannelHandlerContext ctx, int lastStreamId, long errorCode, ByteBuf debugData, ChannelPromise promise);

    void writeFrame(ChannelHandlerContext ctx, byte b, int id, Http2Flags http2Flags, ByteBuf byteBuf, ChannelPromise channelPromise);

    void writePing(ChannelHandlerContext ctx, boolean ack, long data, ChannelPromise promise);

    void writeHeaders(ChannelHandlerContext ctx, int streamId, Http2HeadersMultiMap headers,
                      int streamDependency, short weight, boolean exclusive, int padding, boolean endStream,
                      ChannelPromise promise);

    boolean isWritable(int id);

    boolean consumeBytes(int id, int numBytes) throws Http2Exception;
  }


  private static class MyHttp2FrameAdapter extends Http2FrameAdapter {
    private final GeneralConnectionHandler frameHandler;

    public MyHttp2FrameAdapter(GeneralConnectionHandler frameHandler) {
      this.frameHandler = frameHandler;
    }

    @Override
    public void onSettingsRead(ChannelHandlerContext ctx, Http2Settings newSettings) throws Http2Exception {
      frameHandler.onSettingsRead(newSettings);
    }

    @Override
    public void onHeadersRead(ChannelHandlerContext ctx, int streamId, Http2Headers headers, int streamDependency, short weight, boolean exclusive, int padding, boolean endStream) throws Http2Exception {
      Http2HeadersMultiMap headers1 = new Http2HeadersMultiMap(headers);
      headers1.validate(false);
      frameHandler.onHeadersRead(ctx, streamId, headers1, streamDependency, weight, exclusive, padding, endStream);
    }

    @Override
    public int onDataRead(ChannelHandlerContext ctx, int streamId, ByteBuf data, int padding, boolean endOfStream) throws Http2Exception {
      return frameHandler.onDataRead(ctx, streamId, data, padding, endOfStream);
    }

    @Override
    public void onRstStreamRead(ChannelHandlerContext ctx, int streamId, long errorCode) throws Http2Exception {
      super.onRstStreamRead(ctx, streamId, errorCode);
      frameHandler.onRstStreamRead(streamId, errorCode);
    }

    @Override
    public void onPushPromiseRead(ChannelHandlerContext ctx, int streamId, int promisedStreamId, Http2Headers headers, int padding) throws Http2Exception {
      super.onPushPromiseRead(ctx, streamId, promisedStreamId, headers, padding);
      Http2HeadersMultiMap headers1 = new Http2HeadersMultiMap(headers);
      headers1.validate(true);
      frameHandler.onPushPromiseRead(ctx, streamId, promisedStreamId, headers1, padding);
    }

    @Override
    public void onGoAwayRead(ChannelHandlerContext ctx, int lastStreamId, long errorCode, ByteBuf debugData) throws Http2Exception {
      super.onGoAwayRead(ctx, lastStreamId, errorCode, debugData);
      frameHandler.onGoAwayRead(ctx, lastStreamId, errorCode, debugData);
    }

    @Override
    public void onUnknownFrame(ChannelHandlerContext ctx, byte frameType, int streamId, Http2Flags flags, ByteBuf payload) {
      super.onUnknownFrame(ctx, frameType, streamId, flags, payload);
      frameHandler.onUnknownFrame(ctx, frameType, streamId, flags, payload);
    }

    @Override
    public void onPingRead(ChannelHandlerContext ctx, long data) throws Http2Exception {
      super.onPingRead(ctx, data);
      frameHandler.onPingRead(ctx, data);
    }

    @Override
    public void onWindowUpdateRead(ChannelHandlerContext ctx, int streamId, int windowSizeIncrement) throws Http2Exception {
      super.onWindowUpdateRead(ctx, streamId, windowSizeIncrement);
      frameHandler.onWindowUpdateRead(ctx, streamId, windowSizeIncrement);
    }

    @Override
    public void onPriorityRead(ChannelHandlerContext ctx, int streamId, int streamDependency, short weight, boolean exclusive) throws Http2Exception {
      super.onPriorityRead(ctx, streamId, streamDependency, weight, exclusive);
      frameHandler.onPriorityRead(ctx, streamId, streamDependency, weight, exclusive);
    }
  }


  public static class Http2RequestHandler implements RequestHandler {
    private Connection request;

    public void setConnection(Connection request) {
      this.request = request;
    }

    @Override
    public final int nextStreamId() {
      return request.connection.local().incrementAndGetNextStreamId();
    }

    @Override
    public final void writeSettings(io.vertx.core.http.Http2Settings updatedSettings) {
      request.encoder.writeSettings(request.context, HttpUtils.fromVertxSettings(updatedSettings), request.context.newPromise());
      request.context.flush();
    }

    @Override
    public final void writeHeaders(ChannelHandlerContext ctx, int streamId, Http2HeadersMultiMap headers, int padding, boolean endStream, ChannelPromise promise) {
      request.encoder.writeHeaders(ctx, streamId, (Http2Headers) headers.unwrap(), padding, endStream, promise);
      if (endStream) {
        request.context.flush();
      }
    }

    @Override
    public final void writeHeaders(ChannelHandlerContext ctx, int streamId, Http2HeadersMultiMap headers,
                                   int streamDependency, short weight, boolean exclusive, int padding, boolean endStream,
                                   ChannelPromise promise) {
      request.encoder.writeHeaders(ctx, streamId, (Http2Headers) headers.unwrap(), streamDependency, weight, exclusive, padding, endStream, promise);
      if (endStream) {
        request.context.flush();
      }
    }

    @Override
    public void writeHeaders(ChannelHandlerContext ctx, int streamId, Headers<CharSequence, CharSequence, ?> headers, int padding, boolean endStream, ChannelPromise promise) {
      writeHeaders(ctx, streamId, new Http2HeadersMultiMap(headers), padding, endStream, promise);
    }

    public final void writeData(ChannelHandlerContext ctx, int streamId, ByteBuf data, int padding, boolean endStream, ChannelPromise promise) {
      request.encoder.writeData(ctx, streamId, data, padding, endStream, promise);
      if (endStream) {
        request.context.flush();
      }
    }

    public final void writeRstStream(ChannelHandlerContext ctx, int streamId, long errorCode, ChannelPromise promise) {
      request.encoder.writeRstStream(ctx, streamId, errorCode, promise);
    }

    @Override
    public void writeGoAway(ChannelHandlerContext ctx, int lastStreamId, long errorCode, ByteBuf debugData, ChannelPromise promise) {
      request.encoder.writeGoAway(ctx, lastStreamId, errorCode, debugData, promise);
    }

    @Override
    public void writeFrame(ChannelHandlerContext ctx, byte frameType, int streamId, Http2Flags flags, ByteBuf payload, ChannelPromise promise) {
      request.encoder.writeFrame(ctx, frameType, streamId, flags, payload, promise);
    }

    @Override
    public void writePing(ChannelHandlerContext ctx, boolean ack, long data, ChannelPromise promise) {
      request.encoder.writePing(ctx, ack, data, promise);
    }

    @Override
    public void writePriority(ChannelHandlerContext ctx, int streamId, int streamDependency,
                              short weight, boolean exclusive, ChannelPromise promise) {
      request.encoder.writePriority(ctx, streamId, streamDependency, weight, exclusive, promise);
    }

    @Override
    public final void flush() {
      request.context.flush();
    }

    @Override
    public final boolean isWritable(int id) {
      return request.encoder.flowController().isWritable(request.connection.stream(id));
    }

    @Override
    public boolean consumeBytes(int id, int numBytes) throws Http2Exception {
      return request.decoder.flowController().consumeBytes(request.connection.stream(id), numBytes);
    }
  }


  public static abstract class GeneralConnectionHandler {
    protected RequestHandler requestHandler;

    protected int id;

    public GeneralConnectionHandler() {
    }

    public final void accept0(Connection conn) {
      requestHandler.setConnection(conn);

      if (!(conn.channel instanceof QuicStreamChannel)) {
        conn.decoder.frameListener(new MyHttp2FrameAdapter(this));
        this.id = requestHandler.nextStreamId();
      }

      accept(conn);
    }

    public void accept(Connection request) {
    }

    public int onDataRead(ChannelHandlerContext ctx, int streamId, ByteBuf data, int padding, boolean endOfStream) throws Http2Exception {
      return data.readableBytes() + padding;
    }

    public void onPushPromiseRead(ChannelHandlerContext ctx, int streamId, int promisedStreamId,
                                  Http2HeadersMultiMap headers, int padding) throws Http2Exception {
    }

    public void onSettingsRead(Http2Settings newSettings) {

    }

    public void onHeadersRead(ChannelHandlerContext ctx, int streamId, Http2HeadersMultiMap headers, int streamDependency, short weight, boolean exclusive, int padding, boolean endStream) throws Http2Exception {

    }

    public void onRstStreamRead(int streamId, long errorCode) throws Http2Exception {

    }

    public void onGoAwayRead(ChannelHandlerContext ctx, int lastStreamId, long errorCode, ByteBuf debugData) throws Http2Exception {
    }

    public void onUnknownFrame(ChannelHandlerContext ctx, byte frameType, int streamId, Http2Flags flags, ByteBuf payload) {
    }

    public void onPingRead(ChannelHandlerContext ctx, long data) throws Http2Exception {
    }

    public void onWindowUpdateRead(ChannelHandlerContext ctx, int streamId, int windowSizeIncrement) throws Http2Exception {
    }

    public void onPriorityRead(ChannelHandlerContext ctx, int streamId, int streamDependency, short weight, boolean exclusive) throws Http2Exception {
    }
  }

  class TestClientHandler extends Http2ConnectionHandler {

    private final GeneralConnectionHandler myConnectionHandler;
    private boolean handled;

    public TestClientHandler(
      GeneralConnectionHandler myConnectionHandler,
      Http2ConnectionDecoder decoder,
      Http2ConnectionEncoder encoder,
      Http2Settings initialSettings) {
      super(decoder, encoder, initialSettings);
      this.myConnectionHandler = myConnectionHandler;
    }

    @Override
    public void handlerAdded(ChannelHandlerContext ctx) throws Exception {
      super.handlerAdded(ctx);
      if (ctx.channel().isActive()) {
        checkHandle(ctx);
      }
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
      super.channelActive(ctx);
      checkHandle(ctx);
    }

    private void checkHandle(ChannelHandlerContext ctx) {
      if (!handled) {
        handled = true;
        Connection conn = new Connection(ctx, connection(), encoder(), decoder());
        myConnectionHandler.accept0(conn);
      }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
      // Ignore
    }
  }


  class TestClientHandlerBuilder extends AbstractHttp2ConnectionHandlerBuilder<TestClientHandler, TestClientHandlerBuilder> {

    private final GeneralConnectionHandler myConnectionHandler;

    public TestClientHandlerBuilder(GeneralConnectionHandler myConnectionHandler, RequestHandler requestHandler) {
      this.myConnectionHandler = myConnectionHandler;
      this.myConnectionHandler.requestHandler = requestHandler;
    }

    @Override
    protected TestClientHandler build(Http2ConnectionDecoder decoder, Http2ConnectionEncoder encoder, Http2Settings initialSettings) throws Exception {
      return new TestClientHandler(myConnectionHandler, decoder, encoder, initialSettings);
    }

    public TestClientHandler build(Http2Connection conn) {
      connection(conn);
      initialSettings(settings);
      frameListener(new Http2EventAdapter() {
        @Override
        public int onDataRead(ChannelHandlerContext ctx, int streamId, ByteBuf data, int padding, boolean endOfStream) throws Http2Exception {
          return super.onDataRead(ctx, streamId, data, padding, endOfStream);
        }
      });
      return super.build();
    }
  }


  protected ChannelInitializer channelInitializer(int port, String host, GeneralConnectionHandler handler) {
    return new ChannelInitializer<Channel>() {
      @Override
      protected void initChannel(Channel ch) throws Exception {
        SslContext sslContext = SslContextBuilder
          .forClient()
          .applicationProtocolConfig(new ApplicationProtocolConfig(
            ApplicationProtocolConfig.Protocol.ALPN,
            ApplicationProtocolConfig.SelectorFailureBehavior.NO_ADVERTISE,
            ApplicationProtocolConfig.SelectedListenerFailureBehavior.ACCEPT,
            HttpVersion.HTTP_2.alpnName(), HttpVersion.HTTP_1_1.alpnName()
          )).trustManager(Trust.SERVER_JKS.get().getTrustManagerFactory(vertx))
          .build();
        SslHandler sslHandler = sslContext.newHandler(ByteBufAllocator.DEFAULT, host, port);
        ch.pipeline().addLast(sslHandler);
        ch.pipeline().addLast(new ApplicationProtocolNegotiationHandler("whatever") {
          @Override
          protected void configurePipeline(ChannelHandlerContext ctx, String protocol) {
            if (ApplicationProtocolNames.HTTP_2.equals(protocol)) {
              ChannelPipeline p = ctx.pipeline();
              Http2Connection connection = new DefaultHttp2Connection(false);
              TestClientHandlerBuilder clientHandlerBuilder = new TestClientHandlerBuilder(handler, requestHandler);
              TestClientHandler clientHandler = clientHandlerBuilder.build(connection);
              p.addLast(clientHandler);
              return;
            }
            ctx.close();
            throw new IllegalStateException("unknown protocol: " + protocol);
          }
        });
      }
    };
  }

  public Future connect(int port, String host, GeneralConnectionHandler handler) {
    Bootstrap bootstrap = new Bootstrap();
    NioEventLoopGroup eventLoopGroup = new NioEventLoopGroup();
    eventLoopGroups.add(eventLoopGroup);
    bootstrap.channel(NioSocketChannel.class);
    bootstrap.group(eventLoopGroup);
    bootstrap.handler(channelInitializer(port, host, handler));
    return bootstrap.connect(new InetSocketAddress(host, port));
  }

}
