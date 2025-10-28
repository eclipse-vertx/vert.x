package io.vertx.core.http;

import io.vertx.codegen.annotations.DataObject;
import io.vertx.core.net.KeyCertOptions;
import io.vertx.core.net.QLogConfig;
import io.vertx.core.net.QuicClientAddressValidation;
import io.vertx.core.net.QuicServerOptions;

import java.time.Duration;

@DataObject
public class Http3ServerOptions extends QuicServerOptions {

  public Http3ServerOptions() {
  }

  public Http3ServerOptions(Http3ServerOptions other) {
    super(other);
  }

  @Override
  public Http3ServerOptions setQLogConfig(QLogConfig qLogConfig) {
    return (Http3ServerOptions)super.setQLogConfig(qLogConfig);
  }

  @Override
  public Http3ServerOptions setLoadBalanced(boolean loadBalanced) {
    return (Http3ServerOptions)super.setLoadBalanced(loadBalanced);
  }

  @Override
  public Http3ServerOptions setClientAddressValidation(QuicClientAddressValidation clientAddressValidation) {
    return (Http3ServerOptions)super.setClientAddressValidation(clientAddressValidation);
  }

  @Override
  public Http3ServerOptions setClientAddressValidationTimeWindow(Duration clientAddressValidationTimeWindow) {
    return (Http3ServerOptions)super.setClientAddressValidationTimeWindow(clientAddressValidationTimeWindow);
  }

  @Override
  public Http3ServerOptions setClientAddressValidationKey(KeyCertOptions validationKey) {
    return (Http3ServerOptions)super.setClientAddressValidationKey(validationKey);
  }
}
