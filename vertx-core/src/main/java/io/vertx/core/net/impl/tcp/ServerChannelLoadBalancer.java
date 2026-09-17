/*
 * Copyright (c) 2011-2019 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package io.vertx.core.net.impl.tcp;

import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoop;
import io.vertx.core.Handler;
import io.vertx.core.net.impl.VertxEventLoopGroup;

/**
 * A channel server load balancer that distributes channel processing to a list of workers.
 *
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
class ServerChannelLoadBalancer extends ChannelInitializer<Channel> {

  private final VertxEventLoopGroup workers;

  ServerChannelLoadBalancer() {
    this.workers = new VertxEventLoopGroup();
  }

  public VertxEventLoopGroup workers() {
    return workers;
  }

  @Override
  protected void initChannel(Channel ch) {
    Handler<Channel> handler = chooseInitializer(ch.eventLoop());
    if (handler == null) {
      ch.close();
    } else {
      handler.handle(ch);
    }
  }

  private Handler<Channel> chooseInitializer(EventLoop eventLoop) {
    return workers.chooseHandler(eventLoop);
  }

  public void addWorker(EventLoop eventLoop, Handler<Channel> handler) {
    workers.addHandler(eventLoop, handler);
  }

  public boolean removeWorker(EventLoop worker, Handler<Channel> handler) {
    return workers.removeHandler(worker, handler);
  }
}
