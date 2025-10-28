package io.vertx.core.http;

import io.vertx.codegen.annotations.DataObject;
import io.vertx.core.net.QLogConfig;
import io.vertx.core.net.QuicClientOptions;
import io.vertx.core.net.QuicEndpointOptions;

import java.time.Duration;

@DataObject
public class Http3ClientOptions extends QuicClientOptions {

  public Http3ClientOptions() {
  }

  public Http3ClientOptions(Http3ClientOptions other) {
    super(other);
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
}
