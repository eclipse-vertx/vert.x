/*
 * Copyright (c) 2011-2014 The original author or authors
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
package io.vertx.core.impl;

import io.netty.channel.Channel;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.ServerChannel;
import io.netty.channel.epoll.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.DatagramChannel;
import io.netty.channel.socket.InternetProtocolFamily;
import io.netty.channel.socket.nio.NioDatagramChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;

import java.util.concurrent.ThreadFactory;

/**
 * Handles parsing Epoll enablement.
 *
 * By default even if the
 *
 * @author <a href="mailto:plopes@redhatcom">Paulo Lopes</a>
 */
public final class VertxEpollSupport {

  public static final boolean epollEnabled;

  private VertxEpollSupport() {
  }

  static {
    epollEnabled = "true".equals(System.getProperty("vertx.epoll.enable", "false")) && Epoll.isAvailable();
  }

  public static EventLoopGroup eventLoopGroup(int size, ThreadFactory tf, int ioRatio) {
    if (epollEnabled) {
      return new EpollEventLoopGroup(size, tf);
    } else {
      NioEventLoopGroup eventLoopGroup = new NioEventLoopGroup(size, tf);
      eventLoopGroup.setIoRatio(ioRatio);
      return eventLoopGroup;
    }
  }

  public static Class<? extends ServerChannel> serverChannel() {
    if (epollEnabled) {
      return EpollServerSocketChannel.class;
    } else {
      return NioServerSocketChannel.class;
    }
  }

  public static Class<? extends Channel> channel() {
    if (epollEnabled) {
      return EpollSocketChannel.class;
    } else {
      return NioSocketChannel.class;
    }
  }

  public static Class<? extends Channel> datagramChannel() {
    if (epollEnabled) {
      return EpollDatagramChannel.class;
    } else {
      return NioDatagramChannel.class;
    }
  }

  public static DatagramChannel datagramChannel(io.vertx.core.datagram.impl.InternetProtocolFamily family) {
    if (epollEnabled) {
      return new EpollDatagramChannel();
    } else {
      if (family == null) {
        return new NioDatagramChannel();
      } else {
        switch (family) {
          case IPv4:
            return new NioDatagramChannel(InternetProtocolFamily.IPv4);
          case IPv6:
            return new NioDatagramChannel(InternetProtocolFamily.IPv6);
          default:
            return new NioDatagramChannel();
        }
      }
    }
  }
}
