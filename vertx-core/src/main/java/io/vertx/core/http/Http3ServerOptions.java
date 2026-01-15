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
package io.vertx.core.http;

import io.vertx.codegen.annotations.DataObject;
import io.vertx.core.net.*;

import java.time.Duration;

/**
 * Represents options used by an {@link io.vertx.core.http.HttpServer} instance for HTTP/3
 *
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
@DataObject
public class Http3ServerOptions extends QuicServerOptions {

  /**
   * Default port the server will listen on = 443
   */
  public static final int DEFAULT_PORT = 443;

  /**
   * The default host to listen on = "0.0.0.0" (meaning listen on all available interfaces).
   */
  public static final String DEFAULT_HOST = "0.0.0.0";

  /**
   * Default max size of a form attribute = 8192
   */
  public static final int DEFAULT_MAX_FORM_ATTRIBUTE_SIZE = 8192;

  /**
   * Default max number of form fields = 256
   */
  public static final int DEFAULT_MAX_FORM_FIELDS = 256;

  /**
   * Default max number buffered bytes when decoding a form = 1024
   */
  public static final int DEFAULT_MAX_FORM_BUFFERED_SIZE = 1024;

  private int port;
  private String host;
  private boolean handle100ContinueAutomatically;
  private int maxFormAttributeSize;
  private int maxFormFields;
  private int maxFormBufferedBytes;
  private Http3Settings initialSettings;

  public Http3ServerOptions() {
    this.handle100ContinueAutomatically = HttpServerOptions.DEFAULT_HANDLE_100_CONTINE_AUTOMATICALLY;
    this.port = DEFAULT_PORT;
    this.host = DEFAULT_HOST;
    this.maxFormAttributeSize = DEFAULT_MAX_FORM_ATTRIBUTE_SIZE;
    this.maxFormFields = DEFAULT_MAX_FORM_FIELDS;
    this.maxFormBufferedBytes = DEFAULT_MAX_FORM_BUFFERED_SIZE;
    this.initialSettings = null;
  }

  public Http3ServerOptions(Http3ServerOptions other) {
    super(other);

    this.port = other.port;
    this.host = other.host;
    this.handle100ContinueAutomatically = other.handle100ContinueAutomatically;
    this.maxFormAttributeSize = other.maxFormAttributeSize;
    this.maxFormFields = other.maxFormFields;
    this.maxFormBufferedBytes = other.maxFormBufferedBytes;
    this.initialSettings = other.initialSettings != null ? other.initialSettings.copy() : null;
  }

  /**
   * @return the port
   */
  public int getPort() {
    return port;
  }

  /**
   * Set the port
   *
   * @param port  the port
   * @return a reference to this, so the API can be used fluently
   */
  public Http3ServerOptions setPort(int port) {
    if (port > 65535) {
      throw new IllegalArgumentException("port must be <= 65535");
    }
    this.port = port;
    return this;
  }

  /**
   * @return the host
   */
  public String getHost() {
    return host;
  }

  /**
   * Set the host
   * @param host  the host
   * @return a reference to this, so the API can be used fluently
   */
  public Http3ServerOptions setHost(String host) {
    this.host = host;
    return this;
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

  /**
   * @return Returns the maximum size of a form attribute
   */
  public int getMaxFormAttributeSize() {
    return maxFormAttributeSize;
  }

  /**
   * Set the maximum size of a form attribute. Set to {@code -1} to allow unlimited length
   *
   * @param maxSize the new maximum size
   * @return a reference to this, so the API can be used fluently
   */
  public Http3ServerOptions setMaxFormAttributeSize(int maxSize) {
    this.maxFormAttributeSize = maxSize;
    return this;
  }

  /**
   * @return Returns the maximum number of form fields
   */
  public int getMaxFormFields() {
    return maxFormFields;
  }

  /**
   * Set the maximum number of fields of a form. Set to {@code -1} to allow unlimited number of attributes
   *
   * @param maxFormFields the new maximum
   * @return a reference to this, so the API can be used fluently
   */
  public Http3ServerOptions setMaxFormFields(int maxFormFields) {
    this.maxFormFields = maxFormFields;
    return this;
  }

  /**
   * @return Returns the maximum number of bytes a server can buffer when decoding a form
   */
  public int getMaxFormBufferedBytes() {
    return maxFormBufferedBytes;
  }

  /**
   * Set the maximum number of bytes a server can buffer when decoding a form. Set to {@code -1} to allow unlimited length
   *
   * @param maxFormBufferedBytes the new maximum
   * @return a reference to this, so the API can be used fluently
   */
  public Http3ServerOptions setMaxFormBufferedBytes(int maxFormBufferedBytes) {
    this.maxFormBufferedBytes = maxFormBufferedBytes;
    return this;
  }

  /**
   * @return the initial HTTP/3 connection settings sent by the server when a client connects
   */
  public Http3Settings getInitialSettings() {
    return initialSettings;
  }

  /**
   * Set the HTTP/3 connection settings sent by the server when a client connects.
   *
   * @param settings the settings value
   * @return a reference to this, so the API can be used fluently
   */
  public Http3ServerOptions setInitialSettings(Http3Settings settings) {
    this.initialSettings = settings;
    return this;
  }

  @Override
  public Http3ServerOptions setTransportOptions(QuicOptions transportOptions) {
    return (Http3ServerOptions)super.setTransportOptions(transportOptions);
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
  public Http3ServerOptions setStreamIdleTimeout(Duration idleTimeout) {
    return (Http3ServerOptions)super.setStreamIdleTimeout(idleTimeout);
  }

  @Override
  public Http3ServerOptions setStreamReadIdleTimeout(Duration idleTimeout) {
    return (Http3ServerOptions)super.setStreamReadIdleTimeout(idleTimeout);
  }

  @Override
  public Http3ServerOptions setStreamWriteIdleTimeout(Duration idleTimeout) {
    return (Http3ServerOptions)super.setStreamWriteIdleTimeout(idleTimeout);
  }

  @Override
  public Http3ServerOptions setSslOptions(ServerSSLOptions sslOptions) {
    return (Http3ServerOptions)super.setSslOptions(sslOptions);
  }

  @Override
  public Http3ServerOptions setKeyLogFile(String keyLogFile) {
    return (Http3ServerOptions)super.setKeyLogFile(keyLogFile);
  }
}
