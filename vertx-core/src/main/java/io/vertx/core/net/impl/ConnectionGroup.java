/*
 * Copyright (c) 2011-2025 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package io.vertx.core.net.impl;

import io.netty.channel.Channel;
import io.netty.channel.group.ChannelGroupFuture;
import io.netty.channel.group.DefaultChannelGroup;
import io.netty.util.concurrent.EventExecutor;
import io.netty.util.concurrent.ScheduledFuture;
import io.vertx.core.Completable;
import io.vertx.core.Future;
import io.vertx.core.internal.CloseSequence;

import java.util.concurrent.TimeUnit;

/**
 * Extend a {@link io.netty.channel.group.ChannelGroup} to support a shutdown sequence.
 */
public class ConnectionGroup extends DefaultChannelGroup {

  private final EventExecutor executor;
  private final CloseSequence closeSequence;
  private ChannelGroupFuture graceFuture;
  private ShutdownEvent shutdown;

  public ConnectionGroup(EventExecutor executor) {
    super(executor, true);

    //
    // 3 steps close sequence
    // 2: a {@link CloseEvent} event is broadcast to each channel, channels should react accordingly
    // 1: grace period completed when all channels are inactive or the shutdown timeout is fired
    // 0: sockets are closed
    CloseSequence closeSequence = new CloseSequence(this::close, this::grace, this::shutdown);

    this.executor = executor;
    this.closeSequence = closeSequence;
  }

  public final Future<Void> closeFuture() {
    return closeSequence.future();
  }

  public final boolean isStarted() {
    return closeSequence.started();
  }

  public final Future<Void> shutdown(long timeout, TimeUnit unit) {
    shutdown = new ShutdownEvent(timeout, unit);
    return closeSequence.close();
  }

  private void shutdown(Completable<Void> completion) {
    graceFuture = newCloseFuture();
    handleShutdown((result, failure) -> {
      broadcastShutdownEvent();
      completion.succeed();
    });
  }

  protected void handleShutdown(Completable<Void> completion) {
    completion.succeed();
  }

  private void broadcastShutdownEvent() {
    for (Channel ch : this) {
      ch.pipeline().fireUserEventTriggered(shutdown);
    }
  }

  private void grace(Completable<Void> completion) {
    if (shutdown.timeout() > 0L) {
      ScheduledFuture<?> timeout = executor.schedule(() -> handleGrace(completion), shutdown.timeout(), shutdown.timeUnit());
      graceFuture.addListener(future -> {
        if (timeout.cancel(true)) {
          handleGrace(completion);
        }
      });
    } else {
      handleGrace(completion);
    }
  }

  protected void handleGrace(Completable<Void> completion) {
    completion.succeed();
  }

  private void close(Completable<Void> completion) {
    ChannelGroupFuture f = this.close();
    f.addListener(future -> {
      handleClose(completion);
    });
  }

  protected void handleClose(Completable<Void> completion) {
    completion.succeed();
  }

}
