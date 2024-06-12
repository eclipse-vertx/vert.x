package io.vertx.core.net.impl;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToMessageDecoder;
import io.netty.handler.codec.haproxy.HAProxyMessage;
import io.netty.handler.codec.haproxy.HAProxyProxiedProtocol;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;
import io.netty.util.concurrent.Promise;
import io.vertx.core.impl.logging.Logger;
import io.vertx.core.impl.logging.LoggerFactory;
import io.vertx.core.net.SocketAddress;

import java.io.IOException;
import java.util.List;

public class HAProxyMessageCompletionHandler extends MessageToMessageDecoder<HAProxyMessage> {
  //Public because its used in tests
  public static final IOException UNSUPPORTED_PROTOCOL_EXCEPTION = new IOException("Unsupported HA PROXY transport protocol");

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
  protected void decode(ChannelHandlerContext ctx, HAProxyMessage msg, List<Object> out) {
    HAProxyProxiedProtocol protocol = msg.proxiedProtocol();

    //UDP over IPv4, UDP over IPv6 and UNIX datagram are not supported. Close the connection and fail the promise
    if (protocol.transportProtocol().equals(HAProxyProxiedProtocol.TransportProtocol.DGRAM)) {
      ctx.close();
      promise.tryFailure(UNSUPPORTED_PROTOCOL_EXCEPTION);
    } else {
      /*
      UNKNOWN: the connection is forwarded for an unknown, unspecified
      or unsupported protocol. The sender should use this family when sending
      LOCAL commands or when dealing with unsupported protocol families. The
      receiver is free to accept the connection anyway and use the real endpoint
      addresses or to reject it
       */
      if (!protocol.equals(HAProxyProxiedProtocol.UNKNOWN)) {
        if (msg.sourceAddress() != null) {
          ctx.channel().attr(ConnectionBase.REMOTE_ADDRESS_OVERRIDE)
            .set(createAddress(protocol, msg.sourceAddress(), msg.sourcePort()));
        }

        if (msg.destinationAddress() != null) {
          ctx.channel().attr(ConnectionBase.LOCAL_ADDRESS_OVERRIDE)
            .set(createAddress(protocol, msg.destinationAddress(), msg.destinationPort()));
        }
      }
      ctx.pipeline().remove(this);
      promise.setSuccess(ctx.channel());
    }
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

  private SocketAddress createAddress(HAProxyProxiedProtocol protocol, String sourceAddress, int port) {
    switch (protocol) {
      case TCP4:
      case TCP6:
        return SocketAddress.inetSocketAddress(port, sourceAddress);
      case UNIX_STREAM:
        return SocketAddress.domainSocketAddress(sourceAddress);
      default:
        throw new IllegalStateException("Should never happen");
    }
  }
}
