package io.vertx.core.http.impl;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.ssl.SslHandshakeCompletionEvent;
import io.vertx.core.impl.future.PromiseInternal;

class Http3SslHandshakeHandler extends ChannelInboundHandlerAdapter {
  private final PromiseInternal<HttpClientConnection> promise;

  public Http3SslHandshakeHandler(PromiseInternal<HttpClientConnection> promise) {
    this.promise = promise;
  }

  @Override
  public void userEventTriggered(ChannelHandlerContext ctx, Object evt) {
    if (evt instanceof SslHandshakeCompletionEvent) {
      SslHandshakeCompletionEvent completion = (SslHandshakeCompletionEvent) evt;
      if (!completion.isSuccess()) {
        promise.tryFail(completion.cause());
      }
    } else {
      ctx.fireUserEventTriggered(evt);
    }
  }
}
