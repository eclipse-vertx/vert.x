/*
 * Copyright 2015 Red Hat, Inc. and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package io.vertx.test.core.instrumentation;

import io.vertx.core.Handler;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.*;
import io.vertx.core.impl.VertxInternal;
import io.vertx.core.net.NetClient;
import io.vertx.core.net.NetServer;
import io.vertx.core.spi.instrumentation.InstrumentationFactory;
import io.vertx.test.core.TestUtils;
import io.vertx.test.core.VertxTestBase;
import org.junit.Test;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public class InstrumentationTest extends VertxTestBase {

  private TestInstrumentation instrumentation = new TestInstrumentation();

  @Override
  public void setUp() throws Exception {
    InstrumentationFactory.setInstrumentation(instrumentation);
    super.setUp();
    InstrumentationFactory.setInstrumentation(null);
  }

  @Override
  protected void tearDown() throws Exception {
    super.tearDown();
    instrumentation.assertNoErrors();
  }

  @Test
  public void testNetServer() throws Exception {
    CountDownLatch latch = new CountDownLatch(1);
    TestContinuation cont = instrumentation.continuation();
    cont.resume();
    NetServer server = vertx.createNetServer()
      .connectHandler(so -> {
        assertSame(cont, instrumentation.current());
        so.handler(buff -> {
          assertSame(cont, instrumentation.current());
          so.write(buff);
          so.closeHandler(v -> {
            assertSame(cont, instrumentation.current());
            testComplete();
          });
        });
      }).listen(1234, "localhost", onSuccess(v -> {
        latch.countDown();
      }));
    cont.suspend();
    awaitLatch(latch);
    NetClient client = vertx.createNetClient();
    client.connect(1234, "localhost", onSuccess(so -> {
      so.write("HELLO");
      assertNull(instrumentation.current());
      so.handler(buff -> {
        assertNull(instrumentation.current());
        so.close();
      });
    }));
    await();
  }

  @Test
  public void testNetServerDrainHandler() throws Exception {
    CountDownLatch latch = new CountDownLatch(1);
    CompletableFuture<Void> resume = new CompletableFuture<>();
    TestContinuation cont = instrumentation.continuation();
    cont.resume();
    NetServer server = vertx.createNetServer()
      .connectHandler(so -> {
        while (!so.writeQueueFull()) {
          so.write(TestUtils.randomBuffer(1024));
        }
        so.drainHandler(v -> {
          so.close();
        });
        resume.complete(null);
      }).listen(1234, "localhost", onSuccess(v -> {
        latch.countDown();
      }));
    cont.suspend();
    awaitLatch(latch);
    NetClient client = vertx.createNetClient();
    client.connect(1234, "localhost", onSuccess(so -> {
      so.pause();
      resume.thenAccept(v1 -> {
        so.resume();
        so.closeHandler(v2 -> {
          testComplete();
        });
      });
    }));
    await();
  }

  @Test
  public void testNetClient() throws Exception {
    CountDownLatch latch = new CountDownLatch(1);
    NetServer server = vertx.createNetServer()
      .connectHandler(so -> {
        so.write("ping");
        so.handler(v -> {
          so.close();
        });
      }).listen(1234, "localhost", onSuccess(v -> {
        latch.countDown();
      }));
    awaitLatch(latch);
    NetClient client = vertx.createNetClient();
    TestContinuation cont = instrumentation.continuation();
    cont.resume();
    client.connect(1234, "localhost", onSuccess(so -> {
      assertSame(cont, instrumentation.current());
      so.handler(buff -> {
        assertSame(cont, instrumentation.current());
        so.write("pong");
        so.closeHandler(v -> {
          assertSame(cont, instrumentation.current());
          testComplete();
        });
      });
    }));
    cont.suspend();
    await();
  }

  @Test
  public void testNetClientDrainHandler() throws Exception {
    CountDownLatch latch = new CountDownLatch(1);
    CompletableFuture<Void> resume = new CompletableFuture<>();
    NetServer server = vertx.createNetServer()
      .connectHandler(so -> {
        so.pause();
        resume.thenAccept(v1 -> {
          so.resume();
          so.closeHandler(v2 -> {
            testComplete();
          });
        });
      }).listen(1234, "localhost", onSuccess(v -> {
        latch.countDown();
      }));
    awaitLatch(latch);
    NetClient client = vertx.createNetClient();
    TestContinuation cont = instrumentation.continuation();
    cont.resume();
    client.connect(1234, "localhost", onSuccess(so -> {
      assertSame(cont, instrumentation.current());
      while (!so.writeQueueFull()) {
        so.write(TestUtils.randomBuffer(1024));
      }
      so.drainHandler(v -> {
        assertSame(cont, instrumentation.current());
        so.close();
      });
      resume.complete(null);
    }));
    cont.suspend();
    await();
  }

  @Test
  public void testRunOnContext() throws Exception {
    TestContinuation cont = instrumentation.continuation();
    cont.resume();
    vertx.runOnContext(v1 -> {
      assertSame(cont, instrumentation.current());
      vertx.runOnContext(v2 -> {
        assertSame(cont, instrumentation.current());
        testComplete();
      });
    });
    cont.suspend();
    await();
  }

  @Test
  public void testExecuteBlocking() throws Exception {
    TestContinuation cont = instrumentation.continuation();
    cont.resume();
    vertx.executeBlocking(fut -> {
      assertSame(cont, instrumentation.current());
      new Thread(fut::complete).start();
    }, ar -> {
      assertSame(cont, instrumentation.current());
      testComplete();
    });
    cont.suspend();
    await();
  }

  @Test
  public void testSetTimer() throws Exception {
    TestContinuation cont = instrumentation.continuation();
    cont.resume();
    vertx.setTimer(10, id -> {
      assertSame(cont, instrumentation.current());
      testComplete();
    });
    cont.suspend();
    await();
  }

  @Test
  public void testSetPeriodic() throws Exception {
    TestContinuation cont = instrumentation.continuation();
    cont.resume();
    AtomicInteger count = new AtomicInteger();
    vertx.setPeriodic(10, id -> {
      assertSame(cont, instrumentation.current());
      if (count.incrementAndGet() == 10) {
        vertx.cancelTimer(id);
        testComplete();
      }
    });
    cont.suspend();
    await();
  }

  @Test
  public void testDoubleDipInSameContinuation() throws Exception {
    TestContinuation cont = instrumentation.continuation();
    cont.resume();
    AtomicReference<Handler<Void>> expected = new AtomicReference<>();
    Handler<Void> handler = event -> {
      assertSame(cont, instrumentation.current());
      testComplete();
    };
    handler = ((VertxInternal)vertx).captureContinuation(handler);
    expected.set(handler);
    Handler<Void> doubleDip = ((VertxInternal) vertx).captureContinuation(handler);
    cont.suspend();
    assertSame(handler, doubleDip);
    doubleDip.handle(null);
    await();
  }

  // Corner case to investigate
  @Test(expected = AssertionError.class)
  public void testDoubleDipInDifferentContinuation() throws Exception {
    TestContinuation cont1 = instrumentation.continuation();
    cont1.resume();
    AtomicReference<Handler<Void>> expected = new AtomicReference<>();
    Handler<Void> handler = event -> {
      assertSame(cont1, instrumentation.current());
      testComplete();
    };
    handler = ((VertxInternal)vertx).captureContinuation(handler);
    expected.set(handler);
    cont1.suspend();
    TestContinuation cont2 = instrumentation.continuation();
    cont2.resume();
    Handler<Void> doubleDip = ((VertxInternal) vertx).captureContinuation(handler);
    assertSame(handler, doubleDip);
    doubleDip.handle(null);
    await();
  }

  @Test
  public void testHttpServerListen() {
    TestContinuation cont = instrumentation.continuation();
    cont.resume();
    vertx.createHttpServer().requestHandler(req -> {
      fail();
    }).listen(8080, onSuccess(v -> {
      assertSame(cont, instrumentation.current());
      testComplete();
    }));
    cont.suspend();
    await();
  }

  @Test
  public void testHttpServerRequest() throws Exception {
    TestContinuation cont = instrumentation.continuation();
    cont.resume();
    String expected = TestUtils.randomAlphaString(1024 * 128);
    CountDownLatch latch = new CountDownLatch(1);
    vertx.createHttpServer().requestHandler(req -> {
      assertSame(cont, instrumentation.current());
      Buffer body = Buffer.buffer();
      req.handler(buff -> {
        body.appendBuffer(buff);
        assertSame(cont, instrumentation.current());
      });
      req.endHandler(v -> {
        assertEquals(expected, body.toString());
        assertSame(cont, instrumentation.current());
        req.response().end();
      });
    }).listen(8080, "localhost", onSuccess(v -> {
      latch.countDown();
    }));
    cont.suspend();
    awaitLatch(latch);
    HttpClientRequest req = vertx.createHttpClient().put(8080, "localhost", "/", resp -> {
      assertEquals(200, resp.statusCode());
      testComplete();
    });
    req.end(expected);
    await();
  }

  @Test
  public void testHttpServerResponse() throws Exception {
    waitFor(3);
    TestContinuation cont = instrumentation.continuation();
    CountDownLatch latch1 = new CountDownLatch(1);
    cont.resume();
    vertx.createHttpServer().requestHandler(req -> {
      HttpServerResponse resp = req.response().setChunked(true);
      while (!resp.writeQueueFull()) {
        resp.write(TestUtils.randomBuffer(1024));
      }
      resp.drainHandler(v -> {
        assertSame(cont, instrumentation.current());
        resp.end();
        complete();
      });
      resp.endHandler(v -> {
        assertSame(cont, instrumentation.current());
        complete();
      });
      resp.closeHandler(v -> {
        assertSame(cont, instrumentation.current());
        complete();
      });
    }).listen(8080, "localhost", onSuccess(v -> {
      latch1.countDown();
    }));
    cont.suspend();
    awaitLatch(latch1);
    vertx.createHttpClient().getNow(8080, "localhost", "/", resp -> {
      resp.endHandler(v -> {
        testComplete();
      });
    });
    await();
  }

  @Test
  public void testHttpServerConnection() throws Exception {
    TestContinuation cont = instrumentation.continuation();
    CountDownLatch latch = new CountDownLatch(1);
    HttpServer server = vertx.createHttpServer();
    cont.resume();
    server.connectionHandler(conn -> {
      assertSame(cont, instrumentation.current());
      conn.closeHandler(v -> {
        assertSame(cont, instrumentation.current());
        testComplete();
      });
    });
    cont.suspend();
    server.requestHandler(req -> {
      req.response().end();
    }).listen(8080, "localhost", onSuccess(v -> {
      latch.countDown();
    }));
    awaitLatch(latch);
    vertx.createHttpClient(new HttpClientOptions().setKeepAlive(false)).getNow(8080, "localhost", "/", resp -> {});
    await();
  }

  @Test
  public void testHttpServerException() throws Exception {
    waitFor(3);
    TestContinuation cont = instrumentation.continuation();
    cont.resume();
    CountDownLatch latch = new CountDownLatch(1);
    vertx.createHttpServer().requestHandler(req -> {
      AtomicBoolean b1 = new AtomicBoolean();
      req.connection().exceptionHandler(err -> {
        if (b1.compareAndSet(false, true)) {
          assertSame(cont, instrumentation.current());
          complete();
        }
      });
      AtomicBoolean b2 = new AtomicBoolean();
      req.exceptionHandler(err -> {
        if (b2.compareAndSet(false, true)) {
          assertSame(cont, instrumentation.current());
          complete();
        }
      });
      AtomicBoolean b3 = new AtomicBoolean();
      req.response().exceptionHandler(err -> {
        if (b3.compareAndSet(false, true)) {
          assertSame(cont, instrumentation.current());
          complete();
        }
      });
    }).listen(8080, "localhost", onSuccess(v -> {
      latch.countDown();
    }));
    cont.suspend();
    awaitLatch(latch);
    NetClient client = vertx.createNetClient();
    client.connect(8080, "localhost", onSuccess(so -> {
      so.write("PUT / HTTP/1.1\r\n");
      so.write("Transfer-Encoding: chunked\r\n");
      so.write("\r\n");
      so.write("invalid\r\n");
    }));
    await();
  }

  @Test
  public void testHttpClientRequest() throws Exception {
    waitFor(2);
    CountDownLatch latch = new CountDownLatch(1);
    TestContinuation cont = instrumentation.continuation();
    vertx.createHttpServer().requestHandler(req -> {
      req.endHandler(v -> {
        req.response().end();
      });
    }).listen(8080, onSuccess(v -> {
      latch.countDown();
    }));
    awaitLatch(latch);
    HttpClient client = vertx.createHttpClient();
    cont.resume();
    HttpClientRequest req = client.put(8080, "localhost", "/", resp -> {
      assertSame(cont, instrumentation.current());
      complete();
    }).connectionHandler(v -> {
      assertSame(cont, instrumentation.current());
      complete();
    }).setChunked(true);
    while (!req.writeQueueFull()) {
      req.write(TestUtils.randomBuffer(1024));
    }
    req.drainHandler(v -> {
      assertSame(cont, instrumentation.current());
      req.end();
    });
    cont.suspend();
    await();
  }

  @Test
  public void testHttpClientResponse() throws Exception {
    CountDownLatch latch = new CountDownLatch(1);
    TestContinuation cont = instrumentation.continuation();
    vertx.createHttpServer().requestHandler(req -> {
      req.response().setChunked(true).write("chunk");
      vertx.setTimer(1, id -> {
        req.response().end();
      });
    }).listen(8080, onSuccess(v -> {
      latch.countDown();
    }));
    awaitLatch(latch);
    HttpClient client = vertx.createHttpClient();
    client.getNow(8080, "localhost", "/", resp -> {
      cont.resume();
      resp.handler(buff -> {
        assertSame(cont, instrumentation.current());
      });
      resp.endHandler(v -> {
        assertSame(cont, instrumentation.current());
        testComplete();
      });
      cont.suspend();
    });
    await();
  }

  @Test
  public void testHttpClientException() throws Exception {
    waitFor(2);
    CountDownLatch latch = new CountDownLatch(1);
    TestContinuation cont = instrumentation.continuation();
    CompletableFuture<Void> f = new CompletableFuture<>();
    vertx.createHttpServer().requestHandler(req -> {
      req.response().setChunked(true).write("chunk");
      f.thenAccept(v -> {
        req.response().close();
      });
    }).listen(8080, onSuccess(v -> {
      latch.countDown();
    }));
    awaitLatch(latch);
    HttpClient client = vertx.createHttpClient();
    cont.resume();
    HttpClientRequest req = client.put(8080, "localhost", "/", resp -> {
      resp.exceptionHandler(err -> {
        assertSame(cont, instrumentation.current());
        complete();
      });
      f.complete(null);
    });
    req.exceptionHandler(err -> {
      assertSame(cont, instrumentation.current());
      complete();
    });
    req.setChunked(true).write("chunk");
    cont.suspend();
    await();
  }
}
