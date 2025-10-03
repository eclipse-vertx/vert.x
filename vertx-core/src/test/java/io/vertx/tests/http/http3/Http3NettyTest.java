package io.vertx.tests.http.http3;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.MultiThreadIoEventLoopGroup;
import io.netty.channel.nio.NioIoHandler;
import io.netty.channel.socket.nio.NioDatagramChannel;
import io.netty.handler.codec.http3.DefaultHttp3DataFrame;
import io.netty.handler.codec.http3.DefaultHttp3HeadersFrame;
import io.netty.handler.codec.http3.Http3;
import io.netty.handler.codec.http3.Http3ClientConnectionHandler;
import io.netty.handler.codec.http3.Http3DataFrame;
import io.netty.handler.codec.http3.Http3HeadersFrame;
import io.netty.handler.codec.http3.Http3RequestStreamInboundHandler;
import io.netty.handler.codec.http3.Http3ServerConnectionHandler;
import io.netty.handler.codec.quic.InsecureQuicTokenHandler;
import io.netty.handler.codec.quic.QuicChannel;
import io.netty.handler.codec.quic.QuicSslContext;
import io.netty.handler.codec.quic.QuicSslContextBuilder;
import io.netty.handler.codec.quic.QuicStreamChannel;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import io.netty.handler.ssl.util.SelfSignedCertificate;
import io.netty.util.CharsetUtil;
import io.netty.util.NetUtil;
import io.netty.util.ReferenceCountUtil;
import org.junit.Test;

import java.net.InetSocketAddress;
import java.util.concurrent.TimeUnit;

public class Http3NettyTest {

  private static final byte[] CONTENT = "Hello World!\r\n".getBytes(CharsetUtil.US_ASCII);
  static final int PORT = 9999;

  public static void main(String[] args) throws Exception {
    EventLoopGroup group = new MultiThreadIoEventLoopGroup(1, NioIoHandler.newFactory());


    try {
      Channel channel = server(group, PORT);
      client(group, PORT);
    } finally {
      group.shutdownGracefully();
    }

  }

  public static Channel server(EventLoopGroup group, int port) throws Exception {
    SelfSignedCertificate cert = new SelfSignedCertificate();

    QuicSslContext sslContext = QuicSslContextBuilder.forServer(cert.key(), null, cert.cert())
      .applicationProtocols(Http3.supportedApplicationProtocols()).build();

    ChannelHandler codec = Http3.newQuicServerCodecBuilder()
      .sslContext(sslContext)
      .maxIdleTimeout(5000, TimeUnit.MILLISECONDS)
      .initialMaxData(10000000)
      .initialMaxStreamDataBidirectionalLocal(1000000)
      .initialMaxStreamDataBidirectionalRemote(1000000)
      .initialMaxStreamsBidirectional(100)
      .tokenHandler(InsecureQuicTokenHandler.INSTANCE)
      .handler(new ChannelInitializer<QuicChannel>() {
        @Override
        protected void initChannel(QuicChannel ch) {
          // Called for each connection
          Http3ServerConnectionHandler http3Handler = new Http3ServerConnectionHandler(
            new ChannelInitializer<QuicStreamChannel>() {
              // Called for each request-stream,
              @Override
              protected void initChannel(QuicStreamChannel ch) {
                ch.pipeline().addLast(new Http3RequestStreamInboundHandler() {

                  @Override
                  protected void channelRead(
                    ChannelHandlerContext ctx, Http3HeadersFrame frame) {
                    ReferenceCountUtil.release(frame);
                  }

                  @Override
                  protected void channelRead(
                    ChannelHandlerContext ctx, Http3DataFrame frame) {
                    ReferenceCountUtil.release(frame);
                  }

                  @Override
                  protected void channelInputClosed(ChannelHandlerContext ctx) {
                    Http3HeadersFrame headersFrame = new DefaultHttp3HeadersFrame();
                    headersFrame.headers().status("404");
                    headersFrame.headers().add("server", "netty");
                    headersFrame.headers().addInt("content-length", CONTENT.length);
                    ctx.write(headersFrame);
                    ctx.writeAndFlush(new DefaultHttp3DataFrame(
                        Unpooled.wrappedBuffer(CONTENT)))
                      .addListener(QuicStreamChannel.SHUTDOWN_OUTPUT);
                  }
                });
              }
            });
          ch.pipeline().addLast(http3Handler);
        }
      }).build();
    Bootstrap bs = new Bootstrap();
    Channel channel = bs.group(group)
      .channel(NioDatagramChannel.class)
      .handler(codec)
      .bind(new InetSocketAddress(port)).sync().channel();
    return channel;
  }

  public static void client(EventLoopGroup group, int port) throws Exception {

    QuicSslContext context = QuicSslContextBuilder.forClient()
      .trustManager(InsecureTrustManagerFactory.INSTANCE)
      .applicationProtocols(Http3.supportedApplicationProtocols()).build();
    ChannelHandler codec = Http3.newQuicClientCodecBuilder()
      .sslContext(context)
      .maxIdleTimeout(5000, TimeUnit.MILLISECONDS)
      .initialMaxData(10000000)
      .initialMaxStreamDataBidirectionalLocal(1000000)
      .build();

    Bootstrap bs = new Bootstrap();
    Channel channel = bs.group(group)
      .channel(NioDatagramChannel.class)
      .handler(codec)
      .bind(0).sync().channel();

    QuicChannel quicChannel = QuicChannel.newBootstrap(channel)
      .handler(new Http3ClientConnectionHandler())
      .remoteAddress(new InetSocketAddress(NetUtil.LOCALHOST4, port))
      .connect()
      .get();

    QuicStreamChannel streamChannel = Http3.newRequestStream(quicChannel,
      new Http3RequestStreamInboundHandler() {
        @Override
        protected void channelRead(ChannelHandlerContext ctx, Http3HeadersFrame frame) {
          ReferenceCountUtil.release(frame);
        }

        @Override
        protected void channelRead(ChannelHandlerContext ctx, Http3DataFrame frame) {
          System.out.print(frame.content().toString(CharsetUtil.US_ASCII));
          ReferenceCountUtil.release(frame);
        }

        @Override
        protected void channelInputClosed(ChannelHandlerContext ctx) {
          ctx.close();
        }
      }).sync().getNow();

    // Write the Header frame and send the FIN to mark the end of the request.
    // After this its not possible anymore to write any more data.
    Http3HeadersFrame frame = new DefaultHttp3HeadersFrame();
    frame.headers().method("GET").path("/")
      .authority(NetUtil.LOCALHOST4.getHostAddress() + ":" + port)
      .scheme("https");
    streamChannel.writeAndFlush(frame)
      .addListener(QuicStreamChannel.SHUTDOWN_OUTPUT).sync();

    // Wait for the stream channel and quic channel to be closed (this will happen after we received the FIN).
    // After this is done we will close the underlying datagram channel.
    streamChannel.closeFuture().sync();

    // After we received the response lets also close the underlying QUIC channel and datagram channel.
    quicChannel.close().sync();
    channel.close().sync();
  }
}
