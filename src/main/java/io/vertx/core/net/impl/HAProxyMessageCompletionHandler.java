package io.vertx.core.net.impl;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToMessageDecoder;
import io.netty.handler.codec.haproxy.HAProxyMessage;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;
import io.netty.util.NetUtil;
import io.netty.util.concurrent.Promise;
import io.vertx.core.impl.logging.Logger;
import io.vertx.core.impl.logging.LoggerFactory;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.List;

public class HAProxyMessageCompletionHandler extends MessageToMessageDecoder<HAProxyMessage> {
  private static final Logger log = LoggerFactory.getLogger(HAProxyMessageCompletionHandler.class);
  private static final boolean proxyProtocolCodecFound;

  static {
    boolean proxyProtocolCodecCheck = true;
    try {
      Class.forName("io.netty.handler.codec.haproxy.HAProxyMessageDecoder");
    } catch (Throwable ex) {
      proxyProtocolCodecCheck = false;
    }
    proxyProtocolCodecFound = proxyProtocolCodecCheck;
  }

  public static boolean canUseProxyProtocol(boolean requested) {
    if (requested && !proxyProtocolCodecFound)
      log.warn("Proxy protocol support could not be enabled. Make sure that netty-codec-haproxy is included in your classpath");
    return proxyProtocolCodecFound && requested;
  }

  private final Promise<Channel> promise;

  public HAProxyMessageCompletionHandler(Promise<Channel> promise) {
    this.promise = promise;
  }


  @Override
  protected void decode(ChannelHandlerContext ctx, HAProxyMessage msg, List<Object> out) throws Exception {
    if (msg.sourceAddress() != null && msg.sourcePort() != 0) {
      InetAddress src = InetAddress.getByAddress(
        NetUtil.createByteArrayFromIpAddressString(msg.sourceAddress()));
      ctx.channel().attr(ConnectionBase.REMOTE_ADDRESS_OVERRIDE)
        .set(new InetSocketAddress(src, msg.sourcePort()));
    }

    if (msg.destinationAddress() != null && msg.destinationPort() != 0) {
      InetAddress dst = InetAddress.getByAddress(
        NetUtil.createByteArrayFromIpAddressString(msg.destinationAddress()));
      ctx.channel().attr(ConnectionBase.LOCAL_ADDRESS_OVERRIDE)
        .set(new InetSocketAddress(dst, msg.destinationPort()));
    }
    ctx.pipeline().remove(this);
    promise.setSuccess(ctx.channel());
  }


  @Override
  public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
    promise.tryFailure(cause);
  }

  @Override
  public void userEventTriggered(ChannelHandlerContext ctx, Object evt) {
    if (evt instanceof IdleStateEvent && ((IdleStateEvent) evt).state() == IdleState.ALL_IDLE) {
      ctx.close();
    } else {
      ctx.fireUserEventTriggered(evt);
    }
  }
}
