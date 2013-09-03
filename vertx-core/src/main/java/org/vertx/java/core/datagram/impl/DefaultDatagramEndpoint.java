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
import io.netty.channel.socket.DatagramChannel;
import io.netty.channel.socket.nio.NioDatagramChannel;
import org.vertx.java.core.AsyncResult;
import org.vertx.java.core.Handler;
import org.vertx.java.core.datagram.BoundDatagramChannel;
import org.vertx.java.core.datagram.ConnectedDatagramChannel;
import org.vertx.java.core.datagram.DatagramEndpoint;
import org.vertx.java.core.impl.DefaultContext;
import org.vertx.java.core.impl.VertxInternal;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author <a href="mailto:nmaurer@redhat.com">Norman Maurer</a>
 */
public class DefaultDatagramEndpoint implements DatagramEndpoint {
  private final VertxInternal vertx;
  private final DefaultContext context;
  private Bootstrap bootstrap;
  private final Map<Channel, AbstractDatagramChannel> datagramMap = new ConcurrentHashMap<>();

  DefaultDatagramEndpoint(VertxInternal vertx) {
    this.vertx = vertx;
    context = vertx.getOrCreateContext();
  }

  private Bootstrap bootstrap() {
    if (bootstrap == null) {
      Bootstrap bootstrap = new Bootstrap();
      bootstrap.group(context.getEventLoop());
      bootstrap.channel(NioDatagramChannel.class);

      // TODO: Fill in config
    }
    return bootstrap.clone();
  }

  @Override
  public DatagramEndpoint bind(InetSocketAddress local, Handler<AsyncResult<BoundDatagramChannel>> handler) {
    ChannelFuture future = bootstrap().handler(new BoundDatagramChannelHandler(vertx, datagramMap)).bind(local);
    DefaultBoundDatagramChannel channel = new DefaultBoundDatagramChannel(vertx, (DatagramChannel) future.channel(), context);
    channel.addListener(future, handler);
    return this;
  }

  @Override
  public DatagramEndpoint connect(InetSocketAddress local, Handler<AsyncResult<ConnectedDatagramChannel>> handler) {
    ChannelFuture future = bootstrap().handler(new ConnectedDatagramChannelHandler(vertx, datagramMap)).bind(local);
    DefaultConnectedDatagramChannel channel = new DefaultConnectedDatagramChannel(vertx, (DatagramChannel) future.channel(), context);
    channel.addListener(future, handler);
    return this;
  }

  @Override
  public int getSendBufferSize() {
    return 0;  //To change body of implemented methods use File | Settings | File Templates.
  }

  @Override
  public DatagramEndpoint setSendBufferSize(int sendBufferSize) {
    return null;  //To change body of implemented methods use File | Settings | File Templates.
  }

  @Override
  public int getReceiveBufferSize() {
    return 0;  //To change body of implemented methods use File | Settings | File Templates.
  }

  @Override
  public DatagramEndpoint setReceiveBufferSize(int receiveBufferSize) {
    return null;  //To change body of implemented methods use File | Settings | File Templates.
  }

  @Override
  public int getTrafficClass() {
    return 0;  //To change body of implemented methods use File | Settings | File Templates.
  }

  @Override
  public DatagramEndpoint setTrafficClass(int trafficClass) {
    return null;  //To change body of implemented methods use File | Settings | File Templates.
  }

  @Override
  public boolean isReuseAddress() {
    return false;  //To change body of implemented methods use File | Settings | File Templates.
  }

  @Override
  public DatagramEndpoint setReuseAddress(boolean reuseAddress) {
    return null;  //To change body of implemented methods use File | Settings | File Templates.
  }

  @Override
  public boolean isBroadcast() {
    return false;  //To change body of implemented methods use File | Settings | File Templates.
  }

  @Override
  public DatagramEndpoint setBroadcast(boolean broadcast) {
    return null;  //To change body of implemented methods use File | Settings | File Templates.
  }

  @Override
  public boolean isLoopbackModeDisabled() {
    return false;  //To change body of implemented methods use File | Settings | File Templates.
  }

  @Override
  public DatagramEndpoint setLoopbackModeDisabled(boolean loopbackModeDisabled) {
    return null;  //To change body of implemented methods use File | Settings | File Templates.
  }

  @Override
  public int getTimeToLive() {
    return 0;  //To change body of implemented methods use File | Settings | File Templates.
  }

  @Override
  public DatagramEndpoint setTimeToLive(int ttl) {
    return null;  //To change body of implemented methods use File | Settings | File Templates.
  }

  @Override
  public InetAddress getInterface() {
    return null;  //To change body of implemented methods use File | Settings | File Templates.
  }

  @Override
  public DatagramEndpoint setInterface(InetAddress interfaceAddress) {
    return null;  //To change body of implemented methods use File | Settings | File Templates.
  }

  @Override
  public NetworkInterface getNetworkInterface() {
    return null;  //To change body of implemented methods use File | Settings | File Templates.
  }

  @Override
  public DatagramEndpoint setNetworkInterface(NetworkInterface networkInterface) {
    return null;  //To change body of implemented methods use File | Settings | File Templates.
  }

  @Override
  public DatagramEndpoint setConnectTimeoutMillis(int connectTimeoutMillis) {
    return null;  //To change body of implemented methods use File | Settings | File Templates.
  }
}
