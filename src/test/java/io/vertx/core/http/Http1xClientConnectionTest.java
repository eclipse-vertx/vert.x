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
import io.vertx.core.http.impl.HttpRequestHead;
import io.vertx.core.impl.ContextInternal;
import org.junit.Test;

import java.util.concurrent.atomic.AtomicInteger;

public class Http1xClientConnectionTest extends HttpClientConnectionTest {

  @Test
  public void testResetStreamBeforeSend() throws Exception {
    waitFor(1);
    server.requestHandler(req -> {
    });
    startServer();
    client.connect(testAddress).onComplete(onSuccess(conn -> {
      AtomicInteger evictions = new AtomicInteger();
      conn.evictionHandler(v -> {
        evictions.incrementAndGet();
      });
      conn.createStream((ContextInternal) vertx.getOrCreateContext(), onSuccess(stream -> {
        Exception cause = new Exception();
        stream.closeHandler(v -> {
          assertEquals(0, evictions.get());
          complete();
        });
        stream.reset(cause);
      }));
    }));
    await();
  }

  @Test
  public void testResetStreamRequestSent() throws Exception {
    waitFor(1);
    Promise<Void> continuation = Promise.promise();
    server.requestHandler(req -> {
      continuation.complete();
    });
    startServer();
    client.connect(testAddress).onComplete(onSuccess(conn -> {
      AtomicInteger evictions = new AtomicInteger();
      conn.evictionHandler(v -> {
        evictions.incrementAndGet();
      });
      conn.createStream((ContextInternal) vertx.getOrCreateContext(), onSuccess(stream -> {
        Exception cause = new Exception();
        stream.closeHandler(v -> {
          assertEquals(1, evictions.get());
          complete();
        });
        continuation
          .future()
          .onSuccess(v -> {
            stream.reset(cause);
          });
        stream.writeHead(new HttpRequestHead(
          HttpMethod.GET, "/", MultiMap.caseInsensitiveMultiMap(), "localhost:8080", ""), false, Unpooled.EMPTY_BUFFER, false, new StreamPriority(), false, null);
      }));
    }));
    await();
  }

  @Test
  public void testServerConnectionClose() throws Exception {
    waitFor(1);
    server.requestHandler(req -> {
      req.response().putHeader("Connection", "close").end();
    });
    startServer();
    client.connect(testAddress).onComplete(onSuccess(conn -> {
      AtomicInteger evictions = new AtomicInteger();
      conn.evictionHandler(v -> {
        assertEquals(1, evictions.incrementAndGet());
      });
      conn.createStream((ContextInternal) vertx.getOrCreateContext(), onSuccess(stream -> {
        stream.closeHandler(v -> {
          assertEquals(1, evictions.get());
          complete();
        });
        stream.writeHead(new HttpRequestHead(
          HttpMethod.GET, "/", MultiMap.caseInsensitiveMultiMap(), "localhost:8080", ""), false, Unpooled.EMPTY_BUFFER, true, new StreamPriority(), false, null);
      }));
    }));
    await();
  }
}
