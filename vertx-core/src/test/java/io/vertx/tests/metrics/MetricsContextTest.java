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

package io.vertx.tests.metrics;

import io.vertx.core.*;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.datagram.DatagramSocket;
import io.vertx.core.datagram.DatagramSocketOptions;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.eventbus.EventBusOptions;
import io.vertx.core.eventbus.MessageConsumer;
import io.vertx.core.http.*;
import io.vertx.core.metrics.MetricsOptions;
import io.vertx.core.net.*;
import io.vertx.core.spi.VertxMetricsFactory;
import io.vertx.core.spi.metrics.*;
import io.vertx.core.spi.observability.HttpRequest;
import io.vertx.core.spi.observability.HttpResponse;
import io.vertx.test.core.TestUtils;
import io.vertx.test.core.VertxTestBase;
import io.vertx.test.fakecluster.FakeClusterManager;
import io.vertx.test.http.HttpTestBase;
import org.junit.Ignore;
import org.junit.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class MetricsContextTest extends VertxTestBase {

  @Test
  public void testFactory() throws Exception {
    AtomicReference<Thread> metricsThread = new AtomicReference<>();
    AtomicReference<Context> metricsContext = new AtomicReference<>();
    VertxMetricsFactory factory = (options) -> {
      metricsThread.set(Thread.currentThread());
      metricsContext.set(Vertx.currentContext());
      return new VertxMetrics() {
      };
    };
    vertx(() -> Vertx.builder()
      .with(new VertxOptions().setMetricsOptions(new MetricsOptions().setEnabled(true)))
      .withMetrics(factory)
      .build());
    assertNull(metricsContext.get());
  }

  @Test
  public void testFactoryInCluster() throws Exception {
    AtomicReference<Thread> metricsThread = new AtomicReference<>();
    AtomicReference<Context> metricsContext = new AtomicReference<>();
    Thread testThread = Thread.currentThread();
    VertxMetricsFactory factory = (options) -> {
      metricsThread.set(Thread.currentThread());
      metricsContext.set(Vertx.currentContext());
      return new VertxMetrics() {
      };
    };
    VertxBuilder builder = Vertx.builder().with(new VertxOptions()
        .setMetricsOptions(new MetricsOptions().setEnabled(true))
        .setEventBusOptions(new EventBusOptions()))
      .withClusterManager(getClusterManager())
      .withMetrics(factory);
    builder
      .withClusterManager(new FakeClusterManager())
      .buildClustered()
      .onComplete(onSuccess(vertx -> {
        assertSame(testThread, metricsThread.get());
        assertNull(metricsContext.get());
        vertx.close();
        testComplete();
      }));
    await();
  }

  @Test
  public void testHttpServerRequestEventLoop() throws Exception {
    testHttpServerRequest(eventLoopContextFactory);
  }

  @Test
  public void testHttpServerRequestWorker() throws Exception {
    testHttpServerRequest(workerContextFactory);
  }

  private void testHttpServerRequest(Function<Vertx, Context> contextFactory) throws Exception {
    waitFor(2);
    AtomicReference<Thread> expectedThread = new AtomicReference<>();
    AtomicReference<Context> expectedContext = new AtomicReference<>();
    AtomicBoolean requestBeginCalled = new AtomicBoolean();
    AtomicBoolean responseEndCalled = new AtomicBoolean();
    AtomicBoolean socketConnectedCalled = new AtomicBoolean();
    AtomicBoolean socketDisconnectedCalled = new AtomicBoolean();
    AtomicBoolean bytesReadCalled = new AtomicBoolean();
    AtomicBoolean bytesWrittenCalled = new AtomicBoolean();
    AtomicBoolean closeCalled = new AtomicBoolean();
    VertxMetricsFactory factory = (options) -> new VertxMetrics() {
      @Override
      public HttpServerMetrics createHttpServerMetrics(HttpServerOptions options, SocketAddress localAddress) {
        return new HttpServerMetrics<Void, Void, Void>() {
          @Override
          public Void requestBegin(Void socketMetric, HttpRequest request) {
            requestBeginCalled.set(true);
            return null;
          }
          @Override
          public void responseEnd(Void requestMetric, HttpResponse response, long bytesWritten) {
            responseEndCalled.set(true);
          }
          @Override
          public Void connected(SocketAddress remoteAddress, String remoteName) {
            socketConnectedCalled.set(true);
            return null;
          }
          @Override
          public void disconnected(Void socketMetric, SocketAddress remoteAddress) {
            socketDisconnectedCalled.set(true);
          }
          @Override
          public void bytesRead(Void socketMetric, SocketAddress remoteAddress, long numberOfBytes) {
            bytesReadCalled.set(true);
          }
          @Override
          public void bytesWritten(Void socketMetric, SocketAddress remoteAddress, long numberOfBytes) {
            bytesWrittenCalled.set(true);
          }
          @Override
          public void close() {
            closeCalled.set(true);
          }
        };
      }
    };
    CountDownLatch latch = new CountDownLatch(1);
    Vertx vertx = vertx(() -> Vertx.builder()
      .with(new VertxOptions().setMetricsOptions(new MetricsOptions().setEnabled(true)))
      .withMetrics(factory)
      .build());
    Context ctx = contextFactory.apply(vertx);
    ctx.runOnContext(v1 -> {
      HttpServer server = vertx.createHttpServer().requestHandler(req -> {
        HttpServerResponse response = req.response();
        response.setStatusCode(200).setChunked(true).end("bye");
        req.connection().close();
      });
      server.listen(HttpTestBase.DEFAULT_HTTP_PORT, "localhost").onComplete(onSuccess(s -> {
        expectedThread.set(Thread.currentThread());
        expectedContext.set(Vertx.currentContext());
        latch.countDown();
      }));
    });
    awaitLatch(latch);
    HttpClient client = vertx.httpClientBuilder()
      .withConnectHandler(conn -> {
        conn.closeHandler(v -> {
          vertx.close().onComplete(v4 -> {
            assertTrue(requestBeginCalled.get());
            assertTrue(responseEndCalled.get());
            assertTrue(bytesReadCalled.get());
            assertTrue(bytesWrittenCalled.get());
            assertTrue(socketConnectedCalled.get());
            assertTrue(socketDisconnectedCalled.get());
            assertTrue(closeCalled.get());
            complete();
          });
        });
      })
      .build();
    client.request(HttpMethod.PUT, HttpTestBase.DEFAULT_HTTP_PORT, "localhost", "/")
      .compose(req -> req.send(Buffer.buffer("hello"))
        .onComplete(onSuccess(resp -> {
          complete();
        })));
    await();
  }

  @Test
  public void testHttpServerRequestPipelining() throws Exception {
    waitFor(2);
    AtomicInteger count = new AtomicInteger();
    VertxMetricsFactory factory = (options) -> new VertxMetrics() {
      @Override
      public HttpServerMetrics createHttpServerMetrics(HttpServerOptions options, SocketAddress localAddress) {
        return new HttpServerMetrics<Void, Void, Void>() {
          @Override
          public Void requestBegin(Void socketMetric, HttpRequest request) {
            switch (request.uri()) {
              case "/1":
                assertEquals(0, count.get());
                break;
              case "/2":
                assertEquals(1, count.get());
                break;
            }
            return null;
          }
          @Override
          public void requestEnd(Void requestMetric, HttpRequest request, long bytesRead) {
            switch (request.uri()) {
              case "/1":
                assertEquals(1, count.get());
                break;
              case "/2":
                assertEquals(2, count.get());
                break;
            }
          }
          @Override
          public void responseEnd(Void requestMetric, HttpResponse response, long bytesWritten) {
          }
          @Override
          public Void connected(SocketAddress remoteAddress, String remoteName) {
            return null;
          }
          @Override
          public void disconnected(Void socketMetric, SocketAddress remoteAddress) {
          }
          @Override
          public void bytesRead(Void socketMetric, SocketAddress remoteAddress, long numberOfBytes) {
          }
          @Override
          public void bytesWritten(Void socketMetric, SocketAddress remoteAddress, long numberOfBytes) {
          }
          @Override
          public void close() {
          }
        };
      }
    };
    CountDownLatch latch = new CountDownLatch(1);
    Vertx vertx = vertx(() -> Vertx.builder()
      .with(new VertxOptions().setMetricsOptions(new MetricsOptions().setEnabled(true)))
      .withMetrics(factory)
      .build());
    HttpServer server = vertx.createHttpServer().requestHandler(req -> {
      count.incrementAndGet();
      vertx.setTimer(10, id -> {
        HttpServerResponse response = req.response();
        response.end();
      });
    });
    server.listen(HttpTestBase.DEFAULT_HTTP_PORT, "localhost").onComplete(onSuccess(s -> {
      latch.countDown();
    }));
    awaitLatch(latch);
    HttpClient client = vertx.createHttpClient(new HttpClientOptions().setPipelining(true), new PoolOptions().setHttp1MaxSize(1));
    vertx.runOnContext(v -> {
      for (int i = 0;i < 2;i++) {
        client.request(HttpMethod.GET, HttpTestBase.DEFAULT_HTTP_PORT, "localhost", "/" + (i + 1)).onComplete(onSuccess(req -> {
          req.send().compose(HttpClientResponse::body).onComplete(onSuccess(body -> {
            complete();
          }));
        }));
      }
    });
    await();
  }

  @Test
  public void testHttpServerWebSocketEventLoop() throws Exception {
    testHttpServerWebSocket(eventLoopContextFactory);
  }

  @Ignore("Uncomment later after the inbound read queue merge")
  @Test
  public void testHttpServerWebSocketWorker() throws Exception {
    testHttpServerWebSocket(workerContextFactory);
  }

  private void testHttpServerWebSocket(Function<Vertx, Context> contextFactory) throws Exception {
    AtomicReference<Thread> expectedThread = new AtomicReference<>();
    AtomicReference<Context> expectedContext = new AtomicReference<>();
    AtomicBoolean webSocketConnected = new AtomicBoolean();
    AtomicBoolean webSocketDisconnected = new AtomicBoolean();
    AtomicBoolean socketConnectedCalled = new AtomicBoolean();
    AtomicBoolean socketDisconnectedCalled = new AtomicBoolean();
    AtomicBoolean bytesReadCalled = new AtomicBoolean();
    AtomicBoolean bytesWrittenCalled = new AtomicBoolean();
    AtomicBoolean closeCalled = new AtomicBoolean();
    AtomicInteger httpLifecycle = new AtomicInteger();
    VertxMetricsFactory factory = (options) -> new VertxMetrics() {
      @Override
      public HttpServerMetrics createHttpServerMetrics(HttpServerOptions options, SocketAddress localAddress) {
        return new HttpServerMetrics<Void, Void, Void>() {
          @Override
          public Void requestBegin(Void socketMetric, HttpRequest request) {
            assertEquals(0, httpLifecycle.getAndIncrement());
            return null;
          }
          @Override
          public void requestEnd(Void requestMetric, HttpRequest request, long bytesRead) {
            assertEquals(1, httpLifecycle.getAndIncrement());
          }
          @Override
          public void responseBegin(Void requestMetric, HttpResponse response) {
            assertEquals(2, httpLifecycle.getAndIncrement());
          }
          @Override
          public void responseEnd(Void requestMetric, HttpResponse response, long bytesWritten) {
            assertEquals(3, httpLifecycle.getAndIncrement());
          }
          @Override
          public Void connected(Void socketMetric, Void requestMetric, ServerWebSocket serverWebSocket) {
            assertEquals(4, httpLifecycle.get());
            webSocketConnected.set(true);
            return null;
          }
          @Override
          public void disconnected(Void serverWebSocketMetric) {
            assertEquals(4, httpLifecycle.get());
            webSocketDisconnected.set(true);
          }
          @Override
          public Void connected(SocketAddress remoteAddress, String remoteName) {
            socketConnectedCalled.set(true);
            return null;
          }
          @Override
          public void disconnected(Void socketMetric, SocketAddress remoteAddress) {
            socketDisconnectedCalled.set(true);
          }
          @Override
          public void bytesRead(Void socketMetric, SocketAddress remoteAddress, long numberOfBytes) {
            bytesReadCalled.set(true);
          }
          @Override
          public void bytesWritten(Void socketMetric, SocketAddress remoteAddress, long numberOfBytes) {
            bytesWrittenCalled.set(true);
          }
          @Override
          public void close() {
            closeCalled.set(true);
          }
        };
      }
    };
    CountDownLatch latch = new CountDownLatch(1);
    Vertx vertx = vertx(() -> Vertx.builder()
      .with(new VertxOptions().setMetricsOptions(new MetricsOptions().setEnabled(true)))
      .withMetrics(factory)
      .build());
    Context ctx = contextFactory.apply(vertx);
    ctx.runOnContext(v1 -> {
      HttpServer server = vertx.createHttpServer().webSocketHandler(ws -> {
        ws.handler(buf -> {
          ws.write(Buffer.buffer("bye"));
        });
      });
      server.listen(HttpTestBase.DEFAULT_HTTP_PORT, "localhost").onComplete(onSuccess(s -> {
        expectedThread.set(Thread.currentThread());
        expectedContext.set(Vertx.currentContext());
        latch.countDown();
      }));
    });
    awaitLatch(latch);
    WebSocketClient client = vertx.createWebSocketClient();
    client.connect(HttpTestBase.DEFAULT_HTTP_PORT, "localhost", "/").onComplete(onSuccess(ws -> {
      ws.handler(buf -> {
        ws.closeHandler(v -> {
          vertx.close().onComplete(v4 -> {
            assertTrue(webSocketConnected.get());
            assertTrue(webSocketDisconnected.get());
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

  @Test
  public void testHttpClientRequestEventLoop() throws Exception {
    testHttpClientRequest(eventLoopContextFactory);
  }

  @Test
  public void testHttpClientRequestWorker() throws Exception {
    testHttpClientRequest(workerContextFactory);
  }

  private void testHttpClientRequest(Function<Vertx, Context> contextFactory) throws Exception {
    AtomicReference<Thread> expectedThread = new AtomicReference<>();
    AtomicReference<Context> expectedContext = new AtomicReference<>();
    AtomicReference<String> requestBeginCalled = new AtomicReference();
    AtomicBoolean responseEndCalled = new AtomicBoolean();
    AtomicBoolean socketConnectedCalled = new AtomicBoolean();
    AtomicBoolean socketDisconnectedCalled = new AtomicBoolean();
    AtomicBoolean bytesReadCalled = new AtomicBoolean();
    AtomicBoolean bytesWrittenCalled = new AtomicBoolean();
    AtomicBoolean closeCalled = new AtomicBoolean();
    VertxMetricsFactory factory = (options) -> new VertxMetrics() {
      @Override
      public HttpClientMetrics createHttpClientMetrics(HttpClientOptions options) {
        return new HttpClientMetrics<Void, Void, Void>() {
          @Override
          public ClientMetrics<Void, HttpRequest, HttpResponse> createEndpointMetrics(SocketAddress remoteAddress, int maxPoolSize) {
            return new ClientMetrics<>() {
              @Override
              public Void requestBegin(String uri, HttpRequest request) {
                requestBeginCalled.set(uri);
                return null;
              }
              @Override
              public void responseEnd(Void requestMetric, long bytesRead) {
                responseEndCalled.set(true);
              }
            };
          }
          @Override
          public Void connected(SocketAddress remoteAddress, String remoteName) {
            socketConnectedCalled.set(true);
            return null;
          }
          @Override
          public void disconnected(Void socketMetric, SocketAddress remoteAddress) {
            socketDisconnectedCalled.set(true);
          }
          @Override
          public void bytesRead(Void socketMetric, SocketAddress remoteAddress, long numberOfBytes) {
            bytesReadCalled.set(true);
          }
          @Override
          public void bytesWritten(Void socketMetric, SocketAddress remoteAddress, long numberOfBytes) {
            bytesWrittenCalled.set(true);
          }
          @Override
          public void close() {
            closeCalled.set(true);
          }
        };
      }
    };
    Vertx vertx = vertx(() -> Vertx.builder()
      .with(new VertxOptions().setMetricsOptions(new MetricsOptions().setEnabled(true)))
      .withMetrics(factory)
      .build());
    HttpServer server = vertx.createHttpServer();
    server.requestHandler(req -> {
      req.endHandler(buf -> {
        HttpServerResponse resp = req.response();
        resp.setChunked(true).end(Buffer.buffer("bye"));
        req.connection().close();
      });
    });
    awaitFuture(server.listen(HttpTestBase.DEFAULT_HTTP_PORT, "localhost"));
    Context ctx = contextFactory.apply(vertx);
    ctx.runOnContext(v1 -> {
      expectedThread.set(Thread.currentThread());
      expectedContext.set(Vertx.currentContext());
      HttpClient client = vertx.createHttpClient();
      assertSame(expectedThread.get(), Thread.currentThread());
      client.request(HttpMethod.PUT, HttpTestBase.DEFAULT_HTTP_PORT, "localhost", "/the-uri")
        .compose(req -> req.send(Buffer.buffer("hello")).onComplete(onSuccess(resp -> {
          TestUtils.executeInVanillaVertxThread(() -> {
            client.close();
            Future<Void> close = vertx.close();
            close.onComplete(v2 -> {
              assertEquals("/the-uri", requestBeginCalled.get());
              assertTrue(responseEndCalled.get());
              assertTrue(socketConnectedCalled.get());
              assertTrue(socketDisconnectedCalled.get());
              assertTrue(bytesReadCalled.get());
              assertTrue(bytesWrittenCalled.get());
              assertTrue(closeCalled.get());
              testComplete();
            });
          });
        })));
    });
    await();
  }

  @Test
  public void testHttpClientWebSocketEventLoop() throws Exception {
    testHttpClientWebSocket(eventLoopContextFactory);
  }

  @Test
  public void testHttpClientWebSocketWorker() throws Exception {
    testHttpClientWebSocket(workerContextFactory);
  }

  private void testHttpClientWebSocket(Function<Vertx, Context> contextFactory) throws Exception {
    AtomicBoolean webSocketConnected = new AtomicBoolean();
    AtomicBoolean webSocketDisconnected = new AtomicBoolean();
    AtomicBoolean socketConnectedCalled = new AtomicBoolean();
    AtomicBoolean socketDisconnectedCalled = new AtomicBoolean();
    AtomicBoolean bytesReadCalled = new AtomicBoolean();
    AtomicBoolean bytesWrittenCalled = new AtomicBoolean();
    AtomicBoolean closeCalled = new AtomicBoolean();
    VertxMetricsFactory factory = (options) -> new VertxMetrics() {
      @Override
      public HttpClientMetrics createHttpClientMetrics(HttpClientOptions options) {
        return new HttpClientMetrics<Void, Void, Void>() {
          @Override
          public ClientMetrics<Void, HttpRequest, HttpResponse> createEndpointMetrics(SocketAddress remoteAddress, int maxPoolSize) {
            return new ClientMetrics<>() {
            };
          }
          @Override
          public Void connected(WebSocket webSocket) {
            webSocketConnected.set(true);
            return null;
          }
          @Override
          public void disconnected(Void webSocketMetric) {
            webSocketDisconnected.set(true);
          }
          @Override
          public Void connected(SocketAddress remoteAddress, String remoteName) {
            socketConnectedCalled.set(true);
            return null;
          }
          @Override
          public void disconnected(Void socketMetric, SocketAddress remoteAddress) {
            socketDisconnectedCalled.set(true);
          }
          @Override
          public void bytesRead(Void socketMetric, SocketAddress remoteAddress, long numberOfBytes) {
            bytesReadCalled.set(true);
          }
          @Override
          public void bytesWritten(Void socketMetric, SocketAddress remoteAddress, long numberOfBytes) {
            bytesWrittenCalled.set(true);
          }
          @Override
          public void close() {
            closeCalled.set(true);
          }
        };
      }
    };
    Vertx vertx = vertx(() -> Vertx.builder()
      .with(new VertxOptions().setMetricsOptions(new MetricsOptions().setEnabled(true)))
      .withMetrics(factory)
      .build());
    HttpServer server = vertx.createHttpServer();
    server.webSocketHandler(ws -> {
      ws.handler(buf -> {
        ws.write(Buffer.buffer("bye"));
      });
    });
    awaitFuture(server.listen(HttpTestBase.DEFAULT_HTTP_PORT, "localhost"));
    Context ctx = contextFactory.apply(vertx);
    ctx.runOnContext(v1 -> {
      WebSocketClient client = vertx.createWebSocketClient();
      client.connect(HttpTestBase.DEFAULT_HTTP_PORT, "localhost", "/").onComplete(onSuccess(ws -> {
        ws.handler(buf -> {
          ws.closeHandler(v2 -> {
            TestUtils.executeInVanillaVertxThread(() -> {
              client.close();
              vertx.close().onComplete(v3 -> {
                assertTrue(webSocketConnected.get());
                assertTrue(webSocketDisconnected.get());
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

  @Test
  public void testNetServerEventLoop() throws Exception {
    testNetServer(eventLoopContextFactory);
  }

  @Test
  public void testNetServerWorker() throws Exception {
    testNetServer(workerContextFactory);
  }

  private void testNetServer(Function<Vertx, Context> contextFactory) throws Exception {
    AtomicReference<Thread> expectedThread = new AtomicReference<>();
    AtomicReference<Context> expectedContext = new AtomicReference<>();
    AtomicBoolean socketConnectedCalled = new AtomicBoolean();
    AtomicBoolean socketDisconnectedCalled = new AtomicBoolean();
    AtomicBoolean bytesReadCalled = new AtomicBoolean();
    AtomicBoolean bytesWrittenCalled = new AtomicBoolean();
    AtomicBoolean closeCalled = new AtomicBoolean();
    VertxMetricsFactory factory = (options) -> new VertxMetrics() {
      @Override
      public TCPMetrics createNetServerMetrics(NetServerOptions options, SocketAddress localAddress) {
        return new TCPMetrics<Void>() {
          @Override
          public Void connected(SocketAddress remoteAddress, String remoteName) {
            socketConnectedCalled.set(true);
            return null;
          }
          @Override
          public void disconnected(Void socketMetric, SocketAddress remoteAddress) {
            socketDisconnectedCalled.set(true);
          }
          @Override
          public void bytesRead(Void socketMetric, SocketAddress remoteAddress, long numberOfBytes) {
            bytesReadCalled.set(true);
          }
          @Override
          public void bytesWritten(Void socketMetric, SocketAddress remoteAddress, long numberOfBytes) {
            bytesWrittenCalled.set(true);
          }
          @Override
          public void close() {
            closeCalled.set(true);
          }
        };
      }
    };
    CountDownLatch latch = new CountDownLatch(1);
    Vertx vertx = vertx(() -> Vertx.builder()
      .with(new VertxOptions().setMetricsOptions(new MetricsOptions().setEnabled(true)))
      .withMetrics(factory)
      .build());
    Context ctx = contextFactory.apply(vertx);
    ctx.runOnContext(v1 -> {
      NetServer server = vertx.createNetServer().connectHandler(so -> {
        so.handler(buf -> {
          so.write("bye");
        });
      });
      server.listen(1234, "localhost").onComplete(onSuccess(s -> {
        expectedThread.set(Thread.currentThread());
        expectedContext.set(Vertx.currentContext());
        assertSame(expectedThread.get(), Thread.currentThread());
        latch.countDown();
      }));
    });
    awaitLatch(latch);
    NetClient client = vertx.createNetClient();
    client.connect(1234, "localhost").onComplete(onSuccess(so -> {
      so.handler(buf -> {
        so.closeHandler(v -> {
          TestUtils.executeInVanillaVertxThread(() -> {
            vertx.close().onComplete(v4 -> {
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

  @Test
  public void testNetClientEventLoop() throws Exception {
    testNetClient(eventLoopContextFactory);
  }

  @Test
  public void testNetClientWorker() throws Exception {
    testNetClient(workerContextFactory);
  }

  private void testNetClient(Function<Vertx, Context> contextFactory) throws Exception {
    AtomicReference<Thread> expectedThread = new AtomicReference<>();
    AtomicReference<Context> expectedContext = new AtomicReference<>();
    AtomicBoolean socketConnectedCalled = new AtomicBoolean();
    AtomicBoolean socketDisconnectedCalled = new AtomicBoolean();
    AtomicBoolean bytesReadCalled = new AtomicBoolean();
    AtomicBoolean bytesWrittenCalled = new AtomicBoolean();
    AtomicBoolean closeCalled = new AtomicBoolean();
    VertxMetricsFactory factory = (options) -> new VertxMetrics() {
      @Override
      public TCPMetrics createNetClientMetrics(NetClientOptions options) {
        return new TCPMetrics<Void>() {
          @Override
          public Void connected(SocketAddress remoteAddress, String remoteName) {
            socketConnectedCalled.set(true);
            return null;
          }
          @Override
          public void disconnected(Void socketMetric, SocketAddress remoteAddress) {
            socketDisconnectedCalled.set(true);
          }
          @Override
          public void bytesRead(Void socketMetric, SocketAddress remoteAddress, long numberOfBytes) {
            bytesReadCalled.set(true);
          }
          @Override
          public void bytesWritten(Void socketMetric, SocketAddress remoteAddress, long numberOfBytes) {
            bytesWrittenCalled.set(true);
          }
          @Override
          public void close() {
            closeCalled.set(true);
          }
        };
      }
    };
    Vertx vertx = vertx(() -> Vertx.builder()
      .with(new VertxOptions().setMetricsOptions(new MetricsOptions().setEnabled(true)))
      .withMetrics(factory)
      .build());
    Context ctx = contextFactory.apply(vertx);
    NetServer server = vertx.createNetServer().connectHandler(so -> {
      so.handler(buf -> {
        so.write("bye");
      });
    });
    awaitFuture(server.listen(1234, "localhost"));
    ctx.runOnContext(v1 -> {
      NetClient client = vertx.createNetClient();
      expectedThread.set(Thread.currentThread());
      expectedContext.set(Vertx.currentContext());
      client.connect(1234, "localhost").onComplete(onSuccess(so -> {
        so.handler(buf -> {
          so.closeHandler(v -> {
            assertTrue(bytesReadCalled.get());
            assertTrue(bytesWrittenCalled.get());
            assertTrue(socketConnectedCalled.get());
            assertTrue(socketDisconnectedCalled.get());
            TestUtils.executeInVanillaVertxThread(() -> {
              client.close();
              vertx.close().onComplete(v4 -> {
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

  @Test
  public void testDatagramEventLoop() throws Exception {
    testDatagram(eventLoopContextFactory);
  }

  @Test
  public void testDatagramWorker() throws Exception {
    testDatagram(workerContextFactory);
  }

  private void testDatagram(Function<Vertx, Context> contextFactory) throws Exception {
    AtomicReference<Thread> expectedThread = new AtomicReference<>();
    AtomicReference<Context> expectedContext = new AtomicReference<>();
    AtomicBoolean listening = new AtomicBoolean();
    AtomicBoolean bytesReadCalled = new AtomicBoolean();
    AtomicBoolean bytesWrittenCalled = new AtomicBoolean();
    CountDownLatch closeCalled = new CountDownLatch(1);
    VertxMetricsFactory factory = (options) -> new VertxMetrics() {
      @Override
      public DatagramSocketMetrics createDatagramSocketMetrics(DatagramSocketOptions options) {
        return new DatagramSocketMetrics() {
          @Override
          public void listening(String localName, SocketAddress localAddress) {
            listening.set(true);
          }
          @Override
          public void bytesRead(Void socketMetric, SocketAddress remoteAddress, long numberOfBytes) {
            bytesReadCalled.set(true);
          }
          @Override
          public void bytesWritten(Void socketMetric, SocketAddress remoteAddress, long numberOfBytes) {
            bytesWrittenCalled.set(true);
          }
          @Override
          public void close() {
            closeCalled.countDown();
          }
        };
      }
    };
    Vertx vertx = vertx(() -> Vertx.builder()
      .with(new VertxOptions().setMetricsOptions(new MetricsOptions().setEnabled(true)))
      .withMetrics(factory)
      .build());
    Context ctx = contextFactory.apply(vertx);
    ctx.runOnContext(v1 -> {
      expectedThread.set(Thread.currentThread());
      expectedContext.set(Vertx.currentContext());
      DatagramSocket socket = vertx.createDatagramSocket();
      socket.listen(1234, "localhost").onComplete(onSuccess(v2 -> {
        socket.handler(packet -> {
          assertTrue(listening.get());
          assertTrue(bytesReadCalled.get());
          assertTrue(bytesWrittenCalled.get());
          TestUtils.executeInVanillaVertxThread(socket::close);
        });
        socket.send(Buffer.buffer("msg"), 1234, "localhost");
      }));
    });
    awaitLatch(closeCalled);
  }

  @Test
  public void testEventBusLifecycle() {
    AtomicBoolean closeCalled = new AtomicBoolean();
    VertxMetricsFactory factory = (options) -> new VertxMetrics() {
      @Override
      public EventBusMetrics createEventBusMetrics() {
        return new EventBusMetrics<Void>() {
          @Override
          public void close() {
            closeCalled.set(true);
          }
        };
      }
    };
    Vertx vertx = vertx(() -> Vertx.builder()
      .with(new VertxOptions().setMetricsOptions(new MetricsOptions().setEnabled(true)))
      .withMetrics(factory)
      .build());
    vertx.eventBus();
    TestUtils.executeInVanillaVertxThread(() -> {
      vertx.close().onComplete(onSuccess(v -> {
        assertTrue(closeCalled.get());
        testComplete();
      }));
    });
    await();
  }

  @Test
  public void testMessageHandler() {
    testMessageHandler((vertx, handler) -> handler.handle(null));
  }

  @Test
  public void testMessageHandlerEventLoop() {
    testMessageHandler((vertx, handler) -> eventLoopContextFactory.apply(vertx).runOnContext(handler));
  }

  private void testMessageHandler(BiConsumer<Vertx, Handler<Void>> runOnContext) {
    AtomicReference<Thread> scheduleThread = new AtomicReference<>();
    AtomicReference<Thread> deliveredThread = new AtomicReference<>();
    AtomicBoolean registeredCalled = new AtomicBoolean();
    AtomicBoolean unregisteredCalled = new AtomicBoolean();
    VertxMetricsFactory factory = (options) -> new VertxMetrics() {
      @Override
      public EventBusMetrics createEventBusMetrics() {
        return new EventBusMetrics<Void>() {
          @Override
          public Void handlerRegistered(String address) {
            registeredCalled.set(true);
            return null;
          }
          @Override
          public void handlerUnregistered(Void handler) {
            unregisteredCalled.set(true);
          }
          @Override
          public void scheduleMessage(Void handler, boolean local) {
            scheduleThread.set(Thread.currentThread());
          }
          @Override
          public void messageDelivered(Void handler, boolean local) {
            deliveredThread.set(Thread.currentThread());
          }
        };
      }
    };
    Vertx vertx = vertx(() -> Vertx.builder()
      .with(new VertxOptions().setMetricsOptions(new MetricsOptions().setEnabled(true)))
      .withMetrics(factory)
      .build());
    EventBus eb = vertx.eventBus();
    Thread t = new Thread(() -> {
      eb.send("the_address", "the_msg");
    });
    runOnContext.accept(vertx, v -> {
      MessageConsumer<Object> consumer = eb.consumer("the_address");
      consumer.handler(msg -> {
        Thread consumerThread = Thread.currentThread();
        TestUtils.executeInVanillaVertxThread(() -> {
          vertx.getOrCreateContext().runOnContext(v2 -> {
            consumer.unregister().onComplete(onSuccess(v3 -> {
              assertTrue(registeredCalled.get());
              assertSame(t, scheduleThread.get());
              assertSame(consumerThread, deliveredThread.get());
              assertWaitUntil(() -> unregisteredCalled.get());
              testComplete();
            }));
          });
        });
      }).completion().onComplete(onSuccess(v2 -> {
        t.start();
      }));
    });
    await();
  }

  private Function<Vertx, Context> eventLoopContextFactory = Vertx::getOrCreateContext;

  private Function<Vertx, Context> workerContextFactory = vertx -> {
    AtomicReference<Context> ctx = new AtomicReference<>();
    vertx.deployVerticle(new AbstractVerticle() {
      @Override
      public void start() throws Exception {
        ctx.set(context);
        super.start();
      }
    }, new DeploymentOptions().setThreadingModel(ThreadingModel.WORKER));
    assertWaitUntil(() -> ctx.get() != null);
    return ctx.get();
  };
}
