/*
 * Copyright (c) 2011-2023 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package io.vertx.tests.http;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.ThreadingModel;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.*;
import io.vertx.core.impl.Utils;
import io.vertx.core.net.NetServer;
import io.vertx.test.core.Checkpoint;
import io.vertx.test.core.TestUtils;
import io.vertx.test.http.HttpTestBase2;
import org.junit.Assume;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static io.vertx.test.core.TestUtils.onFailure;
import static io.vertx.test.core.TestUtils.onSuccess;
import static org.junit.Assert.*;

public abstract class HttpClientTimeoutTest extends HttpTestBase2 {

  @Test
  public void testConnectTimeoutDoesFire() throws Exception {
    int timeout = 3000;
    server.requestHandler(req -> {
      req.response().end();
    });
    startServer(testAddress);
    List<HttpClientRequest> requests = new ArrayList<>();
    for (int i = 0;i < 5;i++) {
      HttpClientRequest request = client.request(new RequestOptions(requestOptions)).await();
      requests.add(request);
    }
    long now = System.currentTimeMillis();
    try {
      client.request(new RequestOptions(requestOptions).setConnectTimeout(timeout).setURI("/slow"))
        .await();
      fail();
    } catch (Exception e) {
      assertTrue(System.currentTimeMillis() - now >= timeout);
    }
  }

  @Test
  public void testConnectTimeoutDoesNotFire() throws Exception {
    int timeout = 3000;
    int ratio = 50;
    server.requestHandler(req -> {
      req.response().end();
    });
    startServer(testAddress);
    List<HttpClientRequest> requests = new ArrayList<>();
    for (int i = 0;i < 5;i++) {
      HttpClientRequest request = client.request(new RequestOptions(requestOptions)).await();
      requests.add(request);
    }
    vertx.setTimer(timeout * ratio / 100, id -> {
      requests.forEach(req -> {
        req.send().compose(HttpClientResponse::body);
      });
    });
    long now = System.currentTimeMillis();
    client
      .request(new RequestOptions(requestOptions).setConnectTimeout(timeout).setURI("/slow"))
      .await();
    long elapsed = System.currentTimeMillis() - now;
    assertTrue(elapsed >= timeout * ratio / 100);
    assertTrue(elapsed <= timeout);
  }

  @Test
  public void testTimedOutWaiterDoesNotConnect(Checkpoint checkpoint) throws Exception {
    Assume.assumeTrue("Domain socket don't pass this test", testAddress.isInetSocket());
    Assume.assumeTrue("HTTP/2 don't pass this test", createBaseClientOptions().getProtocolVersion() == HttpVersion.HTTP_1_1);
    long responseDelay = 300;
    int requests = 6;
    client = vertx.createHttpClient(createBaseClientOptions().setKeepAlive(false), new PoolOptions().setHttp1MaxSize(1));
    AtomicInteger requestCount = new AtomicInteger(0);
    // We need a net server because we need to intercept the socket connection, not just full http requests
    NetServer server = vertx.createNetServer();
    server.connectHandler(socket -> {
      Buffer content = Buffer.buffer();
      AtomicBoolean closed = new AtomicBoolean();
      socket.closeHandler(v -> closed.set(true));
      socket.handler(buff -> {
        content.appendBuffer(buff);
        if (buff.toString().endsWith("\r\n\r\n")) {
          // Delay and write a proper http response
          vertx.setTimer(responseDelay, time -> {
            if (!closed.get()) {
              requestCount.incrementAndGet();
              socket.write("HTTP/1.1 200 OK\r\nContent-Length: 2\r\n\r\nOK");
            }
          });
        }
      });
    });
    server.listen(testAddress).await();

    CountDownLatch latch = checkpoint.asLatch(requests);
    for(int count = 0; count < requests; count++) {
      if (count % 2 == 0) {
        client.request(requestOptions)
          .compose(req -> req
            .send()
            .expecting(HttpResponseExpectation.SC_OK)
            .compose(HttpClientResponse::body))
          .onComplete(onSuccess(buff -> {
            assertEquals("OK", buff.toString());
            latch.countDown();
          }));
      } else {
        // Odd requests get a timeout less than the responseDelay, since we have a pool size of one and a delay all but
        // the first request should end up in the wait queue, the odd numbered requests should time out so we should get
        // (requests + 1 / 2) connect attempts
        client
          .request(new RequestOptions(requestOptions).setConnectTimeout(responseDelay / 2))
          .onComplete(onFailure(err -> {
            latch.countDown();
          }));
      }
    }

    checkpoint.awaitSuccess();

    assertEquals("Incorrect number of connect attempts.", (requests + 1) / 2, requestCount.get());
  }

  @Test
  public void testRequestTimeoutIsNotDelayedAfterResponseIsReceived(Checkpoint checkpoint) throws Exception {
    int n = 6;
    CountDownLatch latch = checkpoint.asLatch(n);
    server.requestHandler(req -> {
      req.response().end();
    });
    startServer(testAddress);
    vertx.deployVerticle(new AbstractVerticle() {
      @Override
      public void start() throws Exception {
        client = vertx.createHttpClient(createBaseClientOptions(), new PoolOptions().setHttp1MaxSize(1));
        for (int i = 0;i < n;i++) {
          client.request(requestOptions).onComplete(onSuccess(req -> {
            req.idleTimeout(500);
            req.send().onComplete(onSuccess(resp -> {
              try {
                Thread.sleep(150);
              } catch (InterruptedException e) {
                fail(e.getMessage());
              }
              // Complete later, if some timeout tasks have been queued, this will be executed after
              vertx.runOnContext(v -> latch.countDown());
            }));
          }));
        }
      }
    }, new DeploymentOptions().setThreadingModel(ThreadingModel.WORKER));
  }

  @Test
  public void testRequestTimeoutCanceledWhenRequestEndsNormally(Checkpoint checkpoint) throws Exception {
    server.requestHandler(req -> req.response().end());
    startServer(testAddress);
    AtomicReference<Throwable> exception = new AtomicReference<>();
    vertx.setTimer(1000, id -> {
      assertNull("Did not expect any exception", exception.get());
      checkpoint.succeed();
    });
    client
      .request(requestOptions)
      .onComplete(onSuccess(req -> {
        req
          .exceptionHandler(exception::set)
          .idleTimeout(500)
          .end();
    }));
  }

  @Test
  public void testRequestTimeoutCanceledWhenRequestHasAnOtherError() {
    Assume.assumeFalse(Utils.isWindows());
    AtomicReference<Throwable> exception = new AtomicReference<>();
    NetServer server = vertx
      .createNetServer()
      .connectHandler(so -> {})
      .listen(0)
      .await();
    int port;
    try {
      port = server.actualPort();
    } finally {
      server
        .close()
        .await();
    }
    try {
      client.request(new RequestOptions().setPort(port).setIdleTimeout(800)).await();
      fail();
    } catch (Exception e) {
      assertFalse("Expected to not end with timeout exception, but did: " + exception.get(), exception.get() instanceof TimeoutException);
    }
  }

  @Test
  public void testHttpClientRequestTimeoutResetsTheConnection(Checkpoint cp1, Checkpoint cp2, Checkpoint cp3) throws Exception {
    server.requestHandler(req -> {
      AtomicBoolean errored = new AtomicBoolean();
      req.exceptionHandler(err -> {
        if (errored.compareAndSet(false, true)) {
          if (req.version() == HttpVersion.HTTP_2) {
            StreamResetException reset = (StreamResetException) err;
            assertEquals(8, reset.getCode());
          }
          cp1.succeed();
        }
      });
    });
    startServer(testAddress);
    client.request(requestOptions).onComplete(onSuccess(req -> {
      req.response().onComplete(onFailure(rr -> {
        cp2.succeed();
      }));
      req.setChunked(true).writeHead().onComplete(onSuccess(version -> req.idleTimeout(500)));
      AtomicBoolean errored = new AtomicBoolean();
      req.exceptionHandler(err -> {
        if (errored.compareAndSet(false, true)) {
          cp3.succeed();
        }
      });
    }));
  }

  @Test
  public void testResponseDataTimeout(Checkpoint cp1, Checkpoint cp2) throws Exception {
    Buffer expected = TestUtils.randomBuffer(1000);
    server.requestHandler(req -> {
      req.response().setChunked(true).write(expected);
    });
    startServer(testAddress);
    Buffer received = Buffer.buffer();
    client.request(requestOptions).onComplete(onSuccess(req -> {
      req.response().onComplete(onSuccess(resp -> {
        AtomicInteger count = new AtomicInteger();
        resp.exceptionHandler(t -> {
          if (count.getAndIncrement() == 0) {
            assertTrue(t instanceof TimeoutException);
            assertEquals(expected, received);
            cp1.succeed();
          }
        });
        resp.request().idleTimeout(500);
        resp.handler(buff -> {
          received.appendBuffer(buff);
          // Force the internal timer to be rescheduled with the remaining amount of time
          // e.g around 100 ms
          try {
            Thread.sleep(100);
          } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
          }
        });
      }));
      AtomicInteger count = new AtomicInteger();
      req.exceptionHandler(t -> {
        if (count.getAndIncrement() == 0) {
          assertTrue(t instanceof TimeoutException);
          assertEquals(expected, received);
          cp2.succeed();
        }
      });
      req.writeHead();
    }));
  }

  @Test
  public void testRequestTimesOutWhenIndicatedPeriodExpiresWithoutAResponseFromRemoteServer(Checkpoint checkpoint) throws Exception {
    server.requestHandler(req -> {}); // No response handler so timeout triggers
    AtomicBoolean failed = new AtomicBoolean();
    startServer(testAddress);
    client.request(new RequestOptions(requestOptions).setIdleTimeout(1000))
      .compose(HttpClientRequest::send).onComplete(onFailure(t -> {
        // Catch the first, the second is going to be a connection closed exception when the
        // server is shutdown on testComplete
        if (failed.compareAndSet(false, true)) {
          checkpoint.succeed();
        }
      }));
  }

  @Test
  public void testRequestTimeoutExtendedWhenResponseChunksReceived() throws Exception {
    long timeout = 2000;
    int numChunks = 100;
    AtomicInteger count = new AtomicInteger(0);
    long interval = timeout * 2 / numChunks;

    server.requestHandler(req -> {
      req.response().setChunked(true);
      vertx.setPeriodic(interval, timerID -> {
        req.response().write("foo");
        if (count.incrementAndGet() == numChunks) {
          req.response().end();
          vertx.cancelTimer(timerID);
        }
      });
    });

    startServer(testAddress);

    client.request(new RequestOptions(requestOptions).setIdleTimeout(timeout))
      .compose(req -> req
        .send()
        .expecting(HttpResponseExpectation.SC_OK)
        .compose(HttpClientResponse::end))
      .await();
  }

  @Test
  public void testRequestsTimeoutInQueue(Checkpoint checkpoint) throws Exception {

    server.requestHandler(req -> {
      vertx.setTimer(1000, id -> {
        HttpServerResponse resp = req.response();
        if (!resp.closed()) {
          resp.end();
        }
      });
    });

    client.close();
    client = vertx.createHttpClient(createBaseClientOptions().setKeepAlive(false), new PoolOptions().setHttp1MaxSize(1));

    startServer(testAddress);

    // Add a few requests that should all timeout
    CountDownLatch latch = checkpoint.asLatch(5);
    for (int i = 0; i < 5; i++) {
      client.request(new RequestOptions(requestOptions).setIdleTimeout(500))
        .compose(HttpClientRequest::send)
        .onComplete(onFailure(t -> {
          assertTrue(t instanceof TimeoutException);
          latch.countDown();
        }));
    }
    // Now another request that should not timeout
    client.request(new RequestOptions(requestOptions).setIdleTimeout(3000))
      .compose(request -> request
        .send()
        .expecting(HttpResponseExpectation.SC_OK)
        .compose(resp -> resp.end()))
      .await();
  }
}
