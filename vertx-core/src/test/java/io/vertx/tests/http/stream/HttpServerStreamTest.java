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

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.MultiMap;
import io.vertx.core.Promise;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.http.HttpClientResponse;
import io.vertx.core.http.HttpClosedException;
import io.vertx.core.http.HttpConnection;
import io.vertx.core.http.impl.*;
import io.vertx.core.internal.buffer.BufferInternal;
import io.vertx.core.internal.http.ContentDecoder;
import io.vertx.core.internal.http.FlowController;
import io.vertx.test.core.Checkpoint;
import io.vertx.test.core.TestUtils;
import io.vertx.test.http.HttpConfigurator;
import io.vertx.test.http.SimpleHttpTest2;
import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Supplier;

import static org.junit.Assert.*;

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
  public void testContentDecoderEvents(Checkpoint checkpoint) throws Exception {
    int numChunks = 128;
    String expectedChunk = TestUtils.randomAlphaString(128);
    handler = stream -> {
      AtomicInteger count = new AtomicInteger();
      stream.contentDecoder(new ContentDecoderBase() {
        @Override
        protected void handleImpl(ByteBuf chunk) {
          count.addAndGet(chunk.readableBytes());
        }
        @Override
        protected void handleEndImpl() {
          vertx.setTimer(250, id -> {
            assertEquals(numChunks * expectedChunk.length(), count.get());
            checkpoint.succeed();
          });
        }
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
  public void testContentDecoderLifecycle(Checkpoint checkpoint1, Checkpoint checkpoint2) throws Exception {
    String expectedChunk = TestUtils.randomAlphaString(128);
    handler = stream -> {
      AtomicInteger count = new AtomicInteger();
      stream.contentDecoder(new ContentDecoderBase() {
        @Override
        protected void handleImpl(ByteBuf chunk) {
          count.addAndGet(chunk.readableBytes());
          checkpoint2.succeed();
        }
        @Override
        protected void handleCloseImpl(Throwable cause) {
          checkpoint1.succeed();
        }
      });
    };
    startServer(testAddress);
    HttpClientRequest request = client
      .request(requestOptions).await();
    request.write(expectedChunk).await();
    checkpoint2.awaitSuccess();
    request.connection().close().await();
  }

  @Test
  public void testContentDecoderLifecycleWithFetch(Checkpoint checkpoint) throws Exception {
    int numChunks = 10;
    String expectedChunk = TestUtils.randomAlphaString(128);
    handler = stream -> {
      AtomicInteger count = new AtomicInteger();
      AtomicInteger closed = new AtomicInteger();
      stream.contentDecoder(new ContentDecoderBase() {
        @Override
        protected void handleImpl(ByteBuf chunk) {
          int val = count.incrementAndGet();
          if (val == 1) {
            flowController.pause();
          } else {
            checkpoint.fail(new AssertionError("chunk delivered after abrupt end"));
          }
        }
        @Override
        protected void handleEndImpl() {
          checkpoint.fail(new AssertionError("handleEnd delivered after abrupt close"));
        }
        @Override
        protected void handleCloseImpl(Throwable cause) {
          assertEquals(0, closed.getAndIncrement());
          assertNotNull(cause);
          assertEquals(1, count.get());
          flowController.resume();
          vertx.setTimer(100, id -> checkpoint.succeed());
        }
      });
    };
    startServer(testAddress);
    HttpClientRequest request = client
      .request(requestOptions).await();
    for (int i = 0;i < numChunks;i++) {
      request.write(expectedChunk).await();
    }
    request.connection().close().await();
  }

  @Test
  public void testContentDecoderEndReceivedWhilePaused(Checkpoint checkpoint1, Checkpoint checkpoint2) throws Exception {
    int numChunks = 10;
    String expectedChunk = TestUtils.randomAlphaString(128);
    handler = stream -> {
      AtomicInteger bytes = new AtomicInteger();
      AtomicBoolean closed = new AtomicBoolean();
      stream.contentDecoder(new ContentDecoderBase() {
        @Override
        protected void handleImpl(ByteBuf chunk) {
          if (bytes.getAndAdd(chunk.readableBytes()) == 0) {
            flowController.pause();
            checkpoint2.succeed();
          } else {
            assertTrue(closed.get());
          }
        }
        @Override
        protected void handleEndImpl() {
          assertTrue(closed.get());
          assertEquals(numChunks * expectedChunk.length(), bytes.get());
          checkpoint1.succeed();
        }
        @Override
        protected void handleCloseImpl(Throwable cause) {
          assertNull(cause);
          closed.set(true);
          flowController.resume();
        }
      });
    };
    startServer(testAddress);
    HttpClientRequest request = client
      .request(requestOptions).await();
    for (int i = 0;i < numChunks;i++) {
      request.write(expectedChunk).await();
    }
    request.end().await();
    checkpoint2.awaitSuccess();
    request.connection().close().await();
  }

  @Test
  public void testContentDecoderClosedByServerConnectionCloseWhilePaused(Checkpoint checkpoint1, Checkpoint checkpoint2) throws Exception {
    AtomicReference<HttpConnection> connection = new AtomicReference<>();
    handler = stream -> {
      connection.set(stream.connection());
      stream.contentDecoder(new ContentDecoderBase() {
        @Override
        protected void handleImpl(ByteBuf chunk) {
          flowController.pause();
          checkpoint2.succeed();
        }
        @Override
        protected void handleCloseImpl(Throwable cause) {
          assertTrue(cause instanceof HttpClosedException);
          checkpoint1.succeed();
        }
      });
    };
    startServer(testAddress);
    HttpClientRequest request = client
      .request(requestOptions).await();
    request.setChunked(true).write(TestUtils.randomAlphaString(1024 * 1024));
    checkpoint2.awaitSuccess();
    connection.get().close().await();
  }

  @Test
  public void testContentDecoderReplaced(Checkpoint checkpoint1, Checkpoint checkpoint2) throws Exception {
    handler = stream -> {
      ContentDecoderBase replaced = new ContentDecoderBase() {
        @Override
        protected void handleImpl(ByteBuf chunk) {
          checkpoint1.fail(new AssertionError("content delivered to the replaced decoder"));
        }
      };
      stream.contentDecoder(replaced).onComplete(ar -> {
        assertSame(HttpUtils.STREAM_CLOSED_EXCEPTION, ar.cause());
        checkpoint1.succeed();
      });
      ContentDecoderBase decoder = new ContentDecoderBase() {
      };
      stream.contentDecoder(decoder).onComplete(ar -> {
        assertEquals(RESULT, ar.result());
        assertEquals(List.of("handleClose(" + HttpUtils.STREAM_CLOSED_EXCEPTION + ")"), replaced.events);
        assertEquals(List.of("handle", "handleEnd"), decoder.events);
        checkpoint2.succeed();
      });
    };
    startServer(testAddress);
    client
      .request(requestOptions)
      .compose(request -> request.end("chunk"))
      .await();
  }

  @Test
  public void testContentDecoderHandleFailure(Checkpoint checkpoint) throws Exception {
    testContentDecoderFailure(checkpoint, () -> new ContentDecoderBase() {
      @Override
      protected void handleImpl(ByteBuf chunk) {
        throw FAILURE;
      }
    }, List.of("handle", "future-failure", "handleClose(failure)", "stream-close"), true);
  }

  @Test
  public void testContentDecoderHandleFailureWhilePaused(Checkpoint checkpoint) throws Exception {
    testContentDecoderFailure(checkpoint, () -> new ContentDecoderBase() {
      @Override
      protected void handleImpl(ByteBuf chunk) {
        flowController.pause();
        throw FAILURE;
      }
    }, List.of("handle", "future-failure", "handleClose(failure)", "stream-close"), true, TestUtils.randomAlphaString(1024 * 1024));
  }

  @Test
  public void testContentDecoderHandleEndFailure(Checkpoint checkpoint) throws Exception {
    testContentDecoderFailure(checkpoint, () -> new ContentDecoderBase() {
      @Override
      protected void handleEndImpl() {
        throw FAILURE;
      }
    }, List.of("handle", "handleEnd", "future-failure", "handleClose(failure)", "stream-close"), true);
  }

  @Test
  public void testContentDecoderHandleCloseFailure(Checkpoint checkpoint) throws Exception {
    testContentDecoderFailure(checkpoint, () -> new ContentDecoderBase() {
      @Override
      protected void handleCloseImpl(Throwable cause) {
        throw FAILURE;
      }
    }, List.of("handle", "handleEnd", "future-success:" + RESULT, "handleClose", "context-exception", "stream-close"), false);
  }

  @Test
  public void testContentDecoderInitFailure(Checkpoint checkpoint) throws Exception {
    testContentDecoderFailure(checkpoint, () -> new ContentDecoderBase() {
      @Override
      protected void initImpl(FlowController flowController) {
        throw FAILURE;
      }
    }, List.of("init-thrown", "stream-close"), true);
  }

  private void testContentDecoderFailure(Checkpoint checkpoint, Supplier<ContentDecoderBase> decoder, List<String> expected, boolean ordered) throws Exception {
    testContentDecoderFailure(checkpoint, decoder, expected, ordered, "chunk-2");
  }

  private void testContentDecoderFailure(Checkpoint checkpoint, Supplier<ContentDecoderBase> decoder, List<String> expected, boolean ordered, String lastChunk) throws Exception {
    ContentDecoderBase contentDecoder = decoder.get();
    List<String> events = contentDecoder.events;
    vertx.exceptionHandler(err -> events.add(err == FAILURE ? "context-exception" : "context-exception:" + err));
    handler = stream -> {
      stream.exceptionHandler(err -> events.add(err == FAILURE ? "stream-exception" : "stream-exception:" + err));
      stream.trailersHandler(trailers -> {
        HttpResponseHead head = new HttpResponseHead(200, null, stream.connection().newHeaders());
        stream.writeHead(head, EMPTY_BUFFER, true);
      });
      stream.closeHandler(v -> {
        events.add("stream-close");
        vertx.setTimer(100, id -> {
          List<String> actual = new ArrayList<>(events);
          boolean match = ordered ? expected.equals(actual) : expected.size() == actual.size() && new HashSet<>(expected).equals(new HashSet<>(actual));
          if (match) {
            checkpoint.succeed();
          } else {
            checkpoint.fail(new AssertionError("Expected " + expected + " but was " + actual));
          }
        });
      });
      try {
        stream.contentDecoder(contentDecoder).onComplete(ar -> events.add(outcome(ar)));
      } catch (RuntimeException e) {
        events.add(e == FAILURE ? "init-thrown" : "init-thrown:" + e);
      }
    };
    startServer(testAddress);
    HttpClientRequest request = client
      .request(requestOptions).await();
    request.setChunked(true).write("chunk-1").await();
    request.end(lastChunk);
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
      stream.trailersHandler(trailers -> {
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
      stream.trailersHandler(trailers -> {
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
    assertEquals("Hello", body.toString());
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
    assertEquals(1, trailers.size());
    assertEquals("trailer_value", trailers.get("trailer_key"));
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
      stream.trailersHandler(trailers -> {
        consumer.accept(stream);
      });
    };
    startServer(testAddress);
    client
      .request(requestOptions)
      .compose(HttpClientRequest::end)
      .await();
  }

  static final RuntimeException FAILURE = new RuntimeException("expected failure");
  static final String RESULT = "decoded";

  static String outcome(AsyncResult<?> ar) {
    if (ar.succeeded()) {
      return "future-success:" + ar.result();
    } else {
      return ar.cause() == FAILURE ? "future-failure" : "future-failure:" + ar.cause();
    }
  }

  abstract static class ContentDecoderBase implements ContentDecoder<String> {

    final List<String> events = Collections.synchronizedList(new ArrayList<>());
    protected FlowController flowController;

    @Override
    public final void init(FlowController flowController) {
      this.flowController = flowController;
      initImpl(flowController);
    }

    @Override
    public final void handle(ByteBuf chunk) {
      synchronized (events) {
        if (events.isEmpty() || !events.get(events.size() - 1).equals("handle")) {
          events.add("handle");
        }
      }
      handleImpl(chunk);
    }

    @Override
    public final String handleEnd() {
      events.add("handleEnd");
      handleEndImpl();
      return RESULT;
    }

    @Override
    public final void handleClose(Throwable cause) {
      events.add(cause == null ? "handleClose" : cause == FAILURE ? "handleClose(failure)" : "handleClose(" + cause + ")");
      handleCloseImpl(cause);
    }

    protected void initImpl(FlowController flowController) {
    }

    protected void handleImpl(ByteBuf chunk) {
    }

    protected void handleEndImpl() {
    }

    protected void handleCloseImpl(Throwable cause) {
    }
  }
}
