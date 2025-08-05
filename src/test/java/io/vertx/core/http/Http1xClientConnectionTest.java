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
import io.vertx.core.Promise;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.impl.HttpRequestHead;
import io.vertx.core.impl.ContextInternal;
import io.vertx.core.impl.logging.Logger;
import io.vertx.core.impl.logging.LoggerFactory;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public class Http1xClientConnectionTest extends HttpClientConnectionTest {
  private static final Logger log = LoggerFactory.getLogger(Http1xClientConnectionTest.class);;

  @Test
  public void testResetStreamBeforeSend() throws Exception {
    waitFor(1);
    server.requestHandler(req -> {
    });
    startServer(testAddress);
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
    startServer(testAddress);
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
          HttpMethod.GET, "/", MultiMap.caseInsensitiveMultiMap(), DEFAULT_HTTP_HOST_AND_PORT, "", null), false, Unpooled.EMPTY_BUFFER, false, new StreamPriority(), false, null);
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
    startServer(testAddress);
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
          HttpMethod.GET, "/", MultiMap.caseInsensitiveMultiMap(), DEFAULT_HTTP_HOST_AND_PORT, "", null), false, Unpooled.EMPTY_BUFFER, true, new StreamPriority(), false, null);
      }));
    }));
    await();
  }

  @Test
  /*
   * This test reproduces an issue involving connection pooling and parallel reads to the same server. When
   * you have a client which pauses its reading of data AND that remaining data is able to be read into InboundBuffer,
   * AND then you have another client to the same server on the same port (via pooling) which attempts to read all
   * of the data. Then the second client will "hang" and eventually fail due to a timeout (if there is one).
   *
   * In order to avoid this as-is, connection pooling has to be disabled which may not be practical.
   */
  public void testConnectionReuseLeadsToStuckGetRequest() throws TimeoutException, ExecutionException, InterruptedException {
    // This magic number is based on the high watermark of Http1xClientConnection, which is based on Netty.
    // The HTTP client uses Netty's HTTP object decoder, which uses a chunk size of 8KiB. This means that
    // a socket read of 64KiB (the write queue limit of the channel) will result in up to 8 messages which are
    // passed down to the Vert.x handler. When the original request is paused after reading one buffer, it
    // leaves 56KiB in the bytesWindow (not enough to hit the HWM and disable autoRead).
    // When the next request comes in, it will read 64KiB from the socket. Then 8KiB into the InboundBuffer. Since
    // this will be > the HWM (64KiB + 1), autoRead will be disabled. The second request is not paused, so it will
    // then read the 8KiB which was buffered. However, since autoRead is disabled now, no further bytes will be read
    int contentLength = 57344 + 1;
    // This is the max number of requests to make before considering the test a success
    int maxRequests = 5000;
    // The idle timeout is arbitrary, but is long to show that the request will never complete
    int idleTimeout = 30000;
    Map<Integer, Integer> ports = new HashMap<>();
    HttpClient client = vertx.createHttpClient();
    HttpServer server = vertx.createHttpServer();

    // Set up a generic server which responds with data that will exceed the HWM of Http1xClientConnection (64KiB)
    // However, the data can't be too large because it needs to all be buffered by InboundBuffer, otherwise
    // the original request which is paused won't be complete
    Future<HttpServer> listen = server.requestHandler(req -> {
      req.response().putHeader("Content-Length", contentLength + "");
      req.response().write(Buffer.buffer(new byte[contentLength]));
      req.response().end();
    }).listen(0);

    CompletableFuture<Void> serverStarted = new CompletableFuture<>();
    listen.onComplete(event -> serverStarted.complete(null));
    serverStarted.get(idleTimeout, TimeUnit.MILLISECONDS);
    int portToUse = listen.result().actualPort();
    log.debug("Started server on port: " + portToUse);

    // Sequence to reproduce:
    // first request - reads 56KiB + 1 from socket. auto read -> true. bytes window: 0
    // first request client stream is paused. bytes window: 56KiB + 1. auto read -> true
    // first request - complete because socket read was complete. connection is back to pool. bytes window: 56KiB + 1, auto read -> true
    // second request - read some amount of data < total content length from socket
    // second request - splits into 8KiB chunks, passes one to Vert.x. bytes window: 64KiB + 1. auto read -> false
    // second request - not paused, so it will ack 8KiB + 1. bytes window: 56KiB. auto read -> false
    // second request - will read remaining content that was read from socket. But since auto read is false,
    // it will never read any additional data from the socket and will hang

    AtomicInteger pausedRequestPort = new AtomicInteger();
    AtomicReference<String> pausedRequestId = new AtomicReference<>();
    AtomicInteger pausedRequestBuffer = new AtomicInteger();

    Future<HttpClientRequest> originalRequest = client.request(HttpMethod.GET, portToUse, "localhost", "/");
    originalRequest.onComplete(response -> {
      HttpClientRequest result = response.result();
      String requestId = UUID.randomUUID().toString();
      pausedRequestId.set(requestId);
      pausedRequestPort.set(response.result().connection().localAddress().port());
      log.error("The problematic request id is: " + requestId);
      ports.putIfAbsent(pausedRequestPort.get(), 0);
      ports.put(pausedRequestPort.get(), ports.get(pausedRequestPort.get()) + 1);

      result.response().onComplete(arResult -> {
        HttpClientResponse res = arResult.result();
        // Even though the response stream is paused, the remaining data will fit into InboundBuffer's pending
        // queue. This means its own end handler will be triggered, and the connection is available for reuse
        res.handler(event -> pausedRequestBuffer.getAndAdd(event.length()));
        res.pause();
      });
      result.end();
    });

    // Now loop until we can get a request which shares the same port as the original request which is paused
    // This means the connection was reused
    int requestCount = 0;
    while (requestCount < maxRequests) {
      requestCount++;
      // We create requests sequentially and block on the future completing. The future is complete when all data
      // is retrieved. There is no need to do parallel requests to reproduce the issue.
      CompletableFuture<Void> future = new CompletableFuture<>();
      String requestId = UUID.randomUUID().toString();
      AtomicInteger buffer = new AtomicInteger();

      client.request(HttpMethod.GET, portToUse, "localhost", "/").onComplete(response -> {
        HttpClientRequest result = response.result();
        int localPort = response.result().connection().localAddress().port();
        ports.putIfAbsent(localPort, 0);
        ports.put(localPort, ports.get(localPort) + 1);

        if (localPort == pausedRequestPort.get()) {
          // We can detect that this request should hang because it's reusing the paused request's port.
          log.error(String.format("%s should be a stuck get on port %d which is caused by %s",
            requestId, pausedRequestPort.get(), pausedRequestId.get()));
        }

        // This block of code just processes the results from the server. It completes the request future once
        // the end handler runs. If the end handler never runs, it means we have successfully hit a "stuck get"
        result.response().onComplete(arResult -> {
          HttpClientResponse res = arResult.result();
          if (res == null) {
            log.error(requestId + " had a null result, oops");
            future.completeExceptionally(new NullPointerException());
          } else {
            res.handler(event -> {
              buffer.getAndAdd(event.length());
            });
            res.endHandler(event -> {
              if (buffer.get() != contentLength) {
                future.completeExceptionally(new RuntimeException(String.format("content length doesn't match: Was %s" +
                  " but expected %s", buffer.get(), contentLength)));
              } else {
                future.complete(null);
              }
            });
            res.exceptionHandler(t -> {
              log.error(t.getMessage(), t);
              future.completeExceptionally(t);
            });
          }
        });
        result.end();
      });
      try {
        log.debug("Waiting on: " + requestId);
        future.get(idleTimeout, TimeUnit.MILLISECONDS);
      } catch (InterruptedException | ExecutionException e) {
        throw new RuntimeException(e);
      } catch (TimeoutException e) {
        log.error(String.format("Timed out waiting on: %s. Received %d out of %d bytes", requestId, buffer.get(),
          contentLength));
        log.error(ports);
        throw e;
      }
    }
    log.debug(String.format("Ports in use are below. The problematic request was on port %d so there must be > 1 " +
      "request on that port to reproduce this issue", pausedRequestPort.get()));
    log.debug(ports);
  }
}
