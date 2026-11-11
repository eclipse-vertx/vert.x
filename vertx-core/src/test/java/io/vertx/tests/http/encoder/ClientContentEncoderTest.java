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
package io.vertx.tests.http.encoder;

import io.vertx.core.Handler;
import io.vertx.core.MultiMap;
import io.vertx.core.Promise;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.impl.CleanableHttpClient;
import io.vertx.core.http.impl.HttpClientStream;
import io.vertx.core.http.impl.HttpRequestHead;
import io.vertx.core.internal.http.ContentEncoder;
import io.vertx.core.net.HostAndPort;
import io.vertx.test.core.Checkpoint;
import io.vertx.test.http.HttpConfigurator;
import org.junit.Test;

/**
 * Tests the client stream outbound content.
 */
public abstract class ClientContentEncoderTest extends ContentEncoderTestBase {

  private volatile Handler<HttpServerRequest> handler;
  private CleanableHttpClient client;

  public ClientContentEncoderTest(HttpConfigurator config) {
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

  private HttpRequestHead head(HttpClientStream stream, long contentLength) {
    MultiMap headers = stream.connection().newHttpRequestHeaders();
    headers.set("content-length", Long.toString(contentLength));
    return new HttpRequestHead(
      "https",
      HttpMethod.PUT,
      "/",
      headers,
      HostAndPort.authority("localhost", testAddress.port()),
      null);
  }

  @Test
  public void testWriteReleasesBuffers(Checkpoint checkpoint) throws Exception {
    handler = request -> request.body().onComplete(ar -> {
      if (ar.succeeded() && ar.result().length() == 9 * 128) {
        request.response().end();
      }
    });
    startServer(testAddress);
    HttpClientStream stream = client.openStream(requestOptions).await();
    ContentEncoder encoder = stream.contentEncoder();
    stream.trailersHandler(trailers -> checkpoint.succeed());
    stream.writeHead(head(stream, 9 * 128));
    for (int i = 0;i < 8;i++) {
      encoder.write(allocateBuffer(128));
    }
    encoder.end(allocateBuffer(128));
  }

  @Test
  public void testWriteBeforeHeadReleasesBuffers(Checkpoint checkpoint1, Checkpoint checkpoint2, Checkpoint checkpoint3) throws Exception {
    handler = request -> request.end().onComplete(v -> request.response().end());
    startServer(testAddress);
    HttpClientStream stream = client.openStream(requestOptions).await();
    ContentEncoder encoder = stream.contentEncoder();
    stream.trailersHandler(trailers -> checkpoint3.succeed());
    encoder.write(allocateBuffer(128)).onFailure(err -> checkpoint1.succeed());
    encoder.end(allocateBuffer(128)).onFailure(err -> checkpoint2.succeed());
    stream.head(head(stream, 0)).end();
  }

  @Test
  public void testWriteAfterEndReleasesBuffers(Checkpoint checkpoint1, Checkpoint checkpoint2, Checkpoint checkpoint3) throws Exception {
    handler = request -> request.end().onComplete(v -> request.response().end());
    startServer(testAddress);
    HttpClientStream stream = client.openStream(requestOptions).await();
    ContentEncoder encoder = stream.contentEncoder();
    stream.trailersHandler(trailers -> checkpoint3.succeed());
    stream.head(head(stream, 0)).end();
    encoder.write(allocateBuffer(128)).onFailure(err -> checkpoint1.succeed());
    encoder.end(allocateBuffer(128)).onFailure(err -> checkpoint2.succeed());
  }

  private void fillStream(HttpClientStream stream) {
    ContentEncoder encoder = stream.contentEncoder();
    stream.writeHead(head(stream, 1L << 40));
    while (stream.isWritable()) {
      encoder.write(allocateBuffer(1024));
    }
    for (int i = 0;i < 16;i++) {
      encoder.write(allocateBuffer(1024));
    }
    encoder.end(allocateBuffer(1024));
  }

  @Test
  public void testResetReleasesQueuedBuffers() throws Exception {
    handler = HttpServerRequest::pause;
    startServer(testAddress);
    HttpClientStream stream = client.openStream(requestOptions).await();
    fillStream(stream);
    stream.writeReset(0);
  }

  @Test
  public void testRemoteResetReleasesQueuedBuffers() throws Exception {
    Promise<Void> resetSignal = Promise.promise();
    handler = request -> {
      request.pause();
      resetSignal.future().onComplete(v -> request.response().reset(0));
    };
    startServer(testAddress);
    HttpClientStream stream = client.openStream(requestOptions).await();
    fillStream(stream);
    resetSignal.complete();
  }

  @Test
  public void testConnectionCloseReleasesQueuedBuffers(Checkpoint checkpoint) throws Exception {
    Promise<Void> closeSignal = Promise.promise();
    handler = request -> {
      request.pause();
      closeSignal.future().onComplete(v -> request.connection().close());
    };
    startServer(testAddress);
    HttpClientStream stream = client.openStream(requestOptions).await();
    stream.closeHandler(v -> checkpoint.succeed());
    fillStream(stream);
    closeSignal.complete();
  }
}
