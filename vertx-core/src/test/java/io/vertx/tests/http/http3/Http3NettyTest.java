/*
 * Copyright (c) 2011-2026 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package io.vertx.tests.http.http3;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.channel.nio.NioIoHandler;
import io.netty.channel.socket.nio.NioDatagramChannel;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http3.*;
import io.netty.handler.codec.quic.*;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import io.netty.handler.ssl.util.SelfSignedCertificate;
import io.netty.util.CharsetUtil;
import io.netty.util.ReferenceCountUtil;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.LongConsumer;

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
      return connect(server, new Http3Settings());
    }

    public Connection connect(InetSocketAddress server, Http3Settings localSettings) throws Exception {

      Channel ch = channel;

      if (ch == null) {
        throw new IllegalStateException("Not bound");
      }

      AtomicReference<Connection> connectionRef = new AtomicReference<>();

      QuicChannel.newBootstrap(channel)
        .handler(new ChannelInitializer<>() {
          @Override
          protected void initChannel(Channel ch) {
            Connection connection = new Connection((QuicChannel) ch, server);
            connectionRef.set(connection);
            Http3ClientConnectionHandler handler = new Http3ClientConnectionHandler(connection, null, null, new DefaultHttp3SettingsFrame(localSettings), true);
            ch.pipeline().addLast(handler);
          }
        })
        .remoteAddress(server)
        .connect()
        .get();

      return connectionRef.get();
    }

    public class Connection extends ChannelInboundHandlerAdapter {

      private final QuicChannel channel;
      private final InetSocketAddress address;
      private volatile LongConsumer goAwayHandlerRef;
      private volatile Http3Settings remoteSettings;

      private Connection(QuicChannel quicChannel, InetSocketAddress address) {
        this.channel = quicChannel;
        this.address = address;
      }

      @Override
      public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (msg instanceof Http3ControlStreamFrame) {
          Http3ControlStreamFrame controlStreamFrame = (Http3ControlStreamFrame) msg;
          switch ((int) controlStreamFrame.type()) {
            case 4:
              Http3SettingsFrame settingsFrame = (Http3SettingsFrame) controlStreamFrame;
              remoteSettings = settingsFrame.settings();
              break;
            case 7:
              // Go away
              LongConsumer runnable = goAwayHandlerRef;
              if (runnable != null) {
                Http3GoAwayFrame goAwayFrame = (Http3GoAwayFrame) controlStreamFrame;
                runnable.accept(goAwayFrame.id());
              }
              break;
          }
        } else {
          super.channelRead(ctx, msg);
        }
      }

      public Connection goAwayHandler(LongConsumer handler) {
        goAwayHandlerRef = handler;
        return this;
      }

      public Http3Settings remoteSettings() {
        return remoteSettings;
      }

      public Stream stream() throws Exception {
        Stream stream = new Stream(this);
        stream.streamChannel = Http3.newRequestStream(channel, stream).sync().getNow();
        return stream;
      }

      public void close() throws Exception {
        channel.close().sync();
      }

      public Connection goAway(long streamId) throws Exception {
        QuicStreamChannel localControlStream = Http3.getLocalControlStream(channel);
        localControlStream.write(new DefaultHttp3GoAwayFrame(streamId)).sync();
        return this;
      }
    }

    public class Stream extends Http3RequestStreamInboundHandler {

      private final Connection connection;
      private QuicStreamChannel streamChannel;
      private Consumer<Http3Headers> headersHandler;
      private Consumer<byte[]> chunkHandler;
      private Consumer<Void> endHandler;
      private LongConsumer resetHandler;
      private boolean headersSent;
      private Http3Headers responseHeaders;
      private ByteArrayOutputStream responseCumulation = new ByteArrayOutputStream();
      private CompletableFuture<byte[]> responseBody = new CompletableFuture<>();

      public Stream(Connection connection) {
        this.connection = connection;
      }

      public QuicStreamChannel channel() {
        return streamChannel;
      }

      public long id() {
        return streamChannel.streamId();
      }

      @Override
      public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        if (cause instanceof QuicStreamResetException) {
          LongConsumer handler = resetHandler;
          if (handler != null) {
            handler.accept(((QuicStreamResetException)cause).applicationProtocolCode());
          }
        }
        responseBody.completeExceptionally(cause);
        super.exceptionCaught(ctx, cause);
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
        responseCumulation.writeBytes(chunk);
        Consumer<byte[]> handler = chunkHandler;
        if (handler != null) {
          handler.accept(chunk);
        }
      }

      @Override
      protected void channelInputClosed(ChannelHandlerContext ctx) {
        try {
          responseCumulation.close();
        } catch (IOException ignore) {
        }
        responseBody.complete(responseCumulation.toByteArray());
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

      public Stream resetHandler(LongConsumer resetHandler) {
        this.resetHandler = resetHandler;
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
        headers.set(HttpHeaderNames.CONTENT_LENGTH, "" + body.length);
        write(headers, false);
        end(body);
      }

      public void writeRaw(byte[] buff) {
        ChannelFuture fut = streamChannel.writeAndFlush(Unpooled.wrappedBuffer(buff));
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
        write(headers, false, end);
      }

      public void write(Http3Headers headers, boolean raw, boolean end) throws Exception {
        if (!headersSent) {
          headersSent = true;
          if (!raw) {
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

      public Http3Headers responseHeaders() {
        return responseHeaders;
      }

      public byte[] responseBody() throws Exception {
        try {
          return responseBody.get(10, TimeUnit.SECONDS);
        } catch (ExecutionException e) {
          throw (Exception)e.getCause();
        }
      }

      public void reset(int errorCode) throws Exception {
        if (streamChannel == null) {
          throw new IllegalStateException();
        }
        streamChannel.shutdownOutput(errorCode).sync();
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
