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

import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

import java.nio.channels.SocketChannel;
import java.util.List;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class VertxNioServerSocketChannel extends NioServerSocketChannel {

  private static final Logger log = LoggerFactory.getLogger(VertxNioServerSocketChannel.class);

  @Override
  protected int doReadMessages(List<Object> buf) throws Exception {
    SocketChannel ch = javaChannel().accept();

    try {
      if (ch != null) {
        buf.add(new VertxNioSocketChannel(this, ch));
        return 1;
      }
    } catch (Throwable t) {
      log.warn("Failed to create a new channel from an accepted socket.", t);

      try {
        ch.close();
      } catch (Throwable t2) {
        log.warn("Failed to close a socket.", t2);
      }
    }

    return 0;
  }
}
