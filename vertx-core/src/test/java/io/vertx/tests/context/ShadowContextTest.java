package io.vertx.tests.context;

import io.netty.channel.EventLoop;
import io.vertx.core.*;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.http.*;
import io.vertx.core.impl.LocalSeq;
import io.vertx.core.impl.ShadowContext;
import io.vertx.core.internal.ContextInternal;
import io.vertx.core.internal.VertxBootstrap;
import io.vertx.core.internal.VertxInternal;
import io.vertx.core.net.NetClient;
import io.vertx.core.net.NetServer;
import io.vertx.core.spi.context.storage.AccessMode;
import io.vertx.core.spi.context.storage.ContextLocal;
import io.vertx.core.spi.tracing.SpanKind;
import io.vertx.test.core.AsyncTestBase;
import io.vertx.test.faketracer.FakeTracer;
import io.vertx.test.faketracer.Span;
import org.junit.Assert;
import org.junit.Test;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public class ShadowContextTest extends AsyncTestBase {

  ContextLocal<Object> contextLocal;
  VertxInternal callerVertx;
  VertxInternal calleeVertx;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    contextLocal = ContextLocal.registerLocal(Object.class);
    callerVertx = (VertxInternal) VertxBootstrap.create().init().enableShadowContext(true).vertx();
    calleeVertx = (VertxInternal) Vertx.vertx();
    disableThreadChecks();
  }

  @Override
  protected void tearDown() throws Exception {
    awaitFuture(calleeVertx.close());
    awaitFuture(callerVertx.close());
    LocalSeq.reset();
    super.tearDown();
  }

  @Test
  public void testGetOrCreateEventLoop() {
    ContextInternal callerCtx = callerVertx.createEventLoopContext();
    testGetOrCreate(callerCtx);
  }

  @Test
  public void testGetOrCreateWorker() {
    ContextInternal callerCtx = callerVertx.createWorkerContext();
    testGetOrCreate(callerCtx);
  }

  private void testGetOrCreate(ContextInternal callerCtx) {
    callerCtx.runOnContext(v1 -> {
      ContextInternal calleeCtx = calleeVertx.getOrCreateContext();
      assertSame(callerCtx, callerVertx.getOrCreateContext());
      assertNotSame(calleeCtx, callerCtx);
      assertNotSame(calleeCtx.nettyEventLoop(), callerCtx.nettyEventLoop());
      assertEquals(ThreadingModel.EXTERNAL, calleeCtx.threadingModel());
      calleeCtx.runOnContext(v2 -> {
        assertTrue(callerCtx.inThread());
        assertSame(calleeCtx, calleeVertx.getOrCreateContext());
        assertSame(callerCtx, callerVertx.getOrCreateContext());
        testComplete();
      });
    });
    await();
  }

  @Test
  public void testGetOrCreateDefaultBehavior() {
    VertxInternal callerVertx = (VertxInternal) Vertx.vertx();
    try {
      ContextInternal callerCtx = callerVertx.getOrCreateContext();
      callerCtx.runOnContext(v1 -> {
        Thread expected = Thread.currentThread();
        ContextInternal noncalleeCtx = calleeVertx.getOrCreateContext();
        assertSame(callerCtx, callerVertx.getOrCreateContext());
        assertNotSame(noncalleeCtx, callerCtx);
        assertNotSame(noncalleeCtx.nettyEventLoop(), callerCtx.nettyEventLoop());
        assertEquals(ThreadingModel.EVENT_LOOP, noncalleeCtx.threadingModel());
        noncalleeCtx.runOnContext(v2 -> {
          assertNotSame(expected, Thread.currentThread());
          assertSame(noncalleeCtx, calleeVertx.getOrCreateContext());
          assertNotSame(callerCtx, callerVertx.getOrCreateContext());
          testComplete();
        });
      });
      await();
    } finally {
      callerVertx.close();
    }
  }

  @Test
  public void testEmitFromOriginatingThread() {
    ContextInternal callerCtx = callerVertx.getOrCreateContext();
    callerCtx.runOnContext(v1 -> {
      ContextInternal calleeCtx = calleeVertx.getOrCreateContext();
      AtomicBoolean inThread = new AtomicBoolean(true);
      calleeCtx.emit(v2 -> {
        assertSame(calleeCtx, Vertx.currentContext());
        assertTrue(inThread.get());
        testComplete();
      });
      inThread.set(false);
    });
    await();
  }

  @Test
  public void testEmitFromEventLoop() {
    Context eventLoop = calleeVertx.getOrCreateContext();
    ContextInternal callerCtx = callerVertx.getOrCreateContext();
    callerCtx.runOnContext(v1 -> {
      ContextInternal calleeCtx = calleeVertx.getOrCreateContext();
      AtomicBoolean inThread = new AtomicBoolean(true);
      eventLoop.runOnContext(v -> {
        calleeCtx.emit(v2 -> {
          assertSame(calleeCtx, Vertx.currentContext());
          assertWaitUntil(() -> !inThread.get());
          testComplete();
        });
        inThread.set(false);
      });
    });
    await();
  }

  @Test
  public void testThreadingModel() {
    ContextInternal callerCtx = callerVertx.getOrCreateContext();
    callerCtx.runOnContext(v1 -> {
      ContextInternal calleeCtx = calleeVertx.getOrCreateContext();
      assertEquals(ThreadingModel.EXTERNAL, calleeCtx.threadingModel());
      testComplete();
    });
    await();
  }

  @Test
  public void testPiggyBack() {
    Context callerCtx = callerVertx.getOrCreateContext();
    callerCtx.runOnContext(v1 -> {
      calleeVertx.runOnContext(v2 -> {
        Context shadowContext = calleeVertx.getOrCreateContext();
        assertSame(shadowContext, Vertx.currentContext());
        calleeVertx.runOnContext(v3 -> {
          assertSame(shadowContext, Vertx.currentContext());
          callerVertx.runOnContext(v -> {
            assertSame(callerCtx, Vertx.currentContext());
            testComplete();
          });
        });
      });
    });
    await();
  }

  @Test
  public void testStickyEventLoop() {
    AtomicReference<EventLoop> eventLoop1 = new AtomicReference<>();
    AtomicReference<EventLoop> eventLoop2 = new AtomicReference<>();
    Context ctx1 = callerVertx.getOrCreateContext();
    Context ctx2 = callerVertx.getOrCreateContext();
    ctx1.runOnContext(v1 -> {
      ContextInternal ctx = calleeVertx.getOrCreateContext();
      eventLoop1.set(ctx.nettyEventLoop());
      complete();
    });
    ctx2.runOnContext(v1 -> {
      ContextInternal ctx = (ContextInternal) calleeVertx.getOrCreateContext();
      eventLoop2.set(ctx.nettyEventLoop());
    });
    assertWaitUntil(() -> eventLoop1.get() != null && eventLoop2.get() != null);
    Assert.assertSame(eventLoop1.get(), eventLoop2.get());
  }

  @Test
  public void testHttpClient() throws Exception {
    HttpServer server = calleeVertx.createHttpServer().requestHandler(req -> {
      HttpServerResponse resp = req.response();
      resp.setChunked(true);
      for (int i = 0;i < 8;i++) {
        resp.write("chunk-" + i);
      }
      resp.end();
    });
    awaitFuture(server.listen(8080, "localhost"));
    HttpClientAgent client = calleeVertx.createHttpClient();
    Context ctx = callerVertx.getOrCreateContext();
    ctx.runOnContext(v1 -> {
      Thread expected = Thread.currentThread();
      client.request(HttpMethod.GET, 8080, "localhost", "/")
        .onComplete(onSuccess(req -> {
          Context shadow = Vertx.currentContext();
          assertEquals(ThreadingModel.EXTERNAL, shadow.threadingModel());
          assertSame(shadow, calleeVertx.getOrCreateContext());
          assertSame(expected, Thread.currentThread());
          assertSame(ctx, callerVertx.getOrCreateContext());
          req
            .send()
            .onComplete(onSuccess(resp -> {
              assertSame(expected, Thread.currentThread());
              assertSame(ctx, callerVertx.getOrCreateContext());
              AtomicInteger idx = new AtomicInteger();
              resp.handler(buff -> {
                assertSame(expected, Thread.currentThread());
                assertSame(ctx, callerVertx.getOrCreateContext());
                assertEquals("chunk-" + idx.getAndIncrement(), buff.toString());
              });
              resp.endHandler(v2 -> {
                assertSame(expected, Thread.currentThread());
                assertSame(ctx, callerVertx.getOrCreateContext());
                testComplete();
              });
            }));
        }));
    });
    await();
  }

  @Test
  public void testHttpServer() throws Exception {
    Context callerCtx = callerVertx.getOrCreateContext();
    CountDownLatch latch = new CountDownLatch(1);
    callerCtx.runOnContext(v1 -> {
      HttpServer server = calleeVertx.createHttpServer().requestHandler(req -> {
        ContextInternal shadowDuplicate = calleeVertx.getOrCreateContext();
        assertEquals(ThreadingModel.EXTERNAL, shadowDuplicate.threadingModel());
        assertTrue(shadowDuplicate.isDuplicate());
        ContextInternal shadowDuplicateUnwrap = shadowDuplicate.unwrap();
        assertEquals(ThreadingModel.EXTERNAL, shadowDuplicateUnwrap.threadingModel());
        assertFalse(shadowDuplicateUnwrap.isDuplicate());
        ContextInternal duplicate = callerVertx.getOrCreateContext();
        assertTrue(duplicate.isDuplicate());
        assertSame(callerCtx, duplicate.unwrap());
        HttpServerResponse resp = req.response();
        resp.end("Hello World");
      });
      server.listen(8080, "localhost").onComplete(onSuccess(v -> {
        assertSame(callerCtx, callerVertx.getOrCreateContext());
        latch.countDown();
      }));
    });
    awaitLatch(latch);
    HttpClientAgent client = calleeVertx.createHttpClient();
    Future<Buffer> fut = client.request(new RequestOptions().setPort(8080).setHost("localhost"))
      .compose(req -> req
        .send()
        .compose(HttpClientResponse::body));
    Buffer body = awaitFuture(fut);
    assertEquals("Hello World", body.toString());
  }

  @Test
  public void testSegregatedHttpProxy() throws Exception {
    Vertx vertx = Vertx.vertx();
    try {
      HttpServer server = vertx
        .createHttpServer()
        .requestHandler(req -> {
          req.response().end("Hello World");
        });
      awaitFuture(server.listen(8081, "localhost"));
      HttpClient client = vertx.createHttpClient();
      Set<Context> clientProxyContexts = Collections.synchronizedSet(new HashSet<>());
      HttpClientAgent clientProxy = calleeVertx
        .httpClientBuilder()
        .withConnectHandler(conn -> {
          clientProxyContexts.add(Vertx.currentContext());
        })
        .build();
      HttpServer serverProxy = callerVertx
        .createHttpServer()
        .requestHandler(inboundReq -> {
          ContextInternal callerCtx = callerVertx.getContext();
          inboundReq.body().compose(body -> clientProxy
            .request(inboundReq.method(), 8081, "localhost", inboundReq.uri())
            .compose(outboundReq -> outboundReq
              .send(body)
              .compose(HttpClientResponse::body)
            )).onComplete(ar -> {
            assertSame(callerCtx, callerVertx.getOrCreateContext());
            ContextInternal calleeCtx = calleeVertx.getOrCreateContext();
            assertNotSame(callerCtx, calleeCtx);
            if (ar.succeeded()) {
              inboundReq.response().end(ar.result());
            } else {
              inboundReq.response().setStatusCode(500).end();
            }
          });
        });
      awaitFuture(serverProxy.listen(8080, "localhost"));
      Future<Buffer> fut = client.request(new RequestOptions().setPort(8080).setHost("localhost"))
        .compose(req -> req
          .send()
          .expecting(HttpResponseExpectation.SC_OK)
          .compose(HttpClientResponse::body));
      Buffer body = awaitFuture(fut);
      assertEquals("Hello World", body.toString());
      assertEquals(1, clientProxyContexts.size());
    } finally {
      awaitFuture(vertx.close());
    }
  }

  @Test
  public void testNetSocketClient() throws Exception {
    int num = 8;
    waitFor(2 + 8);
    NetServer server = calleeVertx.createNetServer().connectHandler(so -> {
      Buffer received = Buffer.buffer();
      so.handler(buff -> {
        received.appendBuffer(buff);
        if (received.length() == num * ("msg-x").length()) {
          so.write(received);
          calleeVertx.setTimer(10, id -> so.end());
        }
      });
    });
    awaitFuture(server.listen(1234, "localhost"));
    NetClient client = calleeVertx.createNetClient();
    Context callerCtx = callerVertx.getOrCreateContext();
    callerCtx.runOnContext(v1 -> {
      Thread expected = Thread.currentThread();
      client.connect(1234, "localhost").onComplete(onSuccess(so -> {
        Context shadow = Vertx.currentContext();
        assertEquals(ThreadingModel.EXTERNAL, shadow.threadingModel());
        assertSame(shadow, calleeVertx.getOrCreateContext());
        assertSame(callerCtx, callerVertx.getOrCreateContext());
        assertSame(expected, Thread.currentThread());
        for (int i = 0;i < num;i++) {
          so.write("msg-" + num)
            .onComplete(onSuccess(v2 -> {
              assertSame(shadow, calleeVertx.getOrCreateContext());
              assertSame(callerCtx, callerVertx.getOrCreateContext());
              assertSame(expected, Thread.currentThread());
              complete();
            }));
        }
        so.handler(buff -> {
          assertSame(shadow, calleeVertx.getOrCreateContext());
          assertSame(callerCtx, callerVertx.getOrCreateContext());
          assertSame(expected, Thread.currentThread());
          complete();
        });
        so.endHandler(v -> {
          assertSame(shadow, calleeVertx.getOrCreateContext());
          assertSame(callerCtx, callerVertx.getOrCreateContext());
          assertSame(expected, Thread.currentThread());
          complete();
        });
      }));
    });
    await();
  }

  @Test
  public void testSetTimer() {
    ContextInternal callerCtx = callerVertx.getOrCreateContext();
    callerCtx.runOnContext(v1 -> {
      Thread expected = Thread.currentThread();
      calleeVertx.setTimer(100, id -> {
        assertSame(expected, Thread.currentThread());
        assertEquals(ThreadingModel.EXTERNAL, Vertx.currentContext().threadingModel());
        assertSame(callerCtx, callerVertx.getOrCreateContext());
        testComplete();
      });
    });
    await();
  }

  @Test
  public void testEventBus() {
    ContextInternal callerCtx = callerVertx.getOrCreateContext();
    EventBus eventBus = calleeVertx.eventBus();
    eventBus.consumer("the-address", msg -> msg.reply(msg.body()));
    callerCtx.runOnContext(v1 -> {
      Thread expected = Thread.currentThread();
      eventBus
        .request("the-address", "msg")
        .onComplete(onSuccess(reply -> {
          assertSame(expected, Thread.currentThread());
          assertSame(callerCtx, callerVertx.getOrCreateContext());
          Context shadow = calleeVertx.getOrCreateContext();
          assertNotSame(callerCtx, shadow);
          assertSame(Vertx.currentContext(), shadow);
        testComplete();
      }));
    });
    await();
  }

  @Test
  public void testPeriodic() {
    ContextInternal callerCtx = callerVertx.getOrCreateContext();
    callerCtx.runOnContext(v -> {
      calleeVertx.setPeriodic(10, id -> {
        ContextInternal callbackCtx = callerVertx.getOrCreateContext();
        assertTrue(callbackCtx.isDuplicate());
        assertSame(callerCtx, callbackCtx.unwrap());
        assertSame(callerCtx.nettyEventLoop(), callbackCtx.nettyEventLoop());
        ContextInternal calleeCtx = (ContextInternal) Vertx.currentContext();
        assertEquals(ThreadingModel.EXTERNAL, calleeCtx.threadingModel());
        assertTrue(calleeCtx.isDuplicate());
        ContextInternal shadowUnwrapCtx = calleeCtx.unwrap();
        assertFalse(shadowUnwrapCtx.isDuplicate());
        assertTrue(calleeVertx.cancelTimer(id));
        testComplete();
      });
    });
    await();
  }

  @Test
  public void testTracePropagation() throws Exception {
    FakeTracer tracer = new FakeTracer();
    awaitFuture(callerVertx.close());
    callerVertx = (VertxInternal) VertxBootstrap.create().tracerFactory(options -> tracer).enableShadowContext(true).init().vertx();
    Span rootSpan = tracer.newTrace();
    HttpServer server = calleeVertx.createHttpServer().requestHandler(req -> req.response().end());
    awaitFuture(server.listen(8080, "localhost"));
    HttpClientAgent client = calleeVertx.createHttpClient();
    Context callerCtx = callerVertx.getOrCreateContext();
    callerCtx.runOnContext(v -> {
      tracer.activate(rootSpan);
      client.request(HttpMethod.GET, 8080, "localhost", "/")
        .compose(req -> req.send()
          .expecting(HttpResponseExpectation.SC_OK)
          .compose(HttpClientResponse::body))
        .onComplete(onSuccess(body -> {
          testComplete();
        }));
    });
    await();
    waitUntil(() -> tracer.getFinishedSpans().size() == 1);
    Span span = tracer.getFinishedSpans().get(0);
    Assert.assertEquals(SpanKind.RPC, span.kind);
    Assert.assertEquals(span.traceId, rootSpan.traceId);
    Assert.assertEquals(span.parentId, rootSpan.id);
  }

  @Test
  public void testContextLocalData() {
    Object expected = new Object();
    ContextInternal callerCtx = callerVertx.getOrCreateContext();
    callerCtx.putLocal(contextLocal, AccessMode.CONCURRENT, expected);
    callerCtx.runOnContext(v -> {
      callerVertx.runOnContext(v2 -> {
        ContextInternal calleeCtx = calleeVertx.getOrCreateContext();
        assertSame(expected, calleeCtx.getLocal(contextLocal));
        testComplete();
      });
    });
    await();
  }

  @Test
  public void testContextData() {
    Object expected = new Object();
    ContextInternal callerCtx = callerVertx.getOrCreateContext();
    callerCtx.put("key", expected);
    callerCtx.runOnContext(v -> {
      callerVertx.runOnContext(v2 -> {
        ContextInternal calleeCtx = calleeVertx.getOrCreateContext();
        assertSame(expected, calleeCtx.get("key"));
        testComplete();
      });
    });
    await();
  }

  @Test
  public void testDuplication1() {
    ContextInternal callerCtx = callerVertx.getOrCreateContext();
    callerCtx.runOnContext(v -> {
      ContextInternal calleeCtx = calleeVertx.getOrCreateContext();
      ContextInternal shadowDuplicateCtx = calleeCtx.duplicate();
      assertEquals(ThreadingModel.EXTERNAL, shadowDuplicateCtx.threadingModel());
      assertTrue(shadowDuplicateCtx.isDuplicate());
      ContextInternal shadowUnwrapCtx = shadowDuplicateCtx.unwrap();
      shadowUnwrapCtx.runOnContext(v2 -> {
        assertSame(callerCtx, callerVertx.getOrCreateContext());
        testComplete();
      });
    });
    await();
  }

  @Test
  public void testDuplication2() {
    ContextInternal callerCtx = callerVertx.getOrCreateContext();
    ContextInternal duplicateCtx = callerCtx.duplicate();
    duplicateCtx.runOnContext(v1 -> {
      ContextInternal shadowOfDuplicateCtx = calleeVertx.getOrCreateContext();
      assertTrue(shadowOfDuplicateCtx.isDuplicate());
      shadowOfDuplicateCtx
        .unwrap()
        .runOnContext(v2 -> {
          assertSame(callerCtx, callerVertx.getOrCreateContext());
          testComplete();
      });
    });
    await();
  }

  @Test
  public void testGetOrCreateContextFromUnassociatedEventLoopThread() {
    Executor executor = callerVertx.nettyEventLoopGroup().next();
    testGetOrCreateContextFromUnassociatedThread(executor);
  }

  @Test
  public void testGetOrCreateContextFromUnassociatedWorkerThread() {
    Executor executor = callerVertx.workerPool().executor();
    testGetOrCreateContextFromUnassociatedThread(executor);
  }

  private void testGetOrCreateContextFromUnassociatedThread(Executor executor) {
    executor.execute(() -> {
      Context ctx = calleeVertx.getOrCreateContext();
      assertSame(ctx.owner(), calleeVertx);
      // Maybe should not always be event-loop
      assertEquals(ThreadingModel.EVENT_LOOP, ctx.threadingModel());
      testComplete();
    });
    await();
  }

  @Test
  public void testWorkerExecutorExecuteBlocking() {
    WorkerExecutor exec = calleeVertx.createSharedWorkerExecutor("abc");
    ContextInternal callerCtx = callerVertx.getOrCreateContext();
    callerCtx.runOnContext(v1 -> {
      Thread expected = Thread.currentThread();
      ContextInternal calleeCtx = calleeVertx.getOrCreateContext();
      Future<Context> fut = exec.executeBlocking(() -> {
        ShadowContext ctx = (ShadowContext) Vertx.currentContext();
        assertNotSame(calleeCtx, ctx);
        assertSame(callerCtx, ctx.delegate());
        assertSame(calleeCtx.owner(), calleeVertx);
        return ctx;
      });
      fut.onComplete(onSuccess(res -> {
        ShadowContext ctx = (ShadowContext) Vertx.currentContext();
        assertSame(res, ctx);
        assertSame(calleeCtx.owner(), calleeVertx);
        assertSame(expected, Thread.currentThread());
        testComplete();
      }));
    });
    await();
  }
}
