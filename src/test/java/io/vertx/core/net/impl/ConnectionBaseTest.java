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
package io.vertx.core.net.impl;

import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.ChannelPromise;
import io.vertx.core.Context;
import io.vertx.core.net.NetClient;
import io.vertx.core.net.NetServer;
import io.vertx.test.core.VertxTestBase;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class ConnectionBaseTest extends VertxTestBase {

  @Test
  public void testQueueMessageFromEvent() {
    NetClient client = vertx.createNetClient();
    NetServer server = vertx.createNetServer();
    try {
      server.connectHandler(so -> {
        NetSocketInternal conn = (NetSocketInternal) so;
        ChannelHandlerContext ctx = conn.channelHandlerContext();
        ChannelPipeline pipeline = ctx.pipeline();
        List<String> order = new ArrayList<>();
        pipeline.addBefore("handler", "myhandler", new ChannelDuplexHandler() {
          @Override
          public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
            if (msg instanceof String) {
              String s = (String) msg;
              order.add(s);
              if ("msg1".equals(s)) {
                // Write a message why there are two messages queued on the connection
                conn.writeMessage("msg3");
              }
              if (order.size() == 3) {
                vertx.runOnContext(v -> {
                  assertEquals(Arrays.asList("msg1", "msg2", "msg3"), order);
                  testComplete();
                });
              }
            } else {
              super.write(ctx, msg, promise);
            }
          }
        });
        executeAsyncTask(() -> {
          conn.writeMessage("msg1");
          conn.writeMessage("msg2");
        });
      });
      server.listen(1234, "localhost", onSuccess(s -> {
        client.connect(1234, "localhost", onSuccess(so -> {
        }));
      }));
      await();
    } finally {
      server.close();
      client.close();
    }
  }

  @Test
  public void testQueueFlushFromEventLoop() {
    NetClient client = vertx.createNetClient();
    NetServer server = vertx.createNetServer();
    try {
      server.connectHandler(so -> {
        ConnectionBase conn = (ConnectionBase) so;
        ChannelHandlerContext ctx = conn.channelHandlerContext();
        ChannelPipeline pipeline = ctx.pipeline();
        List<String> order = new ArrayList<>();
        Runnable checkOrder = () -> {
          if (order.size() == 3) {
            vertx.runOnContext(v -> {
              assertEquals(Arrays.asList("msg1", "msg2", "flush"), order);
              testComplete();
            });
          }
        };
        pipeline.addBefore("handler", "myhandler", new ChannelDuplexHandler() {
          @Override
          public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
            if (msg instanceof String) {
              String s = (String) msg;
              order.add(s);
              if ("msg1".equals(s)) {
                // Flush a message why there are two messages queued on the connection
                conn.flush();
              }
              checkOrder.run();
            } else {
              super.write(ctx, msg, promise);
            }
          }
          @Override
          public void flush(ChannelHandlerContext ctx) throws Exception {
            order.add("flush");
            checkOrder.run();
            super.flush(ctx);
          }
        });
        executeAsyncTask(() -> {
          conn.writeToChannel("msg1");
          conn.writeToChannel("msg2");
        });
      });
      server.listen(1234, "localhost", onSuccess(s -> {
        client.connect(1234, "localhost", onSuccess(so -> {
        }));
      }));
      await();
    } finally {
      server.close();
      client.close();
    }
  }

  private void executeAsyncTask(Runnable runnable) {
    assertTrue(Context.isOnEventLoopThread());
    CountDownLatch latch = new CountDownLatch(1);
    new Thread(() -> {
      runnable.run();
      latch.countDown();
    }).start();
    try {
      latch.await(20, TimeUnit.SECONDS);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }
}
