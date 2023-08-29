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

import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.ChannelPromise;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.buffer.impl.BufferInternal;
import io.vertx.core.impl.ContextInternal;
import io.vertx.core.net.NetClient;
import io.vertx.core.net.NetServer;
import io.vertx.core.net.NetSocket;
import io.vertx.test.core.TestUtils;
import io.vertx.test.core.VertxTestBase;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class ConnectionBaseTest extends VertxTestBase {

  private NetClient client;
  private NetServer server;
  private volatile Handler<NetSocketInternal> connectHandler;

  @Override
  public void before() throws Exception {
    super.before();
    client = vertx.createNetClient();
    server = vertx.createNetServer().connectHandler(so -> {
      Handler<NetSocketInternal> handler = connectHandler;
      if (handler != null) {
        handler.handle((NetSocketInternal) so);
      } else {
        so.close();
      }
    });
    awaitFuture(server.listen(1234, "localhost"));
  }

  @Override
  protected void tearDown() throws Exception {
    super.tearDown();
    client = null;
    server = null;
  }

  @Test
  public void testQueueMessagesMissMessage() throws Exception {
    disableThreadChecks();
    CountDownLatch latch1 = new CountDownLatch(1);
    CountDownLatch latch2 = new CountDownLatch(1);
    connectHandler = conn -> {
      ChannelHandlerContext chctx = conn.channelHandlerContext();
      ChannelPipeline pipeline = chctx.pipeline();
      List<String> received = new ArrayList<>();
      pipeline.addBefore("handler", "myhandler", new ChannelDuplexHandler() {
        @Override
        public void write(ChannelHandlerContext chctx, Object msg, ChannelPromise promise) throws Exception {
          if (msg instanceof String) {
            received.add((String)msg);
          } else {
            super.write(chctx, msg, promise);
          }
        }
        int flushCount;
        @Override
        public void flush(ChannelHandlerContext chctx) throws Exception {
          super.flush(chctx);
          switch (++flushCount) {
            case 1:
              assertEquals(List.of("msg-1"), received);
              latch1.countDown();
              awaitLatch(latch2);
              break;
            case 2:
              assertEquals(List.of("msg-1", "msg-2"), received);
              testComplete();
              break;
            default:
              break;
          }
        }
      });
      executeAsyncTask(() -> {
        conn.writeMessage("msg-1");
        try {
          awaitLatch(latch1);
        } catch (InterruptedException e) {
          fail(e);
        }
        conn.writeMessage("msg-2");
        latch2.countDown();
      });
    };
    awaitFuture(client.connect(1234, "localhost"));
    await();
  }

  @Test
  public void testQueueMessageFromInnerWrite() throws Exception {
    connectHandler = conn -> {
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
      executeAsyncTaskAndAwait(() -> {
        conn.writeMessage("msg1");
        conn.writeMessage("msg2");
      });
    };
    awaitFuture(client.connect(1234, "localhost"));
    await();
  }

  @Test
  public void testQueueFlushFromEventLoop() throws Exception {
    connectHandler = conn -> {
      ChannelHandlerContext ctx = conn.channelHandlerContext();
      ChannelPipeline pipeline = ctx.pipeline();
      List<String> order = new ArrayList<>();
      Runnable checkOrder = () -> {
        vertx.runOnContext(v -> {
          assertEquals(Arrays.asList("msg1", "msg2", "flush"), order);
          testComplete();
        });
      };
      pipeline.addBefore("handler", "myhandler", new ChannelDuplexHandler() {
        int flushes;
        @Override
        public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
          if (msg instanceof String) {
            String s = (String) msg;
            order.add(s);
            if ("msg1".equals(s)) {
              // Flush a message why there are two messages queued on the connection
              ((ConnectionBase)conn).flush();
            }
          } else {
            super.write(ctx, msg, promise);
          }
        }
        @Override
        public void flush(ChannelHandlerContext ctx) throws Exception {
          if (flushes++ < 1) {
            order.add("flush");
            if (flushes == 1) {
              checkOrder.run();
            }
          }
          super.flush(ctx);
        }
      });
      CountDownLatch latch = new CountDownLatch(1);
      executeAsyncTaskAndAwait(() -> {
        conn.writeMessage("msg1");
        conn.writeMessage("msg2");
        latch.countDown();
      });
      try {
        latch.await(20, TimeUnit.SECONDS);
      } catch (InterruptedException e) {
        throw new RuntimeException(e);
      }
    };
    awaitFuture(client.connect(1234, "localhost"));
    await();
  }

  @Test
  public void testOverflowDrain() throws Exception {
    BufferInternal chunk = BufferInternal.buffer(TestUtils.randomAlphaString(1024 * 16));
    CompletableFuture<Void> drain = new CompletableFuture<>();
    connectHandler = so -> {
      ContextInternal ctx = (ContextInternal) vertx.getOrCreateContext();
      ConnectionBase conn = (ConnectionBase) so;
      int num = 0;
      while (conn.writeToChannel(chunk.getByteBuf())) {
        num++;
      }
      conn.writeToChannel(Unpooled.EMPTY_BUFFER, future -> {
        ctx.emit(v -> testComplete());
      });
      drain.complete(null);
    };
    client.connect(1234, "localhost")
      .onComplete(onSuccess(so -> {
        so.pause();
        drain.whenComplete((v, e) -> {
          so.resume();
        });
      }));
    await();
  }

  private void fill(NetSocketInternal so, BufferInternal buffer, Handler<Void> cont) {
    Runnable saturate = () -> {
      while (true) {
        if (!((ConnectionBase)so).writeToChannel(buffer.getByteBuf())) {
          break;
        }
      }
    };
    long id = vertx.setTimer(1000, id_ -> {
      saturate.run();
      cont.handle(null);
    });
    saturate.run();
    so.drainHandler(v -> {
      if (vertx.cancelTimer(id)) {
        fill(so, buffer, cont);
      } else {
        fail();
      }
    });
  }

  @Test
  public void testFailedQueueMessages() throws Exception {
    BufferInternal buffer = BufferInternal.buffer(TestUtils.randomAlphaString(16 * 1024));
    CompletableFuture<Void> latch = new CompletableFuture<>();
    connectHandler = conn -> {
      fill(conn, buffer, v1 -> {
        System.out.println("write msg " + conn.writeQueueFull());
        Future<Void> fut = conn.write(buffer);
        fut.onComplete(onFailure(v2 -> {
          testComplete();
        }));
        latch.complete(null);
      });
    };
    NetSocket so = awaitFuture(client.connect(1234, "localhost"));
    so.pause();
    latch.whenComplete((v, err) -> {
      so.close();
    });
    await();
  }

  @Test
  public void testDrainReentrancy() throws Exception {
    connectHandler = so -> {
      ChannelHandlerContext chctx = so.channelHandlerContext();
      chctx.pipeline().addBefore("handler", "myhandler", new ChannelDuplexHandler() {
        int reentrant;
        @Override
        public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
          assertEquals(0, reentrant++);
          switch (msg.toString()) {
            case "msg1":
              // Fill outbound buffer
              assertTrue(ctx.channel().isWritable());
              ctx.write(BufferInternal.buffer(TestUtils.randomAlphaString((int)ctx.channel().bytesBeforeUnwritable())).getByteBuf());
              assertFalse(ctx.channel().isWritable());
              // Flush to trigger writability change
              ctx.flush();
              assertTrue(ctx.channel().isWritable());
              break;
            case "msg2":
              testComplete();
              break;
          }
          reentrant--;
        }
      });

      ConnectionBase conn = (ConnectionBase) so;
      CountDownLatch latch = new CountDownLatch(1);
      executeAsyncTask(() -> {
        conn.writeToChannel("msg1");
        conn.writeToChannel("msg2");
        latch.countDown();
      });
      try {
        awaitLatch(latch);
      } catch (InterruptedException e) {
        fail(e);
      }
    };
    NetSocket so = awaitFuture(client.connect(1234, "localhost"));
    await();
  }

  @Test
  public void testConsolidateFlushInDrain() throws Exception {
    connectHandler = conn -> {
      ChannelHandlerContext ctx = conn.channelHandlerContext();
      ChannelPipeline pipeline = ctx.pipeline();
      pipeline.addBefore("handler", "myhandler", new ChannelDuplexHandler() {
        int flushes;
        int writes;
        @Override
        public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) {
          writes++;
        }
        @Override
        public void flush(ChannelHandlerContext ctx) throws Exception {
          flushes++;
          if (writes == 2) {
            assertEquals(1, flushes);
            testComplete();
          }
        }
      });
      CountDownLatch latch = new CountDownLatch(1);
      executeAsyncTaskAndAwait(() -> {
        conn.writeMessage("msg1");
        conn.writeMessage("msg2");
        latch.countDown();
      });
      try {
        latch.await(20, TimeUnit.SECONDS);
      } catch (InterruptedException e) {
        throw new RuntimeException(e);
      }
    };
    awaitFuture(client.connect(1234, "localhost"));
    await();
  }

  @Test
  public void testWriteQueueDrain() throws Exception {
    CountDownLatch latch = new CountDownLatch(1);
    BufferInternal buffer = BufferInternal.buffer(TestUtils.randomAlphaString(1024));
    connectHandler = conn -> {
      conn.handler(ping -> {
        fill(conn, buffer, v1 -> {
          latch.countDown();
          conn.drainHandler(v2 -> {
            testComplete();
          });
        });
      });
    };
    NetSocket so = awaitFuture(client.connect(1234, "localhost"));
    so.pause();
    so.write("ping");
    awaitLatch(latch);
    so.resume();
    await();
  }

  private CountDownLatch executeAsyncTask(Runnable runnable) {
    assertTrue(Context.isOnEventLoopThread());
    CountDownLatch latch = new CountDownLatch(1);
    new Thread(() -> {
      runnable.run();
      latch.countDown();
    }).start();
    return latch;
  }

  private void executeAsyncTaskAndAwait(Runnable runnable) {
    try {
      executeAsyncTask(runnable).await(20, TimeUnit.SECONDS);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }
}
