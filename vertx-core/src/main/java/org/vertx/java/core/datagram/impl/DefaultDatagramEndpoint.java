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

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.socket.DatagramChannel;
import io.netty.channel.socket.nio.NioDatagramChannel;
import org.vertx.java.core.AsyncResult;
import org.vertx.java.core.Handler;
import org.vertx.java.core.datagram.DatagramEndpoint;
import org.vertx.java.core.datagram.DatagramSocket;
import org.vertx.java.core.impl.DefaultContext;
import org.vertx.java.core.impl.VertxInternal;

import java.net.InetSocketAddress;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;


/**
 * @author <a href="mailto:nmaurer@redhat.com">Norman Maurer</a>
 */
public class DefaultDatagramEndpoint implements DatagramEndpoint {
  final Map<Channel, DefaultDatagramSocket> socketMap = new ConcurrentHashMap<>();

  private final VertxInternal vertx;
  private final DefaultContext actualCtx;
  private Bootstrap bootstrap;
  public DefaultDatagramEndpoint(VertxInternal vertx) {
    this.vertx = vertx;
    actualCtx = vertx.getOrCreateContext();
  }

  @Override
  public DatagramEndpoint bind(InetSocketAddress local, Handler<AsyncResult<DatagramSocket>> handler) {
    if (bootstrap == null) {
      bootstrap = createBootstrap();
    }
    ChannelFuture future = bootstrap.bind(local);
    DefaultDatagramSocket socket = new DefaultDatagramSocket(vertx, (DatagramChannel) future.channel(), actualCtx);
    socket.addListener(future, handler);
    return this;
  }


  private Bootstrap createBootstrap() {
    Bootstrap bootstrap = new Bootstrap();
    bootstrap.group(actualCtx.getEventLoop());
    bootstrap.channel(NioDatagramChannel.class);
    bootstrap.handler(new ChannelInitializer<Channel>() {
      @Override
      protected void initChannel(Channel ch) throws Exception {
        ChannelPipeline pipeline = ch.pipeline();
        pipeline.addLast("handler", new DatagramHandler(vertx, socketMap));
      }
    });
    return bootstrap;
  }

}
