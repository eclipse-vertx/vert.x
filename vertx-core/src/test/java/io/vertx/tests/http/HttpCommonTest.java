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

package io.vertx.tests.http;

import io.netty.channel.Channel;
import io.netty.channel.ChannelPromise;
import io.vertx.core.Future;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.*;
import io.vertx.core.net.JdkSSLEngineOptions;
import io.vertx.core.net.OpenSSLEngineOptions;
import io.vertx.core.net.SSLEngineOptions;
import io.vertx.core.net.impl.ConnectionBase;
import io.vertx.test.core.AsyncTestBase;
import io.vertx.test.core.TestUtils;
import io.vertx.test.tls.Cert;
import org.junit.Test;

import java.io.File;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static io.vertx.test.core.AssertExpectations.*;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public abstract class HttpCommonTest extends HttpTest {

  protected abstract void addMoreOptions(HttpServerOptions opts);
  protected abstract HttpServerOptions setMaxConcurrentStreamsSettings(HttpServerOptions options,
                                                                       int maxConcurrentStreams);

  @Test
  @Override
  public void testCloseHandlerNotCalledWhenConnectionClosedAfterEnd() throws Exception {
    testCloseHandlerNotCalledWhenConnectionClosedAfterEnd(1);
  }

  // Extra test

  @Test
  public void testServerResponseWriteBufferFromOtherThread() throws Exception {
    server.requestHandler(req -> {
      runAsync(() -> {
        req.response().end("hello world");
      });
    });
    startServer(testAddress);
    client.request(requestOptions).compose(req -> req
        .send()
        .expecting(that(resp -> assertEquals(200, resp.statusCode())))
        .compose(HttpClientResponse::body)).
      onComplete(onSuccess(body -> {
        assertEquals(Buffer.buffer("hello world"), body);
        testComplete();
      }));
    await();
  }

  @Test
  public void testServerResponseEndFromOtherThread() throws Exception {
    server.requestHandler(req -> {
      runAsync(() -> {
        req.response().end();
      });
    });
    startServer(testAddress);
    client.request(requestOptions).compose(req -> req
        .send()
        .expecting(HttpResponseExpectation.SC_OK)
        .compose(HttpClientResponse::end))
      .onComplete(onSuccess(v -> testComplete()));
    await();
  }

  @Test
  public void testServerResponseEndWithTrailersFromOtherThread() throws Exception {
    server.requestHandler(req -> {
      runAsync(() -> {
        req.response().putTrailer("some", "trailer").end();
      });
    });
    startServer(testAddress);
    client.request(requestOptions).compose(req -> req
        .send()
        .expecting(HttpResponseExpectation.SC_OK)
        .compose(resp -> resp.end().expecting(that(v -> {
          assertEquals(1, resp.trailers().size());
          assertEquals("trailer", resp.trailers().get("some"));
        }))))
      .onComplete(onSuccess(v -> testComplete()));
    await();
  }

  @Test
  public void testServerResponseResetFromOtherThread() throws Exception {
    waitFor(2);
    server.requestHandler(req -> {
      runAsync(() -> {
        req.response().reset(0);
      });
    });
    startServer(testAddress);
    client.request(requestOptions).onComplete(onSuccess(req -> {
      req
        .response().onComplete(onFailure(err -> {
          assertTrue(err instanceof StreamResetException);
          complete();
        }));
      req.exceptionHandler(err -> {
          assertTrue(err instanceof StreamResetException);
          complete();
        })
        .sendHead();
    }));
    await();
  }

  void runAsync(Runnable runnable) {
    new Thread(() -> {
      try {
        runnable.run();
      } catch (Exception e) {
        fail(e);
      }
    }).start();
  }

  @Test
  public void testClientRequestWriteFromOtherThread() throws Exception {
    disableThreadChecks();
    CountDownLatch latch1 = new CountDownLatch(1);
    CountDownLatch latch2 = new CountDownLatch(1);
    server.requestHandler(req -> {
      latch2.countDown();
      req.endHandler(v -> {
        req.response().end();
      });
    });
    startServer(testAddress);
    client.request(requestOptions)
      .onComplete(onSuccess(req -> {
        req.response().onComplete(onSuccess(resp -> {
          assertEquals(200, resp.statusCode());
          testComplete();
        }));
        req
          .setChunked(true)
          .sendHead();
        new Thread(() -> {
          try {
            awaitLatch(latch2); // The next write won't be buffered
          } catch (InterruptedException e) {
            fail(e);
            return;
          }
          req.write("hello ");
          req.end("world");
        }).start();
      }));
    await();
  }

  @Test
  public void testServerOpenSSL() throws Exception {
    HttpServerOptions opts = new HttpServerOptions()
      .setPort(DEFAULT_HTTPS_PORT)
      .setHost(DEFAULT_HTTPS_HOST)
      .setUseAlpn(true)
      .setSsl(true)
      .addEnabledCipherSuite("TLS_RSA_WITH_AES_128_CBC_SHA") // Non Diffie-helman -> debuggable in wireshark
      .setKeyCertOptions(Cert.SERVER_PEM.get())
      .setSslEngineOptions(new OpenSSLEngineOptions());
    addMoreOptions(opts);
    server.close();
    client.close();
    client = vertx.createHttpClient(createBaseClientOptions());
    server = vertx.createHttpServer(opts);
    server.requestHandler(req -> {
      req.response().end();
    });
    startServer(testAddress);
    client.request(requestOptions)
      .compose(HttpClientRequest::send)
      .onComplete(onSuccess(resp -> {
        assertEquals(200, resp.statusCode());
        testComplete();
      }));
    await();
  }

  @Test
  public void testResetClientRequestNotYetSent() throws Exception {
    server.close();
    server = vertx.createHttpServer(setMaxConcurrentStreamsSettings(createBaseServerOptions(), 1));
    server.requestHandler(req -> {
      fail();
    });
    startServer(testAddress);
    client.request(requestOptions).onComplete(onSuccess(req -> {
      req.response().onComplete(onFailure(err -> complete()));
      assertTrue(req.reset());
    }));
    await();
  }

  @Test
  public void testDiscardConnectionWhenChannelBecomesInactive() throws Exception {
    AtomicInteger count = new AtomicInteger();
    server.requestHandler(req -> {
      if (count.getAndIncrement() == 0) {
        req.connection().close();
      } else {
        req.response().end();
      }
    });
    startServer(testAddress);
    AtomicInteger closed = new AtomicInteger();
    client.close();
    client = vertx.httpClientBuilder()
      .with(createBaseClientOptions())
      .withConnectHandler(conn -> conn.closeHandler(v -> closed.incrementAndGet()))
      .build();
    client.request(requestOptions).onComplete(onSuccess(req -> {
      req.send().onComplete(onFailure(err -> {}));
    }));
    AsyncTestBase.assertWaitUntil(() -> closed.get() == 1);
    client.request(requestOptions)
      .compose(HttpClientRequest::send)
      .onComplete(onSuccess(resp -> {
        testComplete();
      }));
    await();
  }

  @Test
  public void testClientMakeRequestHttp2WithSSLWithoutAlpn() throws Exception {
    client.close();
    client = vertx.createHttpClient(createBaseClientOptions().setUseAlpn(false));
    client.request(requestOptions).onComplete(onFailure(err -> testComplete()));
    await();
  }

  @Test
  public void testServePendingRequests() throws Exception {
    int n = 10;
    waitFor(n);
    LinkedList<HttpServerRequest> requests = new LinkedList<>();
    Set<HttpConnection> connections = new HashSet<>();
    server.requestHandler(req -> {
      requests.add(req);
      connections.add(req.connection());
      assertEquals(1, connections.size());
      if (requests.size() == n) {
        while (requests.size() > 0) {
          requests.removeFirst().response().end();
        }
      }
    });
    startServer(testAddress);
    for (int i = 0;i < n;i++) {
      client.request(requestOptions).onComplete(onSuccess(req -> {
        req.send().onComplete(onSuccess(resp -> complete()));
      }));
    }
    await();
  }

  @Test
  public void testContentLengthNotRequired() throws Exception {
    waitFor(2);
    server.requestHandler(req -> {
      HttpServerResponse resp = req.response();
      resp.write("Hello");
      resp.end("World");
      assertNull(resp.headers().get("content-length"));
      complete();
    });
    startServer(testAddress);
    client.request(requestOptions)
      .compose(req -> req.send().compose(resp -> {
        assertNull(resp.getHeader("content-length"));
        return resp.body();
      }))
      .onComplete(onSuccess(body -> {
        assertEquals("HelloWorld", body.toString());
        complete();
      }));
    await();
  }

  /**
   * Test that socket close (without an HTTP/2 go away frame) removes the connection from the pool
   * before the streams are notified. Otherwise a notified stream might reuse a stale connection from
   * the pool.
   */
  @Test
  public void testConnectionCloseEvictsConnectionFromThePoolBeforeStreamsAreClosed() throws Exception {
    Set<HttpConnection> serverConnections = new HashSet<>();
    server.requestHandler(req -> {
      serverConnections.add(req.connection());
      switch (req.path()) {
        case "/1":
          req.response().end();
          break;
        case "/2":
          assertEquals(1, serverConnections.size());
          // Socket close without HTTP/2 go away
          Channel ch = ((ConnectionBase) req.connection()).channel();
          ChannelPromise promise = ch.newPromise();
          ch.unsafe().close(promise);
          break;
        case "/3":
          assertEquals(2, serverConnections.size());
          req.response().end();
          break;
      }
    });
    startServer(testAddress);
    Future<Buffer> f1 = client.request(new RequestOptions(requestOptions).setURI("/1"))
      .compose(req -> req.send()
        .compose(HttpClientResponse::body));
    f1.onComplete(onSuccess(v -> {
      Future<Buffer> f2 = client.request(new RequestOptions(requestOptions).setURI("/2"))
        .compose(req -> req.send()
          .compose(HttpClientResponse::body));
      f2.onComplete(onFailure(v2 -> {
        Future<Buffer> f3 = client.request(new RequestOptions(requestOptions).setURI("/3"))
          .compose(req -> req.send()
            .compose(HttpClientResponse::body));
        f3.onComplete(onSuccess(vvv -> {
          testComplete();
        }));
      }));
    }));
    await();
  }

  @Test
  public void testRstFloodProtection() throws Exception {
    server.requestHandler(req -> {
    });
    startServer(testAddress);
    int num = HttpServerOptions.DEFAULT_HTTP2_RST_FLOOD_MAX_RST_FRAME_PER_WINDOW + 1;
    for (int i = 0;i < num;i++) {
      int val = i;
      client.request(requestOptions).onComplete(onSuccess(req -> {
        if (val == 0) {
          req
            .connection()
            .goAwayHandler(ga -> {
              assertEquals(11, ga.getErrorCode()); // Enhance your calm
              testComplete();
            });
        }
        req.end().onComplete(onSuccess(v -> {
          req.reset();
        }));
      }));
    }
    await();
  }

  @Test
  public void testStreamResetErrorMapping() throws Exception {
    server.requestHandler(req -> {
    });
    startServer(testAddress);
    client.request(requestOptions).onComplete(onSuccess(req -> {
      req.exceptionHandler(err -> {
        assertTrue(err instanceof StreamResetException);
        StreamResetException sre = (StreamResetException) err;
        assertEquals(10, sre.getCode());
        testComplete();
      });
      // Force stream allocation
      req.sendHead().onComplete(onSuccess(v -> {
        req.reset(10);
      }));
    }));
    await();
  }

  @Test
  public void testUnsupportedAlpnVersion() throws Exception {
    testUnsupportedAlpnVersion(new JdkSSLEngineOptions(), false);
  }

  @Test
  public void testUnsupportedAlpnVersionOpenSSL() throws Exception {
    testUnsupportedAlpnVersion(new OpenSSLEngineOptions(), true);
  }

  private void testUnsupportedAlpnVersion(SSLEngineOptions engine, boolean accept) throws Exception {
    server.close();
    server = vertx.createHttpServer(createBaseServerOptions()
      .setSslEngineOptions(engine)
      .setAlpnVersions(Collections.singletonList(serverAlpnProtocolVersion()))
    );
    server.requestHandler(request -> {
      request.response().end();
    });
    startServer(testAddress);
    client.close();
    client = vertx.createHttpClient(createBaseClientOptions().setProtocolVersion(clientAlpnProtocolVersion()));
    client.request(requestOptions).onComplete(ar -> {
      if (ar.succeeded()) {
        if (accept) {
          ar.result().send().onComplete(onSuccess(resp -> {
            testComplete();
          }));
        } else {
          fail();
        }
      } else {
        if (accept) {
          fail();
        } else {
          testComplete();
        }
      }
    });
    await();
  }

  @Test
  public void testSendFileCancellation() throws Exception {

    Path webroot = Files.createTempDirectory("webroot");
    File res = new File(webroot.toFile(), "large.dat");
    RandomAccessFile f = new RandomAccessFile(res, "rw");
    f.setLength(1024 * 1024);

    AtomicInteger errors = new AtomicInteger();
    vertx.getOrCreateContext().exceptionHandler(err -> {
      errors.incrementAndGet();
    });

    server.requestHandler(request -> {
      request
        .response()
        .sendFile(res.getAbsolutePath())
        .onComplete(onFailure(ar -> {
          assertEquals(0, errors.get());
          testComplete();
        }));
    });

    startServer();

    client.request(requestOptions)
      .onComplete(onSuccess(req -> {
        req.send().onComplete(onSuccess(resp -> {
          assertEquals(200, resp.statusCode());
          assertEquals(serverAlpnProtocolVersion(), resp.version());
          req.connection().close();
        }));
      }));

    await();
  }

  @Test
  public void testAppendToHttpChunks() throws Exception {
    List<String> expected = Arrays.asList("chunk-1", "chunk-2", "chunk-3");
    server.requestHandler(req -> {
      HttpServerResponse resp = req.response();
      expected.forEach(resp::write);
      resp.end(); // Will end an empty chunk
    });
    startServer(testAddress);
    client.request(requestOptions).onComplete(onSuccess(req -> {
      req.send().onComplete(onSuccess(resp -> {
        List<String> chunks = new ArrayList<>();
        resp.handler(chunk -> {
          chunk.appendString("-suffix");
          chunks.add(chunk.toString());
        });
        resp.endHandler(v -> {
          assertEquals(Stream.concat(expected.stream(), Stream.of(""))
            .map(s -> s + "-suffix")
            .collect(Collectors.toList()), chunks);
          testComplete();
        });
      }));
    }));
    await();
  }

}
