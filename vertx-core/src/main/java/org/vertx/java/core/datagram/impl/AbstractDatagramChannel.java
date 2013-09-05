/*
 * Copyright 2013 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.vertx.java.core.datagram.impl;


import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import org.vertx.java.core.AsyncResult;
import org.vertx.java.core.Handler;
import org.vertx.java.core.datagram.DatagramChannel;
import org.vertx.java.core.impl.DefaultContext;
import org.vertx.java.core.impl.DefaultFutureResult;
import org.vertx.java.core.impl.VertxInternal;
import org.vertx.java.core.net.impl.ConnectionBase;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;


/**
 * @author <a href="mailto:nmaurer@redhat.com">Norman Maurer</a>
 */
abstract class AbstractDatagramChannel<T extends DatagramChannel, M> extends ConnectionBase
        implements DatagramChannel<T, M> {
  protected final io.netty.channel.socket.DatagramChannel channel;
  private Handler<Void> drainHandler;

  AbstractDatagramChannel(VertxInternal vertx, io.netty.channel.socket.DatagramChannel channel, DefaultContext context) {
    super(vertx, channel, context);
    this.channel = channel;
  }

  @Override
  public void close(final Handler<AsyncResult<Void>> handler) {
    channel.close().addListener(new ChannelFutureListener() {
      @Override
      public void operationComplete(ChannelFuture future) throws Exception {
        if (future.isSuccess()) {
          handler.handle(null);
        } else {
          handler.handle(new DefaultFutureResult<Void>(future.cause()));
        }
      }
    });
  }

  @SuppressWarnings("unchecked")
  void addListener(ChannelFuture future, Handler<AsyncResult<T>> handler) {
    future.addListener(new DatagramChannelFutureListener<T>((T) this, handler, vertx, context));
  }


  @Override
  @SuppressWarnings("unchecked")
  public T joinGroup(InetAddress multicastAddress, Handler<AsyncResult<T>> handler) {
    addListener(channel.joinGroup(multicastAddress), handler);
    return (T) this;
  }

  @Override
  @SuppressWarnings("unchecked")
  public T joinGroup(InetSocketAddress multicastAddress, NetworkInterface networkInterface, Handler<AsyncResult<T>> handler) {
    addListener(channel.joinGroup(multicastAddress, networkInterface), handler);
    return (T) this;
  }

  @Override
  @SuppressWarnings("unchecked")
  public T joinGroup(InetAddress multicastAddress, NetworkInterface networkInterface, InetAddress source, Handler<AsyncResult<T>> handler) {
    addListener(channel.joinGroup(multicastAddress, networkInterface, source), handler);
    return (T) this;
  }

  @Override
  @SuppressWarnings("unchecked")
  public T leaveGroup(InetAddress multicastAddress, Handler<AsyncResult<T>> handler) {
    addListener(channel.leaveGroup(multicastAddress), handler);
    return (T) this;
  }

  @Override
  @SuppressWarnings("unchecked")
  public T leaveGroup(InetSocketAddress multicastAddress, NetworkInterface networkInterface, Handler<AsyncResult<T>> handler) {
    addListener(channel.leaveGroup(multicastAddress, networkInterface), handler);
    return (T) this;
  }

  @Override
  @SuppressWarnings("unchecked")
  public T leaveGroup(InetAddress multicastAddress, NetworkInterface networkInterface, InetAddress source, Handler<AsyncResult<T>> handler) {
    addListener(channel.leaveGroup(multicastAddress, networkInterface, source), handler);
    return (T) this;
  }

  @Override
  @SuppressWarnings("unchecked")
  public T block(InetAddress multicastAddress, NetworkInterface networkInterface, InetAddress sourceToBlock, Handler<AsyncResult<T>> handler) {
    addListener(channel.block(multicastAddress, networkInterface, sourceToBlock), handler);
    return (T) this;
  }

  @Override
  @SuppressWarnings("unchecked")
  public T block(InetAddress multicastAddress, InetAddress sourceToBlock, Handler<AsyncResult<T>> handler) {
    addListener(channel.block(multicastAddress, sourceToBlock), handler);
    return (T) this;
  }

  @SuppressWarnings("unchecked")
  public T pause() {
    doPause();
    return (T) this;
  }

  @SuppressWarnings("unchecked")
  public T resume() {
    doResume();
    return (T) this;
  }


  @Override
  protected void handleInterestedOpsChanged() {
    if (drainHandler != null) {
      drainHandler.handle(null);
    }
  }

  @Override
  public T setWriteQueueMaxSize(int maxSize) {
    doSetWriteQueueMaxSize(maxSize);
    return (T) this;
  }

  @Override
  public boolean writeQueueFull() {
    return doWriteQueueFull();
  }

  @Override
  public T drainHandler(Handler<Void> handler) {
    drainHandler = handler;
    return (T) this;
  }

  abstract void handleMessage(M message);
}
