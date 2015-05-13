/*
 * Copyright (c) 2011-2013 The original author or authors
 *  ------------------------------------------------------
 *  All rights reserved. This program and the accompanying materials
 *  are made available under the terms of the Eclipse Public License v1.0
 *  and Apache License v2.0 which accompanies this distribution.
 *
 *      The Eclipse Public License is available at
 *      http://www.eclipse.org/legal/epl-v10.html
 *
 *      The Apache License v2.0 is available at
 *      http://www.opensource.org/licenses/apache2.0.php
 *
 *  You may elect to redistribute this code under either of these licenses.
 */

package io.vertx.test.core;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Context;
import io.vertx.core.Verticle;
import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.datagram.DatagramSocket;
import io.vertx.core.datagram.DatagramSocketOptions;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.eventbus.MessageConsumer;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.http.HttpClientResponse;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.http.ServerWebSocket;
import io.vertx.core.http.WebSocket;
import io.vertx.core.metrics.MetricsOptions;
import io.vertx.core.metrics.impl.DummyVertxMetrics;
import io.vertx.core.net.NetClient;
import io.vertx.core.net.NetClientOptions;
import io.vertx.core.net.NetServer;
import io.vertx.core.net.NetServerOptions;
import io.vertx.core.net.NetSocket;
import io.vertx.core.net.SocketAddress;
import io.vertx.core.spi.metrics.DatagramSocketMetrics;
import io.vertx.core.spi.metrics.EventBusMetrics;
import io.vertx.core.spi.metrics.HttpClientMetrics;
import io.vertx.core.spi.metrics.HttpServerMetrics;
import io.vertx.core.spi.metrics.TCPMetrics;
import org.junit.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class MetricsContextTest extends AsyncTestBase {

  @Test
  public void testFactory() throws Exception {
    AtomicReference<Thread> metricsThread = new AtomicReference<>();
    AtomicReference<Context> metricsContext = new AtomicReference<>();
    ConfigurableMetricsFactory.delegate = (vertx, options) -> {
      metricsThread.set(Thread.currentThread());
      metricsContext.set(Vertx.currentContext());
      return new DummyVertxMetrics();
    };
    Vertx vertx = Vertx.vertx(new VertxOptions().setMetricsOptions(new MetricsOptions().setEnabled(true)));
    assertSame(Thread.currentThread(), metricsThread.get());
    assertNull(metricsContext.get());
  }

  @Test
  public void testFactoryInCluster() throws Exception {
    AtomicReference<Thread> metricsThread = new AtomicReference<>();
    AtomicReference<Context> metricsContext = new AtomicReference<>();
    Thread testThread = Thread.currentThread();
    ConfigurableMetricsFactory.delegate = (vertx, options) -> {
      metricsThread.set(Thread.currentThread());
      metricsContext.set(Vertx.currentContext());
      return new DummyVertxMetrics();
    };
    Vertx.clusteredVertx(new VertxOptions().setClustered(true).setMetricsOptions(new MetricsOptions().setEnabled(true)), ar -> {
      assertTrue(ar.succeeded());
      assertSame(testThread, metricsThread.get());
      assertNull(metricsContext.get());
      testComplete();
    });
    await();
  }

  @Test
  public void testHttpServerRequest() throws Exception {
    AtomicReference<Thread> createThread = new AtomicReference<>();
    AtomicReference<Context> createContext = new AtomicReference<>();
    AtomicBoolean requestBeginCalled = new AtomicBoolean();
    AtomicBoolean responseEndCalled = new AtomicBoolean();
    AtomicBoolean socketConnectedCalled = new AtomicBoolean();
    AtomicBoolean socketDisconnectedCalled = new AtomicBoolean();
    AtomicBoolean bytesReadCalled = new AtomicBoolean();
    AtomicBoolean bytesWrittenCalled = new AtomicBoolean();
    AtomicBoolean closeCalled = new AtomicBoolean();
    ConfigurableMetricsFactory.delegate = (vertx, options) -> new DummyVertxMetrics() {
      @Override
      public HttpServerMetrics createMetrics(HttpServer server, SocketAddress localAddress, HttpServerOptions options) {
        createThread.set(Thread.currentThread());
        createContext.set(Vertx.currentContext());
        return new DummyHttpServerMetrics() {
          @Override
          public Void requestBegin(Void socketMetric, HttpServerRequest request) {
            requestBeginCalled.set(true);
            assertSame(Vertx.currentContext(), createContext.get());
            assertSame(Thread.currentThread(), createThread.get());
            return null;
          }
          @Override
          public void responseEnd(Void requestMetric, HttpServerResponse response) {
            responseEndCalled.set(true);
            assertSame(Vertx.currentContext(), createContext.get());
            assertSame(Thread.currentThread(), createThread.get());
          }
          @Override
          public Void connected(SocketAddress remoteAddress) {
            socketConnectedCalled.set(true);
            assertSame(Vertx.currentContext(), createContext.get());
            assertSame(Thread.currentThread(), createThread.get());
            return null;
          }
          @Override
          public void disconnected(Void socketMetric, SocketAddress remoteAddress) {
            socketDisconnectedCalled.set(true);
            assertSame(Thread.currentThread(), createThread.get());
            assertSame(Vertx.currentContext(), createContext.get());
          }
          @Override
          public void bytesRead(Void socketMetric, SocketAddress remoteAddress, long numberOfBytes) {
            bytesReadCalled.set(true);
            assertSame(Vertx.currentContext(), createContext.get());
            assertSame(Thread.currentThread(), createThread.get());
          }
          @Override
          public void bytesWritten(Void socketMetric, SocketAddress remoteAddress, long numberOfBytes) {
            bytesWrittenCalled.set(true);
            assertSame(Vertx.currentContext(), createContext.get());
            assertSame(Thread.currentThread(), createThread.get());
          }
          @Override
          public boolean isEnabled() {
            return true;
          }
          @Override
          public void close() {
            closeCalled.set(true);
            assertSame(Vertx.currentContext(), createContext.get());
            assertSame(Thread.currentThread(), createThread.get());
          }
        };
      }
    };
    CountDownLatch latch = new CountDownLatch(1);
    Vertx vertx = Vertx.vertx(new VertxOptions().setMetricsOptions(new MetricsOptions().setEnabled(true)));
    Context ctx = vertx.getOrCreateContext();
    ctx.runOnContext(v1 -> {
      Thread ctxThread = Thread.currentThread();
      HttpServer server = vertx.createHttpServer().requestHandler(req -> {
        req.endHandler(v2 -> {
          HttpServerResponse response = req.response();
          req.netSocket().closeHandler(v3 -> {
            assertTrue(requestBeginCalled.get());
            assertTrue(responseEndCalled.get());
            assertTrue(bytesReadCalled.get());
            assertTrue(bytesWrittenCalled.get());
            assertTrue(socketConnectedCalled.get());
            assertTrue(socketDisconnectedCalled.get());
            executeInVanillaThread(() -> {
              vertx.close(v4 -> {
                assertTrue(closeCalled.get());
                testComplete();
              });
            });
          });
          response.setChunked(true).write("bye").end();
          response.close();
        });
      });
      server.listen(8080, "localhost", ar -> {
        assertTrue(ar.succeeded());
        assertSame(ctxThread, createThread.get());
        assertSame(ctx, createContext.get());
        latch.countDown();
      });
    });
    awaitLatch(latch);
    HttpClient client = vertx.createHttpClient();
    client.put(8080, "localhost", "/", resp -> {
    }).setChunked(true).write(Buffer.buffer("hello")).end();
    await();
  }

  @Test
  public void testHttpServerWebsocket() throws Exception {
    AtomicReference<Thread> createThread = new AtomicReference<>();
    AtomicReference<Context> createContext = new AtomicReference<>();
    AtomicBoolean websocketConnected = new AtomicBoolean();
    AtomicBoolean websocketDisconnected = new AtomicBoolean();
    AtomicBoolean socketConnectedCalled = new AtomicBoolean();
    AtomicBoolean socketDisconnectedCalled = new AtomicBoolean();
    AtomicBoolean bytesReadCalled = new AtomicBoolean();
    AtomicBoolean bytesWrittenCalled = new AtomicBoolean();
    AtomicBoolean closeCalled = new AtomicBoolean();
    ConfigurableMetricsFactory.delegate = (vertx, options) -> new DummyVertxMetrics() {
      @Override
      public HttpServerMetrics createMetrics(HttpServer server, SocketAddress localAddress, HttpServerOptions options) {
        createThread.set(Thread.currentThread());
        createContext.set(Vertx.currentContext());
        return new DummyHttpServerMetrics() {
          @Override
          public Void connected(Void socketMetric, ServerWebSocket serverWebSocket) {
            websocketConnected.set(true);
            assertSame(Vertx.currentContext(), createContext.get());
            assertSame(Thread.currentThread(), createThread.get());
            return null;
          }
          @Override
          public void disconnected(Void serverWebSocketMetric) {
            websocketDisconnected.set(true);
            assertSame(Vertx.currentContext(), createContext.get());
            assertSame(Thread.currentThread(), createThread.get());
          }
          @Override
          public Void connected(SocketAddress remoteAddress) {
            socketConnectedCalled.set(true);
            assertSame(Vertx.currentContext(), createContext.get());
            assertSame(Thread.currentThread(), createThread.get());
            return null;
          }
          @Override
          public void disconnected(Void socketMetric, SocketAddress remoteAddress) {
            socketDisconnectedCalled.set(true);
            assertSame(Thread.currentThread(), createThread.get());
            assertSame(Vertx.currentContext(), createContext.get());
          }
          @Override
          public void bytesRead(Void socketMetric, SocketAddress remoteAddress, long numberOfBytes) {
            bytesReadCalled.set(true);
            assertSame(Vertx.currentContext(), createContext.get());
            assertSame(Thread.currentThread(), createThread.get());
          }
          @Override
          public void bytesWritten(Void socketMetric, SocketAddress remoteAddress, long numberOfBytes) {
            bytesWrittenCalled.set(true);
            assertSame(Vertx.currentContext(), createContext.get());
            assertSame(Thread.currentThread(), createThread.get());
          }
          @Override
          public boolean isEnabled() {
            return true;
          }
          @Override
          public void close() {
            closeCalled.set(true);
            assertSame(Vertx.currentContext(), createContext.get());
            assertSame(Thread.currentThread(), createThread.get());
          }
        };
      }
    };
    CountDownLatch latch = new CountDownLatch(1);
    Vertx vertx = Vertx.vertx(new VertxOptions().setMetricsOptions(new MetricsOptions().setEnabled(true)));
    Context ctx = vertx.getOrCreateContext();
    ctx.runOnContext(v1 -> {
      Thread ctxThread = Thread.currentThread();
      HttpServer server = vertx.createHttpServer().websocketHandler(ws -> {
        ws.handler(buf -> {
          ws.write(Buffer.buffer("bye"));
        });
        ws.closeHandler(v2 -> {
          assertTrue(websocketConnected.get());
          assertTrue(websocketDisconnected.get());
          assertTrue(bytesReadCalled.get());
          assertTrue(bytesWrittenCalled.get());
          assertTrue(socketConnectedCalled.get());
          assertTrue(socketDisconnectedCalled.get());
          executeInVanillaThread(() -> {
            vertx.close(v4 -> {
              assertTrue(closeCalled.get());
              testComplete();
            });
          });
        });
      });
      server.listen(8080, "localhost", ar -> {
        assertTrue(ar.succeeded());
        assertSame(ctxThread, createThread.get());
        assertSame(ctx, createContext.get());
        latch.countDown();
      });
    });
    awaitLatch(latch);
    HttpClient client = vertx.createHttpClient();
    client.websocket(8080, "localhost", "/", ws -> {
      ws.handler(buf -> {
        ws.close();
      });
      ws.write(Buffer.buffer("hello"));
    });
    await();
  }

  @Test
  public void testHttpClientRequest() throws Exception {
    AtomicReference<Thread> createThread = new AtomicReference<>();
    AtomicReference<Context> createContext = new AtomicReference<>();
    AtomicBoolean requestBeginCalled = new AtomicBoolean();
    AtomicBoolean responseEndCalled = new AtomicBoolean();
    AtomicBoolean socketConnectedCalled = new AtomicBoolean();
    AtomicBoolean socketDisconnectedCalled = new AtomicBoolean();
    AtomicBoolean bytesReadCalled = new AtomicBoolean();
    AtomicBoolean bytesWrittenCalled = new AtomicBoolean();
    AtomicBoolean closeCalled = new AtomicBoolean();
    ConfigurableMetricsFactory.delegate = (vertx, options) -> new DummyVertxMetrics() {
      @Override
      public HttpClientMetrics createMetrics(HttpClient client, HttpClientOptions options) {
        createThread.set(Thread.currentThread());
        createContext.set(Vertx.currentContext());
        return new DummyHttpClientMetrics() {
          @Override
          public Void requestBegin(Void socketMetric, SocketAddress localAddress, SocketAddress remoteAddress, HttpClientRequest request) {
            requestBeginCalled.set(true);
            return null;
          }
          @Override
          public void responseEnd(Void requestMetric, HttpClientResponse response) {
            responseEndCalled.set(true);
          }
          @Override
          public Void connected(SocketAddress remoteAddress) {
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
          @Override
          public boolean isEnabled() {
            return true;
          }
        };
      }
    };
    Vertx vertx = Vertx.vertx(new VertxOptions().setMetricsOptions(new MetricsOptions().setEnabled(true)));
    HttpServer server = vertx.createHttpServer();
    server.requestHandler(req -> {
      req.endHandler(buf -> {
        HttpServerResponse resp = req.response();
        resp.setChunked(true).write(Buffer.buffer("bye")).end();
        resp.close();
      });
    });
    CountDownLatch latch = new CountDownLatch(1);
    server.listen(8080, "localhost", ar -> {
      assertTrue(ar.succeeded());
      latch.countDown();
    });
    awaitLatch(latch);
    Context ctx = vertx.getOrCreateContext();
    ctx.runOnContext(v1 -> {
      Thread ctxThread = Thread.currentThread();
      HttpClient client = vertx.createHttpClient();
      assertSame(ctxThread, createThread.get());
      assertSame(ctx, createContext.get());
      HttpClientRequest req = client.put(8080, "localhost", "/");
      req.handler(resp -> {
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

  @Test
  public void testHttpClientWebsocket() throws Exception {
    AtomicReference<Thread> createThread = new AtomicReference<>();
    AtomicReference<Context> createContext = new AtomicReference<>();
    AtomicBoolean websocketConnected = new AtomicBoolean();
    AtomicBoolean websocketDisconnected = new AtomicBoolean();
    AtomicBoolean socketConnectedCalled = new AtomicBoolean();
    AtomicBoolean socketDisconnectedCalled = new AtomicBoolean();
    AtomicBoolean bytesReadCalled = new AtomicBoolean();
    AtomicBoolean bytesWrittenCalled = new AtomicBoolean();
    AtomicBoolean closeCalled = new AtomicBoolean();
    ConfigurableMetricsFactory.delegate = (vertx, options) -> new DummyVertxMetrics() {
      @Override
      public HttpClientMetrics createMetrics(HttpClient client, HttpClientOptions options) {
        createThread.set(Thread.currentThread());
        createContext.set(Vertx.currentContext());
        return new DummyHttpClientMetrics() {
          @Override
          public Void connected(Void socketMetric, WebSocket webSocket) {
            websocketConnected.set(true);
            return null;
          }
          @Override
          public void disconnected(Void webSocketMetric) {
            websocketDisconnected.set(true);
          }
          @Override
          public Void connected(SocketAddress remoteAddress) {
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
          @Override
          public boolean isEnabled() {
            return true;
          }
        };
      }
    };
    Vertx vertx = Vertx.vertx(new VertxOptions().setMetricsOptions(new MetricsOptions().setEnabled(true)));
    HttpServer server = vertx.createHttpServer();
    server.websocketHandler(ws -> {
      ws.handler(buf -> {
        ws.write(Buffer.buffer("bye"));
      });
    });
    server.requestHandler(req -> {
      req.endHandler(buf -> {
        HttpServerResponse resp = req.response();
        resp.setChunked(true).write(Buffer.buffer("bye")).end();
        resp.close();
      });
    });
    CountDownLatch latch = new CountDownLatch(1);
    server.listen(8080, "localhost", ar -> {
      assertTrue(ar.succeeded());
      latch.countDown();
    });
    awaitLatch(latch);
    Context ctx = vertx.getOrCreateContext();
    ctx.runOnContext(v1 -> {
      Thread ctxThread = Thread.currentThread();
      HttpClient client = vertx.createHttpClient();
      assertSame(ctxThread, createThread.get());
      assertSame(ctx, createContext.get());
      client.websocket(8080, "localhost", "/", ws -> {
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
      });
    });
    await();
  }

  @Test
  public void testNetServer() throws Exception {
    AtomicReference<Thread> createThread = new AtomicReference<>();
    AtomicReference<Context> createContext = new AtomicReference<>();
    AtomicBoolean socketConnectedCalled = new AtomicBoolean();
    AtomicBoolean socketDisconnectedCalled = new AtomicBoolean();
    AtomicBoolean bytesReadCalled = new AtomicBoolean();
    AtomicBoolean bytesWrittenCalled = new AtomicBoolean();
    AtomicBoolean closeCalled = new AtomicBoolean();
    ConfigurableMetricsFactory.delegate = (vertx, options) -> new DummyVertxMetrics() {
      @Override
      public TCPMetrics createMetrics(NetServer server, SocketAddress localAddress, NetServerOptions options) {
        createThread.set(Thread.currentThread());
        createContext.set(Vertx.currentContext());
        return new DummyTCPMetrics() {
          @Override
          public Void connected(SocketAddress remoteAddress) {
            socketConnectedCalled.set(true);
            assertSame(Vertx.currentContext(), createContext.get());
            assertSame(Thread.currentThread(), createThread.get());
            return null;
          }
          @Override
          public void disconnected(Void socketMetric, SocketAddress remoteAddress) {
            socketDisconnectedCalled.set(true);
            assertSame(Thread.currentThread(), createThread.get());
            assertSame(Vertx.currentContext(), createContext.get());
          }
          @Override
          public void bytesRead(Void socketMetric, SocketAddress remoteAddress, long numberOfBytes) {
            bytesReadCalled.set(true);
            assertSame(Vertx.currentContext(), createContext.get());
            assertSame(Thread.currentThread(), createThread.get());
          }
          @Override
          public void bytesWritten(Void socketMetric, SocketAddress remoteAddress, long numberOfBytes) {
            bytesWrittenCalled.set(true);
            assertSame(Vertx.currentContext(), createContext.get());
            assertSame(Thread.currentThread(), createThread.get());
          }
          @Override
          public boolean isEnabled() {
            return true;
          }
          @Override
          public void close() {
            closeCalled.set(true);
            assertSame(Vertx.currentContext(), createContext.get());
            assertSame(Thread.currentThread(), createThread.get());
          }
        };
      }
    };
    CountDownLatch latch = new CountDownLatch(1);
    Vertx vertx = Vertx.vertx(new VertxOptions().setMetricsOptions(new MetricsOptions().setEnabled(true)));
    Context ctx = vertx.getOrCreateContext();
    ctx.runOnContext(v1 -> {
      Thread ctxThread = Thread.currentThread();
      NetServer server = vertx.createNetServer().connectHandler(so -> {
        so.handler(buf -> {
          so.write("bye");
          so.closeHandler(v -> {
            assertTrue(bytesReadCalled.get());
            assertTrue(bytesWrittenCalled.get());
            assertTrue(socketConnectedCalled.get());
            assertTrue(socketDisconnectedCalled.get());
            executeInVanillaThread(() -> {
              vertx.close(v4 -> {
                assertTrue(closeCalled.get());
                testComplete();
              });
            });
          });
          so.close();
        });
      });
      server.listen(1234, "localhost", ar -> {
        assertTrue(ar.succeeded());
        assertSame(ctxThread, createThread.get());
        assertSame(ctx, createContext.get());
        latch.countDown();
      });
    });
    awaitLatch(latch);
    NetClient client = vertx.createNetClient();
    client.connect(1234, "localhost", ar -> {
      assertTrue(ar.succeeded());
      NetSocket so = ar.result();
      so.write("hello");
    });
    await();
  }

  @Test
  public void testNetClient() throws Exception {
    AtomicReference<Thread> createThread = new AtomicReference<>();
    AtomicReference<Context> createContext = new AtomicReference<>();
    AtomicBoolean socketConnectedCalled = new AtomicBoolean();
    AtomicBoolean socketDisconnectedCalled = new AtomicBoolean();
    AtomicBoolean bytesReadCalled = new AtomicBoolean();
    AtomicBoolean bytesWrittenCalled = new AtomicBoolean();
    AtomicBoolean closeCalled = new AtomicBoolean();
    ConfigurableMetricsFactory.delegate = (vertx, options) -> new DummyVertxMetrics() {
      @Override
      public TCPMetrics createMetrics(NetClient client, NetClientOptions options) {
        createThread.set(Thread.currentThread());
        createContext.set(Vertx.currentContext());
        return new DummyTCPMetrics() {
          @Override
          public Void connected(SocketAddress remoteAddress) {
            socketConnectedCalled.set(true);
            assertSame(Vertx.currentContext(), createContext.get());
            assertSame(Thread.currentThread(), createThread.get());
            return null;
          }
          @Override
          public void disconnected(Void socketMetric, SocketAddress remoteAddress) {
            socketDisconnectedCalled.set(true);
            assertSame(Thread.currentThread(), createThread.get());
            assertSame(Vertx.currentContext(), createContext.get());
          }
          @Override
          public void bytesRead(Void socketMetric, SocketAddress remoteAddress, long numberOfBytes) {
            bytesReadCalled.set(true);
            assertSame(Vertx.currentContext(), createContext.get());
            assertSame(Thread.currentThread(), createThread.get());
          }
          @Override
          public void bytesWritten(Void socketMetric, SocketAddress remoteAddress, long numberOfBytes) {
            bytesWrittenCalled.set(true);
            assertSame(Vertx.currentContext(), createContext.get());
            assertSame(Thread.currentThread(), createThread.get());
          }
          @Override
          public boolean isEnabled() {
            return true;
          }
          @Override
          public void close() {
            closeCalled.set(true);
            assertSame(Vertx.currentContext(), createContext.get());
            assertSame(Thread.currentThread(), createThread.get());
          }
        };
      }
    };
    CountDownLatch latch = new CountDownLatch(1);
    Vertx vertx = Vertx.vertx(new VertxOptions().setMetricsOptions(new MetricsOptions().setEnabled(true)));
    Context ctx = vertx.getOrCreateContext();
    NetServer server = vertx.createNetServer().connectHandler(so -> {
      so.handler(buf -> {
        so.write("bye");
      });
    });
    server.listen(1234, "localhost", ar -> {
      assertTrue(ar.succeeded());
      latch.countDown();
    });
    awaitLatch(latch);
    ctx.runOnContext(v1 -> {
      Thread ctxThread = Thread.currentThread();
      NetClient client = vertx.createNetClient();
      assertSame(ctxThread, createThread.get());
      assertSame(ctx, createContext.get());
      client.connect(1234, "localhost", ar -> {
        assertTrue(ar.succeeded());
        NetSocket so = ar.result();
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
      });
    });
    await();
  }

  @Test
  public void testDatagram() {
    AtomicReference<Thread> createThread = new AtomicReference<>();
    AtomicReference<Context> createContext = new AtomicReference<>();
    AtomicBoolean listening = new AtomicBoolean();
    AtomicBoolean bytesReadCalled = new AtomicBoolean();
    AtomicBoolean bytesWrittenCalled = new AtomicBoolean();
    AtomicBoolean closeCalled = new AtomicBoolean();
    ConfigurableMetricsFactory.delegate = (vertx, options) -> new DummyVertxMetrics() {
      @Override
      public DatagramSocketMetrics createMetrics(DatagramSocket socket, DatagramSocketOptions options) {
        createThread.set(Thread.currentThread());
        createContext.set(Vertx.currentContext());
        return new DummyDatagramMetrics() {
          @Override
          public void listening(SocketAddress localAddress) {
            listening.set(true);
            assertSame(Thread.currentThread(), createThread.get());
            assertSame(Vertx.currentContext(), createContext.get());
          }
          @Override
          public void bytesRead(Void socketMetric, SocketAddress remoteAddress, long numberOfBytes) {
            bytesReadCalled.set(true);
            assertSame(Thread.currentThread(), createThread.get());
            assertSame(Vertx.currentContext(), createContext.get());
          }
          @Override
          public void bytesWritten(Void socketMetric, SocketAddress remoteAddress, long numberOfBytes) {
            bytesWrittenCalled.set(true);
            assertSame(Thread.currentThread(), createThread.get());
            assertSame(Vertx.currentContext(), createContext.get());
          }
          @Override
          public void close() {
            closeCalled.set(true);
            assertSame(Thread.currentThread(), createThread.get());
            assertSame(Vertx.currentContext(), createContext.get());
          }
          @Override
          public boolean isEnabled() {
            return true;
          }
        };
      }
    };
    Vertx vertx = Vertx.vertx(new VertxOptions().setMetricsOptions(new MetricsOptions().setEnabled(true)));
    Context ctx = vertx.getOrCreateContext();
    ctx.runOnContext(v1 -> {
      Thread ctxThread = Thread.currentThread();
      DatagramSocket socket = vertx.createDatagramSocket();
      socket.listen(1234, "localhost", ar1 -> {
        assertTrue(ar1.succeeded());
        assertSame(ctxThread, createThread.get());
        assertSame(ctx, createContext.get());
        socket.handler(packet -> {
          assertTrue(listening.get());
          assertTrue(bytesReadCalled.get());
          assertTrue(bytesWrittenCalled.get());
          executeInVanillaThread(() -> {
            socket.close(ar2 -> {
              assertTrue(closeCalled.get());
              assertTrue(ar2.succeeded());
              testComplete();
            });
          });
        });
        socket.send(Buffer.buffer("msg"), 1234, "localhost", ar2 -> {
          assertTrue(ar2.succeeded());
        });
      });
    });
    await();
  }

  @Test
  public void testEventBusLifecycle() {
    AtomicReference<Thread> createThread = new AtomicReference<>();
    AtomicReference<Context> createContext = new AtomicReference<>();
    AtomicBoolean closeCalled = new AtomicBoolean();
    ConfigurableMetricsFactory.delegate = (vertx, options) -> new DummyVertxMetrics() {
      @Override
      public EventBusMetrics createMetrics(EventBus eventBus) {
        createThread.set(Thread.currentThread());
        createContext.set(Vertx.currentContext());
        return new DummyEventBusMetrics() {
          @Override
          public boolean isEnabled() {
            return true;
          }
          @Override
          public void close() {
            closeCalled.set(true);
          }
        };
      }
    };
    Vertx vertx = Vertx.vertx(new VertxOptions().setMetricsOptions(new MetricsOptions().setEnabled(true)));
    vertx.eventBus();
    assertSame(Thread.currentThread(), createThread.get());
    assertNull(createContext.get());
    executeInVanillaThread(() -> {
      vertx.close(ar -> {
        assertTrue(ar.succeeded());
        assertTrue(closeCalled.get());
        testComplete();
      });
    });
    await();
  }

  @Test
  public void testMessageHandler() {
    AtomicReference<Thread> registerThread = new AtomicReference<>();
    AtomicReference<Context> registerContext = new AtomicReference<>();
    AtomicBoolean unregisteredCalled = new AtomicBoolean();
    AtomicBoolean beginHandleCalled = new AtomicBoolean();
    AtomicBoolean endHandleCalled = new AtomicBoolean();
    ConfigurableMetricsFactory.delegate = (vertx, options) -> new DummyVertxMetrics() {
      @Override
      public EventBusMetrics createMetrics(EventBus eventBus) {
        return new DummyEventBusMetrics() {
          @Override
          public boolean isEnabled() {
            return true;
          }
          @Override
          public Void handlerRegistered(String address, boolean replyHandler) {
            registerThread.set(Thread.currentThread());
            registerContext.set(Vertx.currentContext());
            return null;
          }
          @Override
          public void handlerUnregistered(Void handler) {
            unregisteredCalled.set(true);
            assertSame(registerThread.get(), Thread.currentThread());
            assertSame(registerContext.get(), Vertx.currentContext());
          }
          @Override
          public void beginHandleMessage(Void handler, boolean local) {
            beginHandleCalled.set(true);
            assertSame(registerThread.get(), Thread.currentThread());
            assertSame(registerContext.get(), Vertx.currentContext());
          }
          @Override
          public void endHandleMessage(Void handler, Throwable failure) {
            endHandleCalled.set(true);
            assertSame(registerThread.get(), Thread.currentThread());
            assertSame(registerContext.get(), Vertx.currentContext());
          }
        };
      }
    };
    Vertx vertx = Vertx.vertx(new VertxOptions().setMetricsOptions(new MetricsOptions().setEnabled(true)));
    EventBus eb = vertx.eventBus();
    MessageConsumer<Object> consumer = eb.consumer("the_address");
    consumer.handler(msg -> {
      Context ctx = Vertx.currentContext();
      Thread ctxThread = Thread.currentThread();
      assertSame(ctxThread, registerThread.get());
      assertSame(ctx, registerContext.get());
      executeInVanillaThread(() -> {
        vertx.getOrCreateContext().runOnContext(v -> {
          consumer.unregister(ar -> {
            assertTrue(beginHandleCalled.get());
            assertTrue(endHandleCalled.get());
            waitUntil(() -> unregisteredCalled.get());
            testComplete();
          });
        });
      });
    }).completionHandler(ar -> {
      assertTrue(ar.succeeded());
      eb.send("the_address", "the_msg");
    });
    await();
  }

  @Test
  public void testDeploy() {
    AtomicReference<Thread> verticleThread = new AtomicReference<>();
    AtomicReference<Context> verticleContext = new AtomicReference<>();
    AtomicBoolean deployedCalled = new AtomicBoolean();
    AtomicBoolean undeployedCalled = new AtomicBoolean();
    ConfigurableMetricsFactory.delegate = (vertx, options) -> new DummyVertxMetrics() {
      @Override
      public void verticleDeployed(Verticle verticle) {
        assertSame(verticleThread.get(), Thread.currentThread());
        assertSame(verticleContext.get(), Vertx.currentContext());
        deployedCalled.set(true);
      }
      @Override
      public void verticleUndeployed(Verticle verticle) {
        assertSame(verticleThread.get(), Thread.currentThread());
        assertSame(verticleContext.get(), Vertx.currentContext());
        undeployedCalled.set(true);
      }
    };
    Vertx vertx = Vertx.vertx(new VertxOptions().setMetricsOptions(new MetricsOptions().setEnabled(true)));
    vertx.deployVerticle(new AbstractVerticle() {
      @Override
      public void start() throws Exception {
        verticleThread.set(Thread.currentThread());
        verticleContext.set(Vertx.currentContext());
      }
    }, ar1 -> {
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
}
