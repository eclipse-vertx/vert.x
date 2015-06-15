/*
 * Copyright 2014 Red Hat, Inc.
 *
 *  All rights reserved. This program and the accompanying materials
 *  are made available under the terms of the Eclipse Public License v1.0
 *  and Apache License v2.0 which accompanies this distribution.
 *
 *  The Eclipse Public License is available at
 *  http://www.eclipse.org/legal/epl-v10.html
 *
 *  The Apache License v2.0 is available at
 *  http://www.opensource.org/licenses/apache2.0.php
 *
 *  You may elect to redistribute this code under either of these licenses.
 */

package io.vertx.core.net.impl;

import io.netty.channel.Channel;
import io.netty.channel.socket.nio.NioSocketChannel;

import java.nio.channels.SocketChannel;

/**
 * We provide this class so we can store a reference to the connection on it.
 *
 * This means we don't have to do a lookup in the connectionMap for every message received which improves
 * performance.
 *
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class VertxNioSocketChannel<C> extends NioSocketChannel {

  public C conn;

  public VertxNioSocketChannel(Channel parent, SocketChannel socket) {
    super(parent, socket);
  }

  public VertxNioSocketChannel() {
  }
}
