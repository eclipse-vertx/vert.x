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

package io.vertx.core.spi.metrics;

import io.vertx.core.*;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.datagram.DatagramSocket;
import io.vertx.core.datagram.DatagramSocketOptions;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.eventbus.EventBusOptions;
import io.vertx.core.eventbus.MessageConsumer;
import io.vertx.core.http.*;
import io.vertx.core.metrics.MetricsOptions;
import io.vertx.core.metrics.impl.DummyVertxMetrics;
import io.vertx.core.net.*;
import io.vertx.core.spi.VertxMetricsFactory;
import io.vertx.test.core.VertxTestBase;
import org.junit.Ignore;
import org.junit.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class MetricsContextTest extends VertxTestBase {

  @Ignore
  @Test
  public void testFactory() throws Exception {
    AtomicReference<Thread> metricsThread = new AtomicReference<>();
    AtomicReference<Context> metricsContext = new AtomicReference<>();
    VertxMetricsFactory factory = (options) -> {
      metricsThread.set(Thread.currentThread());
      metricsContext.set(Vertx.currentContext());
      return DummyVertxMetrics.INSTANCE;
    };
    vertx(new VertxOptions().setMetricsOptions(new MetricsOptions().setEnabled(true).setFactory(factory)));
    assertNull(metricsContext.get());
  }

  @Ignore
  @Test
  public void testFactoryInCluster() throws Exception {
    AtomicReference<Thread> metricsThread = new AtomicReference<>();
    AtomicReference<Context> metricsContext = new AtomicReference<>();
    Thread testThread = Thread.currentThread();
    VertxMetricsFactory factory = (options) -> {
      metricsThread.set(Thread.currentThread());
      metricsContext.set(Vertx.currentContext());
      return DummyVertxMetrics.INSTANCE;
    };
    VertxOptions options = new VertxOptions()
      .setMetricsOptions(new MetricsOptions().setEnabled(true).setFactory(factory))
      .setEventBusOptions(new EventBusOptions().setClustered(true));
    clusteredVertx(options, onSuccess(vertx -> {
      assertSame(testThread, metricsThread.get());
      assertNull(metricsContext.get());
      testComplete();
    }));
    await();
  }

  @Ignore
  @Test
  public void testHttpServerRequestEventLoop() throws Exception {
    testHttpServerRequest(eventLoopContextFactory, eventLoopChecker);
  }

  @Ignore
  @Test
  public void testHttpServerRequestWorker() throws Exception {
    testHttpServerRequest(workerContextFactory, workerChecker);
  }

  private void testHttpServerRequest(Function<Vertx, Context> contextFactory, BiConsumer<Thread, Context> checker) throws Exception {
    AtomicReference<Thread> expectedThread = new AtomicReference<>();
    AtomicReference<Context> expectedContext = new AtomicReference<>();
    AtomicBoolean requestBeginCalled = new AtomicBoolean();
    AtomicBoolean responseEndCalled = new AtomicBoolean();
    AtomicBoolean socketConnectedCalled = new AtomicBoolean();
    AtomicBoolean socketDisconnectedCalled = new AtomicBoolean();
    AtomicBoolean bytesReadCalled = new AtomicBoolean();
    AtomicBoolean bytesWrittenCalled = new AtomicBoolean();
    AtomicBoolean closeCalled = new AtomicBoolean();
    VertxMetricsFactory factory = (options) -> new DummyVertxMetrics() {
      @Override
      public HttpServerMetrics createHttpServerMetrics(HttpServerOptions options, SocketAddress localAddress) {
        return new DummyHttpServerMetrics() {
          @Override
          public Void requestBegin(Void socketMetric, HttpServerRequest request) {
            requestBeginCalled.set(true);
            checker.accept(expectedThread.get(), expectedContext.get());
            return null;
          }
          @Override
          public void responseEnd(Void requestMetric, HttpServerResponse response) {
            responseEndCalled.set(true);
            checker.accept(expectedThread.get(), expectedContext.get());
          }
          @Override
          public Void connected(SocketAddress remoteAddress, String remoteName) {
            socketConnectedCalled.set(true);
            checker.accept(expectedThread.get(), expectedContext.get());
            return null;
          }
          @Override
          public void disconnected(Void socketMetric, SocketAddress remoteAddress) {
            socketDisconnectedCalled.set(true);
            checker.accept(expectedThread.get(), expectedContext.get());
          }
          @Override
          public void bytesRead(Void socketMetric, SocketAddress remoteAddress, long numberOfBytes) {
            bytesReadCalled.set(true);
            checker.accept(expectedThread.get(), expectedContext.get());
          }
          @Override
          public void bytesWritten(Void socketMetric, SocketAddress remoteAddress, long numberOfBytes) {
            bytesWrittenCalled.set(true);
            checker.accept(expectedThread.get(), expectedContext.get());
          }
          @Override
          public void close() {
            closeCalled.set(true);
          }
        };
      }
    };
    CountDownLatch latch = new CountDownLatch(1);
    Vertx vertx = vertx(new VertxOptions().setMetricsOptions(new MetricsOptions().setEnabled(true).setFactory(factory)));
    Context ctx = contextFactory.apply(vertx);
    ctx.runOnContext(v1 -> {
      HttpServer server = vertx.createHttpServer().requestHandler(req -> {
        HttpServerResponse response = req.response();
        response.setStatusCode(200).setChunked(true).end("bye");
        response.close();
      });
      server.listen(8080, "localhost", onSuccess(s -> {
        expectedThread.set(Thread.currentThread());
        expectedContext.set(Vertx.currentContext());
        latch.countDown();
      }));
    });
    awaitLatch(latch);
    HttpClient client = vertx.createHttpClient();
    client.put(8080, "localhost", "/", onSuccess(resp -> {
      resp.netSocket().closeHandler(v -> {
        vertx.close(v4 -> {
          assertTrue(requestBeginCalled.get());
          assertTrue(responseEndCalled.get());
          assertTrue(bytesReadCalled.get());
          assertTrue(bytesWrittenCalled.get());
          assertTrue(socketConnectedCalled.get());
          assertTrue(socketDisconnectedCalled.get());
          assertTrue(closeCalled.get());
          testComplete();
        });
      });
    })).exceptionHandler(err -> {
      fail(err.getMessage());
    }).setChunked(true).end(Buffer.buffer("hello"));
    await();
  }

  @Ignore
  @Test
  public void testHttpServerWebsocketEventLoop() throws Exception {
    testHttpServerWebsocket(eventLoopContextFactory, eventLoopChecker);
  }

  @Ignore
  @Test
  public void testHttpServerWebsocketWorker() throws Exception {
    testHttpServerWebsocket(workerContextFactory, workerChecker);
  }

  private void testHttpServerWebsocket(Function<Vertx, Context> contextFactory, BiConsumer<Thread, Context> checker) throws Exception {
    AtomicReference<Thread> expectedThread = new AtomicReference<>();
    AtomicReference<Context> expectedContext = new AtomicReference<>();
    AtomicBoolean websocketConnected = new AtomicBoolean();
    AtomicBoolean websocketDisconnected = new AtomicBoolean();
    AtomicBoolean socketConnectedCalled = new AtomicBoolean();
    AtomicBoolean socketDisconnectedCalled = new AtomicBoolean();
    AtomicBoolean bytesReadCalled = new AtomicBoolean();
    AtomicBoolean bytesWrittenCalled = new AtomicBoolean();
    AtomicBoolean closeCalled = new AtomicBoolean();
    VertxMetricsFactory factory = (options) -> new DummyVertxMetrics() {
      @Override
      public HttpServerMetrics createHttpServerMetrics(HttpServerOptions options, SocketAddress localAddress) {
        return new DummyHttpServerMetrics() {
          @Override
          public Void connected(Void socketMetric, Void requestMetric, ServerWebSocket serverWebSocket) {
            websocketConnected.set(true);
            checker.accept(expectedThread.get(), expectedContext.get());
            return null;
          }
          @Override
          public void disconnected(Void serverWebSocketMetric) {
            websocketDisconnected.set(true);
            checker.accept(expectedThread.get(), expectedContext.get());
          }
          @Override
          public Void connected(SocketAddress remoteAddress, String remoteName) {
            socketConnectedCalled.set(true);
            checker.accept(expectedThread.get(), expectedContext.get());
            return null;
          }
          @Override
          public void disconnected(Void socketMetric, SocketAddress remoteAddress) {
            socketDisconnectedCalled.set(true);
            checker.accept(expectedThread.get(), expectedContext.get());
          }
          @Override
          public void bytesRead(Void socketMetric, SocketAddress remoteAddress, long numberOfBytes) {
            bytesReadCalled.set(true);
            checker.accept(expectedThread.get(), expectedContext.get());
          }
          @Override
          public void bytesWritten(Void socketMetric, SocketAddress remoteAddress, long numberOfBytes) {
            bytesWrittenCalled.set(true);
            checker.accept(expectedThread.get(), expectedContext.get());
          }
          @Override
          public void close() {
            closeCalled.set(true);
          }
        };
      }
    };
    CountDownLatch latch = new CountDownLatch(1);
    Vertx vertx = vertx(new VertxOptions().setMetricsOptions(new MetricsOptions().setEnabled(true).setFactory(factory)));
    Context ctx = contextFactory.apply(vertx);
    ctx.runOnContext(v1 -> {
      HttpServer server = vertx.createHttpServer().websocketHandler(ws -> {
        ws.handler(buf -> {
          ws.write(Buffer.buffer("bye"));
        });
      });
      server.listen(8080, "localhost", onSuccess(s -> {
        expectedThread.set(Thread.currentThread());
        expectedContext.set(Vertx.currentContext());
        latch.countDown();
      }));
    });
    awaitLatch(latch);
    HttpClient client = vertx.createHttpClient();
    client.webSocket(8080, "localhost", "/", onSuccess(ws -> {
      ws.handler(buf -> {
        ws.closeHandler(v -> {
          vertx.close(v4 -> {
            assertTrue(websocketConnected.get());
            assertTrue(websocketDisconnected.get());
            assertTrue(bytesReadCalled.get());
            assertTrue(bytesWrittenCalled.get());
            assertTrue(socketConnectedCalled.get());
            assertTrue(socketDisconnectedCalled.get());
            assertTrue(closeCalled.get());
            testComplete();
          });
        });
        ws.close();
      });
      ws.write(Buffer.buffer("hello"));
    }));
    await();
  }

  @Ignore
  @Test
  public void testHttpClientRequestEventLoop() throws Exception {
    testHttpClientRequest(eventLoopContextFactory, eventLoopChecker);
  }

  @Ignore
  @Test
  public void testHttpClientRequestWorker() throws Exception {
    testHttpClientRequest(workerContextFactory, workerChecker);
  }

  private void testHttpClientRequest(Function<Vertx, Context> contextFactory, BiConsumer<Thread, Context> checker) throws Exception {
    AtomicReference<Thread> expectedThread = new AtomicReference<>();
    AtomicReference<Context> expectedContext = new AtomicReference<>();
    AtomicBoolean requestBeginCalled = new AtomicBoolean();
    AtomicBoolean responseEndCalled = new AtomicBoolean();
    AtomicBoolean socketConnectedCalled = new AtomicBoolean();
    AtomicBoolean socketDisconnectedCalled = new AtomicBoolean();
    AtomicBoolean bytesReadCalled = new AtomicBoolean();
    AtomicBoolean bytesWrittenCalled = new AtomicBoolean();
    AtomicBoolean closeCalled = new AtomicBoolean();
    VertxMetricsFactory factory = (options) -> new DummyVertxMetrics() {
      @Override
      public HttpClientMetrics createHttpClientMetrics(HttpClientOptions options) {
        return new DummyHttpClientMetrics() {
          @Override
          public Void requestBegin(Void endpointMetric, Void socketMetric, SocketAddress localAddress, SocketAddress remoteAddress, HttpClientRequest request) {
            requestBeginCalled.set(true);
            return null;
          }
          @Override
          public void responseEnd(Void requestMetric, HttpClientResponse response) {
            responseEndCalled.set(true);
          }
          @Override
          public Void connected(SocketAddress remoteAddress, String remoteName) {
            socketConnectedCalled.set(true);
            checker.accept(expectedThread.get(), expectedContext.get());
            return null;
          }
          @Override
          public void disconnected(Void socketMetric, SocketAddress remoteAddress) {
            socketDisconnectedCalled.set(true);
            checker.accept(expectedThread.get(), expectedContext.get());
          }
          @Override
          public void bytesRead(Void socketMetric, SocketAddress remoteAddress, long numberOfBytes) {
            bytesReadCalled.set(true);
            checker.accept(expectedThread.get(), expectedContext.get());
          }
          @Override
          public void bytesWritten(Void socketMetric, SocketAddress remoteAddress, long numberOfBytes) {
            bytesWrittenCalled.set(true);
            checker.accept(expectedThread.get(), expectedContext.get());
          }
          @Override
          public void close() {
            closeCalled.set(true);
          }
        };
      }
    };
    Vertx vertx = vertx(new VertxOptions().setMetricsOptions(new MetricsOptions().setEnabled(true).setFactory(factory)));
    HttpServer server = vertx.createHttpServer();
    server.requestHandler(req -> {
      req.endHandler(buf -> {
        HttpServerResponse resp = req.response();
        resp.setChunked(true).end(Buffer.buffer("bye"));
        resp.close();
      });
    });
    CountDownLatch latch = new CountDownLatch(1);
    server.listen(8080, "localhost", onSuccess(s -> {
      latch.countDown();
    }));
    awaitLatch(latch);
    Context ctx = contextFactory.apply(vertx);
    ctx.runOnContext(v1 -> {
      expectedThread.set(Thread.currentThread());
      expectedContext.set(Vertx.currentContext());
      HttpClient client = vertx.createHttpClient();
      checker.accept(expectedThread.get(), expectedContext.get());
      HttpClientRequest req = client.put(8080, "localhost", "/");
      req.setHandler(resp -> {
        executeInVanillaThread(() -> {
          client.close();
          vertx.close(v2 -> {
            assertTrue(requestBeginCalled.get());
            assertTrue(responseEndCalled.get());
            assertTrue(socketConnectedCalled.get());
            assertTrue(socketDisconnectedCalled.get());
            assertTrue(bytesReadCalled.get());
            assertTrue(bytesWrittenCalled.get());
            assertTrue(closeCalled.get());
            testComplete();
          });
        });
      });
      req.setChunked(true).write("hello");
      req.end();
    });
    await();
  }

  @Ignore
  @Test
  public void testHttpClientWebsocketEventLoop() throws Exception {
    testHttpClientWebsocket(eventLoopContextFactory, eventLoopChecker);
  }

  // FIXME!! This test intermittently fails
  @Test
  public void testHttpClientWebsocketWorker() throws Exception {
    testHttpClientWebsocket(workerContextFactory, workerChecker);
  }

  private void testHttpClientWebsocket(Function<Vertx, Context> contextFactory, BiConsumer<Thread, Context> checker) throws Exception {
    AtomicReference<Thread> expectedThread = new AtomicReference<>();
    AtomicReference<Context> expectedContext = new AtomicReference<>();
    AtomicBoolean websocketConnected = new AtomicBoolean();
    AtomicBoolean websocketDisconnected = new AtomicBoolean();
    AtomicBoolean socketConnectedCalled = new AtomicBoolean();
    AtomicBoolean socketDisconnectedCalled = new AtomicBoolean();
    AtomicBoolean bytesReadCalled = new AtomicBoolean();
    AtomicBoolean bytesWrittenCalled = new AtomicBoolean();
    AtomicBoolean closeCalled = new AtomicBoolean();
    VertxMetricsFactory factory = (options) -> new DummyVertxMetrics() {
      @Override
      public HttpClientMetrics createHttpClientMetrics(HttpClientOptions options) {
        return new DummyHttpClientMetrics() {
          @Override
          public Void connected(Void endpointMetric, Void socketMetric, WebSocket webSocket) {
            websocketConnected.set(true);
            checker.accept(expectedThread.get(), expectedContext.get());
            return null;
          }
          @Override
          public void disconnected(Void webSocketMetric) {
            websocketDisconnected.set(true);
          }
          @Override
          public Void connected(SocketAddress remoteAddress, String remoteName) {
            socketConnectedCalled.set(true);
            checker.accept(expectedThread.get(), expectedContext.get());
            return null;
          }
          @Override
          public void disconnected(Void socketMetric, SocketAddress remoteAddress) {
            socketDisconnectedCalled.set(true);
            checker.accept(expectedThread.get(), expectedContext.get());
          }
          @Override
          public void bytesRead(Void socketMetric, SocketAddress remoteAddress, long numberOfBytes) {
            bytesReadCalled.set(true);
            checker.accept(expectedThread.get(), expectedContext.get());
          }
          @Override
          public void bytesWritten(Void socketMetric, SocketAddress remoteAddress, long numberOfBytes) {
            bytesWrittenCalled.set(true);
            checker.accept(expectedThread.get(), expectedContext.get());
          }
          @Override
          public void close() {
            closeCalled.set(true);
          }
        };
      }
    };
    Vertx vertx = vertx(new VertxOptions().setMetricsOptions(new MetricsOptions().setEnabled(true).setFactory(factory)));
    HttpServer server = vertx.createHttpServer();
    server.websocketHandler(ws -> {
      ws.handler(buf -> {
        ws.write(Buffer.buffer("bye"));
      });
    });
    CountDownLatch latch = new CountDownLatch(1);
    server.listen(8080, "localhost", onSuccess(s -> {
      latch.countDown();
    }));
    awaitLatch(latch);
    Context ctx = contextFactory.apply(vertx);
    ctx.runOnContext(v1 -> {
      expectedThread.set(Thread.currentThread());
      expectedContext.set(Vertx.currentContext());
      HttpClient client = vertx.createHttpClient();
      checker.accept(expectedThread.get(), expectedContext.get());
      client.webSocket(8080, "localhost", "/", onSuccess(ws -> {
        ws.handler(buf -> {
          ws.closeHandler(v2 -> {
            executeInVanillaThread(() -> {
              client.close();
              vertx.close(v3 -> {
                assertTrue(websocketConnected.get());
                assertTrue(websocketDisconnected.get());
                assertTrue(socketConnectedCalled.get());
                assertTrue(socketDisconnectedCalled.get());
                assertTrue(bytesReadCalled.get());
                assertTrue(bytesWrittenCalled.get());
                assertTrue(closeCalled.get());
                testComplete();
              });
            });
          });
          ws.close();
        });
        ws.write(Buffer.buffer("hello"));
      }));
    });
    await();
  }

  @Ignore
  @Test
  public void testNetServerEventLoop() throws Exception {
    testNetServer(eventLoopContextFactory, eventLoopChecker);
  }

  @Ignore
  @Test
  public void testNetServerWorker() throws Exception {
    testNetServer(workerContextFactory, workerChecker);
  }

  private void testNetServer(Function<Vertx, Context> contextFactory, BiConsumer<Thread, Context> checker) throws Exception {
    AtomicReference<Thread> expectedThread = new AtomicReference<>();
    AtomicReference<Context> expectedContext = new AtomicReference<>();
    AtomicBoolean socketConnectedCalled = new AtomicBoolean();
    AtomicBoolean socketDisconnectedCalled = new AtomicBoolean();
    AtomicBoolean bytesReadCalled = new AtomicBoolean();
    AtomicBoolean bytesWrittenCalled = new AtomicBoolean();
    AtomicBoolean closeCalled = new AtomicBoolean();
    VertxMetricsFactory factory = (options) -> new DummyVertxMetrics() {
      @Override
      public TCPMetrics createNetServerMetrics(NetServerOptions options, SocketAddress localAddress) {
        return new DummyTCPMetrics() {
          @Override
          public Void connected(SocketAddress remoteAddress, String remoteName) {
            socketConnectedCalled.set(true);
            checker.accept(expectedThread.get(), expectedContext.get());
            return null;
          }
          @Override
          public void disconnected(Void socketMetric, SocketAddress remoteAddress) {
            socketDisconnectedCalled.set(true);
            checker.accept(expectedThread.get(), expectedContext.get());
          }
          @Override
          public void bytesRead(Void socketMetric, SocketAddress remoteAddress, long numberOfBytes) {
            bytesReadCalled.set(true);
            checker.accept(expectedThread.get(), expectedContext.get());
          }
          @Override
          public void bytesWritten(Void socketMetric, SocketAddress remoteAddress, long numberOfBytes) {
            bytesWrittenCalled.set(true);
            checker.accept(expectedThread.get(), expectedContext.get());
          }
          @Override
          public void close() {
            closeCalled.set(true);
          }
        };
      }
    };
    CountDownLatch latch = new CountDownLatch(1);
    Vertx vertx = vertx(new VertxOptions().setMetricsOptions(new MetricsOptions().setEnabled(true).setFactory(factory)));
    Context ctx = contextFactory.apply(vertx);
    ctx.runOnContext(v1 -> {
      NetServer server = vertx.createNetServer().connectHandler(so -> {
        so.handler(buf -> {
          so.write("bye");
        });
      });
      server.listen(1234, "localhost", onSuccess(s -> {
        expectedThread.set(Thread.currentThread());
        expectedContext.set(Vertx.currentContext());
        checker.accept(expectedThread.get(), expectedContext.get());
        latch.countDown();
      }));
    });
    awaitLatch(latch);
    NetClient client = vertx.createNetClient();
    client.connect(1234, "localhost", onSuccess(so -> {
      so.handler(buf -> {
        so.closeHandler(v -> {
          executeInVanillaThread(() -> {
            vertx.close(v4 -> {
              assertTrue(bytesReadCalled.get());
              assertTrue(bytesWrittenCalled.get());
              assertTrue(socketConnectedCalled.get());
              assertTrue(socketDisconnectedCalled.get());
              assertTrue(closeCalled.get());
              testComplete();
            });
          });
        });
        so.close();
      });
      so.write("hello");
    }));
    await();
  }

  @Ignore
  @Test
  public void testNetClientEventLoop() throws Exception {
    testNetClient(eventLoopContextFactory, eventLoopChecker);
  }

  @Ignore
  @Test
  public void testNetClientWorker() throws Exception {
    testNetClient(workerContextFactory, workerChecker);
  }

  private void testNetClient(Function<Vertx, Context> contextFactory, BiConsumer<Thread, Context> checker) throws Exception {
    AtomicReference<Thread> expectedThread = new AtomicReference<>();
    AtomicReference<Context> expectedContext = new AtomicReference<>();
    AtomicBoolean socketConnectedCalled = new AtomicBoolean();
    AtomicBoolean socketDisconnectedCalled = new AtomicBoolean();
    AtomicBoolean bytesReadCalled = new AtomicBoolean();
    AtomicBoolean bytesWrittenCalled = new AtomicBoolean();
    AtomicBoolean closeCalled = new AtomicBoolean();
    VertxMetricsFactory factory = (options) -> new DummyVertxMetrics() {
      @Override
      public TCPMetrics createNetClientMetrics(NetClientOptions options) {
        return new DummyTCPMetrics() {
          @Override
          public Void connected(SocketAddress remoteAddress, String remoteName) {
            socketConnectedCalled.set(true);
            checker.accept(expectedThread.get(), expectedContext.get());
            return null;
          }
          @Override
          public void disconnected(Void socketMetric, SocketAddress remoteAddress) {
            socketDisconnectedCalled.set(true);
            checker.accept(expectedThread.get(), expectedContext.get());
          }
          @Override
          public void bytesRead(Void socketMetric, SocketAddress remoteAddress, long numberOfBytes) {
            bytesReadCalled.set(true);
            checker.accept(expectedThread.get(), expectedContext.get());
          }
          @Override
          public void bytesWritten(Void socketMetric, SocketAddress remoteAddress, long numberOfBytes) {
            bytesWrittenCalled.set(true);
            checker.accept(expectedThread.get(), expectedContext.get());
          }
          @Override
          public void close() {
            closeCalled.set(true);
          }
        };
      }
    };
    CountDownLatch latch = new CountDownLatch(1);
    Vertx vertx = vertx(new VertxOptions().setMetricsOptions(new MetricsOptions().setEnabled(true).setFactory(factory)));
    Context ctx = contextFactory.apply(vertx);
    NetServer server = vertx.createNetServer().connectHandler(so -> {
      so.handler(buf -> {
        so.write("bye");
      });
    });
    server.listen(1234, "localhost", onSuccess(s -> {
      latch.countDown();
    }));
    awaitLatch(latch);
    ctx.runOnContext(v1 -> {
      NetClient client = vertx.createNetClient();
      expectedThread.set(Thread.currentThread());
      expectedContext.set(Vertx.currentContext());
      client.connect(1234, "localhost", onSuccess(so -> {
        so.handler(buf -> {
          so.closeHandler(v -> {
            assertTrue(bytesReadCalled.get());
            assertTrue(bytesWrittenCalled.get());
            assertTrue(socketConnectedCalled.get());
            assertTrue(socketDisconnectedCalled.get());
            executeInVanillaThread(() -> {
              client.close();
              vertx.close(v4 -> {
                assertTrue(closeCalled.get());
                testComplete();
              });
            });
          });
          so.close();
        });
        so.write("hello");
      }));
    });
    await();
  }

  @Ignore
  @Test
  public void testDatagramEventLoop() throws Exception {
    testDatagram(eventLoopContextFactory, eventLoopChecker);
  }

  @Ignore
  @Test
  public void testDatagramWorker() throws Exception {
    testDatagram(workerContextFactory, workerChecker);
  }

  private void testDatagram(Function<Vertx, Context> contextFactory, BiConsumer<Thread, Context> checker) throws Exception {
    AtomicReference<Thread> expectedThread = new AtomicReference<>();
    AtomicReference<Context> expectedContext = new AtomicReference<>();
    AtomicBoolean listening = new AtomicBoolean();
    AtomicBoolean bytesReadCalled = new AtomicBoolean();
    AtomicBoolean bytesWrittenCalled = new AtomicBoolean();
    CountDownLatch closeCalled = new CountDownLatch(1);
    VertxMetricsFactory factory = (options) -> new DummyVertxMetrics() {
      @Override
      public DatagramSocketMetrics createDatagramSocketMetrics(DatagramSocketOptions options) {
        return new DummyDatagramMetrics() {
          @Override
          public void listening(String localName, SocketAddress localAddress) {
            listening.set(true);
            checker.accept(expectedThread.get(), expectedContext.get());
          }
          @Override
          public void bytesRead(Void socketMetric, SocketAddress remoteAddress, long numberOfBytes) {
            bytesReadCalled.set(true);
            checker.accept(expectedThread.get(), expectedContext.get());
          }
          @Override
          public void bytesWritten(Void socketMetric, SocketAddress remoteAddress, long numberOfBytes) {
            bytesWrittenCalled.set(true);
            checker.accept(expectedThread.get(), expectedContext.get());
          }
          @Override
          public void close() {
            closeCalled.countDown();
          }
        };
      }
    };
    Vertx vertx = vertx(new VertxOptions().setMetricsOptions(new MetricsOptions().setEnabled(true).setFactory(factory)));
    Context ctx = contextFactory.apply(vertx);
    ctx.runOnContext(v1 -> {
      expectedThread.set(Thread.currentThread());
      expectedContext.set(Vertx.currentContext());
      DatagramSocket socket = vertx.createDatagramSocket();
      socket.listen(1234, "localhost", ar1 -> {
        assertTrue(ar1.succeeded());
        checker.accept(expectedThread.get(), expectedContext.get());
        socket.handler(packet -> {
          assertTrue(listening.get());
          assertTrue(bytesReadCalled.get());
          assertTrue(bytesWrittenCalled.get());
          executeInVanillaThread(socket::close);
        });
        socket.send(Buffer.buffer("msg"), 1234, "localhost", ar2 -> {
          assertTrue(ar2.succeeded());
        });
      });
    });
    awaitLatch(closeCalled);
  }

  @Ignore
  @Test
  public void testEventBusLifecycle() {
    AtomicBoolean closeCalled = new AtomicBoolean();
    VertxMetricsFactory factory = (options) -> new DummyVertxMetrics() {
      @Override
      public EventBusMetrics createEventBusMetrics() {
        return new DummyEventBusMetrics() {
          @Override
          public void close() {
            closeCalled.set(true);
          }
        };
      }
    };
    Vertx vertx = vertx(new VertxOptions().setMetricsOptions(new MetricsOptions().setEnabled(true).setFactory(factory)));
    vertx.eventBus();
    executeInVanillaThread(() -> {
      vertx.close(onSuccess(v -> {
        assertTrue(closeCalled.get());
        testComplete();
      }));
    });
    await();
  }

  @Ignore
  @Test
  public void testMessageHandler() {
    testMessageHandler((vertx, handler) -> handler.handle(null), eventLoopChecker);
  }

  @Ignore
  @Test
  public void testMessageHandlerEventLoop() {
    testMessageHandler((vertx, handler) -> eventLoopContextFactory.apply(vertx).runOnContext(handler), eventLoopChecker);
  }

  private void testMessageHandler(BiConsumer<Vertx, Handler<Void>> runOnContext, BiConsumer<Thread, Context> checker) {
    AtomicReference<Thread> consumerThread = new AtomicReference<>();
    AtomicReference<Context> consumerContext = new AtomicReference<>();
    AtomicBoolean registeredCalled = new AtomicBoolean();
    AtomicBoolean unregisteredCalled = new AtomicBoolean();
    AtomicBoolean beginHandleCalled = new AtomicBoolean();
    AtomicBoolean endHandleCalled = new AtomicBoolean();
    VertxMetricsFactory factory = (options) -> new DummyVertxMetrics() {
      @Override
      public EventBusMetrics createEventBusMetrics() {
        return new DummyEventBusMetrics() {
          @Override
          public Void handlerRegistered(String address, String repliedAddress) {
            registeredCalled.set(true);
            return null;
          }
          @Override
          public void handlerUnregistered(Void handler) {
            unregisteredCalled.set(true);
          }
          @Override
          public void beginHandleMessage(Void handler, boolean local) {
            consumerThread.set(Thread.currentThread());
            consumerContext.set(Vertx.currentContext());
            beginHandleCalled.set(true);
          }
          @Override
          public void endHandleMessage(Void handler, Throwable failure) {
            endHandleCalled.set(true);
            checker.accept(consumerThread.get(), consumerContext.get());
          }
        };
      }
    };
    Vertx vertx = vertx(new VertxOptions().setMetricsOptions(new MetricsOptions().setEnabled(true).setFactory(factory)));
    EventBus eb = vertx.eventBus();
    runOnContext.accept(vertx, v -> {
      MessageConsumer<Object> consumer = eb.consumer("the_address");
      consumer.handler(msg -> {
        checker.accept(consumerThread.get(), consumerContext.get());
        executeInVanillaThread(() -> {
          vertx.getOrCreateContext().runOnContext(v2 -> {
            consumer.unregister(onSuccess(v3 -> {
              assertTrue(registeredCalled.get());
              assertTrue(beginHandleCalled.get());
              assertTrue(endHandleCalled.get());
              assertWaitUntil(() -> unregisteredCalled.get());
              testComplete();
            }));
          });
        });
      }).completionHandler(onSuccess(v2 -> {
        eb.send("the_address", "the_msg");
      }));
    });
    await();
  }

  @Ignore
  @Test
  public void testDeployEventLoop() {
    testDeploy(false, eventLoopChecker);
  }

  @Ignore
  @Test
  public void testDeployWorker() {
    testDeploy(true, workerChecker);
  }

  private void testDeploy(boolean worker, BiConsumer<Thread, Context> checker) {
    AtomicReference<Thread> verticleThread = new AtomicReference<>();
    AtomicReference<Context> verticleContext = new AtomicReference<>();
    AtomicBoolean deployedCalled = new AtomicBoolean();
    AtomicBoolean undeployedCalled = new AtomicBoolean();
    VertxMetricsFactory factory = (options) -> new DummyVertxMetrics() {
      @Override
      public void verticleDeployed(Verticle verticle) {
        deployedCalled.set(true);
        checker.accept(verticleThread.get(), verticleContext.get());
      }
      @Override
      public void verticleUndeployed(Verticle verticle) {
        undeployedCalled.set(true);
        checker.accept(verticleThread.get(), verticleContext.get());
      }
    };
    Vertx vertx = vertx(new VertxOptions().setMetricsOptions(new MetricsOptions().setEnabled(true).setFactory(factory)));
    vertx.deployVerticle(new AbstractVerticle() {
      @Override
      public void start() throws Exception {
        verticleThread.set(Thread.currentThread());
        verticleContext.set(Vertx.currentContext());
      }
    }, new DeploymentOptions().setWorker(worker), ar1 -> {
      assertTrue(ar1.succeeded());
      vertx.undeploy(ar1.result(), ar2 -> {
        assertTrue(ar1.succeeded());
        assertTrue(deployedCalled.get());
        assertTrue(undeployedCalled.get());
        testComplete();
      });
    });
    await();
  }

  private void executeInVanillaThread(Runnable task) {
    new Thread(task).start();
  }

  private Function<Vertx, Context> eventLoopContextFactory = Vertx::getOrCreateContext;

  private BiConsumer<Thread, Context> eventLoopChecker = (thread, ctx) -> {
    assertSame(Thread.currentThread(), thread);
  };

  private Function<Vertx, Context> workerContextFactory = vertx -> {
    AtomicReference<Context> ctx = new AtomicReference<>();
    vertx.deployVerticle(new AbstractVerticle() {
      @Override
      public void start() throws Exception {
        ctx.set(context);
        super.start();
      }
    }, new DeploymentOptions().setWorker(true));
    assertWaitUntil(() -> ctx.get() != null);
    return ctx.get();
  };

  private BiConsumer<Thread, Context> workerChecker = (thread, ctx) -> {
    assertTrue(Context.isOnWorkerThread());
  };
}
