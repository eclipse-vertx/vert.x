package io.vertx.tests.http;

import io.netty.bootstrap.AbstractBootstrap;
import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioDatagramChannel;
import io.netty.incubator.codec.http3.*;
import io.netty.incubator.codec.quic.InsecureQuicTokenHandler;
import io.netty.incubator.codec.quic.QuicError;
import io.netty.incubator.codec.quic.QuicException;
import io.netty.incubator.codec.quic.QuicSslContext;
import io.netty.incubator.codec.quic.QuicSslContextBuilder;
import io.netty.incubator.codec.quic.QuicStreamChannel;
import io.netty.util.ReferenceCountUtil;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.http.StreamPriorityBase;
import io.vertx.core.net.impl.Http3Utils;
import io.vertx.test.tls.Cert;
import org.junit.Ignore;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.TimeUnit;


public class Http3ClientTest extends HttpClientTest {
  @Override
  public void setUp() throws Exception {
    eventLoopGroups.clear();
    serverOptions = HttpOptionsFactory.createH3HttpServerOptions(DEFAULT_HTTPS_PORT, DEFAULT_HTTPS_HOST);
    clientOptions = HttpOptionsFactory.createH3HttpClientOptions();
    super.setUp();
  }

  @Override
  protected void configureDomainSockets() throws Exception {
    // Nope
  }

  @Override
  protected void tearDown() throws Exception {
    super.tearDown();
    for (EventLoopGroup eventLoopGroup : eventLoopGroups) {
      eventLoopGroup.shutdownGracefully(0, 10, TimeUnit.SECONDS);
    }
  }

  @Override
  protected HttpServerOptions createBaseServerOptions() {
    return serverOptions;
  }

  @Override
  protected HttpClientOptions createBaseClientOptions() {
    return clientOptions;
  }

  private Http3ConnectionHandler createHttpConnectionHandler(Http3RequestStreamInboundHandler requestStreamHandler, Handler<Http3GoAwayFrame> goAwayHandler) {
    return Http3Utils
      .newServerConnectionHandlerBuilder()
      .requestStreamHandler(streamChannel -> {
        //    streamChannel.closeFuture().addListener(ignored -> handleOnStreamChannelClosed(streamChannel));
        streamChannel.pipeline().addLast(requestStreamHandler);
      })
      .agentType("SERVER-TEST_MODE")
      .http3GoAwayFrameHandler(goAwayHandler)
      .build();
  }

  private AbstractBootstrap createH3Server(Http3RequestStreamInboundHandler requestStreamHandler, Handler<Http3GoAwayFrame> goAwayHandler) {
    AbstractBootstrap bootstrap = new Bootstrap();

    NioEventLoopGroup eventLoopGroup = new NioEventLoopGroup();
    eventLoopGroups.add(eventLoopGroup);
    bootstrap.group(eventLoopGroup);
    bootstrap.channel(NioDatagramChannel.class);

    QuicSslContext sslContext = null;
    try {
      sslContext = QuicSslContextBuilder.forServer(Cert.SERVER_JKS.get().getKeyManagerFactory(vertx), null)
        .applicationProtocols(Http3.supportedApplicationProtocols()).build();
    } catch (Exception e) {
      throw new RuntimeException(e);
    }

    ChannelHandler codec = Http3.newQuicServerCodecBuilder()
      .sslContext(sslContext)
      .maxIdleTimeout(5000, TimeUnit.MILLISECONDS)
      .initialMaxData(10000000)
      .initialMaxStreamDataBidirectionalLocal(1000000)
      .initialMaxStreamDataBidirectionalRemote(1000000)
      .initialMaxStreamDataUnidirectional(1000000000000L)
      .initialMaxStreamsUnidirectional(1000000000)
      .initialMaxStreamsBidirectional(100)
      .initialMaxStreamsUnidirectional(100)

      .datagram(2000000, 2000000)

      .maxRecvUdpPayloadSize(1000000000000L)  // 1 MB for receiving UDP payloads
      .maxSendUdpPayloadSize(1000000000000L)  // 1 MB for sending UDP payloads

      .tokenHandler(InsecureQuicTokenHandler.INSTANCE)
      .handler(new ChannelInitializer<>() {
        @Override
        protected void initChannel(Channel channel) throws Exception {
          channel.pipeline().addLast(createHttpConnectionHandler(requestStreamHandler, goAwayHandler));
          channel.pipeline().addLast(new ChannelInboundHandlerAdapter() {
            @Override
            public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
              System.out.println(String.format("%s triggered in QuicChannel handler", evt.getClass().getSimpleName()));
              super.userEventTriggered(ctx, evt);
            }

            @Override
            public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
              System.out.println(String.format("Caught exception in QuicChannel handler, %s", cause.getMessage()));
              super.exceptionCaught(ctx, cause);
            }
          });
        }
      })
      .build();

    bootstrap.handler(codec);

    return bootstrap;
  }

  @Override
  protected AbstractBootstrap createServerForGet() {
    return createH3Server(new Http3RequestStreamInboundHandler() {
      @Override
      protected void channelRead(ChannelHandlerContext ctx, Http3HeadersFrame frame) throws Exception {
        vertx.runOnContext(v -> {
          QuicStreamChannel streamChannel = (QuicStreamChannel) ctx.channel();
          ChannelPromise promise = streamChannel.newPromise();
          promise.addListener(future -> streamChannel.close());
          streamChannel.write(new DefaultHttp3HeadersFrame(new DefaultHttp3Headers().status("200")), promise);
          streamChannel.flush();
        });
      }

      @Override
      protected void channelRead(ChannelHandlerContext ctx, Http3DataFrame frame) throws Exception {
        fail("No data should have been sent, and this method should not have been called.");
      }

      @Override
      protected void channelInputClosed(ChannelHandlerContext ctx) throws Exception {
      }

      @Override
      public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        System.out.println(String.format("%s triggered in request stream handler", evt.getClass().getSimpleName()));
        super.userEventTriggered(ctx, evt);
      }
    }, goAwayFrame -> {
      vertx.runOnContext(v -> {
        testComplete();
      });
    });
  }

  @Override
  protected AbstractBootstrap createServerForClientResetServerStream(boolean endServer) {
    return createH3Server(
      new Http3RequestStreamInboundHandler() {
        @Override
        protected void channelRead(ChannelHandlerContext ctx, Http3HeadersFrame frame) throws Exception {
          QuicStreamChannel streamChannel = (QuicStreamChannel) ctx.channel();
          ChannelPromise promise = streamChannel.newPromise();
//          promise.addListener(future -> streamChannel.close());
          streamChannel.write(new DefaultHttp3HeadersFrame(new DefaultHttp3Headers().status("200")), promise);
          streamChannel.flush();
        }

        @Override
        protected void channelRead(ChannelHandlerContext ctx, Http3DataFrame frame) throws Exception {
          QuicStreamChannel streamChannel = (QuicStreamChannel) ctx.channel();
          ChannelPromise promise = streamChannel.newPromise();
//          promise.addListener(future -> streamChannel.close());
          streamChannel.write(new DefaultHttp3DataFrame(Unpooled.copiedBuffer("pong", 0, 4, StandardCharsets.UTF_8)), promise);
          streamChannel.flush();
          ReferenceCountUtil.release(frame);
        }

        @Override
        protected void channelInputClosed(ChannelHandlerContext ctx) throws Exception {
          fail("Channel input should not have been closed.");
        }

        @Override
        protected void handleQuicException(ChannelHandlerContext ctx, QuicException exception) {
          System.out.println(String.format("Caught exception in QuicStreamChannel handler, %s", exception.getMessage()));
          if (exception.error() == QuicError.STREAM_RESET) {
            vertx.runOnContext(v -> {
//              assertEquals(10L, exception.error());
              complete();
            });
          }
          super.handleQuicException(ctx, exception);
        }
      }, goAwayFrame -> {
        System.out.println("GoAwayFrame received: " + goAwayFrame);
      });

  }

  @Override
  protected ServerBootstrap createServerForStreamError() {
    return null;
  }

  @Override
  protected ServerBootstrap createServerForConnectionDecodeError() {
    return null;
  }

  @Override
  protected ServerBootstrap createServerForInvalidServerResponse() {
    return null;
  }

  @Override
  protected ServerBootstrap createServerForClearText(List<String> requests, boolean withUpgrade) {
    return null;
  }

  @Override
  protected ServerBootstrap createServerForConnectionWindowSize() {
    return null;
  }

  @Override
  @Test
  @Ignore("Test ignored: HTTP/3 handles flow control at the QUIC layer; no WINDOW_UPDATE equivalent in HTTP/3")
  public void testConnectionWindowSize() throws Exception {
    super.testConnectionWindowSize();
  }

  @Override
  protected ServerBootstrap createServerForUpdateConnectionWindowSize() {
    return null;
  }

  @Override
  @Test
  @Ignore("Test ignored: HTTP/3 handles flow control at the QUIC layer; no WINDOW_UPDATE equivalent in HTTP/3")
  public void testUpdateConnectionWindowSize() throws Exception {
    super.testUpdateConnectionWindowSize();
  }

  @Override
  protected ServerBootstrap createServerForStreamPriority(StreamPriorityBase requestStreamPriority, StreamPriorityBase responseStreamPriority) {
    return null;
  }

  @Override
  protected ServerBootstrap createServerForStreamPriorityChange(StreamPriorityBase requestStreamPriority, StreamPriorityBase responseStreamPriority, StreamPriorityBase requestStreamPriority2, StreamPriorityBase responseStreamPriority2) {
    return null;
  }

  @Override
  protected ServerBootstrap createServerForClientStreamPriorityNoChange(StreamPriorityBase streamPriority, Promise<Void> latch) {
    return null;
  }

  @Override
  protected ServerBootstrap createServerForServerStreamPriorityNoChange(StreamPriorityBase streamPriority) {
    return null;
  }
}
