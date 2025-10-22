package io.vertx.tests.http.http3;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.channel.nio.NioIoHandler;
import io.netty.channel.socket.nio.NioDatagramChannel;
import io.netty.handler.codec.http3.*;
import io.netty.handler.codec.quic.InsecureQuicTokenHandler;
import io.netty.handler.codec.quic.QuicChannel;
import io.netty.handler.codec.quic.QuicSslContext;
import io.netty.handler.codec.quic.QuicSslContextBuilder;
import io.netty.handler.codec.quic.QuicStreamChannel;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import io.netty.handler.ssl.util.SelfSignedCertificate;
import io.netty.util.CharsetUtil;
import io.netty.util.ReferenceCountUtil;

import java.io.ByteArrayOutputStream;
import java.net.InetSocketAddress;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import static org.junit.Assert.assertTrue;

public class Http3NettyTest {

  private static final byte[] CONTENT = "Hello World!\r\n".getBytes(CharsetUtil.US_ASCII);
  static final int PORT = 9999;

  public static void main(String[] args) throws Exception {
    EventLoopGroup group = new MultiThreadIoEventLoopGroup(1, NioIoHandler.newFactory());


//    try {
//      Channel channel = server(group, PORT);
//      client(group, PORT);
//    } finally {
//      group.shutdownGracefully();
//    }

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

  public static Client client(EventLoopGroup group) throws Exception {
    Client client = new Client(group);
    client.bind(0);
    return client;
  }

  public static class Client {


    private final EventLoopGroup group;
    private Channel channel;

    public Client(EventLoopGroup group) {
      this.group = group;
    }

    public Client bind(int port) throws Exception {
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
      channel = bs.group(group)
        .channel(NioDatagramChannel.class)
        .handler(codec)
        .bind(0).sync().channel();

      return this;
    }

    public void close() throws Exception {
      if (channel != null) {
        channel.close().sync();
      }
    }

    public Connection connect(InetSocketAddress server) throws Exception {

      Channel ch = channel;

      if (ch == null) {
        throw new IllegalStateException("Not bound");
      }

      QuicChannel quicChannel = QuicChannel.newBootstrap(channel)
        .handler(new Http3ClientConnectionHandler())
        .remoteAddress(server)
        .connect()
        .get();

      return new Connection(quicChannel, server);
    }

    public class Connection {

      private final QuicChannel channel;
      private final InetSocketAddress address;

      private Connection(QuicChannel quicChannel, InetSocketAddress address) {
        this.channel = quicChannel;
        this.address = address;
      }

      public Stream stream() throws Exception {
        Stream stream = new Stream(this);
        stream.streamChannel = Http3.newRequestStream(channel, stream).sync().getNow();
        return stream;
      }
    }

    public class Stream extends Http3RequestStreamInboundHandler {

      private final Connection connection;
      private QuicStreamChannel streamChannel;
      private Consumer<Http3Headers> headersHandler;
      private Consumer<byte[]> chunkHandler;
      private Consumer<Void> endHandler;
      private boolean headersSent;
      private Http3Headers responseHeaders;
      private ByteArrayOutputStream responseBody = new ByteArrayOutputStream();
      private CountDownLatch responseEnd = new CountDownLatch(1);

      public Stream(Connection connection) {
        this.connection = connection;
      }

      @Override
      protected void channelRead(ChannelHandlerContext ctx, Http3HeadersFrame frame) {
        ReferenceCountUtil.release(frame);
        Consumer<Http3Headers> handler = headersHandler;
        Http3Headers headers = frame.headers();
        responseHeaders = headers;
        if (handler != null) {
          handler.accept(headers);
        }
      }

      @Override
      protected void channelRead(ChannelHandlerContext ctx, Http3DataFrame frame) {
        byte[] chunk = ByteBufUtil.getBytes(frame.content());
        ReferenceCountUtil.release(frame);
        responseBody.writeBytes(chunk);
        Consumer<byte[]> handler = chunkHandler;
        if (handler != null) {
          handler.accept(chunk);
        }
      }

      @Override
      protected void channelInputClosed(ChannelHandlerContext ctx) {
        responseEnd.countDown();
        Consumer<Void> handler = endHandler;
        if (handler != null) {
          handler.accept(null);
        }
        ctx.close(); // ???
      }

      public Stream headersHandler(Consumer<Http3Headers> headersHandler) {
        this.headersHandler = headersHandler;
        return this;
      }

      public Stream endHandler(Consumer<Void> endHandler) {
        this.endHandler = endHandler;
        return this;
      }

      public Stream chunkHandler(Consumer<byte[]> chunkHandler) {
        this.chunkHandler = chunkHandler;
        return this;
      }

      public void GET(String path) throws Exception{
        Http3Headers headers = new DefaultHttp3Headers();
        headers.method("GET");
        headers.path(path);
        end(headers);
      }

      public void POST(String path, byte[] body) throws Exception{
        Http3Headers headers = new DefaultHttp3Headers();
        headers.method("POST");
        headers.path(path);
        write(headers, false);
        writeBody(body);
      }

      public void writeUnknownFrame(int type, byte[] payload) throws Exception {
        DefaultHttp3UnknownFrame frame = new DefaultHttp3UnknownFrame(type, Unpooled.wrappedBuffer(payload));
        ChannelFuture fut = streamChannel.writeAndFlush(frame);
        fut.sync();
      }

      public void write(Http3Headers headers) throws Exception {
        write(headers, false);
      }

      public void end(Http3Headers headers) throws Exception {
        write(headers, true);
      }

      public void write(Http3Headers headers, boolean end) throws Exception {
        if (!headersSent) {
          headersSent = true;
          if (headers.authority() == null) {
            headers.authority( connection.address.getHostName()+ ":" + connection.address.getPort());
          }
          if (headers.scheme() == null) {
            headers.scheme("https");
          }
          if (headers.method() == null) {
            headers.method("GET");
          }
          if (headers.path() == null) {
            headers.path("/");
          }
        }
        Http3HeadersFrame frame = new DefaultHttp3HeadersFrame(headers);
        ChannelFuture fut = streamChannel.writeAndFlush(frame);
        if (end) {
          fut.addListener(QuicStreamChannel.SHUTDOWN_OUTPUT);
        }
        fut.sync();
      }

      public void write(byte[] chunk) throws Exception {
        write(chunk, false);
      }

      public void end() throws Exception {
        write(new byte[0], true);
      }

      public void end(byte[] chunk) throws Exception {
        write(chunk, true);
      }

      public void write(byte[] chunk, boolean end) throws Exception {
        if (!headersSent) {
          throw new IllegalStateException();
        }
        Http3DataFrame frame = new DefaultHttp3DataFrame(Unpooled.wrappedBuffer(chunk));
        ChannelFuture fut = streamChannel.writeAndFlush(frame);
        if (end) {
          fut.addListener(QuicStreamChannel.SHUTDOWN_OUTPUT);
        }
        fut.sync();
      }

      public void writeBody(byte[] chunk) throws Exception {
        write(chunk, true);
      }

      public Http3Headers responseHeaders() {
        return responseHeaders;
      }

      public byte[] responseBody() throws Exception {
        assertTrue(responseEnd.await(10, TimeUnit.SECONDS));
        return responseBody.toByteArray();
      }
    }
  }

//  public static void client(EventLoopGroup group, int port) throws Exception {
//
//
//    QuicChannel quicChannel = QuicChannel.newBootstrap(channel)
//      .handler(new Http3ClientConnectionHandler())
//      .remoteAddress(new InetSocketAddress(NetUtil.LOCALHOST4, port))
//      .connect()
//      .get();
//
//    QuicStreamChannel streamChannel = Http3.newRequestStream(quicChannel,
//      new Http3RequestStreamInboundHandler() {
//        @Override
//        protected void channelRead(ChannelHandlerContext ctx, Http3HeadersFrame frame) {
//          ReferenceCountUtil.release(frame);
//        }
//
//        @Override
//        protected void channelRead(ChannelHandlerContext ctx, Http3DataFrame frame) {
//          System.out.print(frame.content().toString(CharsetUtil.US_ASCII));
//          ReferenceCountUtil.release(frame);
//        }
//
//        @Override
//        protected void channelInputClosed(ChannelHandlerContext ctx) {
//          ctx.close();
//        }
//      }).sync().getNow();
//
//    // Write the Header frame and send the FIN to mark the end of the request.
//    // After this its not possible anymore to write any more data.
//    Http3HeadersFrame frame = new DefaultHttp3HeadersFrame();
//    frame.headers().method("GET").path("/")
//      .authority(NetUtil.LOCALHOST4.getHostAddress() + ":" + port)
//      .scheme("https");
//    streamChannel.writeAndFlush(frame)
//      .addListener(QuicStreamChannel.SHUTDOWN_OUTPUT).sync();
//
//    // Wait for the stream channel and quic channel to be closed (this will happen after we received the FIN).
//    // After this is done we will close the underlying datagram channel.
//    streamChannel.closeFuture().sync();
//
//    // After we received the response lets also close the underlying QUIC channel and datagram channel.
//    quicChannel.close().sync();
//    channel.close().sync();
//  }
}
