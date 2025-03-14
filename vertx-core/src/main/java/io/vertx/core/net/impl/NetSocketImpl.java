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

package io.vertx.core.net.impl;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.util.CharsetUtil;
import io.netty.util.ReferenceCounted;
import io.vertx.codegen.annotations.Nullable;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.impl.HttpUtils;
import io.vertx.core.internal.buffer.BufferInternal;
import io.vertx.core.eventbus.Message;
import io.vertx.core.eventbus.MessageConsumer;
import io.vertx.core.http.ClientAuth;
import io.vertx.core.http.impl.HttpUtils;
import io.vertx.core.internal.ContextInternal;
import io.vertx.core.internal.PromiseInternal;
import io.vertx.core.internal.concurrent.InboundMessageChannel;
import io.vertx.core.internal.tls.SslContextManager;
import io.vertx.core.internal.net.SslChannelProvider;
import io.vertx.core.internal.net.SslHandshakeCompletionHandler;
import io.vertx.core.net.*;
import io.vertx.core.internal.net.NetSocketInternal;
import io.vertx.core.spi.metrics.TCPMetrics;
import io.vertx.core.streams.impl.InboundBuffer;

import java.io.File;
import java.io.RandomAccessFile;
import java.nio.charset.Charset;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class NetSocketImpl extends VertxConnection implements NetSocketInternal {

  private final String writeHandlerID;
  private final SslContextManager sslContextManager;
  private final SSLOptions sslOptions;
  private final SocketAddress remoteAddress;
  private final TCPMetrics metrics;
  private final InboundMessageChannel<Object> pending;
  private final String negotiatedApplicationLayerProtocol;
  private Handler<Void> endHandler;
  private volatile Handler<Void> drainHandler;
  private MessageConsumer registration;
  private Handler<Buffer> handler;
  private Handler<Object> messageHandler;
  private Handler<Object> eventHandler;

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
    this.messageHandler = new DataMessageHandler();
    this.negotiatedApplicationLayerProtocol = negotiatedApplicationLayerProtocol;
    this.pending = new InboundMessageChannel<>(context.eventLoop(), context.executor()) {
      @Override
      protected void handleResume() {
        NetSocketImpl.this.doResume();
      }
      @Override
      protected void handlePause() {
        NetSocketImpl.this.doPause();
      }
      @Override
      protected void handleMessage(Object msg) {
        if (msg == InboundBuffer.END_SENTINEL) {
          Handler<Void> handler = endHandler();
          if (handler != null) {
            context.dispatch(handler);
          }
        } else {
          Handler<Buffer> handler = handler();
          if (handler != null) {
            context.dispatch((Buffer) msg, handler);
          }
        }
      }
    };
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
  public Future<Void> writeMessage(Object message) {
    PromiseInternal<Void> promise = context.promise();
    writeToChannel(message, promise);
    return promise.future();
  }

  @Override
  public String applicationLayerProtocol() {
    return negotiatedApplicationLayerProtocol;
  }

  @Override
  public Future<Void> write(Buffer data) {
    return writeMessage(((BufferInternal)data).getByteBuf());
  }

  @Override
  public Future<Void> write(String str) {
    return writeMessage(Unpooled.copiedBuffer(str, CharsetUtil.UTF_8));
  }

  @Override
  public Future<Void> write(String str, String enc) {
    return writeMessage(Unpooled.copiedBuffer(str, Charset.forName(enc)));
  }

  private synchronized Handler<Buffer> handler() {
    return handler;
  }

  @Override
  public synchronized NetSocket handler(Handler<Buffer> dataHandler) {
    this.handler = dataHandler;
    return this;
  }

  private synchronized Handler<Object> messageHandler() {
    return messageHandler;
  }

  @Override
  public synchronized NetSocketInternal messageHandler(Handler<Object> handler) {
    messageHandler = handler == null ? new DataMessageHandler() : msg -> context.emit(msg, handler);
    return this;
  }

  @Override
  public synchronized NetSocketInternal eventHandler(Handler<Object> handler) {
    eventHandler = handler;
    return this;
  }

  @Override
  public synchronized NetSocket pause() {
    pending.pause();
    return this;
  }

  @Override
  public NetSocket fetch(long amount) {
    pending.fetch(amount);
    return this;
  }

  @Override
  public synchronized NetSocket resume() {
    return fetch(Long.MAX_VALUE);
  }

  @Override
  public NetSocket setWriteQueueMaxSize(int maxSize) {
    doSetWriteQueueMaxSize(maxSize);
    return this;
  }

  @Override
  public boolean writeQueueFull() {
    return super.writeQueueFull();
  }

  @Override
  protected void handleWriteQueueDrained() {
    Handler<Void> handler = drainHandler;
    if (handler != null) {
      context.emit(null, handler);
    }
  }

  private synchronized Handler<Void> endHandler() {
    return endHandler;
  }

  @Override
  public synchronized NetSocket endHandler(Handler<Void> endHandler) {
    this.endHandler = endHandler;
    return this;
  }

  @Override
  public synchronized NetSocket drainHandler(Handler<Void> drainHandler) {
    this.drainHandler = drainHandler;
    return this;
  }

  @Override
  public Future<Void> sendFile(String filename, long offset, long length) {
    PromiseInternal<Void> promise = context.promise();
    File file = vertx.fileResolver().resolve(filename);
    RandomAccessFile raf;
    try {
      raf = new RandomAccessFile(file, "r");
    } catch (Exception e) {
      return context.failedFuture(e);
    }
    long actualLength = Math.min(length, file.length() - offset);
    long actualOffset = Math.min(offset, file.length());
    ChannelFuture fut = sendFile(raf, actualOffset, actualLength);
    fut.addListener(promise);
    return promise.future();
  }

  public NetSocketImpl exceptionHandler(Handler<Throwable> handler) {
    return (NetSocketImpl) super.exceptionHandler(handler);
  }

  @Override
  public NetSocketImpl closeHandler(Handler<Void> handler) {
    return (NetSocketImpl) super.closeHandler(handler);
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
        f = sslContextManager.resolveSslContextProvider(
          sslOptions,
          clientSSLOptions.getHostnameVerificationAlgorithm(),
          null,
          null,
          context).map(p -> new SslChannelProvider(context.owner(), p, false));
      } else {
        ServerSSLOptions serverSSLOptions = (ServerSSLOptions) sslOptions;
        ClientAuth clientAuth = serverSSLOptions.getClientAuth();
        if (clientAuth == null) {
          clientAuth = ClientAuth.NONE;
        }
        f = sslContextManager.resolveSslContextProvider(
          sslOptions,
          null,
          clientAuth,
          null, context).map(p -> new SslChannelProvider(context.owner(), p, serverSSLOptions.isSni()));
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
              sslHandler = provider.createClientSslHandler(remoteAddress, serverName, sslOptions);
            } else {
              sslHandler = provider.createServerHandler(sslOptions,
                HttpUtils.socketAddressToHostAndPort(chctx.channel().remoteAddress()), null);
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
  public Future<Void> end() {
    return close();
  }

  @Override
  protected void handleShutdown(Object reason, long timeout, TimeUnit unit, ChannelPromise promise) {
  }

  @Override
  protected void handleClosed() {
    pending.write(InboundBuffer.END_SENTINEL);
    super.handleClosed();
  }

  @Override
  public void handleMessage(Object msg) {
    Handler<Object> handler = messageHandler();
    handler.handle(msg);
  }

  @Override
  protected void handleEvent(Object event) {
    Handler<Object> handler;
    synchronized (this) {
      handler = eventHandler;
    }
    if (handler != null) {
      context.emit(event, handler);
    } else {
      super.handleEvent(event);
    }
  }

  @Override
  public NetSocketImpl shutdownHandler(@Nullable Handler<Void> handler) {
    super.shutdownHandler(handler);
    return this;
  }

  private class DataMessageHandler implements Handler<Object> {

    @Override
    public void handle(Object msg) {
      if (msg instanceof ByteBuf) {
        Buffer buffer = BufferInternal.safeBuffer((ByteBuf) msg);
        pending.write(buffer);
      } else {
        handleInvalid(msg);
      }
    }

    private void handleInvalid(Object msg) {
      // ByteBuf are eagerly released when the message is processed
      if (msg instanceof ReferenceCounted && (!(msg instanceof ByteBuf))) {
        ReferenceCounted refCounter = (ReferenceCounted) msg;
        refCounter.release();
      }
    }
  }
}

