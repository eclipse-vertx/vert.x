/*
 * Copyright (c) 2011-2019 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */

package io.vertx.core.net.impl.tcp;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.internal.buffer.BufferInternal;
import io.vertx.core.eventbus.Message;
import io.vertx.core.eventbus.MessageConsumer;
import io.vertx.core.http.impl.HttpUtils;
import io.vertx.core.internal.ContextInternal;
import io.vertx.core.internal.PromiseInternal;
import io.vertx.core.internal.tls.SslContextManager;
import io.vertx.core.internal.net.SslChannelProvider;
import io.vertx.core.internal.net.SslHandshakeCompletionHandler;
import io.vertx.core.net.*;
import io.vertx.core.internal.net.NetSocketInternal;
import io.vertx.core.net.impl.SocketBase;
import io.vertx.core.spi.metrics.TCPMetrics;

import java.util.UUID;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class NetSocketImpl extends SocketBase<NetSocketImpl> implements NetSocketInternal {

  private final String writeHandlerID;
  private final SslContextManager sslContextManager;
  private final SSLOptions sslOptions;
  private final SocketAddress remoteAddress;
  private final TCPMetrics metrics;
  private final String negotiatedApplicationLayerProtocol;
  private MessageConsumer registration;

  public NetSocketImpl(ContextInternal context,
                       ChannelHandlerContext channel,
                       SslContextManager sslContextManager,
                       SSLOptions sslOptions,
                       TCPMetrics metrics,
                       boolean registerWriteHandler) {
    this(context, channel, null, sslContextManager, sslOptions, metrics, null, registerWriteHandler);
  }

  public NetSocketImpl(ContextInternal context,
                       ChannelHandlerContext channel,
                       SocketAddress remoteAddress,
                       SslContextManager sslContextManager,
                       SSLOptions sslOptions,
                       TCPMetrics metrics,
                       String negotiatedApplicationLayerProtocol,
                       boolean registerWriteHandler) {
    super(context, channel);
    this.sslContextManager = sslContextManager;
    this.sslOptions = sslOptions;
    this.writeHandlerID = registerWriteHandler ? "__vertx.net." + UUID.randomUUID() : null;
    this.remoteAddress = remoteAddress;
    this.metrics = metrics;
    this.negotiatedApplicationLayerProtocol = negotiatedApplicationLayerProtocol;
  }

  void registerEventBusHandler() {
    if (writeHandlerID != null) {
      Handler<Message<Buffer>> writeHandler = msg -> write(msg.body());
      registration = vertx.eventBus().<Buffer>localConsumer(writeHandlerID).handler(writeHandler);
    }
  }

  void unregisterEventBusHandler() {
    if (registration != null) {
      MessageConsumer consumer = registration;
      registration = null;
      consumer.unregister();
    }
  }

  @Override
  public TCPMetrics metrics() {
    return metrics;
  }

  @Override
  public String writeHandlerID() {
    return writeHandlerID;
  }

  @Override
  public Future<Void> upgradeToSsl(SSLOptions sslOptions, String serverName, Buffer upgrade) {
    return sslUpgrade(
      serverName,
      sslOptions != null ? sslOptions : this.sslOptions,
      upgrade != null ? ((BufferInternal) upgrade).getByteBuf() : Unpooled.EMPTY_BUFFER);
  }

  private Future<Void> sslUpgrade(String serverName, SSLOptions sslOptions, ByteBuf msg) {
    if (sslOptions == null) {
      return context.failedFuture("Missing SSL options");
    }
    if (sslOptions instanceof ClientSSLOptions && ((ClientSSLOptions)sslOptions).getHostnameVerificationAlgorithm() == null) {
      return context.failedFuture("Missing hostname verification algorithm");
    }
    if (remoteAddress != null && !(sslOptions instanceof ClientSSLOptions)) {
      return context.failedFuture("Client socket upgrade must use ClientSSLOptions");
    } else if (remoteAddress == null && !(sslOptions instanceof ServerSSLOptions)) {
      return context.failedFuture("Server socket upgrade must use ServerSSLOptions");
    }
    if (chctx.pipeline().get("ssl") == null) {
      doPause();
      Future<SslChannelProvider> f;
      if (sslOptions instanceof ClientSSLOptions) {
        ClientSSLOptions clientSSLOptions =  (ClientSSLOptions) sslOptions;
        f = sslContextManager.resolveSslContextProvider(clientSSLOptions, context)
          .map(p -> new SslChannelProvider(context.owner(), p, false));
      } else {
        ServerSSLOptions serverSSLOptions = (ServerSSLOptions) sslOptions;
        f = sslContextManager.resolveSslContextProvider(serverSSLOptions, context)
          .map(p -> new SslChannelProvider(context.owner(), p, serverSSLOptions.isSni()));
      }
      return f.compose(provider -> {
        PromiseInternal<Void> p = context.promise();
        ChannelPromise promise = chctx.newPromise();
        writeToChannel(msg, true, promise);
        promise.addListener(res -> {
          if (res.isSuccess()) {
            ChannelPromise channelPromise = chctx.newPromise();
            chctx.pipeline().addFirst("handshaker", new SslHandshakeCompletionHandler(channelPromise));
            ChannelHandler sslHandler;
            if (sslOptions instanceof ClientSSLOptions) {
              ClientSSLOptions clientSSLOptions = (ClientSSLOptions) sslOptions;
              sslHandler = provider.createClientSslHandler(remoteAddress, serverName, sslOptions.isUseAlpn(), clientSSLOptions.getSslHandshakeTimeout(), clientSSLOptions.getSslHandshakeTimeoutUnit());
            } else {
              sslHandler = provider.createServerHandler(sslOptions.isUseAlpn(), sslOptions.getSslHandshakeTimeout(),
                sslOptions.getSslHandshakeTimeoutUnit(), HttpUtils.socketAddressToHostAndPort(chctx.channel().remoteAddress()));
            }
            chctx.pipeline().addFirst("ssl", sslHandler);
            channelPromise.addListener(p);
          } else {
            p.fail(res.cause());
          }
        });
        return p.future();
      }).transform(ar -> {
        doResume();
        return (Future<Void>) ar;
      });
    } else {
      throw new IllegalStateException(); // ???
    }
  }

  @Override
  public String applicationLayerProtocol() {
    return negotiatedApplicationLayerProtocol;
  }

  @Override
  protected void handleClosed() {
    handleEnd();
    super.handleClosed();
  }
}

