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
package io.vertx.tests.http.http3;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import io.netty.handler.codec.http3.Http3DataFrame;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.http.impl.HttpServerRequestImpl;
import io.vertx.core.http.impl.http3.Http3ServerStream;
import io.vertx.core.internal.net.QuicStreamInternal;
import io.vertx.test.core.Checkpoint;
import io.vertx.test.core.TestUtils;
import io.vertx.tests.http.HttpTest;
import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class Http3Test extends HttpTest {

  public Http3Test() {
    super(Http3Configurator.INSTANCE);
  }

  @Ignore("Introduce stream cancellation")
  @Test
  @Override
  public void testResetClientRequestAwaitingResponse(Checkpoint checkpoint) {
  }

  @Ignore("Implement compression")
  @Test
  @Override
  public void testClientDecompressionError() {
  }

  @Ignore("Requires fixe of stream cancellation")
  @Test
  @Override
  public void testFollowRedirectPropagatesTimeout(Checkpoint checkpoint) {
  }

  @Ignore()
  @Test
  @Override
  public void testListenInvalidPort() {
  }

  @Ignore()
  @Test
  @Override
  public void testListenInvalidHost() {
  }

  @Ignore("Does it make sense for HTTP/3 ?")
  @Test
  @Override
  public void testCloseMulti() {
  }

  @Ignore("Is this test valid ?")
  @Test
  @Override
  public void testResetClientRequestResponseInProgress(Checkpoint checkpoint) throws Exception {
  }

  @Ignore("Requires to implement client local address")
  @Test
  @Override
  public void testClientLocalAddress() {
  }

  @Ignore("Missing feature")
  @Test
  @Override
  public void testDisableIdleTimeoutInPool(Checkpoint checkpoint) {
  }

  @Ignore("Cannot pass because stream channel does not detect the write failure")
  @Test
  @Override
  public void testCancelPartialClientRequest(Checkpoint checkpoint) throws Exception {
  }

  @Ignore("Cannot pass because stream channel does not detect the write failure")
  @Test
  @Override
  public void testCancelPartialServerResponse(Checkpoint checkpoint1, Checkpoint checkpoint2) throws Exception {
  }

  @Ignore("Requires QUIC-level inbound message queue to buffer data across connection close")
  @Test
  @Override
  public void testPausedCompleteResponseAfterConnectionClose() throws Exception {
  }

  @Test
  public void testByteBufLeak(Checkpoint checkpoint) throws Exception {
    List<ByteBuf> buffers = Collections.synchronizedList(new ArrayList<>());
    server.requestHandler(request -> {
      request.pause();
      Http3ServerStream stream = (Http3ServerStream)((HttpServerRequestImpl)request).stream();
      QuicStreamInternal quicStream = (QuicStreamInternal)stream.quicStream();
      ChannelHandlerContext chctx = quicStream.channelHandlerContext();
      ChannelPipeline pipeline = chctx.pipeline();
      pipeline.addBefore("handler", "test", new ChannelDuplexHandler() {
        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
          if (msg instanceof Http3DataFrame) {
            buffers.add(((Http3DataFrame)msg).content());
          }
          super.channelRead(ctx, msg);
        }
      });
      checkpoint.succeed();
    });
    startServer(testAddress);
    HttpClientRequest request = client.request(requestOptions).await();
    request
      .setChunked(true)
      .writeHead()
      .await();
    checkpoint.awaitSuccess();
    request.end(Buffer.buffer(TestUtils.randomAlphaString(512))).await();
    TestUtils.assertWaitUntil(() -> buffers.size() == 1);
    ByteBuf buffer = buffers.get(0);
    Assert.assertTrue(buffer.refCnt() > 0);
    request.connection().close().await();
    TestUtils.assertWaitUntil(() -> buffer.refCnt() == 0);
  }
}
