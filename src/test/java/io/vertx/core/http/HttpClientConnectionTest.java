/*
 * Copyright (c) 2011-2021 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package io.vertx.core.http;

import io.netty.buffer.Unpooled;
import io.vertx.core.MultiMap;
import io.vertx.core.Promise;
import io.vertx.core.http.impl.HttpClientImpl;
import io.vertx.core.http.impl.HttpRequestHead;
import io.vertx.core.impl.ContextInternal;
import io.vertx.core.net.SocketAddress;
import io.vertx.test.core.TestUtils;
import org.junit.Test;

import java.io.File;
import java.util.concurrent.atomic.AtomicInteger;

public abstract class HttpClientConnectionTest extends HttpTestBase {

  private File tmp;
  protected HttpClientImpl client;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    if (USE_DOMAIN_SOCKETS) {
      assertTrue("Native transport not enabled", USE_NATIVE_TRANSPORT);
      tmp = TestUtils.tmpFile(".sock");
      testAddress = SocketAddress.domainSocketAddress(tmp.getAbsolutePath());
      requestOptions.setServer(testAddress);
    }
    this.client = (HttpClientImpl) super.client;
  }

  @Test
  public void testGet() throws Exception {
    waitFor(3);
    server.requestHandler(req -> {
      req.response().end();
    });
    startServer();
    client.connect(testAddress).onComplete(onSuccess(conn -> {
      conn.createStream((ContextInternal) vertx.getOrCreateContext(), onSuccess(stream -> {
        stream.writeHead(new HttpRequestHead(
          HttpMethod.GET, "/", MultiMap.caseInsensitiveMultiMap(), "localhost:8080", ""), false, Unpooled.EMPTY_BUFFER, true, new StreamPriority(), false, onSuccess(v -> {
        }));
        stream.headHandler(resp -> {
          assertEquals(200, resp.statusCode);
          complete();
        });
        stream.endHandler(headers -> {
          assertEquals(0, headers.size());
          complete();
        });
        stream.closeHandler(v -> {
          complete();
        });
      }));
    }));
    await();
  }

  @Test
  public void testConnectionClose() throws Exception {
    waitFor(2);
    server.requestHandler(req -> {
      req.response().close();
    });
    startServer();
    client.connect(testAddress).onComplete(onSuccess(conn -> {
      AtomicInteger evictions = new AtomicInteger();
      conn.evictionHandler(v -> {
        assertEquals(1, evictions.incrementAndGet());
        complete();
      });
      conn.createStream((ContextInternal) vertx.getOrCreateContext(), onSuccess(stream -> {
        stream.writeHead(new HttpRequestHead(
          HttpMethod.GET, "/", MultiMap.caseInsensitiveMultiMap(), "localhost:8080", ""), false, Unpooled.EMPTY_BUFFER, true, new StreamPriority(), false, onSuccess(v -> {
        }));
        stream.headHandler(resp -> {
          fail();
        });
        stream.endHandler(headers -> {
          fail();
        });
        stream.closeHandler(v -> {
          assertEquals(1, evictions.get());
          complete();
        });
      }));
    }));
    await();
  }
}
