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
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.impl.*;
import io.vertx.core.internal.http.ContentDecoder;
import io.vertx.core.internal.http.FlowController;
import io.vertx.core.net.HostAndPort;
import io.vertx.test.core.Checkpoint;
import io.vertx.test.http.HttpConfigurator;
import io.vertx.test.http.SimpleHttpTest2;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

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
    stream.writeHead(head, Buffer.buffer(), true);
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
    stream.contentDecoder(new ContentDecoder() {
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
      public void handleEnd() {
        events.add("handleEnd");
      }
      @Override
      public void handleClose(boolean abrupt) {
        events.add(abrupt ? "handleClose(abrupt)" : "handleClose");
        vertx.setTimer(100, id -> {
          assertEquals(List.of("init", "handle", "handleClose(abrupt)"), events);
          checkpoint.succeed();
        });
      }
    });
    HttpClientConnection connection = stream.connection();
    HttpRequestHead head = new HttpRequestHead(
      "https",
      HttpMethod.GET,
      "/",
      connection.newHttpRequestHeaders(),
      HostAndPort.authority("localhost", testAddress.port()),
      null);
    stream.writeHead(head, Buffer.buffer(), true);
  }
}
