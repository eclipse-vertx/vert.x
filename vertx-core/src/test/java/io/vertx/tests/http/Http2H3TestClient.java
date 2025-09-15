package io.vertx.tests.http;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioDatagramChannel;
import io.netty.handler.codec.Headers;
import io.netty.handler.codec.http2.*;
import io.netty.handler.codec.http3.*;
import io.netty.handler.codec.quic.*;
import io.netty.handler.ssl.ApplicationProtocolNames;
import io.netty.handler.ssl.ApplicationProtocolNegotiationHandler;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.concurrent.Future;
import io.vertx.core.Vertx;
import io.vertx.core.http.impl.http2.Http2HeadersMultiMap;

import java.net.InetSocketAddress;
import java.util.List;
import java.util.concurrent.TimeUnit;

class Http2H3TestClient extends Http2TestClient {

  public Http2H3TestClient(Vertx vertx, List<EventLoopGroup> eventLoopGroups, RequestHandler requestHandler) {
    super(vertx, eventLoopGroups, requestHandler);
  }

  @Override
  protected ChannelInitializer channelInitializer(int port, String host, GeneralConnectionHandler handler) {
    return new ChannelInitializer<Channel>() {
      @Override
      protected void initChannel(Channel ch) throws Exception {
        QuicSslContext context = QuicSslContextBuilder.forClient()
          .trustManager(InsecureTrustManagerFactory.INSTANCE)
          .applicationProtocols(Http3.supportedApplicationProtocols()).build();
        ChannelHandler codec = Http3.newQuicClientCodecBuilder()
          .sslContext(context)
          .maxIdleTimeout(5000, TimeUnit.MILLISECONDS)
          .initialMaxData(10000000)
          .initialMaxStreamDataBidirectionalLocal(1000000)
          .build();

        ch.pipeline().addLast(codec);
      }
    };
  }

  @Override
  public Future connect(int port, String host, GeneralConnectionHandler handler) {
    Bootstrap bootstrap = new Bootstrap();
    NioEventLoopGroup eventLoopGroup = new NioEventLoopGroup();
    eventLoopGroups.add(eventLoopGroup);
    bootstrap.channel(NioDatagramChannel.class);
    bootstrap.group(eventLoopGroup);

    bootstrap.handler(channelInitializer(port, host, handler));
    ChannelFuture fut = bootstrap.connect(new InetSocketAddress(host, port));
    try {
      fut.sync();
    } catch (InterruptedException e) {
      throw new RuntimeException(e);
    }
    NioDatagramChannel nioDatagramChannel = (NioDatagramChannel) fut.channel();
    Future<QuicChannel> fut2 = QuicChannel.newBootstrap(nioDatagramChannel)
      .handler(new ChannelInitializer<QuicChannel>() {
        @Override
        protected void initChannel(QuicChannel ch) throws Exception {
          ch.pipeline().addLast(new Http3ClientConnectionHandler());
        }
      })
      .remoteAddress(new InetSocketAddress(host, port))
      .connect();

    try {
      fut2.sync();
    } catch (InterruptedException e) {
      throw new RuntimeException(e);
    }

    return Http3.newRequestStream(fut2.getNow(), new ChannelInitializer<QuicStreamChannel>() {
      @Override
      protected void initChannel(QuicStreamChannel streamChannel) {
        streamChannel.pipeline().addLast(new StreamChannelHandler(handler));

        handler.requestHandler = Http2H3TestClient.this.requestHandler;
        TestH3ClientHandler clientHandler = new TestH3ClientHandler(handler, null, null, new Http2Settings());
        streamChannel.pipeline().addLast(clientHandler);

      }
    });
  }


  private class StreamChannelHandler extends Http3RequestStreamInboundHandler {
    private final GeneralConnectionHandler handler;
    private boolean headerReceived = false;
    private Http3DataFrame lastDataFrame;

    public StreamChannelHandler(GeneralConnectionHandler handler) {
      this.handler = handler;
    }

    @Override
    public synchronized void channelWritabilityChanged(ChannelHandlerContext ctx) throws Exception {
      super.channelWritabilityChanged(ctx);
    }

    @Override
    protected void channelRead(ChannelHandlerContext ctx, Http3HeadersFrame frame) throws Exception {
      headerReceived = true;
      QuicStreamChannel streamChannel = (QuicStreamChannel) ctx.channel();
      Http2HeadersMultiMap headersMultiMap = new Http2HeadersMultiMap(frame.headers());
      headersMultiMap.validate(false);
      handler.onHeadersRead(ctx, (int) streamChannel.streamId(), headersMultiMap, 0, (short) 0, false, 0, false);
      ReferenceCountUtil.release(frame);
    }

    @Override
    protected void channelRead(ChannelHandlerContext ctx, Http3DataFrame frame) throws Exception {
      headerReceived = false;
      QuicStreamChannel streamChannel = (QuicStreamChannel) ctx.channel();

      if (lastDataFrame != null) {
        handler.onDataRead(ctx, (int) streamChannel.streamId(), lastDataFrame.content(), 0, false);
      }
      lastDataFrame = frame;
    }

    @Override
    protected void channelInputClosed(ChannelHandlerContext ctx) throws Exception {
      if (lastDataFrame != null) {
        handler.onDataRead(ctx, (int) ((QuicStreamChannel)ctx.channel()).streamId(), lastDataFrame.content(), 0, true);
        lastDataFrame = null;
      }
    }

    @Override
    public void channelReadComplete(ChannelHandlerContext ctx) throws Exception {
      super.channelReadComplete(ctx);
    }

    @Override
    protected void handleQuicException(ChannelHandlerContext ctx, QuicException exception) {
      super.handleQuicException(ctx, exception);
      if ("STREAM_RESET".equals(exception.getMessage())) {
        QuicStreamChannel streamChannel = (QuicStreamChannel) ctx.channel();
        try {
          handler.onRstStreamRead((int) streamChannel.streamId(), 1);
        } catch (Http2Exception e) {
          throw new RuntimeException(e);
        }
      }
    }

    @Override
    protected void handleHttp3Exception(ChannelHandlerContext ctx, Http3Exception exception) {
      super.handleHttp3Exception(ctx, exception);
      ctx.close();
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
      super.userEventTriggered(ctx, evt);
    }

    @Override
    protected void channelRead(ChannelHandlerContext ctx, Http3UnknownFrame frame) {
      super.channelRead(ctx, frame);
      handler.onUnknownFrame(ctx, (byte) frame.type(), (int) ((QuicStreamChannel) ctx.channel()).streamId(), null, frame.content());
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
      super.exceptionCaught(ctx, cause);
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
      super.channelActive(ctx);
    }
  }

}


class TestH3ClientHandler extends ChannelInboundHandlerAdapter {

  private final Http2TestClient.GeneralConnectionHandler myConnectionHandler;
  private boolean handled;

  public TestH3ClientHandler(
    Http2TestClient.GeneralConnectionHandler myConnectionHandler,
    Http2ConnectionDecoder decoder,
    Http2ConnectionEncoder encoder,
    Http2Settings initialSettings) {
//      super(decoder, encoder, initialSettings);
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
      Http2TestClient.Connection conn = new Http2TestClient.Connection(ctx, null, null, null);
      myConnectionHandler.accept0(conn);
    }
  }

  @Override
  public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
    // Ignore
  }
}


class Http2H3RequestHandler implements Http2TestClient.RequestHandler {
  private Http2TestClient.Connection request;

  @Override
  public void setConnection(Http2TestClient.Connection request) {
    this.request = request;
  }

  @Override
  public int nextStreamId() {
    throw new RuntimeException("Not Implemented");
  }

  @Override
  public void writeSettings(io.vertx.core.http.Http2Settings updatedSettings) {
    throw new RuntimeException("Not Implemented");
  }

  @Override
  public void writeHeaders(ChannelHandlerContext ctx, int streamId, Headers<CharSequence, CharSequence, ?> headers, int padding, boolean endStream, ChannelPromise promise) {
    ctx.write(new DefaultHttp3HeadersFrame((Http3Headers) headers), promise);
  }

  @Override
  public void writeHeaders(ChannelHandlerContext ctx, int streamId, Http2HeadersMultiMap headers, int padding, boolean endStream, ChannelPromise promise) {
    ctx.write(new DefaultHttp3HeadersFrame((Http3Headers) headers.unwrap()), promise);
    if (endStream) {
      flush();
    }
  }

  @Override
  public void writeData(ChannelHandlerContext ctx, int streamId, ByteBuf data, int padding, boolean endStream, ChannelPromise promise) {
    ctx.write(new DefaultHttp3DataFrame(data), promise);
    if (endStream) {
      flush();
      ctx.close();
    }
  }

  @Override
  public void writeRstStream(ChannelHandlerContext ctx, int streamId, long errorCode, ChannelPromise promise) {
    ((QuicStreamChannel) ctx.channel()).shutdownOutput((int) errorCode, promise);
  }

  @Override
  public void writePriority(ChannelHandlerContext ctx, int streamId, int streamDependency, short weight, boolean exclusive, ChannelPromise promise) {
    throw new RuntimeException("Not Implemented");
  }

  @Override
  public void flush() {
    request.channel.parent().flush();
  }

  @Override
  public void writeGoAway(ChannelHandlerContext ctx, int lastStreamId, long errorCode, ByteBuf debugData, ChannelPromise promise) {
    QuicStreamChannel controlStreamChannel = Http3.getLocalControlStream(ctx.channel().parent());
    controlStreamChannel.write(new DefaultHttp3GoAwayFrame(lastStreamId));
  }

  @Override
  public void writeFrame(ChannelHandlerContext ctx, byte b, int id, Http2Flags http2Flags, ByteBuf byteBuf, ChannelPromise channelPromise) {
    ctx.channel().write(new DefaultHttp3UnknownFrame(b, byteBuf));
  }

  @Override
  public void writePing(ChannelHandlerContext ctx, boolean ack, long data, ChannelPromise promise) {
    throw new RuntimeException("Not Implemented");
  }

  @Override
  public void writeHeaders(ChannelHandlerContext ctx, int streamId, Http2HeadersMultiMap headers, int streamDependency, short weight, boolean exclusive, int padding, boolean endStream, ChannelPromise promise) {
    throw new RuntimeException("Not Implemented");
  }

  @Override
  public boolean isWritable(int id) {
    return request.channel.isWritable();
  }

  @Override
  public boolean consumeBytes(int id, int numBytes) throws Http2Exception {
    return false;
  }
}
