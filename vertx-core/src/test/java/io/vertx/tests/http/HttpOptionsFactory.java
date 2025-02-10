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
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.http.HttpVersion;
import io.vertx.core.net.JdkSSLEngineOptions;
import io.vertx.core.net.NetClientOptions;
import io.vertx.core.net.NetServerOptions;
import io.vertx.test.tls.Cert;
import io.vertx.test.tls.Trust;

import java.util.List;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class HttpOptionsFactory {

  public static HttpServerOptions createHttp2ServerOptions(int port, String host) {
    return new HttpServerOptions()
      .setPort(port)
      .setHost(host)
      .setSslEngineOptions(new JdkSSLEngineOptions())
      .setUseAlpn(true)
      .setSsl(true)
      .addEnabledCipherSuite("TLS_RSA_WITH_AES_128_CBC_SHA")
      .setKeyCertOptions(Cert.SERVER_JKS.get());
  }

  public static HttpClientOptions createHttp2ClientOptions() {
    return new HttpClientOptions()
      .setSslEngineOptions(new JdkSSLEngineOptions())
      .setUseAlpn(true)
      .setSsl(true)
      .setTrustOptions(Trust.SERVER_JKS.get())
      .setProtocolVersion(HttpVersion.HTTP_2);
  }

  public static NetServerOptions createH3NetServerOptions() {
    NetServerOptions options = new NetServerOptions();
    options
      .setUseAlpn(true)
      .setSsl(true)
      .setKeyCertOptions(Cert.SERVER_JKS.get())
      .setHttp3(true)
      .getSslOptions()
      .setApplicationLayerProtocols(List.of(Http3.supportedApplicationProtocols()));
    return options;
  }

  public static NetClientOptions createH3NetClientOptions() {
    NetClientOptions options = new NetClientOptions();
    options
      .setUseAlpn(true)
      .setSsl(true)
      .setHostnameVerificationAlgorithm("")
      .setProtocolVersion(HttpVersion.HTTP_3)
      .setTrustOptions(Trust.SERVER_JKS.get())
      .setHttp3(true)
      .getSslOptions()
      .setApplicationLayerProtocols(List.of(Http3.supportedApplicationProtocols()));

    return options;
  }

  public static NetClientOptions createH2NetClientOptions() {
    return new NetClientOptions();
  }

  public static HttpServerOptions createH3HttpServerOptions(int port, String host) {
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
      .addEnabledCipherSuite("TLS_RSA_WITH_AES_128_CBC_SHA")
      .setKeyCertOptions(Cert.SERVER_JKS.get())
      ;
  }

  public static HttpClientOptions createH3HttpClientOptions() {
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
}
