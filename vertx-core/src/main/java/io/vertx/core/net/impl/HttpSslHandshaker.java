package io.vertx.core.net.impl;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.ssl.SslHandshakeCompletionEvent;
import io.netty.util.concurrent.Promise;

import javax.net.ssl.SSLHandshakeException;

class HttpSslHandshaker extends ChannelInboundHandlerAdapter {

  private final Promise<ChannelHandlerContext> channelHandlerContextPromise;

  public HttpSslHandshaker(Promise<ChannelHandlerContext> channelHandlerContextPromise) {
    this.channelHandlerContextPromise = channelHandlerContextPromise;
  }

  @Override
  public void userEventTriggered(ChannelHandlerContext ctx, Object evt) {
    if (evt instanceof SslHandshakeCompletionEvent) {
      // Notify application
      SslHandshakeCompletionEvent completion = (SslHandshakeCompletionEvent) evt;
      if (completion.isSuccess()) {
        // Remove from the pipeline after handshake result
        ctx.pipeline().remove(this);
        channelHandlerContextPromise.setSuccess(ctx);
      } else {
        SSLHandshakeException sslException = new SSLHandshakeException("Failed to create SSL connection");
        sslException.initCause(completion.cause());
        channelHandlerContextPromise.setFailure(sslException);
      }
    }
    ctx.fireUserEventTriggered(evt);
  }

  @Override
  public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
    // Ignore these exception as they will be reported to the handler
  }
}
