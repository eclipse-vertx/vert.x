/*
 * Copyright (c) 2011-2016 The original author or authors
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
package org.vertx.java.core.impl;

import java.util.concurrent.ThreadFactory;

import io.netty.channel.Channel;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.ServerChannel;
import io.netty.channel.epoll.Epoll;
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

/**
 * This class enables netty epoll support if it is available on your system
 *
 */
public class NettySupport {

	private static final boolean epollSupported = "true".equals(System.getProperty("vertx.epoll.try", "false")) && Epoll.isAvailable();

	public static final EventLoopGroup eventLoopGroup(int size, ThreadFactory tf) {
		if (epollSupported) {
			return new EpollEventLoopGroup(size, tf);
		} else {
			return new NioEventLoopGroup(size, tf);
		}
	}

	public static final Class<? extends ServerChannel> serverChannel() {
		if (epollSupported) {
			return EpollServerSocketChannel.class;
		} else {
			return NioServerSocketChannel.class;
		}
	}

	public static final Class<? extends Channel> channel() {
		if (epollSupported) {
			return EpollSocketChannel.class;
		} else {
			return NioSocketChannel.class;
		}
	}

	public static Class<? extends Channel> datagramChannel() {
		return EpollDatagramChannel.class;
	}

	public static DatagramChannel datagramChannel(org.vertx.java.core.datagram.InternetProtocolFamily family) {
		if (epollSupported) {
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
