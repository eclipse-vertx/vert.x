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
import io.vertx.tests.http.Http2TestBase;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

import static io.vertx.test.http.HttpTestBase.*;

public interface HttpConfig {

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
        public HttpServerConfig setDecompressionSupported(boolean supported) {
          options.setDecompressionSupported(supported);
          return this;
        }
        @Override
        public HttpServerConfig setCompression(HttpCompressionConfig compression) {
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
        public HttpServer create(Vertx vertx) {
          return vertx.createHttpServer(options);
        }
      };
    }
  }

  class Http1x extends Http1xOr2Config {

    public static HttpConfig DEFAULT = new HttpConfig.Http1x();

    private final int port;
    private final String host;

    public Http1x(String host, int port) {
      this.port =  port;
      this.host = host;
    }

    private Http1x() {
      this(DEFAULT_HTTP_HOST, DEFAULT_HTTP_PORT);
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

    Http2(boolean multiplex) {
      this(multiplex, DEFAULT_HTTPS_HOST, DEFAULT_HTTPS_PORT);
    }

    Http2(boolean multiplex, String host, int port) {
      this.multiplex = multiplex;
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

    abstract HttpServerOptions createBaseServerOptions(int port, String host, boolean multiplex);
    abstract HttpClientOptions createBaseClientOptions(int port, String host, boolean multiplex);

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

    public static HttpConfig CODEC = new H2(false);
    public static HttpConfig MULTIPLEX = new H2(true);

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
