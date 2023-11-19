package io.vertx.core.eventbus.impl;

import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.eventbus.Message;
import io.vertx.core.impl.ContextInternal;
import io.vertx.core.impl.VertxInternal;
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
  EventBus eb;

  @Before
  public void setUp () throws Exception{
    vertx = Vertx.vertx();
    eb = vertx.eventBus();
  }

  @After
  public void tearDown () throws Exception{
    vertx.close();
    eb = null;  vertx = null;
  }

  @Test
  public void testSimple () throws InterruptedException{
    var q = new LinkedBlockingQueue<Message<Integer>>();
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

  @Test
  public void testNoHandler () throws InterruptedException{
    var consumer = (MessageConsumerImpl<Integer>) eb.<Integer>consumer("x.y.z");
    assertNull(consumer.getHandler());
    assertFalse(consumer.isRegistered());

    eb.send("x.y.z", 42);

    consumer.handler(null);
    assertNull(consumer.getHandler());
    assertFalse(consumer.isRegistered());

    assertFalse(consumer.doReceive(null));
  }

  static final int THREADS = 14; // < 1.2G RAM
  static final int MAX = 1_000_000*THREADS;

  @Test
  public void testHighload () throws InterruptedException{
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

  @Test
  public void testReply () throws InterruptedException, ExecutionException{
    var q = new LinkedBlockingQueue<Message<Integer>>();
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

  @Test
  public void testHandlerRace () throws InterruptedException{
    var pool = (ThreadPoolExecutor) Executors.newFixedThreadPool(THREADS);
    var startSignal = new CountDownLatch(1);

    var consumer = eb.consumer("testHandlerRace");
    Handler<Message<Object>> handler = m->{};
    var failedToRegister = new AtomicInteger();
    var failedToUnregister = new AtomicInteger();


    IntStream.range(0, 100).forEach(idx -> pool.execute(()->{
      try { startSignal.await(); } catch (InterruptedException ignore){}

      for (int i = 0; i<100_000; i++){
        if (i%2 == 0){
          synchronized(consumer){
            Future<Void> f = consumer.unregister();
            if (consumer.isRegistered()){
              failedToUnregister.incrementAndGet();// must be unregistered after unregister()
            }
            if (!f.isComplete() || f.failed()){
              failedToUnregister.incrementAndGet();
            }
          }

        } else {
          final boolean registered;
          final Future<Void> f;
          final Future<Void> f2;
          synchronized(consumer){
            registered = consumer.isRegistered();
            f = consumer.completion();
            consumer.handler(handler);// register
            if (!consumer.isRegistered()){
              failedToRegister.incrementAndGet();
            }
            f2 = consumer.completion();
          }
          try {
            if (registered){
              f.toCompletionStage().toCompletableFuture().get();//ok completed
            }
          } catch (Exception e){
            failedToRegister.incrementAndGet();
          }
          try {
            f2.toCompletionStage().toCompletableFuture().get();//ok completed
          } catch (Exception e){
            failedToRegister.incrementAndGet();
          }
        }
      }//f
    }));

    startSignal.countDown();
    while (pool.getActiveCount() != 0){
      System.out.println("Active threads = "+pool.getActiveCount());
      Thread.sleep(500);
    }
    assertEquals(0, failedToRegister.get());
    assertEquals(0, failedToUnregister.get());
  }

  @Test
  public void testUnregisterDuringRegister () throws ExecutionException, InterruptedException{
    //1. normal flow: register > registered > unregister > unregistered
    var consumer = (MessageConsumerImpl<Object>) eb.consumer("testUnregisterDuringRegister");
    assertFalse(consumer.isRegistered());
    var f = consumer.unregister();
    f.toCompletionStage().toCompletableFuture().get();// must complete with Void (null), without exception
    assertFalse(consumer.isRegistered());

    Handler<Message<Object>> messageHandler = m->{ };
    var me = consumer.handler(messageHandler);
    assertSame(me, consumer);
    assertSame(messageHandler, consumer.getHandler());
    assertTrue(consumer.isRegistered());
    assertNull(consumer.completion().toCompletionStage().toCompletableFuture().get());// must complete with Void (null), without exception
    assertNull(consumer.completion().cause());
    assertNull(consumer.completion().result());// Void == null

    f = consumer.unregister();
    assertFalse(consumer.isRegistered());
    assertNull(f.toCompletionStage().toCompletableFuture().get());// must complete with Void (null), without exception
    assertFalse(consumer.isRegistered());

    //2. long register e.g. cluster: register > unregister > register failed / unregistered

    EventBusImpl registrationNeverCompletesBus = new EventBusImpl((VertxInternal) vertx) {
      @Override protected <T> void onLocalRegistration (HandlerHolder<T> handlerHolder, Promise<Void> promise){
        assertNotNull(promise);
      }
    };
    registrationNeverCompletesBus.start(((ContextInternal) vertx.getOrCreateContext()).promise());
    consumer = (MessageConsumerImpl<Object>) registrationNeverCompletesBus.consumer("testUnregisterDuringRegister", m->{});
    assertTrue(consumer.isRegistered());
    var oldResult = consumer.completion();
    assertFalse(oldResult.isComplete());// never completes

    f = consumer.unregister();// unregister during registration
    assertFalse(consumer.isRegistered());
    assertFalse(consumer.completion().isComplete());// new `result`
    assertNull(f.toCompletionStage().toCompletableFuture().get());// successful unregister

    assertTrue(oldResult.isComplete());// completed with exception ^ in unregister()
    assertEquals("Future{cause=Consumer unregistered before registration completed}", oldResult.toString());
    assertEquals("io.vertx.core.impl.NoStackTraceThrowable: Consumer unregistered before registration completed", oldResult.cause().toString());
  }
}
