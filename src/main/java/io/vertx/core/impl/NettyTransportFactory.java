/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
package io.vertx.core.impl;

import io.netty.channel.Channel;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.ServerChannel;
import io.netty.channel.epoll.EpollDatagramChannel;
import io.netty.channel.epoll.EpollEventLoopGroup;
import io.netty.channel.epoll.EpollServerSocketChannel;
import io.netty.channel.epoll.EpollSocketChannel;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.DatagramChannel;
import io.netty.channel.socket.InternetProtocolFamily;
import io.netty.channel.socket.nio.NioDatagramChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.vertx.core.VertxOptions;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

import java.util.concurrent.ThreadFactory;

/**
 * Automatically choose netty socket transport: nio or epoll(only for GNU/Linux,
 * higher performance)
 * 
 * @author <a href="http://vertxer.org">Mike Bai <lostitle@gmail.com></a>
 */
public class NettyTransportFactory {
  private static final Logger log = LoggerFactory.getLogger(NettyTransportFactory.class);

  private String nettyTransport = VertxOptions.NETTY_TRANSPORT_NIO;

  /**
   * Get the netty socket transport to be used.
   * 
   * @return the netty transport
   */
  public String getNettyTransport() {
    return nettyTransport;
  }

  /**
   * Set the netty transport to be used while in GNU/Linux, the choices include:
   * nio, epoll Since 4.0.16, Netty provides the native socket transport for
   * GNU/Linux using JNI - epoll The epoll transport has higher performance and
   * produces less garbage, but multicast not supported
   * 
   * @param nettyTransport
   * @return a reference to this, so the API can be used fluently
   */
  public NettyTransportFactory setNettyTransport(String nettyTransport) {
    if (!Utils.isLinux() && VertxOptions.NETTY_TRANSPORT_EPOLL.equals(nettyTransport)) {
      log.warn("can not set nettyTransport to epoll while in non-linux");
    } else {
      this.nettyTransport = nettyTransport;
    }
    return this;
  }

  private boolean isEpoll() {
    return VertxOptions.NETTY_TRANSPORT_EPOLL.equals(nettyTransport);
  }

  // default factory instance
  private static NettyTransportFactory defaultFactory;
  static {
    defaultFactory = new NettyTransportFactory();
    if (VertxOptions.NETTY_TRANSPORT_EPOLL.equals(System.getProperty("nettyTransport"))) {
      defaultFactory.setNettyTransport(VertxOptions.NETTY_TRANSPORT_EPOLL);
    }
  }

  /**
   * return the default netty socket transport factory
   * 
   * @return
   */
  public static NettyTransportFactory getDefaultFactory() {
    return defaultFactory;
  }

  /**
   * @return
   */
  public Class<? extends ServerChannel> chooseServerSocketChannel() {
    if (isEpoll()) {
      return EpollServerSocketChannel.class;
    }
    return NioServerSocketChannel.class;
  }

  /**
   * @return
   */
  public Class<? extends Channel> chooseSocketChannel() {
    if (isEpoll()) {
      return EpollSocketChannel.class;
    }
    return NioSocketChannel.class;
  }

  /**
   * @return
   */
  public Class<? extends DatagramChannel> chooseDatagramChannel() {
    if (isEpoll()) {
      return EpollDatagramChannel.class;
    }
    return NioDatagramChannel.class;
  }

  /**
   * @return
   */
  public DatagramChannel instantiateDatagramChannel() {
    if (isEpoll()) {
      return new EpollDatagramChannel();
    }
    return new NioDatagramChannel();
  }

  /**
   * @param ipFamily
   * @return
   */
  public DatagramChannel instantiateDatagramChannel(InternetProtocolFamily ipFamily) {
    if (isEpoll()) {
      // EpollDatagramChannel use default constructor
      return new EpollDatagramChannel();
    }
    return new NioDatagramChannel(ipFamily);
  }

  /**
   * @return
   */
  public Class<? extends EventLoopGroup> chooseEventLoopGroup() {
    if (isEpoll()) {
      return EpollEventLoopGroup.class;
    }
    return NioEventLoopGroup.class;
  }

  /**
   * @param nThreads
   * @param threadFactory
   * @return
   */
  public EventLoopGroup instantiateEventLoopGroup(int nThreads, ThreadFactory threadFactory) {
    if (isEpoll()) {
      return new EpollEventLoopGroup(nThreads, threadFactory);
    }
    return new NioEventLoopGroup(nThreads, threadFactory);
  }

}
