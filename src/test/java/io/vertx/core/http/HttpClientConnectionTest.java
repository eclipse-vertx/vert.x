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
import io.vertx.core.Future;
import io.vertx.core.MultiMap;
import io.vertx.core.http.impl.HttpClientConnectionInternal;
import io.vertx.core.http.impl.HttpClientInternal;
import io.vertx.core.http.impl.HttpRequestHead;
import io.vertx.core.impl.ContextInternal;
import org.junit.Test;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;

public abstract class HttpClientConnectionTest extends HttpTestBase {

  protected HttpClientInternal client;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    this.client = (HttpClientInternal) super.client;
  }

  @Test
  public void testGet() throws Exception {
    server.requestHandler(req -> {
      req.response().end("Hello World");
    });
    startServer(testAddress);
    client.connect(new HttpConnectOptions().setServer(testAddress).setHost(requestOptions.getHost()).setPort(requestOptions.getPort()))
      .compose(HttpClientConnection::createRequest)
      .compose(request -> request
        .send()
        .andThen(onSuccess(resp -> assertEquals(200, resp.statusCode())))
        .compose(HttpClientResponse::body))
      .onComplete(onSuccess(body -> {
        assertEquals("Hello World", body.toString());
        testComplete();
      }));
    await();
  }

  @Test
  public void testStreamGet() throws Exception {
    waitFor(3);
    server.requestHandler(req -> {
      req.response().end("Hello World");
    });
    startServer(testAddress);
    client.connect(new HttpConnectOptions().setServer(testAddress).setHost(requestOptions.getHost()).setPort(requestOptions.getPort())).onComplete(onSuccess(conn -> {
      ((HttpClientConnectionInternal)conn)
        .createStream((ContextInternal) vertx.getOrCreateContext())
        .onComplete(onSuccess(stream -> {
          stream.writeHead(new HttpRequestHead(
            HttpMethod.GET, "/", MultiMap.caseInsensitiveMultiMap(), DEFAULT_HTTP_HOST_AND_PORT, "", null), false, Unpooled.EMPTY_BUFFER, true, new StreamPriority(), false);
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
      req.connection().close();
    });
    startServer(testAddress);
    client.connect(new HttpConnectOptions().setServer(testAddress).setHost(requestOptions.getHost()).setPort(requestOptions.getPort())).onComplete(onSuccess(conn -> {
      HttpClientConnectionInternal ci = ((HttpClientConnectionInternal)conn);
      AtomicInteger evictions = new AtomicInteger();
      ci.evictionHandler(v -> {
        assertEquals(1, evictions.incrementAndGet());
        complete();
      });
      ci.createStream((ContextInternal) vertx.getOrCreateContext()).onComplete(onSuccess(stream -> {
        stream.writeHead(new HttpRequestHead(
          HttpMethod.GET, "/", MultiMap.caseInsensitiveMultiMap(), DEFAULT_HTTP_HOST_AND_PORT, "", null), false, Unpooled.EMPTY_BUFFER, true, new StreamPriority(), false);
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

  @Test
  public void testConcurrencyLimit() throws Exception {
    server.requestHandler(req -> {
    });
    startServer(testAddress);
    client.connect(new HttpConnectOptions().setServer(testAddress).setHost(requestOptions.getHost()).setPort(requestOptions.getPort())).onComplete(onSuccess(conn -> {
      long concurrency = ((HttpClientConnectionInternal) conn).concurrency();
      createRequestRecursively(conn, 0, (err, num) -> {
        assertEquals(concurrency, (long)num);
        testComplete();
      });
    }));
    await();
  }

  private void createRequestRecursively(HttpClientConnection conn, long num, BiConsumer<Throwable, Long> callback) {
    Future<HttpClientRequest> fut = conn.createRequest(new RequestOptions());
    fut.onComplete(ar1 -> {
      if (ar1.succeeded()) {
        HttpClientRequest req = ar1.result();
        req.setChunked(true);
        req.write("Hello").onComplete(ar2 -> {
          if (ar2.succeeded()) {
            createRequestRecursively(conn, num + 1, callback);
          } else {
            callback.accept(ar2.cause(), num);
          }
        });
      } else {
        callback.accept(ar1.cause(), num);
      }
    });
  }
}
