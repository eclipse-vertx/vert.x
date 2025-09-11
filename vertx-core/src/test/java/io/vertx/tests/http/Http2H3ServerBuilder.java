package io.vertx.tests.http;

import io.netty.bootstrap.AbstractBootstrap;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioDatagramChannel;
import io.netty.handler.codec.http3.Http3;
import io.netty.handler.codec.http3.Http3ConnectionHandler;
import io.netty.handler.codec.http3.Http3DataFrame;
import io.netty.handler.codec.http3.Http3Frame;
import io.netty.handler.codec.http3.Http3GoAwayFrame;
import io.netty.handler.codec.http3.Http3HeadersFrame;
import io.netty.handler.codec.http3.Http3RequestStreamInboundHandler;
import io.netty.handler.codec.quic.*;
import io.netty.util.ReferenceCountUtil;
import io.vertx.core.Handler;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.http.impl.http2.Http3Utils;
import io.vertx.core.internal.logging.Logger;
import io.vertx.core.internal.logging.LoggerFactory;
import io.vertx.core.net.impl.QuicUtils;
import io.vertx.test.tls.Cert;

class Http2H3ServerBuilder {
  private static final Logger log = LoggerFactory.getLogger(Http2H3ServerBuilder.class);
  private static final String AGENT_SERVER = "SERVER-TEST_MODE";

  private final Http2H3ClientTest http2H3ClientTest;
  private final HttpServerOptions serverOptions;

  private Handler<FrameHolder<Http3HeadersFrame>> headerHandler;
  private Handler<FrameHolder<Http3DataFrame>> dataHandler;
  private Handler<Http3GoAwayFrame> goAwayHandler;
  private Handler<ChannelHandlerContext> streamResetHandler;

  public Http2H3ServerBuilder(Http2H3ClientTest http2H3ClientTest, HttpServerOptions httpServerOptions) {
    this.http2H3ClientTest = http2H3ClientTest;
    this.serverOptions = httpServerOptions;
    this.headerHandler = frameHolder -> ReferenceCountUtil.release(frameHolder.frame());
    this.dataHandler = frameHolder -> ReferenceCountUtil.release(frameHolder.frame());
    this.goAwayHandler = ReferenceCountUtil::release;
    this.streamResetHandler = ctx -> {
    };
  }

  public AbstractBootstrap build() {
    AbstractBootstrap bootstrap = new Bootstrap();

    NioEventLoopGroup eventLoopGroup = new NioEventLoopGroup();
    http2H3ClientTest.addEventLoop(eventLoopGroup);
    bootstrap.group(eventLoopGroup);
    bootstrap.channel(NioDatagramChannel.class);

    QuicSslContext sslContext = null;
    try {
      sslContext = QuicSslContextBuilder.forServer(Cert.SERVER_JKS.get().getKeyManagerFactory(http2H3ClientTest.getVertx()), null)
        .applicationProtocols(Http3.supportedApplicationProtocols()).build();
    } catch (Exception e) {
      throw new RuntimeException(e);
    }

    ChannelHandler codec = QuicUtils.configureQuicCodecBuilder(Http3.newQuicServerCodecBuilder()
      .sslContext(sslContext)
      .datagram(2000000, 2000000)

      .maxRecvUdpPayloadSize(1000000000000L)  // 1 MB for receiving UDP payloads
      .maxSendUdpPayloadSize(1000000000000L)  // 1 MB for sending UDP payloads

      .tokenHandler(InsecureQuicTokenHandler.INSTANCE)
      .handler(new ChannelInitializer<>() {
        @Override
        protected void initChannel(Channel channel) throws Exception {
          channel.pipeline().addLast(createHttpConnectionHandler());
          channel.pipeline().addLast(new MyLoggerHandler());
        }
      }), serverOptions.getQuicOptions(), serverOptions.getSslHandshakeTimeout(), serverOptions.getSslHandshakeTimeoutUnit())
      .build();

    bootstrap.handler(codec);

    return bootstrap;
  }

  private Http3ConnectionHandler createHttpConnectionHandler() {
    return Http3Utils
      .newServerConnectionHandlerBuilder()
      .requestStreamHandler(streamChannel -> {
        //    streamChannel.closeFuture().addListener(ignored -> handleOnStreamChannelClosed(streamChannel));
        streamChannel.pipeline().addLast(new MyHttp3RequestStreamInboundHandler());
      })
      .agentType(AGENT_SERVER)
      .http3GoAwayFrameHandler(goAwayFrame -> {
        log.debug(String.format("%s GoAwayFrame received: %s", AGENT_SERVER, goAwayFrame));
        goAwayHandler.handle(goAwayFrame);
      })
      .build();
  }

  private class MyHttp3RequestStreamInboundHandler extends Http3RequestStreamInboundHandler {
    @Override
    protected void channelRead(ChannelHandlerContext ctx, Http3HeadersFrame frame) throws Exception {
      log.debug(String.format("%s - Received Header frame for channelId: %s", AGENT_SERVER, ctx.channel().id()));
      if (log.isDebugEnabled()) {
        log.debug(String.format("%s - Header is: %s", AGENT_SERVER, frame.headers()));
      }
      headerHandler.handle(new FrameHolder<>(ctx, frame));
      ReferenceCountUtil.release(frame);
    }

    @Override
    protected void channelRead(ChannelHandlerContext ctx, Http3DataFrame frame) throws Exception {
      if (log.isDebugEnabled()) {
        log.debug(String.format("%s - Received Data frame for channelId: %s and streamId: %s is: %s", AGENT_SERVER, ctx.channel().id(), ((QuicStreamChannel) ctx.channel()).streamId(), byteBufToString(frame.content())));
      } else {
        log.debug(String.format("%s - Received Data frame for channelId: %s and streamId: %s", AGENT_SERVER, ctx.channel().id(), ((QuicStreamChannel) ctx.channel()).streamId()));
      }

      dataHandler.handle(new FrameHolder<>(ctx, frame));
      ReferenceCountUtil.release(frame);
    }

    @Override
    protected void channelInputClosed(ChannelHandlerContext ctx) throws Exception {
      log.debug(String.format("%s - ChannelInputClosed called for channelId: %s, streamId: %s", AGENT_SERVER, ctx.channel().id(),
        ((QuicStreamChannel) ctx.channel()).streamId()));
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
      log.debug(String.format("%s - %s triggered in request stream handler", AGENT_SERVER, evt.getClass().getSimpleName()));
      super.userEventTriggered(ctx, evt);
    }

    @Override
    protected void handleQuicException(ChannelHandlerContext ctx, QuicException exception) {
      log.debug(String.format("%s - Caught exception in QuicStreamChannel handler, %s", AGENT_SERVER, exception.getMessage()));
      if ("STREAM_RESET".equals(exception.getMessage())) {
        streamResetHandler.handle(ctx);
      }
      super.handleQuicException(ctx, exception);
    }
  }

  private final static class MyLoggerHandler extends ChannelInboundHandlerAdapter {
    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
      log.debug(String.format("%s - %s triggered in QuicChannel handler", AGENT_SERVER, evt.getClass().getSimpleName()));
      super.userEventTriggered(ctx, evt);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
      log.debug(String.format("%s - Caught exception in QuicChannel handler, %s", AGENT_SERVER, cause.getMessage()));
      super.exceptionCaught(ctx, cause);
    }
  }

  public static final class FrameHolder<T extends Http3Frame> {
    private final QuicStreamChannel streamChannel;
    private final T frame;

    public FrameHolder(ChannelHandlerContext ctx, T frame) {
      this.frame = frame;
      this.streamChannel = (QuicStreamChannel) ctx.channel();
    }

    public T frame() {
      return frame;
    }

    public QuicStreamChannel streamChannel() {
      return streamChannel;
    }
  }

  private static String byteBufToString(ByteBuf content) {
    byte[] arr = new byte[content.readableBytes()];
    content.getBytes(content.readerIndex(), arr);
    return new String(arr);

  }

  public Http2H3ServerBuilder headerHandler(Handler<FrameHolder<Http3HeadersFrame>> headerHandler) {
    this.headerHandler = headerHandler;
    return this;
  }

  public Http2H3ServerBuilder dataHandler(Handler<FrameHolder<Http3DataFrame>> dataHandler) {
    this.dataHandler = dataHandler;
    return this;
  }

  public Http2H3ServerBuilder goAwayHandler(Handler<Http3GoAwayFrame> goAwayHandler) {
    this.goAwayHandler = goAwayHandler;
    return this;
  }

  public Http2H3ServerBuilder streamResetHandler(Handler<ChannelHandlerContext> streamResetHandler) {
    this.streamResetHandler = streamResetHandler;
    return this;
  }
}
