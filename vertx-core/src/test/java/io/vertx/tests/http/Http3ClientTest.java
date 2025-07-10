/*
 * Copyright (c) 2011-2019 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */

package io.vertx.tests.http;

import io.netty.bootstrap.AbstractBootstrap;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelPromise;
import io.netty.channel.EventLoopGroup;
import io.netty.incubator.codec.http3.DefaultHttp3DataFrame;
import io.netty.incubator.codec.http3.DefaultHttp3Headers;
import io.netty.incubator.codec.http3.DefaultHttp3HeadersFrame;
import io.netty.incubator.codec.quic.QuicStreamChannel;
import io.netty.incubator.codec.quic.QuicStreamPriority;
import io.vertx.core.Vertx;
import io.vertx.core.http.*;
import io.vertx.core.http.impl.HttpFrameImpl;
import io.vertx.test.core.TestUtils;
import org.junit.Ignore;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;


/**
 * @author <a href="mailto:zolfaghari19@gmail.com">Iman Zolfaghari</a>
 */
public class Http3ClientTest extends HttpClientTest {

  protected Vertx getVertx() {
    return vertx;
  }

  @Override
  public void setUp() throws Exception {
    eventLoopGroups.clear();
    serverOptions = HttpOptionsFactory.createH3HttpServerOptions(DEFAULT_HTTPS_PORT, DEFAULT_HTTPS_HOST);
    clientOptions = HttpOptionsFactory.createH3HttpClientOptions();
    super.setUp();
  }

  @Override
  protected HttpVersion httpVersion() {
    return HttpVersion.HTTP_3;
  }

  @Override
  protected StreamPriorityBase generateStreamPriority() {
    return new Http3StreamPriority(new QuicStreamPriority(TestUtils.randomPositiveInt(127), TestUtils.randomBoolean()));
  }

  @Override
  protected StreamPriorityBase defaultStreamPriority() {
    return new Http3StreamPriority(new QuicStreamPriority(0, false));
  }

  @Override
  protected HttpFrame generateCustomFrame() {
    return new HttpFrameImpl(TestUtils.randomPositiveInt(50) + 64, 0, TestUtils.randomBuffer(500));
  }

  @Override
  protected void configureDomainSockets() throws Exception {
    // Nope
  }

  @Override
  protected void tearDown() throws Exception {
    super.tearDown();
    for (EventLoopGroup eventLoopGroup : eventLoopGroups) {
      eventLoopGroup.shutdownGracefully(0, 10, TimeUnit.SECONDS);
    }
  }

  @Override
  protected HttpServerOptions createBaseServerOptions() {
    return serverOptions;
  }

  @Override
  protected HttpClientOptions createBaseClientOptions() {
    return clientOptions;
  }

  @Override
  protected void resetResponse(HttpServerResponse response, int code) {
    response.reset();
  }

  @Override
  protected void assertStreamReset(int expectedCode, StreamResetException reset) {
    assertEquals(0, reset.getCode());
  }

  @Override
  protected void manageMaxQueueRequestsCount(Long max) {
    if (max != null) {
      serverOptions.getSslOptions().setHttp3InitialMaxStreamsBidirectional(max);
      clientOptions.getSslOptions().setHttp3InitialMaxStreamsBidirectional(max);
    }
    Http3Settings serverSettings = new Http3Settings();
    serverOptions.setInitialHttp3Settings(serverSettings);
  }

  @Override
  protected AbstractBootstrap createServerForGet() {
    return new H3ServerBuilder(this)
      .headerHandler(headersHolder -> {
        vertx.runOnContext(v -> {
          ChannelPromise promise = headersHolder.streamChannel().newPromise();
          promise.addListener(QuicStreamChannel.SHUTDOWN_OUTPUT);
          headersHolder.streamChannel().write(new DefaultHttp3HeadersFrame(new DefaultHttp3Headers().status("200")), promise);
          headersHolder.streamChannel().flush();
        });
      })
      .dataHandler(ignored -> fail("Unexpected data received: this handler should never have been invoked during the test."))
      .goAwayHandler(goAwayFrame -> {
        vertx.runOnContext(v -> {
          testComplete();
        });
      })
      .build();
  }

  @Override
  protected AbstractBootstrap createServerForInvalidServerResponse() {
    return new H3ServerBuilder(this)
      .headerHandler(headersHolder -> {
        ChannelPromise promise = headersHolder.streamChannel().newPromise();
        promise.addListener(QuicStreamChannel.SHUTDOWN_OUTPUT);
        headersHolder.streamChannel().write(new DefaultHttp3HeadersFrame(new DefaultHttp3Headers().status("xyz")), promise);
        headersHolder.streamChannel().flush();
      })
      .dataHandler(ignored -> fail("Unexpected data received: this handler should never have been invoked during the test."))
      .build();
  }

  @Override
  protected AbstractBootstrap createServerForClientResetServerStream(boolean endServer) {
    return new H3ServerBuilder(this)
      .headerHandler(headersHolder -> {
        ChannelPromise promise = headersHolder.streamChannel().newPromise();
        headersHolder.streamChannel().write(new DefaultHttp3HeadersFrame(new DefaultHttp3Headers().status("200")), promise);
        headersHolder.streamChannel().flush();
      })
      .dataHandler(dataHolder -> {
        ChannelPromise promise = dataHolder.streamChannel().newPromise();
        if (endServer) {
          promise.addListener(QuicStreamChannel.SHUTDOWN_OUTPUT);
        }

        dataHolder.streamChannel().write(new DefaultHttp3DataFrame(Unpooled.copiedBuffer("pong", 0, 4, StandardCharsets.UTF_8)), promise);
        dataHolder.streamChannel().flush();
      })
      .streamResetHandler(ctx -> {
        //              assertEquals(10L, exception.error());
        vertx.runOnContext(v -> {
          complete();
        });
      })
      .build();
  }

  @Override
  protected AbstractBootstrap createServerForConnectionDecodeError() {
    return new H3ServerBuilder(this)
      .headerHandler(headersHolder -> {
        vertx.runOnContext(v -> {
          ChannelPromise promise1 = headersHolder.streamChannel().newPromise();
          ChannelPromise promise2 = headersHolder.streamChannel().newPromise();
          headersHolder.streamChannel().write(new DefaultHttp3HeadersFrame(new DefaultHttp3Headers().status("200")), promise1);
          headersHolder.streamChannel().shutdownOutput(21, promise2);
          headersHolder.streamChannel().flush();
        });
      })
      .dataHandler(ignored -> fail())
      .build();
  }

  @Test
  @Override
  @Ignore("It is not possible to create a corrupted frame in HTTP/3 as easily as in HTTP/2")
  public void testStreamError() throws Exception {
    super.testStreamError();
  }

  @Override
  @Test
  @Ignore("Ignoring test: not applicable in HTTP/3 (QUIC-based protocol with no upgrade from HTTP/1.1 or HTTP/2)")
  public void testClearTextUpgrade() throws Exception {
    super.testClearTextUpgrade();
  }

  @Override
  @Test
  @Ignore("Ignoring test: not applicable in HTTP/3 (QUIC-based protocol with no upgrade from HTTP/1.1 or HTTP/2)")
  public void testClearTextUpgradeWithPreflightRequest() throws Exception {
    super.testClearTextUpgradeWithPreflightRequest();
  }

  @Override
  @Test
  @Ignore("Ignoring test: not applicable in HTTP/3 (QUIC-based protocol with no upgrade from HTTP/1.1 or HTTP/2)")
  public void testClearTextWithPriorKnowledge() throws Exception {
    super.testClearTextWithPriorKnowledge();
  }

  @Override
  @Test
  @Ignore("Ignoring test: not applicable in HTTP/3 (QUIC-based protocol with no upgrade from HTTP/1.1 or HTTP/2)")
  public void testRejectClearTextUpgrade() throws Exception {
    super.testRejectClearTextUpgrade();
  }

  @Override
  @Test
  @Ignore("Ignoring test: not applicable in HTTP/3 (QUIC-based protocol with no upgrade from HTTP/1.1 or HTTP/2)")
  public void testRejectClearTextDirect() throws Exception {
    super.testRejectClearTextDirect();
  }

  @Override
  @Test
  @Ignore("Ignoring test: not applicable in HTTP/3 (QUIC-based protocol with no upgrade from HTTP/1.1 or HTTP/2)")
  public void testIdleTimeoutClearTextUpgrade() throws Exception {
    super.testIdleTimeoutClearTextUpgrade();
  }

  @Override
  @Test
  @Ignore("Ignoring test: not applicable in HTTP/3 (QUIC-based protocol with no upgrade from HTTP/1.1 or HTTP/2)")
  public void testIdleTimeoutClearTextDirect() throws Exception {
    super.testIdleTimeoutClearTextDirect();
  }

  @Override
  @Test
  @Ignore("Ignoring test: not applicable in HTTP/3 (QUIC-based protocol with no upgrade from HTTP/1.1 or HTTP/2)")
  public void testDisableIdleTimeoutClearTextUpgrade() throws Exception {
    super.testDisableIdleTimeoutClearTextUpgrade();
  }

  @Override
  @Test
  @Ignore("Test ignored: HTTP/3 handles flow control at the QUIC layer; no WINDOW_UPDATE equivalent in HTTP/3")
  public void testConnectionWindowSize() throws Exception {
    super.testConnectionWindowSize();
  }

  @Override
  @Test
  @Ignore("Test ignored: HTTP/3 handles flow control at the QUIC layer; no WINDOW_UPDATE equivalent in HTTP/3")
  public void testUpdateConnectionWindowSize() throws Exception {
    super.testUpdateConnectionWindowSize();
  }

  @Test
  @Override
  @Ignore("Ignored because stream priority is not exchanged in HTTP/3 as it is in HTTP/2.")
  public void testStreamPriorityChange() throws Exception {
    super.testStreamPriorityChange();
  }

  @Test
  @Override
  @Ignore("Ignored because stream priority is not exchanged in HTTP/3 as it is in HTTP/2.")
  public void testStreamPriority() throws Exception {
    super.testStreamPriority();
  }

  @Test
  @Override
  @Ignore("Push message will be implemented in the next PR")
  public void testResetActivePushPromise() throws Exception {
    super.testResetActivePushPromise();
  }

  @Test
  @Override
  @Ignore("Push message will be implemented in the next PR")
  public void testPushPromise() throws Exception {
    super.testPushPromise();
  }

  @Test
  @Override
  @Ignore("Push message will be implemented in the next PR")
  public void testResetPushPromiseNoHandler() throws Exception {
    super.testResetPushPromiseNoHandler();
  }

  @Test
  @Override
  @Ignore("Push message will be implemented in the next PR")
  public void testResetPendingPushPromise() throws Exception {
    super.testResetPendingPushPromise();
  }

  @Test
  @Override
  @Ignore("Cannot fallback from HTTP/3 to HTTP/1 or HTTP/2 due to protocol differences: UDP vs TCP")
  public void testFallbackOnHttp1() throws Exception {
    super.testFallbackOnHttp1();
  }

  @Test
  @Override
  @Ignore("No PING handling needed in HTTP/3 — QUIC manages liveness.")
  public void testSendPing() throws Exception {
    super.testSendPing();
  }

  @Test
  @Override
  @Ignore("No PING handling needed in HTTP/3 — QUIC manages liveness.")
  public void testReceivePing() throws Exception {
    super.testReceivePing();
  }

  @Test
  @Override
  @Ignore("Settings are not exchanged at the connection level in HTTP/3.")
  public void testClientSettings() throws Exception {
    super.testClientSettings();
  }

  @Test
  @Override
  @Ignore("Settings are not exchanged at the connection level in HTTP/3.")
  public void testServerSettings() throws Exception {
    super.testServerSettings();
  }

  @Test
  @Override
  @Ignore
  public void testResponseCompressionEnabled() throws Exception {
    //TODO: correct me
    super.testResponseCompressionEnabled();
  }

  @Test
  @Override
  @Ignore
  public void testClientRequestWriteability() throws Exception {
    //TODO: correct me
    super.testClientRequestWriteability();
  }

  @Test
  @Override
  @Ignore
  public void testReduceMaxConcurrentStreams() throws Exception {
    //TODO: correct me
    super.testReduceMaxConcurrentStreams();
  }

  @Test
  @Override
  @Ignore
  public void testMaxConcurrencyMultipleConnections() throws Exception {
    //TODO: correct me
    super.testMaxConcurrencyMultipleConnections();
  }

  @Test
  @Override
  @Ignore
  public void testClientResetServerStream2() throws Exception {
    //TODO: resolve this test issue. This test had no problem on old http3 structure.
    super.testClientResetServerStream2();
  }

  @Test
  @Override
  @Ignore
  public void testClientResponsePauseResume() throws Exception {
    //TODO: resolve this test issue. This test had no problem on old http3 structure.
    super.testClientResponsePauseResume();
  }

  @Test
  @Override
  @Ignore
  public void testServerResetClientStreamDuringRequest() throws Exception {
    //TODO: resolve this test issue. This test had no problem on old http3 structure.
    super.testServerResetClientStreamDuringRequest();
  }

  @Test
  @Override
  @Ignore
  public void testServerResetClientStreamDuringResponse() throws Exception {
    //TODO: resolve this test issue. This test had no problem on old http3 structure.
    super.testServerResetClientStreamDuringResponse();
  }

}
