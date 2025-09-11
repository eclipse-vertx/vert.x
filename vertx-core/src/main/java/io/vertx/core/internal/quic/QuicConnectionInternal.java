package io.vertx.core.internal.quic;

import io.netty.channel.ChannelHandlerContext;
import io.vertx.core.quic.QuicConnection;

public interface QuicConnectionInternal extends QuicConnection {

  ChannelHandlerContext channelHandlerContext();

}
