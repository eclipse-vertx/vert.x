/*
 * Copyright (c) 2011-2025 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package io.vertx.core.net;

import io.vertx.codegen.annotations.DataObject;

import java.time.Duration;
import java.util.Objects;

/**
 * Config operations of a Quic server.
 *
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
@DataObject
public class QuicServerOptions extends QuicEndpointOptions {

  public static final boolean DEFAULT_LOAD_BALANCED = false;
  public static final QuicClientAddressValidation DEFAULT_CLIENT_ADDRESS_VALIDATION = QuicClientAddressValidation.BASIC;
  public static final KeyCertOptions DEFAULT_CLIENT_ADDRESS_VALIDATION_KEY = null;
  public static final Duration DEFAULT_CLIENT_ADDRESS_VALIDATION_TIME_WINDOW = Duration.ofSeconds(30);

  private boolean loadBalanced;
  private QuicClientAddressValidation clientAddressValidation;
  private Duration clientAddressValidationTimeWindow;
  private KeyCertOptions clientAddressValidationKey;

  public QuicServerOptions() {
    loadBalanced = DEFAULT_LOAD_BALANCED;
    clientAddressValidation = DEFAULT_CLIENT_ADDRESS_VALIDATION;
    clientAddressValidationKey = DEFAULT_CLIENT_ADDRESS_VALIDATION_KEY;
    clientAddressValidationTimeWindow = DEFAULT_CLIENT_ADDRESS_VALIDATION_TIME_WINDOW;
  }

  public QuicServerOptions(QuicServerOptions other) {
    super(other);

    KeyCertOptions tokenValidationKey = other.clientAddressValidationKey;

    this.loadBalanced = other.loadBalanced;
    this.clientAddressValidation = other.clientAddressValidation;
    this.clientAddressValidationTimeWindow = other.clientAddressValidationTimeWindow;
    this.clientAddressValidationKey = tokenValidationKey != null ? tokenValidationKey.copy() : null;
  }

  @Override
  public QuicServerOptions setQLogConfig(QLogConfig qLogConfig) {
    return (QuicServerOptions) super.setQLogConfig(qLogConfig);
  }

  @Override
  public QuicServerOptions setKeyLogFile(String keyLogFile) {
    return (QuicServerOptions) super.setKeyLogFile(keyLogFile);
  }

  @Override
  public QuicServerOptions setStreamIdleTimeout(Duration idleTimeout) {
    return (QuicServerOptions) super.setStreamIdleTimeout(idleTimeout);
  }

  @Override
  public QuicServerOptions setStreamReadIdleTimeout(Duration idleTimeout) {
    return (QuicServerOptions) super.setStreamReadIdleTimeout(idleTimeout);
  }

  @Override
  public QuicServerOptions setStreamWriteIdleTimeout(Duration idleTimeout) {
    return (QuicServerOptions) super.setStreamWriteIdleTimeout(idleTimeout);
  }

  @Override
  public ServerSSLOptions getSslOptions() {
    return (ServerSSLOptions) super.getSslOptions();
  }

  @Override
  protected ServerSSLOptions getOrCreateSSLOptions() {
    return new ServerSSLOptions();
  }

  /**
   * @return whether the server is load balanced
   */
  public boolean isLoadBalanced() {
    return loadBalanced;
  }

  /**
   * Set to {@code true} enables to bind multiples instances of a server on the same UDP port with the {@code SO_REUSE} options and let
   * set of bound server route UDP packets to the correct server instance.
   *
   * @param loadBalanced whether the server can be load balanced
   * @return this exact object instance
   */
  public QuicServerOptions setLoadBalanced(boolean loadBalanced) {
    this.loadBalanced = loadBalanced;
    return this;
  }

  /**
   * @return whether the server performs address validation
   */
  public QuicClientAddressValidation getClientAddressValidation() {
    return clientAddressValidation;
  }

  /**
   * <p>Configure the server to validate the client address using a (retry) token, by default this feature is disabled.
   * You should enable this feature for production servers.</p>
   *
   * <p>Client address validation requires you to also {@link #setClientAddressValidationKey(KeyCertOptions) set} a key for token signing/verification.</p>
   *
   * @param clientAddressValidation whether to perform address validation
   * @return this exact object instance
   */
  public QuicServerOptions setClientAddressValidation(QuicClientAddressValidation clientAddressValidation) {
    this.clientAddressValidation = Objects.requireNonNull(clientAddressValidation);
    return this;
  }

  /**
   * @return the client address validation token time window
   */
  public Duration getClientAddressValidationTimeWindow() {
    return clientAddressValidationTimeWindow;
  }

  /**
   * Set the time window by which a Quic token issued by the server to a client remains valid.
   *
   * @param clientAddressValidationTimeWindow the client address validation time window
   * @return this exact object instance
   */
  public QuicServerOptions setClientAddressValidationTimeWindow(Duration clientAddressValidationTimeWindow) {
    if (clientAddressValidationTimeWindow.isNegative() || clientAddressValidationTimeWindow.isZero()) {
      throw new IllegalArgumentException("Token validation time window must be > 0");
    }
    this.clientAddressValidationTimeWindow = clientAddressValidationTimeWindow;
    return this;
  }

  /**
   * @return the cryptographic key used for client address validation tokens
   */
  public KeyCertOptions getClientAddressValidationKey() {
    return clientAddressValidationKey;
  }

  /**
   * Set the cryptographic key used for client address validation tokens, the {@code validationKey} must point to a
   * keystore containing a private key / certificate pair or to a keystore containing symmetric key.
   *
   * @param validationKey the validation key
   * @return this exact object instance
   */
  public QuicServerOptions setClientAddressValidationKey(KeyCertOptions validationKey) {
    this.clientAddressValidationKey = validationKey;
    return this;
  }
}
