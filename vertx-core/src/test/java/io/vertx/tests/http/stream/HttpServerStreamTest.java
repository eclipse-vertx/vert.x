/*
 * Copyright (c) 2011-2026 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package io.vertx.tests.http.stream;

import io.netty.buffer.Unpooled;
import io.vertx.core.Handler;
import io.vertx.core.MultiMap;
import io.vertx.core.Promise;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.http.HttpClientResponse;
import io.vertx.core.http.HttpClosedException;
import io.vertx.core.http.HttpConnection;
import io.vertx.core.http.impl.HttpResponseHead;
import io.vertx.core.http.impl.HttpServerConnection;
import io.vertx.core.http.impl.HttpServerStream;
import io.vertx.core.internal.buffer.BufferInternal;
import io.vertx.test.core.Checkpoint;
import io.vertx.test.core.TestUtils;
import io.vertx.test.http.HttpConfigurator;
import io.vertx.test.http.SimpleHttpTest2;
import org.junit.Assert;
import org.junit.Test;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

public abstract class HttpServerStreamTest extends SimpleHttpTest2 {

  private static final BufferInternal EMPTY_BUFFER = BufferInternal.buffer(Unpooled.EMPTY_BUFFER);

  private volatile Handler<HttpServerStream> handler;

  public HttpServerStreamTest(HttpConfigurator config) {
    super(config);
  }

  @Override
  public void setUp() throws Exception {
    super.setUp();
    server.requestHandler(request -> {
      Assert.fail();
    });
    server.connectionHandler(connection -> {
      HttpServerConnection serverConnection = (HttpServerConnection)connection;
      serverConnection.streamHandler(handler);
    });
  }

  @Test
  public void testHandleStream(Checkpoint checkpoint) throws Exception {
    handler = stream -> {
      checkpoint.succeed();
    };
    startServer(testAddress);
    client
      .request(requestOptions)
      .compose(HttpClientRequest::writeHead)
      .await();
  }

  @Test
  public void testHandleRequestHead(Checkpoint checkpoint) throws Exception {
    handler = stream -> {
      stream.headHandler(head -> {
        checkpoint.succeed();
      });
    };
    startServer(testAddress);
    client
      .request(requestOptions)
      .compose(HttpClientRequest::writeHead)
      .await();
  }

  @Test
  public void testHandleResponseChunks(Checkpoint checkpoint) throws Exception {
    int numChunks = 128;
    String expectedChunk = TestUtils.randomAlphaString(128);
    handler = stream -> {
      AtomicInteger count = new AtomicInteger();
      stream.dataHandler(chunk -> {
        count.addAndGet(chunk.length());
      });
      stream.endHandler(v -> {
        Assert.assertEquals(numChunks * expectedChunk.length(), count.get());
        checkpoint.succeed();
      });
    };
    startServer(testAddress);
    HttpClientRequest request = client
      .request(requestOptions).await();
    for (int i = 0;i < numChunks;i++) {
      request.write(expectedChunk).await();
    }
    request.end();
  }

  @Test
  public void testHandleCloseEndingResponse1(Checkpoint checkpoint) throws Exception {
    testHandleCloseEndingResponse(checkpoint, stream -> {
      HttpResponseHead head = new HttpResponseHead(200, null, stream.connection().newHeaders());
      stream.writeHead(head, EMPTY_BUFFER, true);
    });
  }

  @Test
  public void testHandleCloseEndingResponse2(Checkpoint checkpoint) throws Exception {
    testHandleCloseEndingResponse(checkpoint, stream -> {
      HttpResponseHead head = new HttpResponseHead(200, null, stream.connection().newHeaders());
      stream.writeHead(head, EMPTY_BUFFER, false);
      stream.write(EMPTY_BUFFER, true);
    });
  }

  @Test
  public void testHandleCloseEndingResponse3(Checkpoint checkpoint) throws Exception {
    testHandleCloseEndingResponse(checkpoint, stream -> {
      HttpResponseHead head = new HttpResponseHead(200, null, stream.connection().newHeaders());
      stream.writeHead(head, EMPTY_BUFFER, false);
      stream.end(stream.connection().newHeaders());
    });
  }

  private void testHandleCloseEndingResponse(Checkpoint checkpoint, Consumer<HttpServerStream> h) throws Exception {
    handler = stream -> {
      AtomicBoolean sent = new AtomicBoolean();
      stream.closeHandler(head -> {
        Assert.assertTrue(sent.get());
        checkpoint.succeed();
      });
      stream.endHandler(v -> {
        vertx.setTimer(250, id -> {
          sent.set(true);
          h.accept(stream);
        });
      });
    };
    startServer(testAddress);
    client
      .request(requestOptions)
      .compose(request -> request
        .send()
        .compose(HttpClientResponse::body))
      .await();
  }

  @Test
  public void testHandleCloseEndingRequest1(Checkpoint checkpoint) throws Exception {
    testHandleCloseEndingRequest(checkpoint, stream -> {
      HttpResponseHead head = new HttpResponseHead(200, null, stream.connection().newHeaders());
      stream.writeHead(head, EMPTY_BUFFER, true);
    });
  }

  @Test
  public void testHandleCloseEndingRequest2(Checkpoint checkpoint) throws Exception {
    testHandleCloseEndingRequest(checkpoint, stream -> {
      HttpResponseHead head = new HttpResponseHead(200, null, stream.connection().newHeaders());
      stream.writeHead(head, EMPTY_BUFFER, false);
      stream.write(EMPTY_BUFFER, true);
    });
  }

  @Test
  public void testHandleCloseEndingRequest3(Checkpoint checkpoint) throws Exception {
    testHandleCloseEndingRequest(checkpoint, stream -> {
      HttpResponseHead head = new HttpResponseHead(200, null, stream.connection().newHeaders());
      stream.writeHead(head, EMPTY_BUFFER, false);
      stream.end(stream.connection().newHeaders());
    });
  }

  private void testHandleCloseEndingRequest(Checkpoint checkpoint, Consumer<HttpServerStream> h) throws Exception {
    handler = stream -> {
      stream.closeHandler(head -> checkpoint.succeed());
      h.accept(stream);
    };
    startServer(testAddress);
    HttpClientRequest request = client
      .request(requestOptions)
      .compose(r -> r
        .writeHead()
        .compose(v -> r.response())
        .map(v -> r))
      .await();
    request.end();
  }

  @Test
  public void testHandleCloseClosingConnection1(Checkpoint checkpoint1, Checkpoint checkpoint2) throws Exception {
    handler = stream -> {
      stream.closeHandler(head -> checkpoint1.succeed());
      stream.exceptionHandler(err -> {
        if (err instanceof HttpClosedException) {
          Assert.assertNotNull(err);
          checkpoint2.succeed();
        }
      });
      HttpResponseHead head = new HttpResponseHead(200, null, stream.connection().newHeaders());
      stream.writeHead(head, EMPTY_BUFFER, true);
    };
    startServer(testAddress);
    HttpConnection connection = client
      .request(requestOptions)
      .compose(request -> request
        .writeHead()
        .compose(v -> request.response())
        .map(v -> request.connection()))
      .await();
    connection.close().await();
  }

  @Test
  public void testHandleCloseClosingConnection2(Checkpoint checkpoint1, Checkpoint checkpoint2, Checkpoint checkpoint3) throws Exception {
    handler = stream -> {
      stream.closeHandler(head -> checkpoint2.succeed());
      stream.exceptionHandler(err -> {
        if (err instanceof HttpClosedException) {
          Assert.assertNotNull(err);
          checkpoint3.succeed();
        }
      });
      checkpoint1.succeed();
    };
    startServer(testAddress);
    HttpConnection connection = client
      .request(requestOptions)
      .compose(request -> request
        .end()
        .map(response -> request.connection()))
      .await();
    checkpoint1.awaitSuccess();
    connection.close().await();
  }

  @Test
  public void testWriteChunkedResponse(Checkpoint checkpoint1, Checkpoint checkpoint2) throws Exception {
    testHttpResponse(stream -> {
      HttpResponseHead head = new HttpResponseHead(
        200,
        null,
        stream.connection().newHeaders());
      stream.writeHead(head, EMPTY_BUFFER, false)
        .onComplete(checkpoint1);
      stream.write(Buffer.buffer("Hello"), true)
        .onComplete(checkpoint2);
    });
  }

  @Test
  public void testWriteFullHttpResponse(Checkpoint checkpoint) throws Exception {
    testHttpResponse(stream -> {
      HttpResponseHead head = new HttpResponseHead(
        200,
        null,
        stream.connection().newHeaders());
      stream.writeHead(head, Buffer.buffer("Hello"), true)
        .onComplete(checkpoint);
    });
  }

  private void testHttpResponse(Consumer<HttpServerStream> consumer) throws Exception {
    handler = stream -> {
      stream.endHandler(v -> {
        consumer.accept(stream);
      });
    };
    startServer(testAddress);
    Buffer body = client
      .request(requestOptions)
      .compose(request -> request
        .send()
        .compose(HttpClientResponse::body))
      .await();
    Assert.assertEquals("Hello", body.toString());
  }

  @Test
  public void testWriteTrailers(Checkpoint checkpoint) throws Exception {
    handler = stream -> {
      HttpResponseHead head = new HttpResponseHead(
        200,
        null,
        stream.connection().newHeaders());
      MultiMap trailers = stream.connection().newHeaders();
      trailers.set("trailer_key", "trailer_value");
      stream.writeHead(head, EMPTY_BUFFER, false);
      stream.end(trailers)
        .onComplete(checkpoint);
    };
    startServer(testAddress);
    MultiMap trailers = client
      .request(requestOptions)
      .compose(request -> request
        .send()
        .compose(response -> response.end().map(v -> response.trailers())))
      .await();
    Assert.assertEquals(1, trailers.size());
    Assert.assertEquals("trailer_value", trailers.get("trailer_key"));
  }

  @Test
  public void testResponseFlowControl() throws Exception {
    Promise<Void> resume = Promise.promise();
    Buffer chunk = Buffer.buffer(TestUtils.randomAlphaString(128));
    handler = stream -> {
      HttpResponseHead head = new HttpResponseHead(
        200,
        null,
        stream.connection().newHeaders());
      stream.writeHead(head, EMPTY_BUFFER, false);
      int count = 0;
      while (stream.isWritable()) {
        stream.write(chunk, false);
        count++;
      }
      stream.drainHandler(v -> {
        stream.write(EMPTY_BUFFER, true);
      });
      resume.succeed();
    };
    startServer(testAddress);
    client
      .request(requestOptions)
      .compose(request -> request
        .send()
        .compose(response -> {
          response.pause();
          resume.future().onSuccess(v -> {
            response.resume();
          });
          return response.body();
        }))
      .await();
  }

  @Test
  public void testWriteInvalidChunk(Checkpoint checkpoint) throws Exception {
    testInvalidMessage(stream -> {
      stream.write(Buffer.buffer("Hello World"), false).onComplete(ar -> {
        Assert.assertTrue(ar.failed());
        checkpoint.succeed();
      });
    });
  }

  @Test
  public void testWriteInvalidTrailers(Checkpoint checkpoint) throws Exception {
    testInvalidMessage(stream -> {
      stream.write(EMPTY_BUFFER, true).onComplete(ar -> {
        Assert.assertTrue(ar.failed());
        checkpoint.succeed();
      });
    });
  }

  private void testInvalidMessage(Consumer<HttpServerStream> consumer) throws Exception {
    handler = stream -> {
      stream.headHandler(head -> {
      });
      stream.endHandler(v -> {
        consumer.accept(stream);
      });
    };
    startServer(testAddress);
    client
      .request(requestOptions)
      .compose(HttpClientRequest::end)
      .await();
  }
}
