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

import io.netty.incubator.codec.http3.Http3;
import io.vertx.core.http.*;
import org.junit.Ignore;
import org.junit.Test;

import java.util.List;

/**
 * @author <a href="mailto:zolfaghari19@gmail.com">Iman Zolfaghari</a>
 */
public class Http3Test extends HttpCommonTest {

  @Override
  protected HttpServerOptions createBaseServerOptions() {
    return HttpOptionsFactory.createHttp3ServerOptions(DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST);
  }

  @Override
  protected HttpClientOptions createBaseClientOptions() {
    return HttpOptionsFactory.createHttp3ClientOptions();
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
      .setApplicationLayerProtocols(
        List.of(Http3.supportedApplicationProtocols())
      );
  }

  @Override
  protected HttpServerOptions setMaxConcurrentStreamsSettings(HttpServerOptions options, int maxConcurrentStreams) {
    return options.setInitialHttp3Settings(new Http3Settings());
  }


  @Test
  @Ignore
  public void testClientDrainHandler() throws Exception {
    //TODO: resolve this test issue.
  }

  @Test
  @Ignore
  public void testServerDrainHandler() throws Exception {
    //TODO: resolve this test issue.
  }

  @Ignore("This test is ignored because UDP is based on a single connectionless protocol.")
  @Test
  public void testCloseMulti() {}

  @Ignore
  @Test
  public void testInitialMaxConcurrentStreamZero() throws Exception {
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
  public void testRstFloodProtection() throws Exception {
    //TODO: resolve this test issue.
  }

  @Test
  @Ignore
  public void testUnsupportedAlpnVersion() throws Exception {
    //TODO: resolve this test issue.
  }

  @Ignore
  @Test
  public void testConnectionCloseEvictsConnectionFromThePoolBeforeStreamsAreClosed() throws Exception {
    //TODO: resolve this test issue.
  }

  @Test
  @Ignore
  public void testDeliverPausedBufferWhenResume() throws Exception {
    //TODO: resolve this test issue.
  }

  @Test
  @Ignore
  public void testDeliverPausedBufferWhenResumeOnOtherThread() throws Exception {
    //TODO: resolve this test issue.
  }

  @Test
  @Ignore
  public void testPausedHttpServerRequest() throws Exception {
    //TODO: resolve this test issue.
  }

  @Test
  @Ignore
  public void testClientReadStreamInWorker() throws Exception {
    //TODO: resolve this test issue.
  }

  @Test
  @Ignore
  public void testDumpManyRequestsOnQueue() throws Exception {
    //TODO: resolve this test issue.
  }

  @Test
  @Ignore
  public void testServerLogging() throws Exception {
    //TODO: resolve this test issue.
  }

  @Test
  @Ignore
  public void testClientLogging() throws Exception {
    //TODO: resolve this test issue.
  }

  @Test
  @Ignore
  public void testClientDecompressionError() throws Exception {
    //TODO: resolve this test issue.
  }

  @Test
  @Ignore
  public void testDisableIdleTimeoutInPool() throws Exception {
    //TODO: resolve this test issue.
  }

  @Test
  @Ignore
  public void testNetSocketConnectSuccessClientInitiatesCloseImmediately() throws Exception {
    //TODO: resolve this test issue.
  }

  @Test
  @Ignore
  public void testNetSocketConnectSuccessServerInitiatesCloseOnReply() throws Exception {
    //TODO: resolve this test issue.
  }

  @Test
  @Ignore
  public void testResetClientRequestResponseInProgress() throws Exception {
    //TODO: resolve this test issue.
  }

  @Test
  @Ignore
  public void testClientRequestWithLargeBodyInSmallChunksChunked() throws Exception {
    //TODO: resolve this test issue.
  }

  @Test
  @Ignore
  public void testClientRequestWithLargeBodyInSmallChunksChunkedWithHandler() throws Exception {
    //TODO: resolve this test issue.
  }

  @Test
  @Ignore
  public void testHAProxyProtocolIdleTimeout() throws Exception {
    //TODO: Resolve this test issue. It fails on 5.x version, regardless of HTTP/3.
  }

  @Test
  @Ignore
  public void testHAProxyProtocolIdleTimeoutNotHappened() throws Exception {
    //TODO: Resolve this test issue. It fails on 5.x version, regardless of HTTP/3.
  }

  @Test
  @Ignore
  public void testHAProxyProtocolVersion1TCP4() throws Exception {
    //TODO: Resolve this test issue. It fails on 5.x version, regardless of HTTP/3.
  }

  @Test
  @Ignore
  public void testHAProxyProtocolVersion1TCP6() throws Exception {
    //TODO: Resolve this test issue. It fails on 5.x version, regardless of HTTP/3.
  }

  @Test
  @Ignore
  public void testHAProxyProtocolVersion1Unknown() throws Exception {
    //TODO: Resolve this test issue. It fails on 5.x version, regardless of HTTP/3.
  }

  @Test
  @Ignore
  public void testHAProxyProtocolVersion2TCP4() throws Exception {
    //TODO: Resolve this test issue. It fails on 5.x version, regardless of HTTP/3.
  }

  @Test
  @Ignore
  public void testHAProxyProtocolVersion2TCP6() throws Exception {
    //TODO: Resolve this test issue. It fails on 5.x version, regardless of HTTP/3.
  }

  @Test
  @Ignore
  public void testHAProxyProtocolVersion2UnixSocket() throws Exception {
    //TODO: Resolve this test issue. It fails on 5.x version, regardless of HTTP/3.
  }

  @Test
  @Ignore
  public void testHAProxyProtocolVersion2Unknown() throws Exception {
    //TODO: Resolve this test issue. It fails on 5.x version, regardless of HTTP/3.
  }

  @Test
  @Ignore
  public void testHAProxyProtocolVersion2UDP4() throws Exception {
    //TODO: Resolve this test issue. It fails on 5.x version, regardless of HTTP/3.
  }

  @Test
  @Ignore
  public void testHAProxyProtocolVersion2UDP6() throws Exception {
    //TODO: Resolve this test issue. It fails on 5.x version, regardless of HTTP/3.
  }

  @Test
  @Ignore
  public void testHAProxyProtocolVersion2UnixDataGram() throws Exception {
    //TODO: Resolve this test issue. It fails on 5.x version, regardless of HTTP/3.
  }

  @Test
  @Ignore
  public void testHAProxyProtocolEmptyHeader() throws Exception {
    //TODO: Resolve this test issue. It fails on 5.x version, regardless of HTTP/3.
  }

  @Test
  @Ignore
  public void testHAProxyProtocolIllegalHeader() throws Exception {
    //TODO: Resolve this test issue. It fails on 5.x version, regardless of HTTP/3.
  }

  @Test
  @Ignore
  public void testDnsClientSideLoadBalancingDisabled() throws Exception {
    //TODO: Resolve this test issue. It fails on 5.x version, regardless of HTTP/3.
  }

  @Test
  @Ignore
  public void testDnsClientSideLoadBalancingEnabled() throws Exception {
    //TODO: Resolve this test issue. It fails on 5.x version, regardless of HTTP/3.
  }
}
