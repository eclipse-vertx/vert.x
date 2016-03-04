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

import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http2.Http2EventAdapter;
import io.netty.handler.codec.http2.Http2Exception;
import io.netty.handler.codec.http2.Http2Headers;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.http.HttpVersion;
import io.vertx.core.net.JksOptions;
import io.vertx.core.net.NetServerOptions;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class Http2ClientTest extends Http2TestBase {

  @Override
  public void setUp() throws Exception {
    super.setUp();
    client = vertx.createHttpClient(new HttpClientOptions().
        setUseAlpn(true).
        setTrustStoreOptions((JksOptions) getClientTrustOptions(Trust.JKS)).
        setProtocolVersion(HttpVersion.HTTP_2));
  }

  @Test
  public void testGet() throws Exception {
    String expected = TestUtils.randomAlphaString(100);
    AtomicInteger reqCount = new AtomicInteger();
    server.requestHandler(req -> {
      assertEquals("https", req.scheme());
      assertEquals(HttpMethod.GET, req.method());
      assertEquals("/somepath", req.path());
      assertEquals("foo_request_value", req.getHeader("Foo_request"));
      assertEquals("bar_request_value", req.getHeader("bar_request"));
      assertEquals(2, req.headers().getAll("juu_request").size());
      assertEquals("juu_request_value_1", req.headers().getAll("juu_request").get(0));
      assertEquals("juu_request_value_2", req.headers().getAll("juu_request").get(1));
      reqCount.incrementAndGet();
      HttpServerResponse resp = req.response();
      resp.putHeader("content-type", "text/plain");
      resp.putHeader("Foo_response", "foo_value");
      resp.putHeader("bar_response", "bar_value");
      resp.putHeader("juu_response", (List<String>) Arrays.asList("juu_value_1", "juu_value_2"));
      resp.end(expected);
    });
    startServer();
    client.get(4043, "localhost", "/somepath", resp -> {
      assertEquals(1, reqCount.get());
      assertEquals(HttpVersion.HTTP_2, resp.version());
      assertEquals("text/plain", resp.getHeader("content-type"));
      assertEquals("200", resp.getHeader(":status"));
      assertEquals("foo_value", resp.getHeader("foo_response"));
      assertEquals("bar_value", resp.getHeader("bar_response"));
      assertEquals(2, resp.headers().getAll("juu_response").size());
      assertEquals("juu_value_1", resp.headers().getAll("juu_response").get(0));
      assertEquals("juu_value_2", resp.headers().getAll("juu_response").get(1));
      Buffer content = Buffer.buffer();
      resp.handler(content::appendBuffer);
      resp.endHandler(v -> {
        assertEquals(expected, content.toString());
        testComplete();
      });
    })
        .putHeader("Foo_request", "foo_request_value")
        .putHeader("bar_request", "bar_request_value")
        .putHeader("juu_request", Arrays.<CharSequence>asList("juu_request_value_1", "juu_request_value_2"))
        .exceptionHandler(err -> fail())
        .end();
    await();
  }

  @Test
  public void testTrailers() throws Exception {
    server.requestHandler(req -> {
      HttpServerResponse resp = req.response();
      resp.setChunked(true);
      resp.write("some-content");
      resp.putTrailer("Foo", "foo_value");
      resp.putTrailer("bar", "bar_value");
      resp.putTrailer("juu", (List<String>)Arrays.asList("juu_value_1", "juu_value_2"));
      resp.end();
    });
    startServer();
    client.getNow(4043, "localhost", "/somepeth", resp -> {
      assertEquals(null, resp.getTrailer("foo"));
      resp.endHandler(v -> {
        assertEquals("foo_value", resp.getTrailer("foo"));
        assertEquals("foo_value", resp.getTrailer("Foo"));
        assertEquals("bar_value", resp.getTrailer("bar"));
        assertEquals(2, resp.trailers().getAll("juu").size());
        assertEquals("juu_value_1", resp.trailers().getAll("juu").get(0));
        assertEquals("juu_value_2", resp.trailers().getAll("juu").get(1));
        testComplete();
      });
    });
    await();
  }

  @Test
  public void testPost() throws Exception {
    Buffer content = Buffer.buffer();
    String expected = TestUtils.randomAlphaString(100);
    server.requestHandler(req -> {
      assertEquals(HttpMethod.POST, req.method());
      req.handler(content::appendBuffer);
      req.endHandler(v -> {
        req.response().end();
      });
    });
    startServer();
    client.post(4043, "localhost", "/somepath", resp -> {
      resp.endHandler(v -> {
        assertEquals(expected, content.toString());
        testComplete();
      });
    }).exceptionHandler(err -> {
      fail();
    }).end(expected);
    await();
  }

  @Test
  public void testClientRequestWriteability() throws Exception {
    Buffer content = Buffer.buffer();
    Buffer expected = Buffer.buffer();
    String chunk = TestUtils.randomAlphaString(100);
    CompletableFuture<Void> done = new CompletableFuture<>();
    AtomicBoolean paused = new AtomicBoolean();
    AtomicInteger numPause = new AtomicInteger();
    server.requestHandler(req -> {
      Context ctx = vertx.getOrCreateContext();
      done.thenAccept(v1 -> {
        paused.set(false);
        ctx.runOnContext(v2 -> {
          req.resume();
        });
      });
      numPause.incrementAndGet();
      req.pause();
      paused.set(true);
      req.handler(content::appendBuffer);
      req.endHandler(v -> {
        assertEquals(expected, content);
        testComplete();
      });
    });
    startServer();
    HttpClientRequest req = client.post(4043, "localhost", "/somepath", resp -> {
    }).setChunked(true).exceptionHandler(err -> {
      fail();
    });
    AtomicInteger count = new AtomicInteger();
    vertx.setPeriodic(1, timerID -> {
      if (req.writeQueueFull()) {
        assertTrue(paused.get());
        assertEquals(1, numPause.get());
        req.drainHandler(v -> {
          assertEquals(1, numPause.get());
          assertFalse(paused.get());
          req.end();
        });
        vertx.cancelTimer(timerID);
        done.complete(null);
      } else {
        count.incrementAndGet();
        expected.appendString(chunk);
        req.write(chunk);
      }
    });
    await();
  }

  @Test
  public void testClientResponsePauseResume() throws Exception {
    String content = TestUtils.randomAlphaString(1024);
    Buffer expected = Buffer.buffer();
    Future<Void> whenFull = Future.future();
    AtomicBoolean drain = new AtomicBoolean();
    server.requestHandler(req -> {
      HttpServerResponse resp = req.response();
      resp.putHeader("content-type", "text/plain");
      resp.setChunked(true);
      vertx.setPeriodic(1, timerID -> {
        if (resp.writeQueueFull()) {
          resp.drainHandler(v -> {
            Buffer last = Buffer.buffer("last");
            expected.appendBuffer(last);
            resp.end(last);
            assertEquals(expected.toString().getBytes().length, resp.bytesWritten());
          });
          vertx.cancelTimer(timerID);
          drain.set(true);
          whenFull.complete();
        } else {
          Buffer chunk = Buffer.buffer(content);
          expected.appendBuffer(chunk);
          resp.write(chunk);
        }
      });
    });
    startServer();
    client.getNow(4043, "localhost", "/somepath", resp -> {
      Buffer received = Buffer.buffer();
      resp.pause();
      resp.handler(received::appendBuffer);
      resp.endHandler(v -> {
        assertEquals(expected.toString(), received.toString());
        testComplete();
      });
      whenFull.setHandler(v -> {
        resp.resume();
      });
    });
    await();
  }

  @Test
  public void testQueueRequests() throws Exception {
    int numReq = 100;
    waitFor(numReq);
    String expected = TestUtils.randomAlphaString(100);
    server.requestHandler(req -> {
      req.response().end(expected);
    });
    startServer();
    for (int i = 0;i < numReq;i++) {
      client.get(4043, "localhost", "/somepath", resp -> {
        Buffer content = Buffer.buffer();
        resp.handler(content::appendBuffer);
        resp.endHandler(v -> {
          assertEquals(expected, content.toString());
          complete();
        });
      }).exceptionHandler(err -> {
        fail();
      }).end();
    }
    await();
  }

  @Test
  public void testReuseConnection() throws Exception {
    server.requestHandler(req -> {
      req.response().end();
    });
    startServer();
    CountDownLatch doReq = new CountDownLatch(1);
    client.get(4043, "localhost", "/somepath", resp -> {
      resp.endHandler(v -> {
        doReq.countDown();
      });
    }).exceptionHandler(err -> {
      fail();
    }).end();
    awaitLatch(doReq);
    client.get(4043, "localhost", "/somepath", resp -> {
      resp.endHandler(v -> {
        testComplete();
      });
    }).exceptionHandler(err -> {
      fail();
    }).end();
    await();
  }

  @Test
  public void testConnectionFailed() throws Exception {
    client.get(4044, "localhost", "/somepath", resp -> {
    }).exceptionHandler(err -> {
      assertEquals(err.getClass(), java.net.ConnectException.class);
      testComplete();
    }).end();
    await();
  }

  @Test
  public void testFallbackOnHttp1() throws Exception {
    server.close();
    server = vertx.createHttpServer(serverOptions.setUseAlpn(false));
    server.requestHandler(req -> {
      assertEquals(HttpVersion.HTTP_1_1, req.version());
      req.response().end();
    });
    startServer();
    client.get(4043, "localhost", "/somepath", resp -> {
      testComplete();
    }).exceptionHandler(err -> {
      fail();
    }).end();
    await();
  }
}
