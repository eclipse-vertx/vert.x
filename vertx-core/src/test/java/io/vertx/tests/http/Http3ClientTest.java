package io.vertx.tests.http;

import io.netty.bootstrap.AbstractBootstrap;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioDatagramChannel;
import io.netty.incubator.codec.http3.*;
import io.netty.incubator.codec.quic.*;
import io.netty.util.ReferenceCountUtil;
import io.vertx.core.Handler;
import io.vertx.core.http.Http3StreamPriority;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.http.HttpFrame;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.http.StreamPriorityBase;
import io.vertx.core.http.impl.HttpFrameImpl;
import io.vertx.core.net.impl.Http3Utils;
import io.vertx.test.core.TestUtils;
import io.vertx.test.tls.Cert;
import org.junit.Ignore;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;


public class Http3ClientTest extends HttpClientTest {

  public static final String AGENT_SERVER = "SERVER-TEST_MODE";

  @Override
  public void setUp() throws Exception {
    eventLoopGroups.clear();
    serverOptions = HttpOptionsFactory.createH3HttpServerOptions(DEFAULT_HTTPS_PORT, DEFAULT_HTTPS_HOST);
    clientOptions = HttpOptionsFactory.createH3HttpClientOptions();
    super.setUp();
  }

  @Override
  protected StreamPriorityBase generateStreamPriority() {
    return new Http3StreamPriority(new QuicStreamPriority(TestUtils.randomPositiveInt(127), TestUtils.randomBoolean()));
  }

  @Override
  protected StreamPriorityBase defaultStreamPriority() {
    return new Http3StreamPriority(new QuicStreamPriority(0, false));
  }

  @Override
  protected HttpFrame generateCustomFrame() {
    return new HttpFrameImpl(TestUtils.randomPositiveInt(50) + 64, 0, TestUtils.randomBuffer(500));
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

  private static String byteBufToString(ByteBuf content) {
    byte[] arr = new byte[content.readableBytes()];
    content.getBytes(content.readerIndex(), arr);
    return new String(arr);
  }

  private Http3ConnectionHandler createHttpConnectionHandler(Supplier<Http3RequestStreamInboundHandler> requestStreamHandler, Handler<Http3GoAwayFrame> goAwayHandler) {
    return Http3Utils
      .newServerConnectionHandlerBuilder()
      .requestStreamHandler(streamChannel -> {
        //    streamChannel.closeFuture().addListener(ignored -> handleOnStreamChannelClosed(streamChannel));
        streamChannel.pipeline().addLast(requestStreamHandler.get());
      })
      .agentType(AGENT_SERVER)
      .http3GoAwayFrameHandler(goAwayHandler)
      .build();
  }

  private AbstractBootstrap createH3Server(Supplier<Http3RequestStreamInboundHandler> requestStreamHandler, Handler<Http3GoAwayFrame> goAwayHandler) {
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
              log.debug(String.format("%s - %s triggered in QuicChannel handler", AGENT_SERVER, evt.getClass().getSimpleName()));
              super.userEventTriggered(ctx, evt);
            }

            @Override
            public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
              log.debug(String.format("%s - Caught exception in QuicChannel handler, %s", AGENT_SERVER, cause.getMessage()));
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
    return createH3Server(() -> new Http3RequestStreamInboundHandler() {
      @Override
      protected void channelRead(ChannelHandlerContext ctx, Http3HeadersFrame frame) throws Exception {
        log.debug(String.format("%s - Received Header frame for channelId: %s", AGENT_SERVER, ctx.channel().id()));
        if (log.isDebugEnabled()) {
          log.debug(String.format("%s - Header is: %s", AGENT_SERVER, frame.headers()));
        }
        vertx.runOnContext(v -> {
          QuicStreamChannel streamChannel = (QuicStreamChannel) ctx.channel();
          ChannelPromise promise = streamChannel.newPromise();
          promise.addListener(QuicStreamChannel.SHUTDOWN_OUTPUT);
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
        log.debug(String.format("%s - %s triggered in request stream handler", AGENT_SERVER, evt.getClass().getSimpleName()));
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
          log.debug(String.format("Caught exception in QuicStreamChannel handler, %s", exception.getMessage()));
          if (exception.error() == QuicError.STREAM_RESET) {
            vertx.runOnContext(v -> {
//              assertEquals(10L, exception.error());
              complete();
            });
          }
          super.handleQuicException(ctx, exception);
        }
      }, goAwayFrame -> {
        log.debug("GoAwayFrame received: " + goAwayFrame);
      });

  }

  @Override
  protected ServerBootstrap createServerForStreamError() {
    return null;
  }

      @Override
      protected void channelInputClosed(ChannelHandlerContext ctx) throws Exception {
      }

  @Override
  protected ServerBootstrap createServerForInvalidServerResponse() {
    return null;
  }

  @Override
  @Test
  @Ignore("Ignoring test: not applicable in HTTP/3 (QUIC-based protocol with no upgrade from HTTP/1.1 or HTTP/2)")
  public void testClearTextUpgrade() throws Exception {
    super.testClearTextUpgrade();
  }

  @Override
  @Test
  @Ignore("Ignoring test: not applicable in HTTP/3 (QUIC-based protocol with no upgrade from HTTP/1.1 or HTTP/2)")
  public void testClearTextUpgradeWithPreflightRequest() throws Exception {
    super.testClearTextUpgradeWithPreflightRequest();
  }

  @Override
  @Test
  @Ignore("Ignoring test: not applicable in HTTP/3 (QUIC-based protocol with no upgrade from HTTP/1.1 or HTTP/2)")
  public void testClearTextWithPriorKnowledge() throws Exception {
    super.testClearTextWithPriorKnowledge();
  }

  @Override
  @Test
  @Ignore("Ignoring test: not applicable in HTTP/3 (QUIC-based protocol with no upgrade from HTTP/1.1 or HTTP/2)")
  public void testRejectClearTextUpgrade() throws Exception {
    super.testRejectClearTextUpgrade();
  }

  @Override
  @Test
  @Ignore("Ignoring test: not applicable in HTTP/3 (QUIC-based protocol with no upgrade from HTTP/1.1 or HTTP/2)")
  public void testRejectClearTextDirect() throws Exception {
    super.testRejectClearTextDirect();
  }

  @Override
  @Test
  @Ignore("Ignoring test: not applicable in HTTP/3 (QUIC-based protocol with no upgrade from HTTP/1.1 or HTTP/2)")
  public void testIdleTimeoutClearTextUpgrade() throws Exception {
    super.testIdleTimeoutClearTextUpgrade();
  }

  @Override
  @Test
  @Ignore("Ignoring test: not applicable in HTTP/3 (QUIC-based protocol with no upgrade from HTTP/1.1 or HTTP/2)")
  public void testIdleTimeoutClearTextDirect() throws Exception {
    super.testIdleTimeoutClearTextDirect();
  }

  @Override
  @Test
  @Ignore("Ignoring test: not applicable in HTTP/3 (QUIC-based protocol with no upgrade from HTTP/1.1 or HTTP/2)")
  public void testDisableIdleTimeoutClearTextUpgrade() throws Exception {
    super.testDisableIdleTimeoutClearTextUpgrade();
  }

  @Override
  @Test
  @Ignore("Test ignored: HTTP/3 handles flow control at the QUIC layer; no WINDOW_UPDATE equivalent in HTTP/3")
  public void testConnectionWindowSize() throws Exception {
    super.testConnectionWindowSize();
  }

  @Override
  @Test
  @Ignore("Test ignored: HTTP/3 handles flow control at the QUIC layer; no WINDOW_UPDATE equivalent in HTTP/3")
  public void testUpdateConnectionWindowSize() throws Exception {
    super.testUpdateConnectionWindowSize();
  }

  @Test
  @Override
  @Ignore("Ignored because stream priority is not exchanged in HTTP/3 as it is in HTTP/2.")
  public void testStreamPriorityChange() throws Exception {
    super.testStreamPriorityChange();
  }

  @Test
  @Override
  @Ignore("Ignored because stream priority is not exchanged in HTTP/3 as it is in HTTP/2.")
  public void testStreamPriority() throws Exception {
    super.testStreamPriority();
  }

  @Test
  @Override
  @Ignore
  public void testFallbackOnHttp1() throws Exception {
    //TODO: correct me
    super.testFallbackOnHttp1();
  }

  @Test
  @Override
  @Ignore
  public void testClientResponsePauseResume() throws Exception {
    //TODO: correct me
    super.testClientResponsePauseResume();
  }

  @Test
  @Override
  @Ignore
  public void testPushPromise() throws Exception {
    //TODO: correct me
    super.testPushPromise();
  }

  @Test
  @Override
  @Ignore
  public void testResetPushPromiseNoHandler() throws Exception {
    //TODO: correct me
    super.testResetPushPromiseNoHandler();
  }

  @Test
  @Override
  @Ignore
  public void testServerShutdownConnection() throws Exception {
    //TODO: correct me
    super.testServerShutdownConnection();
  }

  @Test
  @Override
  @Ignore
  public void testConnectionDecodeError() throws Exception {
    //TODO: correct me
    super.testConnectionDecodeError();
  }

  @Test
  @Override
  @Ignore
  public void testResponseCompressionEnabled() throws Exception {
    //TODO: correct me
    super.testResponseCompressionEnabled();
  }

  @Test
  @Override
  @Ignore
  public void testMaxConcurrencyMultipleConnections() throws Exception {
    //TODO: correct me
    super.testMaxConcurrencyMultipleConnections();
  }

  @Test
  @Override
  @Ignore
  public void testClientSettings() throws Exception {
    //TODO: correct me
    super.testClientSettings();
  }

  @Test
  @Override
  @Ignore
  public void testServerSettings() throws Exception {
    //TODO: correct me
    super.testServerSettings();
  }

  @Test
  @Override
  @Ignore
  public void testHeaders() throws Exception {
    //TODO: correct me
    super.testHeaders();
  }

  @Test
  @Override
  @Ignore
  public void testResetPendingPushPromise() throws Exception {
    //TODO: correct me
    super.testResetPendingPushPromise();
  }

  @Test
  @Override
  @Ignore
  public void testQueueingRequests() throws Exception {
    //TODO: correct me
    super.testQueueingRequests();
  }

  @Test
  @Override
  @Ignore
  public void testSendHeadersCompletionHandler() throws Exception {
    //TODO: correct me
    super.testSendHeadersCompletionHandler();
  }

  @Test
  @Override
  @Ignore
  public void testStreamError() throws Exception {
    //TODO: correct me
    super.testStreamError();
  }

  @Test
  @Override
  @Ignore
  public void testClientRequestWriteability() throws Exception {
    //TODO: correct me
    super.testClientRequestWriteability();
  }

  @Test
  @Override
  @Ignore
  public void testServerResetClientStreamDuringRequest() throws Exception {
    //TODO: correct me
    super.testServerResetClientStreamDuringRequest();
  }

  @Test
  @Override
  @Ignore
  public void testReduceMaxConcurrentStreams() throws Exception {
    //TODO: correct me
    super.testReduceMaxConcurrentStreams();
  }

  @Test
  @Override
  @Ignore
  public void testQueueingRequestsMaxConcurrentStream() throws Exception {
    //TODO: correct me
    super.testQueueingRequestsMaxConcurrentStream();
  }

  @Test
  @Override
  @Ignore
  public void testServerResetClientStreamDuringResponse() throws Exception {
    //TODO: correct me
    super.testServerResetClientStreamDuringResponse();
  }

  @Test
  @Override
  @Ignore
  public void testReceivePing() throws Exception {
    //TODO: correct me
    super.testReceivePing();
  }

  @Test
  @Override
  @Ignore
  public void testInvalidServerResponse() throws Exception {
    //TODO: correct me
    super.testInvalidServerResponse();
  }

  @Test
  @Override
  @Ignore
  public void testSendPing() throws Exception {
    //TODO: correct me
    super.testSendPing();
  }

  @Override
  protected AbstractBootstrap createServerForClientResetServerStream(boolean endServer) {
    return createH3Server(() -> new Http3RequestStreamInboundHandler() {
      @Override
      protected void channelRead(ChannelHandlerContext ctx, Http3HeadersFrame frame) throws Exception {
        log.debug(String.format("%s - Received Header frame for channelId: %s", AGENT_SERVER, ctx.channel().id()));
        QuicStreamChannel streamChannel = (QuicStreamChannel) ctx.channel();
        ChannelPromise promise = streamChannel.newPromise();
        streamChannel.write(new DefaultHttp3HeadersFrame(new DefaultHttp3Headers().status("200")), promise);
        streamChannel.flush();
        ReferenceCountUtil.release(frame);
      }

      @Override
      protected void channelRead(ChannelHandlerContext ctx, Http3DataFrame frame) throws Exception {
        log.debug(String.format("%s - Received Data frame for channelId: %s", AGENT_SERVER, ctx.channel().id()));
        if (log.isDebugEnabled()) {
          log.debug(String.format("%s - Frame data is: %s", AGENT_SERVER, byteBufToString(frame.content())));
        }

        QuicStreamChannel streamChannel = (QuicStreamChannel) ctx.channel();
        ChannelPromise promise = streamChannel.newPromise();
        if (endServer) {
          promise.addListener(QuicStreamChannel.SHUTDOWN_OUTPUT);
        }

        streamChannel.write(new DefaultHttp3DataFrame(Unpooled.copiedBuffer("pong", 0, 4, StandardCharsets.UTF_8)), promise);
        streamChannel.flush();
        ReferenceCountUtil.release(frame);
      }

      @Override
      protected void channelInputClosed(ChannelHandlerContext ctx) throws Exception {
        log.debug(String.format("%s - ChannelInputClosed called for channelId: %s, streamId: %s", AGENT_SERVER, ctx.channel().id(),
        ((QuicStreamChannel) ctx.channel()).streamId()));
      }

      @Override
      protected void handleQuicException(ChannelHandlerContext ctx, QuicException exception) {
        log.debug(String.format("%s - Caught exception in QuicStreamChannel handler, %s", AGENT_SERVER, exception.getMessage()));
        if (exception.error() == QuicError.STREAM_RESET) {
//              assertEquals(10L, exception.error());
          vertx.runOnContext(v -> {
            complete();
          });
        }
        super.handleQuicException(ctx, exception);
      }

      @Override
      public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        log.debug(String.format("%s - %s triggered in request stream handler", AGENT_SERVER, evt.getClass().getSimpleName()));
        super.userEventTriggered(ctx, evt);
      }
    }, goAwayFrame -> {
      log.debug(String.format("%s GoAwayFrame received: %s", AGENT_SERVER, goAwayFrame));
    });
  }

  @Test
  @Override
  @Ignore
  public void testResetActivePushPromise() throws Exception {
    //TODO: correct me
    super.testResetActivePushPromise();
  }
}
