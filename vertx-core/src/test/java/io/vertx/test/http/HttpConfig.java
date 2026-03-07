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
package io.vertx.test.http;

import io.vertx.core.Vertx;
import io.vertx.core.http.*;
import io.vertx.core.net.ClientSSLOptions;
import io.vertx.core.net.ProxyOptions;
import io.vertx.core.net.ServerSSLOptions;
import io.vertx.tests.http.Http2TestBase;

import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import static io.vertx.test.http.HttpTestBase.*;
import static org.junit.Assert.assertTrue;

public interface HttpConfig {

  HttpVersion version();

  int port();

  String host();

  HttpServerConfig forServer();
  HttpClientConfig forClient();

  abstract class Http1xOr2Config implements HttpConfig {


    public abstract HttpServerOptions createBaseServerOptions();

    public abstract HttpClientOptions createBaseClientOptions();

    @Override
    public HttpClientConfig forClient() {
      HttpClientOptions options = createBaseClientOptions();
      return new HttpClientConfig() {
        @Override
        public HttpClientConfig setSsl(boolean ssl) {
          options.setSsl(ssl);
          return this;
        }
        @Override
        public HttpClientConfig setVerifyHost(boolean verify) {
          options.setVerifyHost(verify);
          return this;
        }
        @Override
        public HttpClientConfig setForceSni(boolean forceSni) {
          options.setForceSni(forceSni);
          return this;
        }
        @Override
        public HttpClientConfig setProxyOptions(ProxyOptions proxyOptions) {
          options.setProxyOptions(proxyOptions);
          return this;
        }
        @Override
        public HttpClientConfig setConnectTimeout(Duration connectTimeout) {
          options.setConnectTimeout((int)connectTimeout.toMillis());
          return this;
        }
        @Override
        public HttpClientConfig setDecompressionSupported(boolean decompressionSupported) {
          options.setDecompressionSupported(decompressionSupported);
          return this;
        }
        @Override
        public HttpClientConfig setLocalAddress(String localAddress) {
          options.setLocalAddress(localAddress);
          return this;
        }
        @Override
        public HttpClientConfig setLogActivity(boolean logActivity) {
          options.setLogActivity(logActivity);
          return this;
        }
        @Override
        public HttpClientConfig setMetricsName(String name) {
          options.setMetricsName(name);
          return this;
        }
        @Override
        public HttpClientConfig setIdleTimeout(Duration timeout) {
          options.setIdleTimeout((int)timeout.toMillis());
          options.setIdleTimeoutUnit(TimeUnit.MILLISECONDS);
          return this;
        }

        @Override
        public HttpClientConfig setKeepAliveTimeout(Duration timeout) {
          options.setKeepAliveTimeout((int)timeout.toSeconds());
          options.setHttp2KeepAliveTimeout((int)timeout.toSeconds());
          return this;
        }
        @Override
        public HttpClientConfig configureSsl(Consumer<ClientSSLOptions> configurator) {
          // Trigger creation of lazy SSL options
          options.setKeyCertOptions(options.getKeyCertOptions());
          configurator.accept(options.getSslOptions());
          return this;
        }
        @Override
        public HttpClientBuilder builder(Vertx vertx) {
          return vertx.httpClientBuilder().with(options);
        }
      };
    }

    @Override
    public HttpServerConfig forServer() {
      HttpServerOptions options = createBaseServerOptions();
      return new HttpServerConfig() {
        @Override
        public HttpServerConfig setSsl(boolean ssl) {
          options.setSsl(ssl);
          return this;
        }
        @Override
        public HttpServerConfig setUseProxyProtocol(boolean useProxyProtocol) {
          options.setUseProxyProtocol(useProxyProtocol);
          return this;
        }
        @Override
        public HttpServerConfig setDecompressionSupported(boolean supported) {
          options.setDecompressionSupported(supported);
          return this;
        }
        @Override
        public HttpServerConfig setCompression(CompressionConfig compression) {
          if (compression != null) {
            options.setCompression(compression);
            options.setCompressionSupported(true);
          } else {
            options.setCompressionSupported(false);
            options.setCompression(null);
          }
          return this;
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
          options.setLogActivity(logActivity);
          return this;
        }
        @Override
        public HttpServerConfig setIdleTimeout(Duration timeout) {
          options.setIdleTimeout((int)timeout.toMillis());
          options.setIdleTimeoutUnit(TimeUnit.MILLISECONDS);
          return this;
        }
        @Override
        public HttpServerConfig setHandle100ContinueAutomatically(boolean b) {
          options.setHandle100ContinueAutomatically(b);
          return this;
        }
        @Override
        public HttpServerConfig configureSsl(Consumer<ServerSSLOptions> configurator) {
          // Trigger creation of lazy SSL options
          options.setKeyCertOptions(options.getKeyCertOptions());
          configurator.accept(options.getSslOptions());
          return this;
        }
        @Override
        public HttpServerBuilder builder(Vertx vertx) {
          return vertx.httpServerBuilder()
            .with(new io.vertx.core.http.HttpServerConfig(options))
            .with(options.getSslOptions());
        }
      };
    }
  }

  class Http1x extends Http1xOr2Config {

    public static HttpConfig DEFAULT = new HttpConfig.Http1x(DEFAULT_HTTP_HOST, DEFAULT_HTTP_PORT);

    private final int port;
    private final String host;

    public Http1x(String host, int port) {
      this.port =  port;
      this.host = host;
    }

    @Override
    public HttpVersion version() {
      return HttpVersion.HTTP_1_1;
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
    public HttpServerOptions createBaseServerOptions() {
      return new HttpServerOptions().setPort(port).setHost(host);
    }

    @Override
    public HttpClientOptions createBaseClientOptions() {
      return new HttpClientOptions().setDefaultPort(port).setDefaultHost(host);
    }
  }

  abstract class Http2 extends Http1xOr2Config {

    private final boolean multiplex;
    private final int port;
    private final String host;

    protected Http2(boolean multiplex) {
      this(multiplex, DEFAULT_HTTPS_HOST, DEFAULT_HTTPS_PORT);
    }

    Http2(boolean multiplex, String host, int port) {
      this.multiplex = multiplex;
      this.port = port;
      this.host = host;
    }

    @Override
    public HttpVersion version() {
      return HttpVersion.HTTP_2;
    }

    @Override
    public int port() {
      return port;
    }

    @Override
    public String host() {
      return host;
    }

    protected abstract HttpServerOptions createBaseServerOptions(int port, String host, boolean multiplex);
    protected abstract HttpClientOptions createBaseClientOptions(int port, String host, boolean multiplex);

    @Override
    public HttpServerOptions createBaseServerOptions() {
      return createBaseServerOptions(port, host, multiplex);
    }

    @Override
    public HttpClientOptions createBaseClientOptions() {
      return createBaseClientOptions(port, host, multiplex);
    }
  }

  class H2 extends Http2 {

    public static H2 CODEC = new H2(false);
    public static H2 MULTIPLEX = new H2(true);

    public H2(boolean multiplex) {
      super(multiplex);
    }

    public H2(boolean multiplex, String host, int port) {
      super(multiplex, host, port);
    }

    @Override
    public HttpServerOptions createBaseServerOptions(int port, String host, boolean multiplex) {
      return Http2TestBase.createHttp2ServerOptions(port, host).setHttp2MultiplexImplementation(multiplex);
    }

    @Override
    public HttpClientOptions createBaseClientOptions(int port, String host, boolean multiplex) {
      return Http2TestBase.createHttp2ClientOptions()
        .setDefaultPort(port)
        .setDefaultHost(host)
        .setHttp2MultiplexImplementation(multiplex);
    }
  }

  class H2C extends Http2 {

    public static HttpConfig CODEC = new H2C(false);
    public static HttpConfig MULTIPLEX = new H2C(true);

    public H2C(boolean multiplex) {
      super(multiplex);
    }

    public H2C(boolean multiplex, String host, int port) {
      super(multiplex, host, port);
    }

    @Override
    public HttpServerOptions createBaseServerOptions(int port, String host, boolean multiplex) {
      return new HttpServerOptions()
        .setHost(host)
        .setPort(port)
        .setHttp2MultiplexImplementation(multiplex);
    }

    @Override
    public HttpClientOptions createBaseClientOptions(int port, String host, boolean multiplex) {
      return new HttpClientOptions()
        .setProtocolVersion(HttpVersion.HTTP_2)
        .setDefaultHost(host)
        .setDefaultPort(port)
        .setHttp2MultiplexImplementation(multiplex);
    }
  }
}
