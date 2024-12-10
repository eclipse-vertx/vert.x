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
package io.vertx.tests.net;

import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.channel.embedded.EmbeddedChannel;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.internal.buffer.BufferInternal;
import io.vertx.core.internal.ContextInternal;
import io.vertx.core.internal.VertxInternal;
import io.vertx.core.net.NetClient;
import io.vertx.core.net.NetServer;
import io.vertx.core.net.NetSocket;
import io.vertx.core.internal.net.NetSocketInternal;
import io.vertx.core.net.impl.VertxConnection;
import io.vertx.core.net.impl.VertxHandler;
import io.vertx.core.transport.Transport;
import io.vertx.test.core.TestUtils;
import io.vertx.test.core.VertxTestBase;
import org.junit.Ignore;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiFunction;

import static org.junit.Assume.assumeTrue;

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
              ((VertxConnection)conn).flush();
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
      VertxConnection conn = (VertxConnection) so;
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
        if (!((VertxConnection)so).writeToChannel(buffer.getByteBuf())) {
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
        public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) {
          assertEquals(0, reentrant++);
          try {
            switch (msg.toString()) {
              case "msg1":
                // Fill outbound buffer
                assertTrue(ctx.channel().isWritable());
                ChannelFuture f = ctx.write(BufferInternal.buffer(TestUtils.randomAlphaString((int) ctx.channel().bytesBeforeUnwritable())).getByteBuf());
                assertFalse(ctx.channel().isWritable());
                // Flush to trigger writability change
                ctx.flush();
                assertEquals(TRANSPORT != Transport.IO_URING, ctx.channel().isWritable());
                break;
              case "msg2":
                testComplete();
                break;
            }
          } finally {
            reentrant--;
          }
        }
      });

      VertxConnection conn = (VertxConnection) so;
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

  @Test
  public void testClose() {
    AtomicBoolean shutdown = new AtomicBoolean();
    AtomicBoolean closed = new AtomicBoolean();
    EmbeddedChannel channel = channel((ctx, chctx) -> new VertxConnection(ctx, chctx) {
      @Override
      protected void handleEvent(Object event) {
        if ("test".equals(event)) {
          close();
        }
      }
      @Override
      protected void handleShutdown(Object reason, long timeout, TimeUnit unit, ChannelPromise promise) {
        shutdown.set(true);
      }
      @Override
      protected void handleClose(Object reason, ChannelPromise promise) {
        closed.set(true);
        promise.setSuccess();
      }
    });
    channel.pipeline().fireUserEventTriggered("test");
    assertFalse(shutdown.get());
    assertTrue(closed.get());
  }

  @Test
  public void testShutdownZeroDoesClose() {
    AtomicBoolean shutdown = new AtomicBoolean();
    AtomicBoolean closed = new AtomicBoolean();
    EmbeddedChannel channel = channel((ctx, chctx) -> new VertxConnection(ctx, chctx) {
      @Override
      protected void handleEvent(Object event) {
        if ("test".equals(event)) {
          shutdown(0L, TimeUnit.SECONDS);
        }
      }
      @Override
      protected void handleShutdown(Object reason, long timeout, TimeUnit unit, ChannelPromise promise) {
        shutdown.set(true);
      }
      @Override
      protected void handleClose(Object reason, ChannelPromise promise) {
        closed.set(true);
      }
    });
    channel.pipeline().fireUserEventTriggered("test");
    assertFalse(shutdown.get());
    assertTrue(closed.get());
  }

  @Ignore
  @Test
  public void testShutdownReentrantClose() {
    AtomicBoolean shutdown = new AtomicBoolean();
    AtomicBoolean closed = new AtomicBoolean();
    EmbeddedChannel channel = channel((ctx, chctx) -> new VertxConnection(ctx, chctx) {
      @Override
      protected void handleEvent(Object event) {
        if ("test".equals(event)) {
          shutdown(10L, TimeUnit.SECONDS);
        }
      }
      @Override
      protected void handleShutdown(Object reason, long timeout, TimeUnit unit, ChannelPromise promise) {
        shutdown.set(true);
        close(reason);
        assertTrue(closed.get());
      }
      @Override
      protected void handleClose(Object reason, ChannelPromise promise) {
        closed.set(true);
      }
    });
    channel.pipeline().fireUserEventTriggered("test");
    assertTrue(shutdown.get());
    assertTrue(closed.get());
  }

  @Test
  public void testShutdownTimeout() {
    AtomicInteger shutdown = new AtomicInteger();
    AtomicInteger closed = new AtomicInteger();
    EmbeddedChannel channel = channel((ctx, chctx) -> new VertxConnection(ctx, chctx) {
      @Override
      protected void handleEvent(Object event) {
        if ("test".equals(event)) {
          shutdown(100L, TimeUnit.MILLISECONDS);
        }
      }
      @Override
      protected void handleShutdown(Object reason, long timeout, TimeUnit unit, ChannelPromise promise) {
        shutdown.getAndIncrement();
        assertEquals(0L, closed.get());
        // Force run tasks at this stage since the task will be cancelled after by embedded channel close
        EmbeddedChannel a = (EmbeddedChannel) chctx.channel();
        a.advanceTimeBy(100, TimeUnit.MILLISECONDS);
        a.runPendingTasks();
        assertEquals(1L, closed.get());
      }
      @Override
      protected void handleClose(Object reason, ChannelPromise promise) {
        closed.getAndIncrement();
      }
    });
    channel.pipeline().fireUserEventTriggered("test");
    assertEquals(1L, shutdown.get());
    assertEquals(1L, closed.get());
  }

  private <C extends VertxConnection> EmbeddedChannel channel(BiFunction<ContextInternal, ChannelHandlerContext, C> connectionFactory) {
    return new EmbeddedChannel(VertxHandler.create(chctx -> {
      ContextInternal ctx = ((VertxInternal)vertx).createEventLoopContext(chctx.channel().eventLoop(), null, null);
      return connectionFactory.apply(ctx, chctx);
    }));
  }

  private class TestConnection extends VertxConnection {
    Handler<Message> handler;
    public TestConnection(ChannelHandlerContext chctx) {
      super(((VertxInternal)ConnectionBaseTest.this.vertx)
        .createEventLoopContext((EventLoop) chctx.executor(), null, null), chctx);
    }
    @Override
    protected void handleMessage(Object msg) {
      Handler<Message> h = handler;
      if (h != null) {
        h.handle((Message) msg);
      }
    }

    public void pause() {
      doPause();
    }

    public void resume() {
      chctx.executor().execute(this::doResume);
    }
  }

  static class Message {
    final String id;
    Message(String id) {
      this.id = id;
    }
  }

  static class MessageFactory {
    int seq = 0;
    Message next() {
      return new Message("msg-" + seq++);
    }
    Message[] next(int num) {
      Message[] messages = new Message[num];
      for (int i = 0;i < num;i++) {
        messages[i] = next();
      }
      return messages;
    }
  }

  @Test
  public void testDisableAutoReadWhenPaused() {
    List<Object> receivedMessages = new ArrayList<>();
    MessageFactory factory = new MessageFactory();
    EmbeddedChannel ch = new EmbeddedChannel();
    ChannelPipeline pipeline = ch.pipeline();
    pipeline.addLast(VertxHandler.create(chctx -> new TestConnection(chctx)));
    TestConnection connection = (TestConnection) pipeline.get(VertxHandler.class).getConnection();
    connection.handler = receivedMessages::add;
    connection.pause();
    ch.writeInbound((Object[])factory.next(8));
    assertEquals(Collections.emptyList(), receivedMessages);
    assertFalse(ch.config().isAutoRead());
  }

  @Test
  public void testConsolidatesFlushesWhenResuming() {
    MessageFactory factory = new MessageFactory();
    EmbeddedChannel ch = new EmbeddedChannel() {
    };
    ChannelPipeline pipeline = ch.pipeline();
    pipeline.addLast(VertxHandler.create(chctx -> new TestConnection(chctx)));
    TestConnection connection = (TestConnection) pipeline.get(VertxHandler.class).getConnection();
    connection.pause();
    ch.writeInbound((Object[])factory.next(8));
    assertFalse(ch.config().isAutoRead());
    connection.resume();
    assertTrue(ch.hasPendingTasks());
    connection.handler = msg -> {
      connection.writeToChannel(msg);
      // Try to consume the messages (if it was flushed)
      while (ch.readOutbound() != null) {

      }
    };
    ch.runPendingTasks();
    List<Object> flushed = new ArrayList<>();
    Object outbound;
    while ((outbound = ch.readOutbound()) != null) {
      flushed.add(outbound);
    }
    assertEquals(8, flushed.size());
    assertTrue(ch.config().isAutoRead());
  }

  @Test
  public void testPauseWhenResuming() {
    MessageFactory factory = new MessageFactory();
    EmbeddedChannel ch = new EmbeddedChannel() {
    };
    ChannelPipeline pipeline = ch.pipeline();
    pipeline.addLast(VertxHandler.create(chctx -> new TestConnection(chctx)));
    TestConnection connection = (TestConnection) pipeline.get(VertxHandler.class).getConnection();
    connection.pause();
    ch.writeInbound((Object[])factory.next(4));
    connection.resume();
    assertTrue(ch.hasPendingTasks());
    AtomicInteger count = new AtomicInteger();
    connection.handler = event -> {
      if (count.incrementAndGet() == 2) {
        connection.pause();
      }
    };
    ch.runPendingTasks();
    assertEquals(2, count.get());
  }

  @Test
  public void testResumeWhenReadInProgress() {
    MessageFactory factory = new MessageFactory();
    EmbeddedChannel ch = new EmbeddedChannel();
    ChannelPipeline pipeline = ch.pipeline();
    pipeline.addLast(VertxHandler.create(chctx -> new TestConnection(chctx)));
    TestConnection connection = (TestConnection) pipeline.get(VertxHandler.class).getConnection();
    AtomicInteger count = new AtomicInteger();
    connection.handler = event -> count.incrementAndGet();
    connection.pause();
    pipeline.fireChannelRead(factory.next());
    assertEquals(0, count.get());
    Object expected = new Object();
    connection.write(expected, false, ch.newPromise());
    connection.resume();
    assertEquals(0, count.get());
    assertTrue(ch.hasPendingTasks());
    ch.runPendingTasks();
    assertEquals(1, count.get());
    Object outbound = ch.readOutbound();
    assertNull(outbound);
    pipeline.fireChannelReadComplete();
    outbound = ch.readOutbound();
    assertSame(expected, outbound);
  }
}
