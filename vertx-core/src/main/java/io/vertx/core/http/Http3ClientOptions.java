package io.vertx.core.http;

import io.vertx.codegen.annotations.DataObject;
import io.vertx.core.net.ClientOptionsBase;
import io.vertx.core.net.QLogConfig;
import io.vertx.core.net.QuicClientOptions;
import io.vertx.core.net.QuicEndpointOptions;

import java.time.Duration;

@DataObject
public class Http3ClientOptions extends QuicClientOptions {

  /**
   * The default value for host name = "localhost"
   */
  public static final String DEFAULT_DEFAULT_HOST = "localhost";

  /**
   * The default value for port = 443
   */
  public static final int DEFAULT_DEFAULT_PORT = 443;

  private int defaultPort;
  private String defaultHost;
  private String metricsName;

  public Http3ClientOptions() {
    this.defaultPort = DEFAULT_DEFAULT_PORT;
    this.defaultHost = DEFAULT_DEFAULT_HOST;
  }

  public Http3ClientOptions(Http3ClientOptions other) {
    super(other);

    this.defaultPort = other.defaultPort;
    this.defaultHost = other.defaultHost;
    this.metricsName = other.metricsName;
  }

  @Override
  public Http3ClientOptions setQLogConfig(QLogConfig qLogConfig) {
    return (Http3ClientOptions)super.setQLogConfig(qLogConfig);
  }

  @Override
  public Http3ClientOptions setKeyLogFile(String keyLogFile) {
    return (Http3ClientOptions)super.setKeyLogFile(keyLogFile);
  }

  @Override
  public Http3ClientOptions setConnectTimeout(Duration connectTimeout) {
    return (Http3ClientOptions)super.setConnectTimeout(connectTimeout);
  }

  @Override
  public Http3ClientOptions setIdleTimeout(Duration idleTimeout) {
    return (Http3ClientOptions)super.setIdleTimeout(idleTimeout);
  }

  @Override
  public Http3ClientOptions setReadIdleTimeout(Duration idleTimeout) {
    return (Http3ClientOptions)super.setReadIdleTimeout(idleTimeout);
  }

  @Override
  public Http3ClientOptions setWriteIdleTimeout(Duration idleTimeout) {
    return (Http3ClientOptions)super.setWriteIdleTimeout(idleTimeout);
  }

  /**
   * Get the default host name to be used by this client in requests if none is provided when making the request.
   *
   * @return  the default host name
   */
  public String getDefaultHost() {
    return defaultHost;
  }

  /**
   * Set the default host name to be used by this client in requests if none is provided when making the request.
   *
   * @return a reference to this, so the API can be used fluently
   */
  public Http3ClientOptions setDefaultHost(String defaultHost) {
    this.defaultHost = defaultHost;
    return this;
  }

  /**
   * Get the default port to be used by this client in requests if none is provided when making the request.
   *
   * @return  the default port
   */
  public int getDefaultPort() {
    return defaultPort;
  }

  /**
   * Set the default port to be used by this client in requests if none is provided when making the request.
   *
   * @return a reference to this, so the API can be used fluently
   */
  public Http3ClientOptions setDefaultPort(int defaultPort) {
    this.defaultPort = defaultPort;
    return this;
  }

  /**
   * @return the metrics name identifying the reported metrics.
   */
  public String getMetricsName() {
    return metricsName;
  }

  /**
   * Set the metrics name identifying the reported metrics, useful for grouping metrics
   * with the same name.
   *
   * @param metricsName the metrics name
   * @return a reference to this, so the API can be used fluently
   */
  public Http3ClientOptions setMetricsName(String metricsName) {
    this.metricsName = metricsName;
    return this;
  }
}
