package io.vertx.test.http;

import io.vertx.core.Vertx;
import io.vertx.core.http.*;
import io.vertx.core.net.SSLEngineOptions;
import io.vertx.core.net.SocketAddress;
import io.vertx.tests.http.Http2TestBase;

import java.time.Duration;
import java.util.List;
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
        public HttpServerConfig setCompression(HttpCompressionOptions compression) {
          if (compression != null) {
            options.setCompressionSupported(true);
            options.setCompression(compression);
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

    protected Http1x() {
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

  class Http2 extends Http1xOr2Config {

    public static HttpConfig CODEC = new HttpConfig.Http2(false);
    public static HttpConfig MULTIPLEX = new HttpConfig.Http2(true);

    private final boolean multiplex;
    private final int port;
    private final String host;

    public Http2(boolean multiplex) {
      this(multiplex, DEFAULT_HTTPS_HOST, DEFAULT_HTTPS_PORT);
    }

    public Http2(boolean multiplex, String host, int port) {
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

    @Override
    public HttpServerOptions createBaseServerOptions() {
      return Http2TestBase.createHttp2ServerOptions(port, host)
        .setHttp2MultiplexImplementation(multiplex);
    }

    @Override
    public HttpClientOptions createBaseClientOptions() {
      return Http2TestBase.createHttp2ClientOptions()
        .setDefaultPort(port)
        .setDefaultHost(host)
        .setHttp2MultiplexImplementation(multiplex);
    }
  }
}
