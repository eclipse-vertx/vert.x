package io.vertx.tests.http.http3;

import io.vertx.core.Vertx;
import io.vertx.core.http.*;
import io.vertx.test.http.HttpClientConfig;
import io.vertx.test.http.HttpConfig;
import io.vertx.test.http.HttpServerConfig;
import io.vertx.test.tls.Cert;
import io.vertx.test.tls.Trust;

import java.time.Duration;

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
    Http3ServerOptions options = new Http3ServerOptions();
    options.setPort(port);
    options.setHost(host);
    options.getSslOptions().setKeyCertOptions(Cert.SERVER_JKS.get());
    return new HttpServerConfig() {
      @Override
      public HttpServerConfig setDecompressionSupported(boolean supported) {
        throw new UnsupportedOperationException();
      }
      @Override
      public HttpServerConfig setCompression(HttpCompressionOptions compression) {
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
        options.setIdleTimeout(timeout);
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
    Http3ClientOptions options = new Http3ClientOptions();
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
        throw new UnsupportedOperationException();
      }
      @Override
      public HttpClientBuilder builder(Vertx vertx) {
        return vertx.httpClientBuilder().with(options);
      }
    };
  }
}
