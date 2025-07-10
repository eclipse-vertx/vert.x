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

import io.netty.incubator.codec.quic.QuicStreamPriority;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.*;
import io.vertx.core.net.NetClientOptions;
import io.vertx.core.net.NetServerOptions;
import io.vertx.core.net.SocketAddress;
import io.vertx.core.net.impl.Http3Utils;
import io.vertx.test.core.TestUtils;
import io.vertx.test.proxy.HAProxy;
import org.junit.Ignore;
import org.junit.Test;

import java.util.List;

/**
 * @author <a href="mailto:zolfaghari19@gmail.com">Iman Zolfaghari</a>
 */
public class Http3Test extends HttpCommonTest {

  @Override
  protected HttpServerOptions createBaseServerOptions() {
    return HttpOptionsFactory.createH3HttpServerOptions(DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST);
  }

  @Override
  protected NetClientOptions createNetClientOptions() {
    return HttpOptionsFactory.createH3NetClientOptions();
  }

  @Override
  protected NetServerOptions createNetServerOptions() {
    return HttpOptionsFactory.createH3NetServerOptions();
  }

  @Override
  protected HAProxy createHAProxy(SocketAddress remoteAddress, Buffer header) {
    HAProxy haProxy = new HAProxy(remoteAddress, header);
    haProxy.http3(true);
    return haProxy;
  }

  @Override
  protected HttpClientOptions createBaseClientOptions() {
    return HttpOptionsFactory.createH3HttpClientOptions();
  }

  @Override
  protected HttpVersion clientAlpnProtocolVersion() {
    return HttpVersion.HTTP_3;
  }

  @Override
  protected HttpVersion serverAlpnProtocolVersion() {
    return HttpVersion.HTTP_3;
  }

  @Override
  protected void addMoreOptions(HttpServerOptions opts) {
    opts.setHttp3(true);

    opts.setAlpnVersions(List.of(
      HttpVersion.HTTP_3,
      HttpVersion.HTTP_3_27,
      HttpVersion.HTTP_3_29,
      HttpVersion.HTTP_3_30,
      HttpVersion.HTTP_3_31,
      HttpVersion.HTTP_3_32,
      HttpVersion.HTTP_2,
      HttpVersion.HTTP_1_1,
      HttpVersion.HTTP_1_0
    ));

    opts
      .getSslOptions()
      .setApplicationLayerProtocols(Http3Utils.supportedApplicationProtocols());
  }

  @Override
  protected HttpServerOptions setMaxConcurrentStreamsSettings(HttpServerOptions options, int maxConcurrentStreams) {
    return options.setInitialHttp3Settings(new Http3Settings());
  }

  @Override
  protected void assertEqualsStreamPriority(StreamPriorityBase expectedStreamPriority,
                                            StreamPriorityBase actualStreamPriority) {
    assertEquals(expectedStreamPriority.urgency(), actualStreamPriority.urgency());
    assertEquals(expectedStreamPriority.isIncremental(), actualStreamPriority.isIncremental());
  }

  @Override
  protected StreamPriorityBase generateStreamPriority() {
    return new Http3StreamPriority(new QuicStreamPriority(TestUtils.randomPositiveInt(127), TestUtils.randomBoolean()));
  }

  @Override
  protected StreamPriorityBase defaultStreamPriority() {
    return new Http3StreamPriority(new QuicStreamPriority(0, false));
  }

  @Test
  @Override
  @Ignore("Stream priority exchange for HTTP/3 is not fully supported by Netty.")
  public void testStreamPriority() throws Exception {
    super.testStreamPriority();
  }

  @Test
  @Override
  @Ignore("Stream priority exchange for HTTP/3 is not fully supported by Netty.")
  public void testStreamPriorityChange() throws Exception {
    super.testStreamPriorityChange();
  }

  @Test
  @Override
  @Ignore("Stream priority exchange for HTTP/3 is not fully supported by Netty.")
  public void testServerStreamPriorityNoChange() throws Exception {
    super.testServerStreamPriorityNoChange();
  }

  @Test
  @Override
  @Ignore("Stream priority exchange for HTTP/3 is not fully supported by Netty.")
  public void testClientStreamPriorityNoChange() throws Exception {
    super.testClientStreamPriorityNoChange();
  }

  @Test
  @Override
  @Ignore("Stream priority exchange for HTTP/3 is not fully supported by Netty.")
  public void testStreamPriorityInheritance() throws Exception {
    super.testStreamPriorityInheritance();
  }

  @Test
  @Override
  @Ignore("Stream priority exchange for HTTP/3 is not fully supported by Netty.")
  public void testDefaultPriority() throws Exception {
    super.testDefaultPriority();
  }

  @Test
  @Override
  @Ignore("Stream priority exchange for HTTP/3 is not fully supported by Netty.")
  public void testStreamPriorityPushPromise() throws Exception {
    super.testStreamPriorityPushPromise();
  }

  @Test
  @Override
  @Ignore("Stream priority exchange for HTTP/3 is not fully supported by Netty.")
  public void testStreamPriorityInheritancePushPromise() throws Exception {
    super.testStreamPriorityInheritancePushPromise();
  }

  @Ignore("This test assumes an HTTP/1.1 connection, which isn't compatible with HTTP/3")
  @Test
  public void testListenSocketAddress() throws Exception {
    super.testListenSocketAddress();
  }

  @Test
  @Ignore("HAProxy protocol is TCP-based and not applicable to Netty's HTTP/3 (UDP/QUIC) transport.")
  public void testHAProxyProtocolIdleTimeout() throws Exception {
    super.testHAProxyProtocolIdleTimeout();
  }

  @Test
  @Ignore("HAProxy protocol is TCP-based and not applicable to Netty's HTTP/3 (UDP/QUIC) transport.")
  public void testHAProxyProtocolIdleTimeoutNotHappened() throws Exception {
    super.testHAProxyProtocolIdleTimeoutNotHappened();
  }

  @Test
  @Ignore("HAProxy protocol is TCP-based and not applicable to Netty's HTTP/3 (UDP/QUIC) transport.")
  public void testHAProxyProtocolVersion1TCP4() throws Exception {
    super.testHAProxyProtocolVersion1TCP4();
  }

  @Test
  @Ignore("HAProxy protocol is TCP-based and not applicable to Netty's HTTP/3 (UDP/QUIC) transport.")
  public void testHAProxyProtocolVersion1TCP6() throws Exception {
    super.testHAProxyProtocolVersion1TCP6();
  }

  @Test
  @Ignore("HAProxy protocol is TCP-based and not applicable to Netty's HTTP/3 (UDP/QUIC) transport.")
  public void testHAProxyProtocolVersion1Unknown() throws Exception {
    super.testHAProxyProtocolVersion1Unknown();
  }

  @Test
  @Ignore("HAProxy protocol is TCP-based and not applicable to Netty's HTTP/3 (UDP/QUIC) transport.")
  public void testHAProxyProtocolVersion2TCP4() throws Exception {
    super.testHAProxyProtocolVersion2TCP4();
  }

  @Test
  @Ignore("HAProxy protocol is TCP-based and not applicable to Netty's HTTP/3 (UDP/QUIC) transport.")
  public void testHAProxyProtocolVersion2TCP6() throws Exception {
    super.testHAProxyProtocolVersion2TCP6();
  }

  @Test
  @Ignore("HAProxy protocol is TCP-based and not applicable to Netty's HTTP/3 (UDP/QUIC) transport.")
  public void testHAProxyProtocolVersion2UnixSocket() throws Exception {
    super.testHAProxyProtocolVersion2UnixSocket();
  }

  @Test
  @Ignore("HAProxy protocol is TCP-based and not applicable to Netty's HTTP/3 (UDP/QUIC) transport.")
  public void testHAProxyProtocolVersion2Unknown() throws Exception {
    super.testHAProxyProtocolVersion2Unknown();
  }

  @Test
  @Ignore("HAProxy protocol is TCP-based and not applicable to Netty's HTTP/3 (UDP/QUIC) transport.")
  public void testHAProxyProtocolVersion2UDP4() throws Exception {
    super.testHAProxyProtocolVersion2UDP4();
  }

  @Test
  @Ignore("HAProxy protocol is TCP-based and not applicable to Netty's HTTP/3 (UDP/QUIC) transport.")
  public void testHAProxyProtocolVersion2UDP6() throws Exception {
    super.testHAProxyProtocolVersion2UDP6();
  }

  @Test
  @Ignore("HAProxy protocol is TCP-based and not applicable to Netty's HTTP/3 (UDP/QUIC) transport.")
  public void testHAProxyProtocolVersion2UnixDataGram() throws Exception {
    super.testHAProxyProtocolVersion2UnixDataGram();
  }

  @Test
  @Ignore("HAProxy protocol is TCP-based and not applicable to Netty's HTTP/3 (UDP/QUIC) transport.")
  public void testHAProxyProtocolEmptyHeader() throws Exception {
    super.testHAProxyProtocolEmptyHeader();
  }

  @Test
  @Ignore("HAProxy protocol is TCP-based and not applicable to Netty's HTTP/3 (UDP/QUIC) transport.")
  public void testHAProxyProtocolIllegalHeader() throws Exception {
    super.testHAProxyProtocolIllegalHeader();
  }

  @Test
  @Override
  @Ignore("Ignored because \"clear text direct\" is not applicable under HTTP/3.")
  public void testClearTextDirect() throws Exception {
    super.testClearTextDirect();
  }

  @Ignore
  @Test
  public void testInitialMaxConcurrentStreamZero() throws Exception {
    //TODO: resolve this test issue.
    waitFor(2);
    server.close();
    server =
      vertx.createHttpServer(createBaseServerOptions().setInitialHttp3Settings(new Http3Settings().setMaxFieldSectionSize(50000)));
    server.requestHandler(req -> {
      req.response().end();
    });
    server.connectionHandler(conn -> {
      vertx.setTimer(500, id -> {
        conn.updateHttpSettings(new Http3Settings().setMaxFieldSectionSize(10));
      });
    });
    startServer(testAddress);
    client.close();
    client = vertx.httpClientBuilder()
      .with(createBaseClientOptions())
      .withConnectHandler(conn -> {
        assertEquals(50000, ((Http3Settings) conn.remoteHttpSettings()).getMaxFieldSectionSize());
        conn.remoteHttpSettingsHandler(settings -> {
          assertEquals(10, ((Http3Settings) conn.remoteHttpSettings()).getMaxFieldSectionSize());
          complete();
        });
      })
      .build();
    client.request(new RequestOptions(requestOptions).setTimeout(10000))
      .compose(HttpClientRequest::send)
      .onComplete(onSuccess(resp -> complete()));
    await();
  }

  @Test
  public void testMaxHaderListSize() throws Exception {
    server.close();
    server =
      vertx.createHttpServer(createBaseServerOptions().setInitialHttp3Settings(new Http3Settings()));
    server.requestHandler(req -> {
      req.response().end();
    });
    startServer(testAddress);
    client.request(new RequestOptions(requestOptions).setTimeout(10000))
      .compose(HttpClientRequest::send)
      .onComplete(onSuccess(resp -> {
        assertEquals(Http3Settings.DEFAULT_MAX_FIELD_SECTION_SIZE,
          ((Http3Settings) (resp.request().connection().remoteHttpSettings())).getMaxFieldSectionSize());
        testComplete();
      }));
    await();
  }

  @Test
  @Ignore
  @Override
  public void testEventHandlersNotHoldingLockOnClose() throws Exception {
    //TODO: resolve this test issue.
    super.testEventHandlersNotHoldingLockOnClose();
  }

  @Test
  @Ignore
  public void testRstFloodProtection() throws Exception {
    //TODO: resolve this test issue.
    super.testRstFloodProtection();
  }

  @Test
  @Ignore
  public void testUnsupportedAlpnVersion() throws Exception {
    //TODO: resolve this test issue.
    super.testUnsupportedAlpnVersion();
  }

  @Test
  @Ignore
  public void testDumpManyRequestsOnQueue() throws Exception {
    //TODO: resolve this test issue.
    super.testDumpManyRequestsOnQueue();
  }

  @Test
  @Ignore
  public void testServerLogging() throws Exception {
    //TODO: resolve this test issue.
    super.testServerLogging();
  }

  @Test
  @Ignore
  public void testClientLogging() throws Exception {
    //TODO: resolve this test issue.
    super.testClientLogging();
  }

  @Test
  @Ignore
  public void testClientDecompressionError() throws Exception {
    //TODO: resolve this test issue.
    super.testClientDecompressionError();
  }

  @Test
  @Ignore
  public void testDisableIdleTimeoutInPool() throws Exception {
    //TODO: resolve this test issue.
    super.testDisableIdleTimeoutInPool();
  }

  @Test
  @Ignore
  public void testResetClientRequestResponseInProgress() throws Exception {
    //TODO: resolve this test issue.
    super.testResetClientRequestResponseInProgress();
  }

  @Test
  @Ignore
  public void testClientDrainHandler() throws Exception {
    //TODO: resolve this test issue.
    super.testClientDrainHandler();
  }

  @Test
  @Ignore
  public void testServerDrainHandler() throws Exception {
    //TODO: resolve this test issue.
    super.testServerDrainHandler();
  }

  @Test
  @Ignore
  @Override
  public void testClientDoesNotSupportAlpn() throws Exception {
    //TODO: resolve this test issue.
    super.testClientDoesNotSupportAlpn();
  }

  @Test
  @Ignore
  @Override
  public void testServerDoesNotSupportAlpn() throws Exception {
    //TODO: resolve this test issue.
    super.testServerDoesNotSupportAlpn();
  }

  @Test
  @Ignore
  @Override
  public void testClearTextUpgradeWithBody() throws Exception {
    //TODO: resolve this test issue.
    super.testClearTextUpgradeWithBody();
  }

  @Test
  @Ignore
  @Override
  public void testClearTextUpgradeWithBodyTooLongFrameResponse() throws Exception {
    //TODO: resolve this test issue.
    super.testClearTextUpgradeWithBodyTooLongFrameResponse();
  }

  @Test
  @Ignore
  @Override
  public void testSslHandshakeTimeout() throws Exception {
    //TODO: resolve this test issue.
    super.testSslHandshakeTimeout();
  }

  @Test
  @Ignore
  @Override
  public void testNonUpgradedH2CConnectionIsEvictedFromThePool() throws Exception {
    //TODO: resolve this test issue.
    super.testNonUpgradedH2CConnectionIsEvictedFromThePool();
  }

  @Test
  @Ignore
  @Override
  public void testClientKeepAliveTimeoutNoStreams() throws Exception {
    //TODO: resolve this test issue.
    super.testClientKeepAliveTimeoutNoStreams();
  }

  @Test
  @Override
  @Ignore
  public void testClientShutdown() throws Exception {
    //TODO: resolve this test issue. This test had no problem on old http3 structure.
    super.testClientShutdown();
  }

  @Test
  @Override
  @Ignore
  public void testServerShutdown() throws Exception {
    //TODO: resolve this test issue. This test had no problem on old http3 structure.
    super.testServerShutdown();
  }
}
