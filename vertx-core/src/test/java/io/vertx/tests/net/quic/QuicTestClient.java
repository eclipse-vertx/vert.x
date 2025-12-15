/*
 * Copyright (c) 2011-2025 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package io.vertx.tests.net.quic;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPromise;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.ChannelInputShutdownReadComplete;
import io.netty.channel.socket.DatagramPacket;
import io.netty.channel.socket.nio.NioDatagramChannel;
import io.netty.handler.codec.quic.*;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import io.netty.util.internal.PlatformDependent;
import io.vertx.core.Handler;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.function.LongConsumer;

/**
 * Minimal Quic client for testing Vert.x QuicServer
 */
public class QuicTestClient {

  public class Connection extends ChannelDuplexHandler {

    private final Channel channel;
    private QuicChannel quicChannel;
    private Handler<Void> closeHandler;
    private QuicConnectionCloseEvent closeEvent;
    private Consumer<Stream> handler;
    private Consumer<byte[]> datagramHandler;

    public Connection(Channel channel) {
      this.channel = Objects.requireNonNull(channel);
    }

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

    public Stream newStream() {
      return new Stream(this);
    }

    public Connection handler(Consumer<Stream> handler) {
      this.handler = handler;
      return this;
    }

    public Connection datagramHandler(Consumer<byte[]> handler) {
      this.datagramHandler = handler;
      return this;
    }

    public void writeDatagram(byte[] dgram) throws Exception {
      QuicChannel ch = quicChannel;
      if (ch == null) {
        throw new IllegalStateException();
      }
      ByteBuf byteBuf = Unpooled.copiedBuffer(dgram);
      ChannelFuture fut = ch.writeAndFlush(byteBuf);
      fut.sync();
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

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
      if (msg instanceof ByteBuf) {
        ByteBuf byteBuf = (ByteBuf) msg;
        byte[] datagram;
        try {
          datagram = new byte[byteBuf.readableBytes()];
          byteBuf.readBytes(datagram);
        } finally {
          byteBuf.release();
        }
        Consumer<byte[]> handler = datagramHandler;
        if (handler != null) {
          handler.accept(datagram);
        }
      } else {
        super.channelRead(ctx, msg);
      }
    }

    public void close() throws Exception {
      close(0, "bye".getBytes());
    }

    public void close(int error, byte[] reason) throws Exception {
      quicChannel.close(true, error, Unpooled.copiedBuffer(reason)).sync();
    }

    public Connection connect(InetSocketAddress addr) {
      QuicChannel ch = quicChannel;
      if (ch != null) {
        throw new IllegalStateException();
      }
      try {
        ch = QuicChannel.newBootstrap(channel)
          .handler(this)
          .streamHandler(new ChannelInitializer<>() {
            @Override
            protected void initChannel(Channel ch) {
              Stream stream = new Stream(Connection.this, (QuicStreamChannel) ch);
              handler.accept(stream);
              ch.pipeline().addLast(stream);
            }
          })
          .remoteAddress(addr)
          .connect()
          .get();
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        PlatformDependent.throwException(e);
      } catch (ExecutionException e) {
        PlatformDependent.throwException(e.getCause());
      }
      quicChannel = ch;
      return this;
    }
  }

  public class Stream extends ChannelDuplexHandler {

    final Connection connection;
    QuicStreamChannel streamChannel;
    Consumer<byte[]> handler;
    Runnable closeHandler;
    LongConsumer resetHandler;
    Consumer<Exception> exceptionHandler;

    private Stream(Connection connection) {
      this.connection = connection;
    }

    private Stream(Connection connection, QuicStreamChannel streamChannel) {
      this.connection = connection;
      this.streamChannel = streamChannel;
    }

    public Stream create() throws Exception {
      return create(true);
    }

    public Stream create(boolean bidi) throws Exception {
      if (streamChannel != null) {
        throw new IllegalStateException();
      }
      QuicChannel ch = connection.quicChannel;
      streamChannel = ch.createStream(bidi ? QuicStreamType.BIDIRECTIONAL : QuicStreamType.UNIDIRECTIONAL, this).sync().getNow();
      return this;
    }

    public void reset(int errorCode) throws Exception {
      if (streamChannel == null) {
        throw new IllegalStateException();
      }
      streamChannel.shutdownOutput(errorCode).sync();
    }

    public void abort(int errorCode) throws Exception {
      if (streamChannel == null) {
        throw new IllegalStateException();
      }
      streamChannel.shutdownInput(errorCode).sync();
    }

    public Stream handler(Consumer<byte[]> handler) {
      this.handler = handler;
      return this;
    }

    public Stream closeHandler(Runnable handler) {
      closeHandler = handler;
      return this;
    }

    public Stream resetHandler(LongConsumer handler) {
      resetHandler = handler;
      return this;
    }

    public Stream exceptionHandler(Consumer<Exception> handler) {
      exceptionHandler = handler;
      return this;
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
      Consumer<byte[]> h = handler;
      if (h != null) {
        h.accept(data);
      }
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) {
      if (evt == ChannelInputShutdownReadComplete.INSTANCE) {
        Runnable handler = closeHandler;
        if (handler != null) {
          handler.run();
        }
      }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
      if (cause instanceof QuicException) {
        QuicException quicCause = (QuicException) cause;
        if (quicCause instanceof QuicStreamResetException) {
          QuicStreamResetException reset = (QuicStreamResetException)quicCause;
          LongConsumer handler = resetHandler;
          if (handler != null) {
            resetHandler.accept(reset.applicationProtocolCode());
            return;
          }
        }
        Consumer<Exception> handler = exceptionHandler;
        if (handler != null) {
          handler.accept(quicCause);
          return;
        }
      }
      super.exceptionCaught(ctx, cause);
    }

    public Stream write(String data) {
      return write(data.getBytes(StandardCharsets.UTF_8));
    }

    public Stream write(byte[] data) {
      streamChannel.writeAndFlush(Unpooled.copiedBuffer(data));
      return this;
    }

    public void close() throws Exception {
      streamChannel.writeAndFlush(Unpooled.EMPTY_BUFFER)
        .addListener(QuicStreamChannel.SHUTDOWN_OUTPUT);
      streamChannel.closeFuture().sync();
    }
  }

  private final NioEventLoopGroup group;
  private Channel channel;
  private Consumer<ByteBuf> tokenHandler;

  public QuicTestClient(NioEventLoopGroup group) {
    this.group = group;
  }

  public QuicTestClient tokenHandler(Consumer<ByteBuf> tokenHandler) {
    this.tokenHandler = tokenHandler;
    return this;
  }

  private Channel channel() throws Exception {
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
        .initialMaxStreamDataBidirectionalRemote(1000000)
        .initialMaxStreamDataUnidirectional(1000000)
        .initialMaxStreamsBidirectional(100)
        .initialMaxStreamsUnidirectional(100)
        .datagram(10, 10)
        .build();

      Bootstrap bs = new Bootstrap();
      ch = bs.group(group)
        .channel(NioDatagramChannel.class)
        .handler(new ChannelInitializer<>() {
          @Override
          protected void initChannel(Channel ch) throws Exception {

            Consumer<ByteBuf> tokenHandler = QuicTestClient.this.tokenHandler;
            if (tokenHandler != null) {
              ch.pipeline().addLast(new ChannelDuplexHandler() {
                @Override
                public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
                  if (msg instanceof DatagramPacket) {
                    DatagramPacket datagram = (DatagramPacket) msg;
                    ByteBuf byteBuf = datagram.content();
                    byte b = byteBuf.getByte(byteBuf.readerIndex());
                    if ((b & 0xF0) == 0b11000000) {
                      int dstConnIdLength = byteBuf.getByte(byteBuf.readerIndex() + 5);
                      int srcConnIdLength = byteBuf.getByte(byteBuf.readerIndex() + 5 + 1 + dstConnIdLength);
                      int payloadIndex = byteBuf.readerIndex() + 5 + 1 + dstConnIdLength + 1 + srcConnIdLength;
                      // read variable length
                      byte a = byteBuf.getByte(payloadIndex++);
                      int tokenLength;
                      switch (a & 0b11000000) {
                        case 0:
                          tokenLength = a;
                          break;
                        case 0b1000000:
                          tokenLength = a & 0x3F;
                          int next = byteBuf.getByte(payloadIndex++);
                          tokenLength = (tokenLength << 8) + next;
                          break;
                        default:
                          throw new UnsupportedOperationException();
                      }
                      if (tokenLength > 0) {
                        ByteBuf token = byteBuf.slice(payloadIndex, tokenLength);
                        tokenHandler.accept(token);
                      }
                    }
                  }
                  super.write(ctx, msg, promise);
                }
              });
            }

            ch.pipeline().addLast(codec);
          }
        })
        .bind(0).sync().channel();
      channel = ch;
    }
    return ch;
  }

  public Connection connection() throws Exception {
    return new Connection(channel());
  }

  public Connection connect(InetSocketAddress addr) throws Exception {
    return connection().connect(addr);
  }

  public void close() throws Exception {
    channel.close().sync();
  }
}
