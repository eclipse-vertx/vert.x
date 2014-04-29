/*
 * Copyright (c) 2011-2013 The original author or authors
 * ------------------------------------------------------
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * and Apache License v2.0 which accompanies this distribution.
 *
 *     The Eclipse Public License is available at
 *     http://www.eclipse.org/legal/epl-v10.html
 *
 *     The Apache License v2.0 is available at
 *     http://www.opensource.org/licenses/apache2.0.php
 *
 * You may elect to redistribute this code under either of these licenses.
 */

package org.vertx.java.core.net.impl;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.handler.ssl.SslHandler;
import io.netty.util.CharsetUtil;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.GenericFutureListener;
import org.vertx.java.core.AsyncResult;
import org.vertx.java.core.Handler;
import org.vertx.java.core.VoidHandler;
import org.vertx.java.core.buffer.Buffer;
import org.vertx.java.core.eventbus.Message;
import org.vertx.java.core.file.impl.PathAdjuster;
import org.vertx.java.core.impl.DefaultContext;
import org.vertx.java.core.impl.DefaultFutureResult;
import org.vertx.java.core.impl.VertxInternal;
import org.vertx.java.core.net.NetSocket;

import java.io.File;
import java.net.InetSocketAddress;
import java.nio.charset.Charset;
import java.util.ArrayDeque;
import java.util.Queue;
import java.util.UUID;

public class DefaultNetSocket extends ConnectionBase implements NetSocket {

  private final String writeHandlerID;

  private Handler<Buffer> dataHandler;
  private Handler<Void> endHandler;
  private Handler<Void> drainHandler;
  private final Handler<Message<Buffer>> writeHandler;
  private Queue<Buffer> pendingData;
  private boolean paused = false;
  private final TCPSSLHelper helper;
  private boolean client;

  public DefaultNetSocket(VertxInternal vertx, Channel channel, DefaultContext context, TCPSSLHelper helper, boolean client) {
    super(vertx, channel, context);
    this.helper = helper;
    this.client = client;
    this.writeHandlerID = UUID.randomUUID().toString();
    writeHandler = new Handler<Message<Buffer>>() {
      public void handle(Message<Buffer> msg) {
        write(msg.body());
      }
    };
    vertx.eventBus().registerLocalHandler(writeHandlerID, writeHandler);
  }

  @Override
  public String writeHandlerID() {
    return writeHandlerID;
  }

  @Override
  public NetSocket write(Buffer data) {
    ByteBuf buf = data.getByteBuf();
    write(buf);
    return this;
  }

  @Override
  public NetSocket write(String str) {
    write(Unpooled.copiedBuffer(str, CharsetUtil.UTF_8));
    return this;
  }

  @Override
  public NetSocket write(String str, String enc) {
    if (enc == null) {
      write(str);
    } else {
      write(Unpooled.copiedBuffer(str, Charset.forName(enc)));
    }
    return this;
  }

  @Override
  public NetSocket dataHandler(Handler<Buffer> dataHandler) {
    this.dataHandler = dataHandler;
    return this;
  }

  @Override
  public NetSocket pause() {
    paused = true;
    doPause();
    return this;
  }

  @Override
  public NetSocket resume() {
    if (!paused) {
      return this;
    }
    paused = false;
    if (pendingData != null) {
      for (;;) {
        final Buffer buf = pendingData.poll();
        if (buf == null) {
          break;
        }
        vertx.runOnContext(new VoidHandler() {
          @Override
          protected void handle() {
            handleDataReceived(buf);
          }
        });
      }
    }
    doResume();
    return this;
  }

  @Override
  public NetSocket setWriteQueueMaxSize(int maxSize) {
    doSetWriteQueueMaxSize(maxSize);
    return this;
  }

  @Override
  public boolean writeQueueFull() {
    return doWriteQueueFull();
  }

  @Override
  public NetSocket endHandler(Handler<Void> endHandler) {
    this.endHandler = endHandler;
    return this;
  }

  @Override
  public NetSocket drainHandler(Handler<Void> drainHandler) {
    this.drainHandler = drainHandler;
    vertx.runOnContext(new VoidHandler() {
      public void handle() {
        callDrainHandler(); //If the channel is already drained, we want to call it immediately
      }
    });
    return this;
  }

  @Override
  public NetSocket sendFile(String filename) {
    return sendFile(filename, null);
  }

  @Override
  public NetSocket sendFile(String filename, final Handler<AsyncResult<Void>> resultHandler) {
    File f = new File(PathAdjuster.adjust(vertx, filename));
    if (f.isDirectory()) {
      throw new IllegalArgumentException("filename must point to a file and not to a directory");
    }
    ChannelFuture future = super.sendFile(f);

    if (resultHandler != null) {
      future.addListener(new ChannelFutureListener() {
        public void operationComplete(ChannelFuture future) throws Exception {
          final AsyncResult<Void> res;
          if (future.isSuccess()) {
            res = new DefaultFutureResult<>((Void)null);
          } else {
            res = new DefaultFutureResult<>(future.cause());
          }
          vertx.runOnContext(new Handler<Void>() {
            @Override
            public void handle(Void v) {
              resultHandler.handle(res);
            }
          });
        }
      });
    }

    return this;
  }

  @Override
  public InetSocketAddress remoteAddress() {
    return super.remoteAddress();
  }

  public InetSocketAddress localAddress() {
    return super.localAddress();
  }

  @Override
  public NetSocket exceptionHandler(Handler<Throwable> handler) {
    this.exceptionHandler = handler;
    return this;
  }

  @Override
  public NetSocket closeHandler(Handler<Void> handler) {
    this.closeHandler = handler;
    return this;
  }

  @Override
  public void close() {
    if (writeFuture != null) {
      // Close after all data is written
      writeFuture.addListener(ChannelFutureListener.CLOSE);
      channel.flush();
    } else {
      super.close();
    }
  }

  protected DefaultContext getContext() {
    return super.getContext();
  }

  protected void handleClosed() {
    setContext();
    if (endHandler != null) {
      try {
        endHandler.handle(null);
      } catch (Throwable t) {
        handleHandlerException(t);
      }
    }
    super.handleClosed();
    if (vertx.eventBus() != null) {
      vertx.eventBus().unregisterHandler(writeHandlerID, writeHandler);
    }
  }

  public void handleInterestedOpsChanged() {
    setContext();
    callDrainHandler();
  }

  void handleDataReceived(Buffer data) {
    if (paused) {
      if (pendingData == null) {
        pendingData = new ArrayDeque<>();
      }
      pendingData.add(data);
      return;
    }
    if (dataHandler != null) {
      setContext();
      try {
        dataHandler.handle(data);
      } catch (Throwable t) {
        handleHandlerException(t);
      }
    }
  }

  private ChannelFuture writeFuture;

  private void write(ByteBuf buff) {
    writeFuture = super.write(buff);
  }

  private void callDrainHandler() {
    if (drainHandler != null) {
      if (!writeQueueFull()) {
        try {
          drainHandler.handle(null);
        } catch (Throwable t) {
          handleHandlerException(t);
        }
      }
    }
  }

  @Override
  public NetSocket ssl(final Handler<Void> handler) {
    SslHandler sslHandler = channel.pipeline().get(SslHandler.class);
    if (sslHandler == null) {
      sslHandler = helper.createSslHandler(vertx, client);
      channel.pipeline().addFirst(sslHandler);
    }
    sslHandler.handshakeFuture().addListener(new GenericFutureListener<Future<Channel>>() {
      @Override
      public void operationComplete(final Future<Channel> future) throws Exception {
        if (context.isOnCorrectWorker(channel.eventLoop())) {
          if (future.isSuccess()) {
            try {
              vertx.setContext(context);
              handler.handle(null);
            } catch (Throwable t) {
              context.reportException(t);
            }
          } else {
            context.reportException(future.cause());
          }

        } else {
          context.execute(new Runnable() {
            public void run() {
              if (future.isSuccess()) {
                handler.handle(null);
              } else {
                context.reportException(future.cause());
              }
            }
          });
        }
      }
    });
    return this;
  }

  @Override
  public boolean isSsl() {
    return channel.pipeline().get(SslHandler.class) != null;
  }
}

