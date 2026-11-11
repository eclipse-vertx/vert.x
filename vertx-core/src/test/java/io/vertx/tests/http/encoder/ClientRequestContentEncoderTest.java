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
import io.vertx.core.Promise;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.http.HttpClientResponse;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.RequestOptions;
import io.vertx.core.internal.http.ContentDecoder;
import io.vertx.core.internal.http.ContentEncoder;
import io.vertx.core.internal.http.FlowController;
import io.vertx.core.internal.http.HttpClientRequestInternal;
import io.vertx.core.internal.http.HttpClientResponseInternal;
import io.vertx.test.core.Checkpoint;
import io.vertx.test.core.TestUtils;
import io.vertx.test.http.HttpConfigurator;
import org.junit.Test;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Tests the client request content encoder and the client response content decoder.
 */
public abstract class ClientRequestContentEncoderTest extends ContentEncoderTestBase {

  public ClientRequestContentEncoderTest(HttpConfigurator config) {
    super(config);
  }

  private static ContentEncoder encoder(HttpClientRequest request) {
    return ((HttpClientRequestInternal) request).contentEncoder();
  }

  @Test
  public void testWriteReleasesBuffers() throws Exception {
    server.requestHandler(request -> request.body().onComplete(TestUtils.onSuccess(body -> request.response().end(String.valueOf(body.length())))));
    startServer(testAddress);
    Buffer body = client
      .request(new RequestOptions(requestOptions).setMethod(HttpMethod.POST))
      .compose(request -> {
        request.setChunked(true);
        Future<Buffer> response = request.response().compose(HttpClientResponse::body);
        ContentEncoder encoder = encoder(request);
        for (int i = 0;i < 8;i++) {
          encoder.write(allocateBuffer(128));
        }
        encoder.end(allocateBuffer(128));
        return response;
      })
      .await();
    assertEquals(String.valueOf(9 * 128), body.toString());
  }

  @Test
  public void testWriteAfterEndReleasesBuffers(Checkpoint checkpoint1, Checkpoint checkpoint2) throws Exception {
    server.requestHandler(request -> request.response().end());
    startServer(testAddress);
    client
      .request(requestOptions)
      .compose(request -> {
        Future<HttpClientResponse> response = request.response();
        ContentEncoder encoder = encoder(request);
        encoder.end();
        encoder.write(allocateBuffer(128)).onFailure(err -> checkpoint1.succeed());
        encoder.end(allocateBuffer(128)).onFailure(err -> checkpoint2.succeed());
        return response.compose(HttpClientResponse::end);
      })
      .await();
  }

  @Test
  public void testResponseContentDecoder() throws Exception {
    Buffer expected = TestUtils.randomBuffer(64 * 1024);
    server.requestHandler(request -> request.response().end(expected));
    startServer(testAddress);
    AtomicBoolean handlerCalled = new AtomicBoolean();
    Buffer received = client
      .request(requestOptions)
      .compose(request -> request
        .send()
        .compose(response -> {
          response.handler(buff -> handlerCalled.set(true));
          return ((HttpClientResponseInternal) response).contentDecoder(new BufferDecoder());
        }))
      .await();
    assertEquals(expected, received);
    assertFalse(handlerCalled.get());
  }

  @Test
  public void testPipeContent() throws Exception {
    Buffer body = TestUtils.randomBuffer(4 * 1024 * 1024);
    server.requestHandler(request -> {
      request.response().setChunked(true);
      request.handler(buff -> request.response().write(buff));
      request.endHandler(v -> request.response().end());
    });
    startServer(testAddress);
    Buffer received = client
      .request(new RequestOptions(requestOptions).setMethod(HttpMethod.POST))
      .compose(request -> {
        request.setChunked(true);
        Promise<Buffer> result = Promise.promise();
        request.response().onComplete(TestUtils.onSuccess(response -> ((HttpClientResponseInternal) response)
          .contentDecoder(new BufferDecoder())
          .onComplete(result)));
        ContentEncoder encoder = encoder(request);
        int chunk = 16 * 1024;
        for (int pos = 0;pos < body.length();pos += chunk) {
          ByteBuf buf = allocateBuffer(0);
          buf.writeBytes(body.getBytes(pos, Math.min(pos + chunk, body.length())));
          encoder.write(buf);
        }
        encoder.end();
        return result.future();
      })
      .await(20, TimeUnit.SECONDS);
    assertEquals(body.length(), received.length());
    assertTrue(body.equals(received));
  }

  /**
   * Aggregates the content, checking the chunks are released after each handle.
   */
  private class BufferDecoder implements ContentDecoder<Buffer> {
    private final Buffer content = Buffer.buffer();
    @Override
    public void init(FlowController flowController) {
    }
    @Override
    public void handle(ByteBuf chunk) {
      trackBuffer(chunk);
      byte[] bytes = new byte[chunk.readableBytes()];
      chunk.getBytes(chunk.readerIndex(), bytes);
      content.appendBytes(bytes);
    }
    @Override
    public Buffer handleEnd() {
      return content;
    }
    @Override
    public void handleClose(Throwable cause) {
    }
  }
}
