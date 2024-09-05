package io.vertx.core.net.impl;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.ssl.SslHandler;
import io.netty.handler.ssl.SslHandshakeCompletionEvent;
import io.netty.incubator.codec.quic.QuicChannel;
import io.netty.util.concurrent.Promise;
import io.vertx.core.Handler;
import io.vertx.core.http.HttpVersion;
import io.vertx.core.internal.ContextInternal;

import javax.net.ssl.SSLHandshakeException;
import java.util.Objects;

class HttpSslHandshaker extends ChannelInboundHandlerAdapter {

  private final ContextInternal context;
  private final Handler<Channel> handler;
  private final Promise<Channel> channelHandler;
  private final HttpVersion version;
  private final ChannelHandler sslHandler;
  private final Handler<String> applicationProtocolHandler;

  public HttpSslHandshaker(ContextInternal context, Handler<Channel> handler, Promise<Channel> channelHandler,
                           HttpVersion version, ChannelHandler sslHandler,
                           Handler<String> applicationProtocolHandler) {
    this.context = context;
    this.handler = handler;
    this.channelHandler = channelHandler;
    this.version = version;
    this.sslHandler = sslHandler;
    this.applicationProtocolHandler = applicationProtocolHandler;
  }

  @Override
  public void userEventTriggered(ChannelHandlerContext ctx, Object evt) {
    if (evt instanceof SslHandshakeCompletionEvent) {
      // Notify application
      SslHandshakeCompletionEvent completion = (SslHandshakeCompletionEvent) evt;
      if (completion.isSuccess()) {
        // Remove from the pipeline after handshake result
        ctx.pipeline().remove(this);
        String protocol;
        if (version == HttpVersion.HTTP_3) {
          protocol = Objects.requireNonNull(((QuicChannel) ctx.channel()).sslEngine()).getApplicationProtocol();
        } else {
          protocol = ((SslHandler) sslHandler).applicationProtocol();
        }
        applicationProtocolHandler.handle(protocol);
        if (handler != null) {
          context.dispatch(ctx.channel(), handler);
        }
        channelHandler.setSuccess(ctx.channel());
      } else {
        SSLHandshakeException sslException = new SSLHandshakeException("Failed to create SSL connection");
        sslException.initCause(completion.cause());
        channelHandler.setFailure(sslException);
      }
    }
    ctx.fireUserEventTriggered(evt);
  }

  @Override
  public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
    // Ignore these exception as they will be reported to the handler
  }
}
