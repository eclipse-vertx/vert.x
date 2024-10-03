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

import io.netty.channel.EventLoopGroup;
import io.netty.incubator.codec.http3.Http3;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.http.HttpVersion;
import io.vertx.core.net.JdkSSLEngineOptions;
import io.vertx.test.http.HttpTestBase;
import io.vertx.test.tls.Cert;
import io.vertx.test.tls.Trust;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * @author <a href="mailto:zolfaghari19@gmail.com">Iman Zolfaghari</a>
 */
public class Http3TestBase extends HttpTestBase {

  public static HttpServerOptions createHttp3ServerOptions(int port, String host) {
    HttpServerOptions options = new HttpServerOptions();

    options
      .setHttp3(true)
      .getSslOptions()
      .setApplicationLayerProtocols(
        List.of(Http3.supportedApplicationProtocols())
      );

    options.setAlpnVersions(List.of(
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

    return options
        .setPort(port)
        .setHost(host)
        .setSslEngineOptions(new JdkSSLEngineOptions())
        .setUseAlpn(true)
        .setSsl(true)
        .addEnabledCipherSuite("TLS_RSA_WITH_AES_128_CBC_SHA") // Non Diffie-helman -> debuggable in wireshark
        .setKeyCertOptions(Cert.SERVER_JKS.get())
      ;
  };

  public static HttpClientOptions createHttp3ClientOptions() {
    HttpClientOptions httpClientOptions = new HttpClientOptions();
    httpClientOptions
      .setHttp3(true)
      .getSslOptions()
      .setApplicationLayerProtocols(
        List.of(Http3.supportedApplicationProtocols())
      );
    return httpClientOptions
      .setSslEngineOptions(new JdkSSLEngineOptions())
      .setUseAlpn(true)
      .setSsl(true)
      .setTrustOptions(Trust.SERVER_JKS.get())
      .setProtocolVersion(HttpVersion.HTTP_3);
  }

  protected HttpServerOptions serverOptions;
  protected HttpClientOptions clientOptions;
  protected List<EventLoopGroup> eventLoopGroups = new ArrayList<>();

  @Override
  public void setUp() throws Exception {
    eventLoopGroups.clear();
    serverOptions =  createHttp3ServerOptions(DEFAULT_HTTPS_PORT, DEFAULT_HTTPS_HOST);
    clientOptions = createHttp3ClientOptions();
    super.setUp();
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
}
