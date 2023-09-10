package io.vertx.core.eventbus.impl;

import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.Message;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.BitSet;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 @author Andrey Fink 2023-09-09
 @see io.vertx.core.eventbus.impl.MessageConsumerImpl */
public class MessageConsumerImplTest {

  Vertx vertx;

  @Before public void setUp () throws Exception{
    vertx = Vertx.vertx();
  }

  @After public void tearDown () throws Exception{
    vertx.close();
    vertx = null;
  }

  @Test public void testSimple () throws InterruptedException{
    var q = new LinkedBlockingQueue<Message<Integer>>();
    var eb = vertx.eventBus();
    Handler<Message<Integer>> add = q::add;

    var consumer = (MessageConsumerImpl<Integer>) eb.consumer("x.y.z", add);
    assertSame(add, consumer.getHandler());
    assertTrue(consumer.isRegistered());

    eb.send("x.y.z", 42);
    var msg = q.take();
    assertEquals(42, msg.body().intValue());
    assertTrue(msg.isSend());
    assertEquals("x.y.z", msg.address());
    assertNull(msg.replyAddress());

    consumer.handler(null);
    assertSame(add, consumer.getHandler());// not null! :-)
    assertFalse(consumer.isRegistered());

    // dev/null
    eb.send("x.y.z", 45);
    assertTrue(q.isEmpty());// but not used ^
  }

  @Test public void testNoHandler () throws InterruptedException{
    var eb = vertx.eventBus();

    var consumer = (MessageConsumerImpl<Integer>) eb.<Integer>consumer("x.y.z");
    assertNull(consumer.getHandler());
    assertFalse(consumer.isRegistered());

    eb.send("x.y.z", 42);

    consumer.handler(null);
    assertNull(consumer.getHandler());
    assertFalse(consumer.isRegistered());
  }

  static final int THREADS = 17;
  static final int MAX = 1_000_000*THREADS;

  @Test public void testHighload () throws InterruptedException{
    var eb = vertx.eventBus();

    var cnt = new AtomicInteger();
    var inFlight = new AtomicBoolean();
    var numbers = new BitSet(MAX);

    Handler<Message<Integer>> add = m -> {
      if (inFlight.get()){
        fail("concurrent call");
      }
      inFlight.set(true);
      int i = m.body();
      assertFalse(numbers.get(i));
      numbers.set(i);
      cnt.incrementAndGet();
      inFlight.set(false);
    };

    var consumer = (MessageConsumerImpl<Integer>) eb.consumer("MessageConsumerImplTest.testHighload", add);
    assertSame(add, consumer.getHandler());
    assertTrue(consumer.isRegistered());

    var pool = (ThreadPoolExecutor) Executors.newFixedThreadPool(THREADS);
    var startSignal = new CountDownLatch(1);

    IntStream.range(0, THREADS).forEach(idx ->pool.execute(()->{
      try { startSignal.await(); } catch (InterruptedException ignore){}

      for (int i = 0; i<1_000_000; i++){
        int n = idx * 1_000_000 + i;
        eb.send("MessageConsumerImplTest.testHighload", n);
      }
    }));

    Thread.sleep(200);// Time to start all threads. Not scientific, but less code than second CountDownLatch
    long t = System.nanoTime();
    startSignal.countDown();

    var runtime = Runtime.getRuntime();
    runtime.gc();
    System.out.println("Max memory: "+runtime.maxMemory()/1024/1024+", total = "+runtime.totalMemory()/1024/1024);

    while (cnt.get() < MAX){
      System.out.println("q = "+cnt+"\t p = "+consumer.getPendingQueueSize()+"\t mem = "+(runtime.totalMemory()-runtime.freeMemory())/1024/1024);
      Thread.sleep(500);
      runtime.gc();// low -Xmx => clean tmp objs
    }

    t = System.nanoTime() - t;

    System.out.println("q is full :-)");

    assertEquals(0, pool.getActiveCount());
    assertEquals(THREADS, pool.getCompletedTaskCount());
    pool.shutdownNow();

    assertEquals(MAX, cnt.get());
    assertEquals(MAX, numbers.size());

    System.out.println("Msg/sec = "+MAX*1_000_000_000L/t);
  }

  @Test public void testReply () throws InterruptedException, ExecutionException{
    var q = new LinkedBlockingQueue<Message<Integer>>();
    var eb = vertx.eventBus();
    Handler<Message<Integer>> add = q::add;

    var consumer = (MessageConsumerImpl<Integer>) eb.consumer("x.y.z", add);
    assertSame(add, consumer.getHandler());
    assertTrue(consumer.isRegistered());

    var requestFuture = eb.<Integer>request("x.y.z", 42);
    var msg = q.take();
    assertEquals(42, msg.body().intValue());
    assertTrue(msg.isSend());
    assertEquals("x.y.z", msg.address());
    assertNotNull(msg.replyAddress());

    msg.reply(2023);

    int recvdReplyBody = requestFuture.toCompletionStage().toCompletableFuture().get().body();
    assertEquals(2023, recvdReplyBody);
  }
}
