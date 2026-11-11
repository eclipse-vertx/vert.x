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

import io.netty.buffer.ByteBuf;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.http.HttpClientResponse;
import io.vertx.core.http.HttpConnection;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.RequestOptions;
import io.vertx.core.http.impl.HttpResponseHead;
import io.vertx.core.http.impl.HttpServerConnection;
import io.vertx.core.http.impl.HttpServerStream;
import io.vertx.core.internal.http.ContentDecoder;
import io.vertx.core.internal.http.ContentEncoder;
import io.vertx.core.internal.http.FlowController;
import io.vertx.core.internal.streams.WriteResult;
import io.vertx.test.core.Checkpoint;
import io.vertx.test.core.TestUtils;
import io.vertx.test.http.HttpConfigurator;
import org.junit.Assert;
import org.junit.Test;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Tests the server stream outbound content.
 */
public abstract class ServerContentEncoderTest extends ContentEncoderTestBase {

  private volatile Handler<HttpServerStream> handler;

  public ServerContentEncoderTest(HttpConfigurator config) {
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
  public void testWriteReleasesBuffers() throws Exception {
    handler = stream -> {
      ContentEncoder encoder = stream.contentEncoder();
      stream.trailersHandler(trailers -> {
        HttpResponseHead head = new HttpResponseHead(200, null, stream.connection().newHeaders());
        stream.writeHead(head);
        for (int i = 0;i < 8;i++) {
          encoder.write(allocateBuffer(128));
        }
        encoder.end(allocateBuffer(128));
      });
    };
    startServer(testAddress);
    Buffer body = client
      .request(requestOptions)
      .compose(request -> request
        .send()
        .compose(HttpClientResponse::body))
      .await();
    assertEquals(9 * 128, body.length());
  }

  @Test
  public void testWriteBeforeHeadReleasesBuffers(Checkpoint checkpoint1, Checkpoint checkpoint2) throws Exception {
    handler = stream -> {
      ContentEncoder encoder = stream.contentEncoder();
      stream.trailersHandler(trailers -> {
        encoder.write(allocateBuffer(128)).onFailure(err -> checkpoint1.succeed());
        encoder.end(allocateBuffer(128)).onFailure(err -> checkpoint2.succeed());
        HttpResponseHead head = new HttpResponseHead(200, null, stream.connection().newHeaders());
        stream.head(head).end();
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
  public void testWriteAfterEndReleasesBuffers(Checkpoint checkpoint1, Checkpoint checkpoint2) throws Exception {
    handler = stream -> {
      ContentEncoder encoder = stream.contentEncoder();
      stream.trailersHandler(trailers -> {
        HttpResponseHead head = new HttpResponseHead(200, null, stream.connection().newHeaders());
        stream.head(head).end();
        encoder.write(allocateBuffer(128)).onFailure(err -> checkpoint1.succeed());
        encoder.end(allocateBuffer(128)).onFailure(err -> checkpoint2.succeed());
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

  private void fillStream(HttpServerStream stream) {
    ContentEncoder encoder = stream.contentEncoder();
    HttpResponseHead head = new HttpResponseHead(200, null, stream.connection().newHeaders());
    stream.writeHead(head);
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
    handler = stream -> {
      fillStream(stream);
      stream.writeReset(0);
    };
    startServer(testAddress);
    try {
      client
        .request(requestOptions)
        .compose(request -> request
          .send()
          .compose(response -> {
            response.pause();
            return response.body();
          }))
        .await(2, TimeUnit.SECONDS);
    } catch (Exception expected) {
    }
  }

  @Test
  public void testRemoteResetReleasesQueuedBuffers() throws Exception {
    Promise<Void> filled = Promise.promise();
    handler = stream -> {
      fillStream(stream);
      filled.complete();
    };
    startServer(testAddress);
    HttpClientRequest request = client
      .request(requestOptions)
      .compose(req -> req
        .send()
        .map(response -> {
          response.pause();
          return req;
        }))
      .await();
    filled.future().await(20, TimeUnit.SECONDS);
    request.reset(0);
  }

  @Test
  public void testConnectionCloseReleasesQueuedBuffers() throws Exception {
    handler = stream -> fillStream(stream);
    startServer(testAddress);
    HttpConnection connection = client
      .request(requestOptions)
      .compose(request -> request
        .send()
        .map(response -> {
          response.pause();
          return request.connection();
        }))
      .await();
    connection.close().await(20, TimeUnit.SECONDS);
  }

  @Test
  public void testPipeContent() throws Exception {
    Buffer body = TestUtils.randomBuffer(4 * 1024 * 1024);
    AtomicInteger pauses = new AtomicInteger();
    handler = stream -> {
      ContentEncoder encoder = stream.contentEncoder();
      stream.contentDecoder(new ContentDecoder<Void>() {
        FlowController flowController;
        @Override
        public void init(FlowController flowController) {
          this.flowController = flowController;
        }
        @Override
        public void handle(ByteBuf chunk) {
          WriteResult result = encoder.write(trackBuffer(chunk.retain()));
          if (!result.isWritable()) {
            pauses.incrementAndGet();
            flowController.pause();
            result.drainHandler(v -> flowController.resume());
          }
        }
        @Override
        public Void handleEnd() {
          encoder.end();
          return null;
        }
        @Override
        public void handleClose(Throwable cause) {
        }
      });
      HttpResponseHead head = new HttpResponseHead(200, null, stream.connection().newHeaders());
      stream.writeHead(head);
    };
    startServer(testAddress);
    Buffer received = client
      .request(new RequestOptions(requestOptions).setMethod(HttpMethod.POST))
      .compose(request -> {
        // Subscribe before sending: the response is piped while the request is still sent
        Future<Buffer> response = request.response().compose(resp -> {
          resp.pause();
          vertx.setTimer(250, id -> resp.resume());
          return resp.body();
        });
        request.end(body);
        return response;
      })
      .await(20, TimeUnit.SECONDS);
    assertEquals(body.length(), received.length());
    assertTrue(body.equals(received));
    assertTrue(pauses.get() > 0);
  }
}
