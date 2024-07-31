package io.vertx.core.http.impl;

import io.netty.incubator.codec.http3.DefaultHttp3HeadersFrame;
import io.netty.incubator.codec.http3.Http3;
import io.netty.incubator.codec.http3.Http3HeadersFrame;
import io.netty.incubator.codec.quic.QuicChannel;
import io.netty.incubator.codec.quic.QuicStreamChannel;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.GenericFutureListener;
import io.vertx.core.net.SocketAddress;

public class VertxHttp3 {

  public static void newRequestStream(Future<QuicChannel> quicChannelFuture, SocketAddress peerAddress) {
    Http3
      .newRequestStream(quicChannelFuture.getNow(), new VertxHttp3RequestStreamInboundHandler())
      .addListener((GenericFutureListener<Future<QuicStreamChannel>>) quicStreamChannelFuture -> {
        flush(peerAddress, quicStreamChannelFuture.get());
      });
  }

  private static void flush(SocketAddress peerAddress, QuicStreamChannel quicStreamChannel) {
    Http3HeadersFrame frame = new DefaultHttp3HeadersFrame();
    frame.headers().method("GET").path("/")
      .authority(peerAddress.hostAddress() + ":" + peerAddress.port())
      .scheme("https");
    quicStreamChannel.writeAndFlush(frame).addListener(QuicStreamChannel.SHUTDOWN_OUTPUT);
  }

}
