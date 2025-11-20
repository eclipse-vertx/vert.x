package io.vertx.core.http;

import io.vertx.codegen.annotations.DataObject;
import io.vertx.core.net.*;

import java.time.Duration;

@DataObject
public class Http3ServerOptions extends QuicServerOptions {

  private boolean handle100ContinueAutomatically;

  public Http3ServerOptions() {
    this.handle100ContinueAutomatically = HttpServerOptions.DEFAULT_HANDLE_100_CONTINE_AUTOMATICALLY;
  }

  public Http3ServerOptions(Http3ServerOptions other) {
    super(other);

    this.handle100ContinueAutomatically = other.handle100ContinueAutomatically;
  }

  /**
   * @return whether 100 Continue should be handled automatically
   */
  public boolean isHandle100ContinueAutomatically() {
    return handle100ContinueAutomatically;
  }

  /**
   * Set whether 100 Continue should be handled automatically
   * @param handle100ContinueAutomatically {@code true} if it should be handled automatically
   * @return a reference to this, so the API can be used fluently
   */
  public Http3ServerOptions setHandle100ContinueAutomatically(boolean handle100ContinueAutomatically) {
    this.handle100ContinueAutomatically = handle100ContinueAutomatically;
    return this;
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

  @Override
  public Http3ServerOptions setIdleTimeout(Duration idleTimeout) {
    return (Http3ServerOptions)super.setIdleTimeout(idleTimeout);
  }

  @Override
  public Http3ServerOptions setReadIdleTimeout(Duration idleTimeout) {
    return (Http3ServerOptions)super.setReadIdleTimeout(idleTimeout);
  }

  @Override
  public Http3ServerOptions setWriteIdleTimeout(Duration idleTimeout) {
    return (Http3ServerOptions)super.setWriteIdleTimeout(idleTimeout);
  }

  @Override
  public Http3ServerOptions setKeyLogFile(String keyLogFile) {
    return (Http3ServerOptions)super.setKeyLogFile(keyLogFile);
  }
}
