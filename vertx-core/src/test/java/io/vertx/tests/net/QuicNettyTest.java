package io.vertx.tests.net;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.ChannelInputShutdownReadComplete;
import io.netty.channel.socket.nio.NioDatagramChannel;
import io.netty.handler.codec.quic.InsecureQuicTokenHandler;
import io.netty.handler.codec.quic.QuicChannel;
import io.netty.handler.codec.quic.QuicClientCodecBuilder;
import io.netty.handler.codec.quic.QuicConnectionCloseEvent;
import io.netty.handler.codec.quic.QuicServerCodecBuilder;
import io.netty.handler.codec.quic.QuicSslContext;
import io.netty.handler.codec.quic.QuicSslContextBuilder;
import io.netty.handler.codec.quic.QuicStreamChannel;
import io.netty.handler.codec.quic.QuicStreamType;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import io.netty.handler.ssl.util.SelfSignedCertificate;
import io.netty.util.Mapping;
import io.netty.util.NetUtil;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.internal.ContextInternal;
import io.vertx.core.internal.tls.SslContextManager;
import io.vertx.core.internal.tls.SslContextProvider;
import io.vertx.core.net.SSLEngineOptions;
import io.vertx.core.net.ServerSSLOptions;
import io.vertx.core.spi.tls.QuicSslContextFactory;
import io.vertx.core.spi.tls.SslContextFactory;
import io.vertx.test.tls.Cert;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public class QuicNettyTest {

  public static void main(String[] args) throws Exception {

    Vertx vertx = Vertx.vertx();

    SslContextManager manager = new SslContextManager(new SSLEngineOptions() {
      @Override
      public SSLEngineOptions copy() {
        return this;
      }
      @Override
      public SslContextFactory sslContextFactory() {
        return new QuicSslContextFactory();
      }
    });

    Cert.SERVER_JKS.get();
    Future<SslContextProvider> f1 = manager.buildSslContextProvider(new ServerSSLOptions().setKeyCertOptions(Cert.SERVER_JKS.get()), "HTTP", null, false, (ContextInternal) vertx.getOrCreateContext());
    SslContextProvider serverProvider = f1.await();

    NioEventLoopGroup group = new NioEventLoopGroup(1);

    TestServer server = new TestServer(group, serverProvider);

    server.bind(new InetSocketAddress(9999));

    TestClient client = new TestClient(group);

    TestClient.Connection connection = client.connect(new InetSocketAddress(NetUtil.LOCALHOST4, 9999));

    TestClient.Stream stream = connection.newStream();

    stream.handler(data -> {
      System.out.println("Got: " + new String(data));
    });

    stream.create();

    stream.write("1");
    Thread.sleep(1);
    stream.write("2");
    Thread.sleep(1);
    stream.write("3");

    stream.close();

//    client.close();

  }

  static class TestServer {

    private final NioEventLoopGroup group;
    private final SslContextProvider sslContextProvider;
    private Channel channel;

    public TestServer(NioEventLoopGroup group, SslContextProvider sslContextProvider) {
      this.group = group;
      this.sslContextProvider = sslContextProvider;
    }

    public void bind(InetSocketAddress addr) throws Exception {


      SelfSignedCertificate selfSignedCertificate = new SelfSignedCertificate();
      Mapping<? super String, ? extends SslContext> mapping = sslContextProvider.serverNameMapping(true);
      QuicSslContext context = QuicSslContextBuilder.buildForServerWithSni(new Mapping<String, QuicSslContext>() {
        @Override
        public QuicSslContext map(String input) {
          SslContext obtained = mapping.map(input);
          QuicSslContext a = (QuicSslContext) obtained;
          return a;
        }
      });

      ChannelHandler codec = new QuicServerCodecBuilder().sslContext(context)
        .maxIdleTimeout(5000, TimeUnit.MILLISECONDS)
        // Configure some limits for the maximal number of streams (and the data) that we want to handle.
        .initialMaxData(10000000)
        .initialMaxStreamDataBidirectionalLocal(1000000)
        .initialMaxStreamDataBidirectionalRemote(1000000)
        .initialMaxStreamsBidirectional(100)
        .initialMaxStreamsUnidirectional(100)
        .activeMigration(true)

        // Setup a token handler. In a production system you would want to implement and provide your custom
        // one.
        .tokenHandler(InsecureQuicTokenHandler.INSTANCE)
        // ChannelHandler that is added into QuicChannel pipeline.
        .handler(new ChannelInboundHandlerAdapter() {
          @Override
          public void channelActive(ChannelHandlerContext ctx) {
            QuicChannel channel = (QuicChannel) ctx.channel();
            // Create streams etc..
          }

          public void channelInactive(ChannelHandlerContext ctx) {
            // OK
            ((QuicChannel) ctx.channel()).collectStats().addListener(f -> {
              if (f.isSuccess()) {
                System.out.println("Connection closed: " + f.getNow());
              }
              //only test for first connection
              ctx.channel().parent().close();
            });
          }

          @Override
          public boolean isSharable() {
            return true;
          }
        })
        .streamHandler(new ChannelInitializer<QuicStreamChannel>() {
          @Override
          protected void initChannel(QuicStreamChannel ch)  {
            // Add a LineBasedFrameDecoder here as we just want to do some simple HTTP 0.9 handling.
            ch.pipeline()
              .addLast(new ChannelInboundHandlerAdapter() {
                @Override
                public void channelRead(ChannelHandlerContext ctx, Object msg) {
                  ByteBuf byteBuf = (ByteBuf) msg;
                  // Write the buffer and shutdown the output by writing a FIN.
                  ctx.writeAndFlush(byteBuf);
                  // .addListener(QuicStreamChannel.SHUTDOWN_OUTPUT);
                }
              });
          }
        }).build();

      Bootstrap bs = new Bootstrap();
      channel = bs.group(group)
        .channel(NioDatagramChannel.class)
        .handler(codec)
        .bind(addr).sync().channel();
    }
  }

  // new InetSocketAddress(9999)

  public static class TestClient {

    public class Connection extends ChannelDuplexHandler {

      private QuicChannel quicChannel;
      private Handler<Void> closeHandler;
      private QuicConnectionCloseEvent closeEvent;

      public Connection closeHandler(Handler<Void> handler) {
        closeHandler = handler;
        return this;
      }

      public int closeError() {
        return closeEvent.error();
      }

      public byte[] closeReason() {
        return closeEvent.reason();
      }

      public boolean closeApplicationClose() {
        return closeEvent.isApplicationClose();
      }

      public Stream newStream() throws Exception {
        return new Stream(this);
      }

      @Override
      public void handlerAdded(ChannelHandlerContext ctx) throws Exception {
        quicChannel = (QuicChannel) ctx.channel();
      }

      @Override
      public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        if (evt instanceof QuicConnectionCloseEvent) {
          closeEvent = (QuicConnectionCloseEvent) evt;
        }
        super.userEventTriggered(ctx, evt);
      }
      @Override
      public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        Handler<Void> handler = closeHandler;
        if (handler != null) {
          handler.handle(null);
        }
      }

      public void close() throws Exception {
        close(0, "bye".getBytes());
      }

      public void close(int error, byte[] reason) throws Exception {
        quicChannel.close(true, error, Unpooled.copiedBuffer(reason)).sync();
      }
    }

    public class Stream extends ChannelDuplexHandler {

      final Connection connection;
      QuicStreamChannel streamChannel;
      Consumer<byte[]> handler;

      private Stream(Connection connection) {
        this.connection = connection;
      }

      public void create() throws Exception {
        streamChannel = connection.quicChannel.createStream(QuicStreamType.BIDIRECTIONAL, this).sync().getNow();
      }

      public void handler(Consumer<byte[]> handler) {
        this.handler = handler;
      }

      @Override
      public void channelRead(ChannelHandlerContext ctx, Object msg) {
        ByteBuf byteBuf = (ByteBuf) msg;
        byte[] data;
        try {
          data = new byte[byteBuf.readableBytes()];
          byteBuf.readBytes(data);
        } finally {
          byteBuf.release();
        }
        handler.accept(data);
      }

      @Override
      public void userEventTriggered(ChannelHandlerContext ctx, Object evt) {
        if (evt == ChannelInputShutdownReadComplete.INSTANCE) {
          System.out.println("HELLO");
          // Close the connection once the remote peer did send the FIN for this stream.
          ((QuicChannel) ctx.channel().parent()).close(true, 0,
            ctx.alloc().directBuffer(16)
              .writeBytes(new byte[]{'k', 't', 'h', 'x', 'b', 'y', 'e'}));
        }
      }

      public void write(String data) {
        write(data.getBytes(StandardCharsets.UTF_8));
      }

      public void write(byte[] data) {
        streamChannel.writeAndFlush(Unpooled.copiedBuffer(data));
      }

      public void close() throws Exception {
        streamChannel.writeAndFlush(Unpooled.EMPTY_BUFFER)
          .addListener(QuicStreamChannel.SHUTDOWN_OUTPUT);
        streamChannel.closeFuture().sync();
      }
    }

    private final NioEventLoopGroup group;
    private Channel channel;

    public TestClient(NioEventLoopGroup group) {
      this.group = group;
    }


    public Connection connect(InetSocketAddress addr) throws Exception {

      Channel ch = channel;
      if (ch == null) {
        QuicSslContext context = QuicSslContextBuilder.forClient().trustManager(InsecureTrustManagerFactory.INSTANCE).
          applicationProtocols("test-protocol").build();

        ChannelHandler codec = new QuicClientCodecBuilder()
          .sslContext(context)
          .maxIdleTimeout(5000, TimeUnit.MILLISECONDS)
          .initialMaxData(10000000)
          // As we don't want to support remote initiated streams just setup the limit for local initiated
          // streams in this example.
          .initialMaxStreamDataBidirectionalLocal(1000000)
          .build();

        Bootstrap bs = new Bootstrap();
        ch = bs.group(group)
          .channel(NioDatagramChannel.class)
          .handler(codec)
          .bind(0).sync().channel();
        channel = ch;
      }

      Connection connection = new Connection();
      QuicChannel.newBootstrap(channel)
        .handler(connection)
        .remoteAddress(addr)
        .connect()
        .get();
      return connection;
    }

    public void close() throws Exception {
      channel.close().sync();
    }
  }
}
