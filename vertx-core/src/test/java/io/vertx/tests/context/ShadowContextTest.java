package io.vertx.tests.context;

import io.netty.channel.EventLoop;
import io.vertx.core.*;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.http.*;
import io.vertx.core.impl.LocalSeq;
import io.vertx.core.impl.ShadowContext;
import io.vertx.core.internal.ContextInternal;
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
  VertxInternal shadowVertx;
  VertxInternal actualVertx;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    contextLocal = ContextLocal.registerLocal(Object.class);
    shadowVertx = (VertxInternal) Vertx.vertx();
    actualVertx = (VertxInternal) Vertx.vertx();
    disableThreadChecks();
  }

  @Override
  protected void tearDown() throws Exception {
    awaitFuture(shadowVertx.close());
    awaitFuture(actualVertx.close());
    LocalSeq.reset();
    super.tearDown();
  }

  @Test
  public void testGetOrCreate() {
    ContextInternal actualCtx = actualVertx.getOrCreateContext();
    actualCtx.runOnContext(v1 -> {
      Thread expected = Thread.currentThread();
      ContextInternal shadowCtx = shadowVertx.getOrCreateContext();
      assertSame(actualCtx, actualVertx.getOrCreateContext());
      assertNotSame(shadowCtx, actualCtx);
      assertNotSame(shadowCtx.nettyEventLoop(), actualCtx.nettyEventLoop());
      shadowCtx.runOnContext(v2 -> {
        assertSame(expected, Thread.currentThread());
        assertSame(shadowCtx, shadowVertx.getOrCreateContext());
        assertSame(actualCtx, actualVertx.getOrCreateContext());
        testComplete();
      });
    });
    await();
  }

  @Test
  public void testEmitFromOriginatingThread() {
    ContextInternal actualCtx = actualVertx.getOrCreateContext();
    actualCtx.runOnContext(v1 -> {
      ContextInternal shadowCtx = shadowVertx.getOrCreateContext();
      AtomicBoolean inThread = new AtomicBoolean(true);
      shadowCtx.emit(v2 -> {
        assertSame(shadowCtx, Vertx.currentContext());
        assertTrue(inThread.get());
        testComplete();
      });
      inThread.set(false);
    });
    await();
  }

  @Test
  public void testEmitFromEventLoop() {
    Context eventLoop = shadowVertx.getOrCreateContext();
    ContextInternal actualCtx = actualVertx.getOrCreateContext();
    actualCtx.runOnContext(v1 -> {
      ContextInternal shadowCtx = shadowVertx.getOrCreateContext();
      AtomicBoolean inThread = new AtomicBoolean(true);
      eventLoop.runOnContext(v -> {
        shadowCtx.emit(v2 -> {
          assertSame(shadowCtx, Vertx.currentContext());
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
    ContextInternal actualCtx = actualVertx.getOrCreateContext();
    actualCtx.runOnContext(v1 -> {
      ContextInternal shadowCtx = shadowVertx.getOrCreateContext();
      assertEquals(ThreadingModel.OTHER, shadowCtx.threadingModel());
      testComplete();
    });
    await();
  }

  @Test
  public void testPiggyBack() {
    Context actualCtx = actualVertx.getOrCreateContext();
    actualCtx.runOnContext(v1 -> {
      shadowVertx.runOnContext(v2 -> {
        Context shadowContext = shadowVertx.getOrCreateContext();
        assertSame(shadowContext, Vertx.currentContext());
        shadowVertx.runOnContext(v3 -> {
          assertSame(shadowContext, Vertx.currentContext());
          actualVertx.runOnContext(v -> {
            assertSame(actualCtx, Vertx.currentContext());
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
    Context ctx1 = actualVertx.getOrCreateContext();
    Context ctx2 = actualVertx.getOrCreateContext();
    ctx1.runOnContext(v1 -> {
      ContextInternal ctx = shadowVertx.getOrCreateContext();
      eventLoop1.set(ctx.nettyEventLoop());
      complete();
    });
    ctx2.runOnContext(v1 -> {
      ContextInternal ctx = (ContextInternal) shadowVertx.getOrCreateContext();
      eventLoop2.set(ctx.nettyEventLoop());
    });
    assertWaitUntil(() -> eventLoop1.get() != null && eventLoop2.get() != null);
    Assert.assertSame(eventLoop1.get(), eventLoop2.get());
  }

  @Test
  public void testHttpClient() throws Exception {
    HttpServer server = shadowVertx.createHttpServer().requestHandler(req -> {
      HttpServerResponse resp = req.response();
      resp.setChunked(true);
      for (int i = 0;i < 8;i++) {
        resp.write("chunk-" + i);
      }
      resp.end();
    });
    awaitFuture(server.listen(8080, "localhost"));
    HttpClientAgent client = shadowVertx.createHttpClient();
    Context ctx = actualVertx.getOrCreateContext();
    ctx.runOnContext(v1 -> {
      Thread expected = Thread.currentThread();
      client.request(HttpMethod.GET, 8080, "localhost", "/")
        .onComplete(onSuccess(req -> {
          Context shadow = Vertx.currentContext();
          assertEquals(ThreadingModel.OTHER, shadow.threadingModel());
          assertSame(shadow, shadowVertx.getOrCreateContext());
          assertSame(expected, Thread.currentThread());
          assertSame(ctx, actualVertx.getOrCreateContext());
          req
            .send()
            .onComplete(onSuccess(resp -> {
              assertSame(expected, Thread.currentThread());
              assertSame(ctx, actualVertx.getOrCreateContext());
              AtomicInteger idx = new AtomicInteger();
              resp.handler(buff -> {
                assertSame(expected, Thread.currentThread());
                assertSame(ctx, actualVertx.getOrCreateContext());
                assertEquals("chunk-" + idx.getAndIncrement(), buff.toString());
              });
              resp.endHandler(v2 -> {
                assertSame(expected, Thread.currentThread());
                assertSame(ctx, actualVertx.getOrCreateContext());
                testComplete();
              });
            }));
        }));
    });
    await();
  }

  @Test
  public void testHttpServer() throws Exception {
    Context actualCtx = actualVertx.getOrCreateContext();
    CountDownLatch latch = new CountDownLatch(1);
    actualCtx.runOnContext(v1 -> {
      HttpServer server = shadowVertx.createHttpServer().requestHandler(req -> {
        ContextInternal shadowDuplicate = shadowVertx.getOrCreateContext();
        assertEquals(ThreadingModel.OTHER, shadowDuplicate.threadingModel());
        assertTrue(shadowDuplicate.isDuplicate());
        ContextInternal shadowDuplicateUnwrap = shadowDuplicate.unwrap();
        assertEquals(ThreadingModel.OTHER, shadowDuplicateUnwrap.threadingModel());
        assertFalse(shadowDuplicateUnwrap.isDuplicate());
        ContextInternal duplicate = actualVertx.getOrCreateContext();
        assertTrue(duplicate.isDuplicate());
        assertSame(actualCtx, duplicate.unwrap());
        HttpServerResponse resp = req.response();
        resp.end("Hello World");
      });
      server.listen(8080, "localhost").onComplete(onSuccess(v -> {
        assertSame(actualCtx, actualVertx.getOrCreateContext());
        latch.countDown();
      }));
    });
    awaitLatch(latch);
    HttpClientAgent client = shadowVertx.createHttpClient();
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
      HttpClientAgent clientProxy = shadowVertx
        .httpClientBuilder()
        .withConnectHandler(conn -> {
          clientProxyContexts.add(Vertx.currentContext());
        })
        .build();
      HttpServer serverProxy = actualVertx
        .createHttpServer()
        .requestHandler(inboundReq -> {
          ContextInternal actualCtx = actualVertx.getContext();
          inboundReq.body().compose(body -> clientProxy
            .request(inboundReq.method(), 8081, "localhost", inboundReq.uri())
            .compose(outboundReq -> outboundReq
              .send(body)
              .compose(HttpClientResponse::body)
            )).onComplete(ar -> {
            assertSame(actualCtx, actualVertx.getOrCreateContext());
            ContextInternal shadowCtx = shadowVertx.getOrCreateContext();
            assertNotSame(actualCtx, shadowCtx);
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
    NetServer server = shadowVertx.createNetServer().connectHandler(so -> {
      Buffer received = Buffer.buffer();
      so.handler(buff -> {
        received.appendBuffer(buff);
        if (received.length() == num * ("msg-x").length()) {
          so.write(received);
          shadowVertx.setTimer(10, id -> so.end());
        }
      });
    });
    awaitFuture(server.listen(1234, "localhost"));
    NetClient client = shadowVertx.createNetClient();
    Context actualCtx = actualVertx.getOrCreateContext();
    actualCtx.runOnContext(v1 -> {
      Thread expected = Thread.currentThread();
      client.connect(1234, "localhost").onComplete(onSuccess(so -> {
        Context shadow = Vertx.currentContext();
        assertEquals(ThreadingModel.OTHER, shadow.threadingModel());
        assertSame(shadow, shadowVertx.getOrCreateContext());
        assertSame(actualCtx, actualVertx.getOrCreateContext());
        assertSame(expected, Thread.currentThread());
        for (int i = 0;i < num;i++) {
          so.write("msg-" + num)
            .onComplete(onSuccess(v2 -> {
              assertSame(shadow, shadowVertx.getOrCreateContext());
              assertSame(actualCtx, actualVertx.getOrCreateContext());
              assertSame(expected, Thread.currentThread());
              complete();
            }));
        }
        so.handler(buff -> {
          assertSame(shadow, shadowVertx.getOrCreateContext());
          assertSame(actualCtx, actualVertx.getOrCreateContext());
          assertSame(expected, Thread.currentThread());
          complete();
        });
        so.endHandler(v -> {
          assertSame(shadow, shadowVertx.getOrCreateContext());
          assertSame(actualCtx, actualVertx.getOrCreateContext());
          assertSame(expected, Thread.currentThread());
          complete();
        });
      }));
    });
    await();
  }

  @Test
  public void testSetTimer() {
    ContextInternal actualCtx = actualVertx.getOrCreateContext();
    actualCtx.runOnContext(v1 -> {
      Thread expected = Thread.currentThread();
      shadowVertx.setTimer(100, id -> {
        assertSame(expected, Thread.currentThread());
        assertEquals(ThreadingModel.OTHER, Vertx.currentContext().threadingModel());
        assertSame(actualCtx, actualVertx.getOrCreateContext());
        testComplete();
      });
    });
    await();
  }

  @Test
  public void testEventBus() {
    ContextInternal actualCtx = actualVertx.getOrCreateContext();
    EventBus eventBus = shadowVertx.eventBus();
    eventBus.consumer("the-address", msg -> msg.reply(msg.body()));
    actualCtx.runOnContext(v1 -> {
      Thread expected = Thread.currentThread();
      eventBus
        .request("the-address", "msg")
        .onComplete(onSuccess(reply -> {
          assertSame(expected, Thread.currentThread());
          assertSame(actualCtx, actualVertx.getOrCreateContext());
          Context shadow = shadowVertx.getOrCreateContext();
          assertNotSame(actualCtx, shadow);
          assertSame(Vertx.currentContext(), shadow);
        testComplete();
      }));
    });
    await();
  }

  @Test
  public void testPeriodic() {
    ContextInternal actualCtx = actualVertx.getOrCreateContext();
    actualCtx.runOnContext(v -> {
      shadowVertx.setPeriodic(10, id -> {
        ContextInternal callbackCtx = actualVertx.getOrCreateContext();
        assertTrue(callbackCtx.isDuplicate());
        assertSame(actualCtx, callbackCtx.unwrap());
        assertSame(actualCtx.nettyEventLoop(), callbackCtx.nettyEventLoop());
        ContextInternal shadowCtx = (ContextInternal) Vertx.currentContext();
        assertEquals(ThreadingModel.OTHER, shadowCtx.threadingModel());
        assertTrue(shadowCtx.isDuplicate());
        ContextInternal shadowUnwrapCtx = shadowCtx.unwrap();
        assertFalse(shadowUnwrapCtx.isDuplicate());
        assertTrue(shadowVertx.cancelTimer(id));
        testComplete();
      });
    });
    await();
  }

  @Test
  public void testTracePropagation() throws Exception {
    FakeTracer tracer = new FakeTracer();
    awaitFuture(actualVertx.close());
    actualVertx = (VertxInternal) Vertx.builder().withTracer(options -> tracer).build();
    Span rootSpan = tracer.newTrace();
    HttpServer server = shadowVertx.createHttpServer().requestHandler(req -> req.response().end());
    awaitFuture(server.listen(8080, "localhost"));
    HttpClientAgent client = shadowVertx.createHttpClient();
    Context actualCtx = actualVertx.getOrCreateContext();
    actualCtx.runOnContext(v -> {
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
    ContextInternal actualCtx = shadowVertx.getOrCreateContext();
    actualCtx.putLocal(contextLocal, AccessMode.CONCURRENT, expected);
    actualCtx.runOnContext(v -> {
      actualVertx.runOnContext(v2 -> {
        ContextInternal shadowCtx = actualVertx.getOrCreateContext();
        assertSame(expected, shadowCtx.getLocal(contextLocal));
        testComplete();
      });
    });
    await();
  }

  @Test
  public void testContextData() {
    Object expected = new Object();
    ContextInternal actualCtx = shadowVertx.getOrCreateContext();
    actualCtx.put("key", expected);
    actualCtx.runOnContext(v -> {
      actualVertx.runOnContext(v2 -> {
        ContextInternal shadowCtx = actualVertx.getOrCreateContext();
        assertSame(expected, shadowCtx.get("key"));
        testComplete();
      });
    });
    await();
  }

  @Test
  public void testDuplication1() {
    ContextInternal actualCtx = actualVertx.getOrCreateContext();
    actualCtx.runOnContext(v -> {
      ContextInternal shadowCtx = shadowVertx.getOrCreateContext();
      ContextInternal shadowDuplicateCtx = shadowCtx.duplicate();
      assertEquals(ThreadingModel.OTHER, shadowDuplicateCtx.threadingModel());
      assertTrue(shadowDuplicateCtx.isDuplicate());
      ContextInternal shadowUnwrapCtx = shadowDuplicateCtx.unwrap();
      shadowUnwrapCtx.runOnContext(v2 -> {
        assertSame(actualCtx, actualVertx.getOrCreateContext());
        testComplete();
      });
    });
    await();
  }

  @Test
  public void testDuplication2() {
    ContextInternal actualCtx = actualVertx.getOrCreateContext();
    ContextInternal duplicateCtx = actualCtx.duplicate();
    duplicateCtx.runOnContext(v1 -> {
      ContextInternal shadowOfDuplicateCtx = shadowVertx.getOrCreateContext();
      assertTrue(shadowOfDuplicateCtx.isDuplicate());
      shadowOfDuplicateCtx
        .unwrap()
        .runOnContext(v2 -> {
          assertSame(actualCtx, actualVertx.getOrCreateContext());
          testComplete();
      });
    });
    await();
  }

  @Test
  public void testGetOrCreateContextFromUnassociatedEventLoopThread() {
    Executor executor = actualVertx.nettyEventLoopGroup().next();
    testGetOrCreateContextFromUnassociatedThread(executor);
  }

  @Test
  public void testGetOrCreateContextFromUnassociatedWorkerThread() {
    Executor executor = actualVertx.workerPool().executor();
    testGetOrCreateContextFromUnassociatedThread(executor);
  }

  private void testGetOrCreateContextFromUnassociatedThread(Executor executor) {
    executor.execute(() -> {
      Context ctx = shadowVertx.getOrCreateContext();
      assertSame(ctx.owner(), shadowVertx);
      // Maybe should not always be event-loop
      assertEquals(ThreadingModel.EVENT_LOOP, ctx.threadingModel());
      testComplete();
    });
    await();
  }

  @Test
  public void testWorkerExecutorExecuteBlocking() {
    WorkerExecutor exec = shadowVertx.createSharedWorkerExecutor("abc");
    ContextInternal actualCtx = actualVertx.getOrCreateContext();
    actualCtx.runOnContext(v1 -> {
      Thread expected = Thread.currentThread();
      ContextInternal shadowCtx = shadowVertx.getOrCreateContext();
      Future<Context> fut = exec.executeBlocking(() -> {
        ShadowContext ctx = (ShadowContext) Vertx.currentContext();
        assertNotSame(shadowCtx, ctx);
        assertSame(actualCtx, ctx.delegate());
        assertSame(shadowCtx.owner(), shadowVertx);
        return ctx;
      });
      fut.onComplete(onSuccess(res -> {
        ShadowContext ctx = (ShadowContext) Vertx.currentContext();
        assertSame(res, ctx);
        assertSame(shadowCtx.owner(), shadowVertx);
        assertSame(expected, Thread.currentThread());
        testComplete();
      }));
    });
    await();
  }
}
