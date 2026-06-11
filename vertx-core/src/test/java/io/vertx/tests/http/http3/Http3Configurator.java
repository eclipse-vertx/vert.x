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
import io.vertx.core.net.ClientSSLOptions;
import io.vertx.core.net.LogConfig;
import io.vertx.core.net.ProxyOptions;
import io.vertx.core.net.ServerSSLOptions;
import io.vertx.test.http.HttpClientConfigurator;
import io.vertx.test.http.HttpConfigurator;
import io.vertx.test.http.HttpServerConfigurator;
import io.vertx.test.tls.Cert;
import io.vertx.test.tls.Trust;

import java.time.Duration;
import java.util.Set;
import java.util.function.Consumer;

import static io.vertx.test.http.AbstractHttpTest.DEFAULT_HTTPS_HOST;
import static io.vertx.test.http.AbstractHttpTest.DEFAULT_HTTPS_PORT;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class Http3Configurator implements HttpConfigurator {

  public static final Http3Configurator INSTANCE = new Http3Configurator();

  private final int port;
  private final String host;

  public Http3Configurator() {
    this(DEFAULT_HTTPS_PORT, DEFAULT_HTTPS_HOST);
  }

  public Http3Configurator(int port, String host) {
    this.port = port;
    this.host = host;
  }

  @Override
  public HttpVersion version() {
    return HttpVersion.HTTP_3;
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
  public HttpServerConfigurator forServer() {
    HttpServerConfig config = new HttpServerConfig();
    config.setVersions(HttpVersion.HTTP_3);
    config.setQuicPort(port);
    config.setQuicHost(host);
    ServerSSLOptions sslOptions = new ServerSSLOptions().setKeyCertOptions(Cert.SERVER_JKS.get());
//    options.setClientAddressValidation(QuicClientAddressValidation.NONE);
//    options.setKeyLogFile("/Users/julien/keylogfile.txt");
    return new HttpServerConfigurator() {
      @Override
      public HttpServerConfigurator setSsl(boolean ssl) {
        assertTrue(ssl);
        return this;
      }
      @Override
      public HttpServerConfigurator setUseProxyProtocol(boolean useProxyProtocol) {
        assertFalse(useProxyProtocol);
        return this;
      }
      @Override
      public HttpServerConfigurator setDecompressionSupported(boolean supported) {
        throw new UnsupportedOperationException();
      }
      @Override
      public HttpServerConfigurator setCompression(CompressionConfig compression) {
        throw new UnsupportedOperationException();
      }
      private FormDecoderConfig formDecoderConfig() {
        FormDecoderConfig cfg = config.getFormDecoderConfig();
        if (cfg == null) {
          config.setFormDecoderConfig(cfg = new FormDecoderConfig());
        }
        return cfg;
      }
      @Override
      public HttpServerConfigurator setMaxFormBufferedBytes(int maxFormBufferedBytes) {
        formDecoderConfig()
          .setMaxBufferedBytes(maxFormBufferedBytes);
        return this;
      }
      @Override
      public HttpServerConfigurator setMaxFormAttributeSize(int maxSize) {
        formDecoderConfig()
          .setMaxAttributeSize(maxSize);
        return this;
      }
      @Override
      public HttpServerConfigurator setMaxFormFields(int maxFormFields) {
        formDecoderConfig()
          .setMaxFields(maxFormFields);
        return this;
      }
      @Override
      public HttpServerConfigurator setLogActivity(boolean logActivity) {
        config.getQuicConfig().setLogConfig(new LogConfig().setEnabled(logActivity));
        return this;
      }
      @Override
      public HttpServerConfigurator setIdleTimeout(Duration timeout) {
        config.getQuicConfig().setIdleTimeout(timeout);
        return this;
      }
      @Override
      public HttpServerConfigurator setHandle100ContinueAutomatically(boolean b) {
        config.setHandle100ContinueAutomatically(b);
        return this;
      }
      @Override
      public HttpServerConfig config() {
        return config;
      }
      @Override
      public ServerSSLOptions sslOptions() {
        return sslOptions;
      }
      @Override
      public HttpServerConfigurator configureSsl(Consumer<ServerSSLOptions> configurator) {
        configurator.accept(sslOptions);
        return this;
      }
      @Override
      public HttpServerBuilder builder(Vertx vertx) {
        return vertx.httpServerBuilder().with(config).with(sslOptions);
      }
    };
  }

  @Override
  public HttpClientConfigurator forClient() {
    Http3ClientConfig http3Config = new Http3ClientConfig();
    io.vertx.core.http.HttpClientConfig config = new io.vertx.core.http.HttpClientConfig();
    config.setVersions(HttpVersion.HTTP_3);
    config.setDefaultHost(host);
    config.setDefaultPort(port);
    config.setHttp3Config(http3Config);
    ClientSSLOptions sslOptions = new ClientSSLOptions().setTrustOptions(Trust.SERVER_JKS.get());
    ObservabilityConfig observabilityConfig = new ObservabilityConfig();
    config.setObservabilityConfig(observabilityConfig);
    return new HttpClientConfigurator() {
      @Override
      public HttpClientConfigurator setSsl(boolean ssl) {
        assertTrue(ssl);
        return this;
      }
      @Override
      public HttpClientConfigurator setVerifyHost(boolean verify) {
        config.setVerifyHost(verify);
        return this;
      }
      @Override
      public HttpClientConfigurator setForceSni(boolean forceSni) {
        if (forceSni) {
          throw new UnsupportedOperationException();
        }
        return this;
      }
      @Override
      public HttpClientConfigurator setProxyOptions(ProxyOptions proxyOptions) {
        throw new UnsupportedOperationException();
      }
      @Override
      public HttpClientConfigurator setConnectTimeout(Duration connectTimeout) {
        config.setConnectTimeout(connectTimeout);
        return this;
      }
      @Override
      public HttpClientConfigurator setDecompressionSupported(boolean decompressionSupported) {
        throw new UnsupportedOperationException();
      }
      @Override
      public HttpClientConfigurator setLocalAddress(String localAddress) {
        throw new UnsupportedOperationException();
      }
      @Override
      public HttpClientConfigurator setLogActivity(boolean logActivity) {
        config.getQuicConfig().setLogConfig(new LogConfig().setEnabled(logActivity));
        return this;
      }
      @Override
      public HttpClientConfigurator setMetricsName(String name) {
        observabilityConfig.setMetricsName(name);
        return this;
      }
      @Override
      public HttpClientConfigurator configureSsl(Consumer<ClientSSLOptions> configurator) {
        configurator.accept(sslOptions);
        return this;
      }
      @Override
      public HttpClientConfigurator setSameOriginRedirectBlockedHeaders(Set<String> headers) {
        ClientRedirectConfig redirectConfig = config.getRedirectConfig();
        if (redirectConfig == null) {
          config.setRedirectConfig(redirectConfig = new ClientRedirectConfig());
        }
        redirectConfig.setSameOriginBlockedHeaders(headers);
        return this;
      }
      @Override
      public HttpClientConfigurator setCrossOriginRedirectBlockedHeaders(Set<String> headers) {
        ClientRedirectConfig redirectConfig = config.getRedirectConfig();
        if (redirectConfig == null) {
          config.setRedirectConfig(redirectConfig = new ClientRedirectConfig());
        }
        redirectConfig.setCrossOriginBlockedHeaders(headers);
        return this;
      }
      @Override
      public HttpClientConfigurator setIdleTimeout(Duration timeout) {
        config.setIdleTimeout(timeout);
        return this;
      }
      @Override
      public HttpClientConfigurator setKeepAliveTimeout(Duration timeout) {
        http3Config.setKeepAliveTimeout(timeout.toMillis() > 0 ? timeout : null);
        return this;
      }
      @Override
      public HttpClientConfigurator setMaxRedirectBufferSize(int maxRedirectBufferSize) {
        ClientRedirectConfig redirectConfig = config.getRedirectConfig();
        if (redirectConfig == null) {
          redirectConfig = new ClientRedirectConfig();
          config.setRedirectConfig(redirectConfig);
        }
        redirectConfig.setMaxBufferedSize(maxRedirectBufferSize);
        return this;
      }
      @Override
      public HttpClientBuilder builder(Vertx vertx) {
        return vertx.httpClientBuilder().with(config).with(sslOptions);
      }
    };
  }
}
