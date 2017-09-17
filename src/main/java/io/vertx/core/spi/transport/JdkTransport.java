/*
 * Copyright (c) 2011-2017 The original author or authors
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
package io.vertx.core.spi.transport;

import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.DatagramChannel;
import io.netty.channel.socket.InternetProtocolFamily;
import io.netty.channel.socket.ServerSocketChannel;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioDatagramChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.vertx.core.spi.Transport;

import java.util.concurrent.ThreadFactory;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class JdkTransport implements Transport {

  public static final JdkTransport INSTANCE = new JdkTransport();

  private JdkTransport() {
  }

  @Override
  public Throwable unavailabilityCause() {
    return null;
  }

  @Override
  public boolean isAvailable() {
    return true;
  }

  @Override
  public EventLoopGroup eventLoopGroup(int nThreads, ThreadFactory threadFactory, int ioRatio) {
    NioEventLoopGroup eventLoopGroup = new NioEventLoopGroup(nThreads, threadFactory);
    eventLoopGroup.setIoRatio(ioRatio);
    return eventLoopGroup;
  }

  @Override
  public DatagramChannel datagramChannel() {
    return new NioDatagramChannel();
  }

  @Override
  public DatagramChannel datagramChannel(InternetProtocolFamily family) {
    switch (family) {
      case IPv4:
        return new NioDatagramChannel(InternetProtocolFamily.IPv4);
      case IPv6:
        return new NioDatagramChannel(InternetProtocolFamily.IPv6);
      default:
        throw new UnsupportedOperationException();
    }
  }

  @Override
  public SocketChannel socketChannel() {
    return new NioSocketChannel();
  }

  @Override
  public ServerSocketChannel serverSocketChannel() {
    return new NioServerSocketChannel();
  }
}
