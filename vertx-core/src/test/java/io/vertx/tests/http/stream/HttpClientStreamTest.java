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
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.MultiMap;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpVersion;
import io.vertx.core.http.impl.*;
import io.vertx.core.internal.http.ContentDecoder;
import io.vertx.core.internal.http.FlowController;
import io.vertx.core.net.HostAndPort;
import io.vertx.test.core.Checkpoint;
import io.vertx.test.core.TestUtils;
import io.vertx.test.http.HttpConfigurator;
import io.vertx.test.http.SimpleHttpTest2;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.function.Supplier;

import static org.junit.Assert.assertEquals;

public abstract class HttpClientStreamTest extends SimpleHttpTest2 {

  private volatile Handler<HttpServerRequest> handler;
  private CleanableHttpClient client;

  public HttpClientStreamTest(HttpConfigurator config) {
    super(config);
  }

  @Override
  public void setUp() throws Exception {
    super.setUp();
    server.requestHandler(request -> {
      Handler<HttpServerRequest> requestHandler = handler;
      if (requestHandler != null) {
        requestHandler.handle(request);
      }
    });
    client = (CleanableHttpClient)super.client;
  }

  @Test
  public void testOpenStream(Checkpoint checkpoint1, Checkpoint checkpoint2) throws Exception {
    handler = request -> {
      request
        .response()
        .end("Hello World");
    };
    startServer(testAddress);
    HttpClientStream stream = client.openStream(requestOptions).await();
    stream.headHandler(head -> {
      checkpoint1.succeed();
    });
    stream.trailersHandler(trailers -> {
      checkpoint2.succeed();
    });
    HttpClientConnection connection = stream.connection();
    HttpRequestHead head = new HttpRequestHead(
      "https",
      HttpMethod.GET,
      "/",
      connection.newHttpRequestHeaders(),
      HostAndPort.authority("localhost", testAddress.port()),
      null);
    stream.head(head).end();
  }

  @Test
  public void testWriteTrailers(Checkpoint checkpoint) throws Exception {
    handler = request -> request.body().onComplete(ar -> {
      if (ar.succeeded() && "the-body".equals(ar.result().toString()) && "trailer_value".equals(request.getTrailer("trailer_key"))) {
        checkpoint.succeed();
      } else {
        checkpoint.fail(new AssertionError("Unexpected request " + ar + " " + request.trailers()));
      }
      request.response().end();
    });
    startServer(testAddress);
    HttpClientStream stream = client.openStream(requestOptions).await();
    HttpClientConnection connection = stream.connection();
    MultiMap headers = connection.newHttpRequestHeaders();
    if (stream.version() == HttpVersion.HTTP_1_1) {
      headers.set(HttpHeaders.TRANSFER_ENCODING, HttpHeaders.CHUNKED);
    }
    HttpRequestHead head = new HttpRequestHead(
      "https",
      HttpMethod.POST,
      "/",
      headers,
      HostAndPort.authority("localhost", testAddress.port()),
      null);
    MultiMap trailers = connection.newHttpRequestHeaders();
    trailers.set("trailer_key", "trailer_value");
    stream.head(head).write(Buffer.buffer("the-body"));
    stream.end(trailers);
  }

  @Test
  public void testWriteHeadAndFlush(Checkpoint checkpoint) throws Exception {
    Promise<Void> received = Promise.promise();
    handler = request -> {
      received.complete();
      request.body().onComplete(ar -> {
        if (ar.succeeded() && "the-body".equals(ar.result().toString())) {
          checkpoint.succeed();
        } else {
          checkpoint.fail(new AssertionError("Unexpected request " + ar));
        }
        request.response().end();
      });
    };
    startServer(testAddress);
    HttpClientStream stream = client.openStream(requestOptions).await();
    HttpClientConnection connection = stream.connection();
    MultiMap headers = connection.newHttpRequestHeaders();
    if (stream.version() == HttpVersion.HTTP_1_1) {
      headers.set(HttpHeaders.TRANSFER_ENCODING, HttpHeaders.CHUNKED);
    }
    HttpRequestHead head = new HttpRequestHead(
      "https",
      HttpMethod.POST,
      "/",
      headers,
      HostAndPort.authority("localhost", testAddress.port()),
      null);
    stream.head(head).write(Buffer.buffer());
    received.future().await();
    stream.end(Buffer.buffer("the-body"));
  }

  @Test
  public void testContentDecoderAbruptEnd(Checkpoint checkpoint) throws Exception {
    Promise<Void> received = Promise.promise();
    handler = request -> {
      request
        .response()
        .setChunked(true)
        .write("chunk");
      received.future().onComplete(v -> request.connection().close());
    };
    startServer(testAddress);
    HttpClientStream stream = client.openStream(requestOptions).await();
    List<String> events = Collections.synchronizedList(new ArrayList<>());
    stream.contentDecoder(new ContentDecoder<Void>() {
      @Override
      public void init(FlowController flowController) {
        events.add("init");
      }
      @Override
      public void handle(ByteBuf chunk) {
        events.add("handle");
        received.tryComplete();
      }
      @Override
      public Void handleEnd() {
        events.add("handleEnd");
        return null;
      }
      @Override
      public void handleClose(Throwable cause) {
        events.add(cause != null ? "handleClose(abrupt)" : "handleClose");
        vertx.setTimer(100, id -> {
          assertEquals(List.of("init", "handle", "future-failure", "handleClose(abrupt)"), events);
          checkpoint.succeed();
        });
      }
    }).onComplete(ar -> events.add(ar.succeeded() ? "future-success" : "future-failure"));
    HttpClientConnection connection = stream.connection();
    HttpRequestHead head = new HttpRequestHead(
      "https",
      HttpMethod.GET,
      "/",
      connection.newHttpRequestHeaders(),
      HostAndPort.authority("localhost", testAddress.port()),
      null);
    stream.head(head).end();
  }

  @Test
  public void testContentDecoderHandleFailure(Checkpoint checkpoint) throws Exception {
    testContentDecoderFailure(checkpoint, () -> new HttpServerStreamTest.ContentDecoderBase() {
      @Override
      protected void handleImpl(ByteBuf chunk) {
        throw HttpServerStreamTest.FAILURE;
      }
    }, List.of("handle", "future-failure", "handleClose(failure)", "stream-close"), true);
  }

  @Test
  public void testContentDecoderHandleFailureWhilePaused(Checkpoint checkpoint) throws Exception {
    testContentDecoderFailure(checkpoint, () -> new HttpServerStreamTest.ContentDecoderBase() {
      @Override
      protected void handleImpl(ByteBuf chunk) {
        flowController.pause();
        throw HttpServerStreamTest.FAILURE;
      }
    }, List.of("handle", "future-failure", "handleClose(failure)", "stream-close"), true, TestUtils.randomAlphaString(1024 * 1024));
  }

  @Test
  public void testContentDecoderHandleEndFailure(Checkpoint checkpoint) throws Exception {
    testContentDecoderFailure(checkpoint, () -> new HttpServerStreamTest.ContentDecoderBase() {
      @Override
      protected void handleEndImpl() {
        throw HttpServerStreamTest.FAILURE;
      }
    }, List.of("handle", "handleEnd", "future-failure", "handleClose(failure)", "stream-close"), true);
  }

  @Test
  public void testContentDecoderHandleCloseFailure(Checkpoint checkpoint) throws Exception {
    testContentDecoderFailure(checkpoint, () -> new HttpServerStreamTest.ContentDecoderBase() {
      @Override
      protected void handleCloseImpl(Throwable cause) {
        throw HttpServerStreamTest.FAILURE;
      }
    }, List.of("handle", "handleEnd", "future-success:" + HttpServerStreamTest.RESULT, "handleClose", "context-exception", "stream-close"), false);
  }

  @Test
  public void testContentDecoderInitFailure(Checkpoint checkpoint) throws Exception {
    testContentDecoderFailure(checkpoint, () -> new HttpServerStreamTest.ContentDecoderBase() {
      @Override
      protected void initImpl(FlowController flowController) {
        throw HttpServerStreamTest.FAILURE;
      }
    }, List.of("init-thrown", "stream-close"), true);
  }

  private void testContentDecoderFailure(Checkpoint checkpoint, Supplier<HttpServerStreamTest.ContentDecoderBase> decoder, List<String> expected, boolean ordered) throws Exception {
    testContentDecoderFailure(checkpoint, decoder, expected, ordered, "chunk-2");
  }

  private void testContentDecoderFailure(Checkpoint checkpoint, Supplier<HttpServerStreamTest.ContentDecoderBase> decoder, List<String> expected, boolean ordered, String lastChunk) throws Exception {
    RuntimeException failure = HttpServerStreamTest.FAILURE;
    HttpServerStreamTest.ContentDecoderBase contentDecoder = decoder.get();
    List<String> events = contentDecoder.events;
    vertx.exceptionHandler(err -> events.add(err == failure ? "context-exception" : "context-exception:" + err));
    handler = request -> request
      .response()
      .setChunked(true)
      .write("chunk-1")
      .compose(v -> request.response().end(lastChunk));
    startServer(testAddress);
    HttpClientStream stream = client.openStream(requestOptions).await();
    stream.exceptionHandler(err -> events.add(err == failure ? "stream-exception" : "stream-exception:" + err));
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
      stream.contentDecoder(contentDecoder).onComplete(ar -> events.add(HttpServerStreamTest.outcome(ar)));
    } catch (RuntimeException e) {
      events.add(e == failure ? "init-thrown" : "init-thrown:" + e);
    }
    HttpClientConnection connection = stream.connection();
    HttpRequestHead head = new HttpRequestHead(
      "https",
      HttpMethod.GET,
      "/",
      connection.newHttpRequestHeaders(),
      HostAndPort.authority("localhost", testAddress.port()),
      null);
    stream.head(head).end();
  }
}
