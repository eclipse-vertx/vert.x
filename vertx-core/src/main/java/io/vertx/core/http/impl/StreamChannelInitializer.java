package io.vertx.core.http.impl;

import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelInitializer;
import io.netty.incubator.codec.quic.QuicStreamChannel;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;
import io.vertx.core.Handler;

class StreamChannelInitializer extends ChannelInitializer<QuicStreamChannel> {
  private static final InternalLogger logger = InternalLoggerFactory.getInstance(StreamChannelInitializer.class);

  private final Handler<QuicStreamChannel> onComplete;
  private final String agentType;
  private final ChannelHandler handler;

  public StreamChannelInitializer(ChannelHandler handler, String agentType) {
    this(handler, agentType, null);
  }

  public StreamChannelInitializer(ChannelHandler handler, String agentType, Handler<QuicStreamChannel> onComplete) {
    this.handler = handler;
    this.agentType = agentType;
    this.onComplete = onComplete;
  }

  @Override
  protected void initChannel(QuicStreamChannel streamChannel) {
    logger.debug("{} - Initialize streamChannel with channelId: {}, streamId: ", agentType, streamChannel.id(),
      streamChannel.streamId());

    streamChannel.pipeline().addLast(handler);
    if (onComplete != null) {
      onComplete.handle(streamChannel);
    }
  }
}
