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

import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpVersion;
import io.vertx.core.net.JksOptions;
import io.vertx.core.net.NetServerOptions;
import org.junit.Test;

import java.util.concurrent.CountDownLatch;
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
      reqCount.incrementAndGet();
      req.response().end(expected);
    });
    startServer();
    client.get(4043, "localhost", "/somepath", resp -> {
      assertEquals(1, reqCount.get());
      Buffer content = Buffer.buffer();
      resp.handler(content::appendBuffer);
      resp.endHandler(v -> {
        assertEquals(expected, content.toString());
        testComplete();
      });
    }).exceptionHandler(err -> {
      fail();
    }).end();
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
