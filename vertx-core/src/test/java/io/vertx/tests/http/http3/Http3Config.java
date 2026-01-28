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

import io.vertx.core.Vertx;
import io.vertx.core.http.*;
import io.vertx.test.http.HttpClientConfig;
import io.vertx.test.http.HttpConfig;
import io.vertx.test.http.HttpServerConfig;
import io.vertx.test.tls.Cert;
import io.vertx.test.tls.Trust;
import junit.runner.Version;

import java.time.Duration;
import java.util.EnumSet;
import java.util.List;

import static io.vertx.test.http.AbstractHttpTest.DEFAULT_HTTPS_HOST;
import static io.vertx.test.http.AbstractHttpTest.DEFAULT_HTTPS_PORT;

public class Http3Config implements HttpConfig {

  public static final Http3Config INSTANCE = new Http3Config();

  private final int port;
  private final String host;

  public Http3Config() {
    this(DEFAULT_HTTPS_PORT, DEFAULT_HTTPS_HOST);
  }

  public Http3Config(int port, String host) {
    this.port = port;
    this.host = host;
  }

  @Override
  public int port() {
    return port;
  }

  @Override
  public String host() {
    return host;
  }

  @Override
  public HttpServerConfig forServer() {
    io.vertx.core.http.HttpServerConfig options = new io.vertx.core.http.HttpServerConfig();
    options.setSupportedVersions(EnumSet.of(HttpVersion.HTTP_3));
    options.setQuicPort(port);
    options.setQuicHost(host);
    options.getSslOptions().setKeyCertOptions(Cert.SERVER_JKS.get());
//    options.setClientAddressValidation(QuicClientAddressValidation.NONE);
//    options.setKeyLogFile("/Users/julien/keylogfile.txt");
    return new HttpServerConfig() {
      @Override
      public HttpServerConfig setDecompressionSupported(boolean supported) {
        throw new UnsupportedOperationException();
      }
      @Override
      public HttpServerConfig setCompression(HttpCompressionConfig compression) {
        throw new UnsupportedOperationException();
      }
      @Override
      public HttpServerConfig setMaxFormBufferedBytes(int maxFormBufferedBytes) {
        options.setMaxFormBufferedBytes(maxFormBufferedBytes);
        return this;
      }
      @Override
      public HttpServerConfig setMaxFormAttributeSize(int maxSize) {
        options.setMaxFormAttributeSize(maxSize);
        return this;
      }
      @Override
      public HttpServerConfig setMaxFormFields(int maxFormFields) {
        options.setMaxFormFields(maxFormFields);
        return this;
      }
      @Override
      public HttpServerConfig setLogActivity(boolean logActivity) {
        throw new UnsupportedOperationException();
      }
      @Override
      public HttpServerConfig setIdleTimeout(Duration timeout) {
        options.getQuicConfig().setStreamIdleTimeout(timeout);
        return this;
      }
      @Override
      public HttpServerConfig setHandle100ContinueAutomatically(boolean b) {
        options.setHandle100ContinueAutomatically(b);
        return this;
      }
      @Override
      public HttpServer create(Vertx vertx) {
        return vertx.createHttpServer(options);
      }
    };
  }

  @Override
  public HttpClientConfig forClient() {
    io.vertx.core.http.HttpClientConfig options = new io.vertx.core.http.HttpClientConfig();
    options.setSupportedVersions(List.of(HttpVersion.HTTP_3));
    options.setDefaultHost(host);
    options.setDefaultPort(port);
    options.getSslOptions().setTrustOptions(Trust.SERVER_JKS.get());
    options.getSslOptions().setHostnameVerificationAlgorithm("");
    return new HttpClientConfig() {
      @Override
      public HttpClientConfig setConnectTimeout(Duration connectTimeout) {
        options.setConnectTimeout(connectTimeout);
        return this;
      }
      @Override
      public HttpClientConfig setDecompressionSupported(boolean decompressionSupported) {
        throw new UnsupportedOperationException();
      }
      @Override
      public HttpClientConfig setLocalAddress(String localAddress) {
        throw new UnsupportedOperationException();
      }
      @Override
      public HttpClientConfig setLogActivity(boolean logActivity) {
        return null;
      }
      @Override
      public HttpClientConfig setIdleTimeout(Duration timeout) {
        options.setIdleTimeout(timeout);
        return this;
      }
      @Override
      public HttpClientConfig setKeepAliveTimeout(Duration timeout) {
        options.getHttp3Config().setKeepAliveTimeout(timeout.toMillis() > 0 ? timeout : null);
        return this;
      }
      @Override
      public HttpClientBuilder builder(Vertx vertx) {
        return vertx.httpClientBuilder().with(options);
      }
    };
  }
}
