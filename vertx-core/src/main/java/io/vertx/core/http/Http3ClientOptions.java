package io.vertx.core.http;

import io.vertx.codegen.annotations.DataObject;
import io.vertx.core.net.QLogConfig;
import io.vertx.core.net.QuicClientOptions;

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
}
