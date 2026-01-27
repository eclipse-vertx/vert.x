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
import io.vertx.codegen.annotations.Unstable;
import io.vertx.core.net.*;
import io.vertx.core.tracing.TracingPolicy;

import java.time.Duration;

/**
 * Configuration of a {@link HttpServer}
 *
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
@DataObject
public abstract class HttpServerConfig {

  private int maxFormAttributeSize;
  private int maxFormFields;
  private int maxFormBufferedBytes;
  private boolean handle100ContinueAutomatically;
  private boolean strictThreadMode;
  private TracingPolicy tracingPolicy;

  protected HttpServerConfig(HttpServerOptions options) {
    this.maxFormAttributeSize = options.getMaxFormAttributeSize();
    this.maxFormFields = options.getMaxFormFields();
    this.maxFormBufferedBytes = options.getMaxFormBufferedBytes();
    this.handle100ContinueAutomatically = options.isHandle100ContinueAutomatically();
    this.strictThreadMode = options.getStrictThreadMode();
    this.tracingPolicy = options.getTracingPolicy();
  }

  /**
   * @return the client SSL options.
   */
  public abstract ServerSSLOptions getSslOptions();

  /**
   * Set the server SSL options.
   *
   * @param sslOptions the options
   * @return a reference to this, so the API can be used fluently
   */
  public abstract HttpServerConfig setSslOptions(ServerSSLOptions sslOptions);

  /**
   * @return the SSL engine implementation to use
   */
  public abstract SSLEngineOptions getSslEngineOptions();

  /**
   * Set to use SSL engine implementation to use.
   *
   * @param sslEngineOptions the ssl engine to use
   * @return a reference to this, so the API can be used fluently
   */
  public abstract HttpServerConfig setSslEngineOptions(SSLEngineOptions sslEngineOptions);

  /**
   *
   * @return the port
   */
  public abstract int getPort();

  /**
   * Set the port
   *
   * @param port  the port
   * @return a reference to this, so the API can be used fluently
   */
  public abstract HttpServerConfig setPort(int port);

  /**
   *
   * @return the host
   */
  public abstract String getHost();

  /**
   * Set the host
   * @param host  the host
   * @return a reference to this, so the API can be used fluently
   */
  public abstract HttpServerConfig setHost(String host);

  /**
   * @return the idle timeout
   */
  public abstract Duration getIdleTimeout();

  /**
   * Set the idle timeout, default time unit is seconds. Zero means don't time out.
   * This determines if a connection will timeout and be closed if no data is received nor sent within the timeout.
   *
   * @param idleTimeout  the timeout
   * @return a reference to this, so the API can be used fluently
   */
  public abstract HttpServerConfig setIdleTimeout(Duration idleTimeout);

  /**
   * @return the read idle timeout
   */
  public abstract Duration getReadIdleTimeout();

  /**
   * Set the read idle timeout. Zero means don't time out.
   * This determines if a connection will timeout and be closed if no data is received within the timeout.
   *
   * @param idleTimeout  the read timeout
   * @return a reference to this, so the API can be used fluently
   */
  public abstract HttpServerConfig setReadIdleTimeout(Duration idleTimeout);

  /**
   * @return the write idle timeout.
   */
  public abstract Duration getWriteIdleTimeout();

  /**
   * Set the write idle timeout, default time unit is seconds. Zero means don't time out.
   * This determines if a connection will timeout and be closed if no data is sent within the timeout.
   *
   * @param idleTimeout  the write timeout
   * @return a reference to this, so the API can be used fluently
   */
  public abstract HttpServerConfig setWriteIdleTimeout(Duration idleTimeout);

  /**
   *
   * @return is SSL/TLS enabled?
   */
  public abstract boolean isSsl();

  /**
   * Set whether SSL/TLS is enabled
   *
   * @param ssl  true if enabled
   * @return a reference to this, so the API can be used fluently
   */
  public abstract HttpServerConfig setSsl(boolean ssl);

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
  public HttpServerConfig setMaxFormAttributeSize(int maxSize) {
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
  public HttpServerConfig setMaxFormFields(int maxFormFields) {
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
  public HttpServerConfig setMaxFormBufferedBytes(int maxFormBufferedBytes) {
    this.maxFormBufferedBytes = maxFormBufferedBytes;
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
  public HttpServerConfig setHandle100ContinueAutomatically(boolean handle100ContinueAutomatically) {
    this.handle100ContinueAutomatically = handle100ContinueAutomatically;
    return this;
  }

  /**
   * @return whether to use the strict thread mode.
   */
  @Unstable("Experimental")
  public boolean getStrictThreadMode() {
    return strictThreadMode;
  }

  /**
   * Indicates the server that the HTTP request/response interactions will happen exclusively on the expected thread when
   * the threading model is event-loop.
   *
   * @param strictThreadMode whether to use the strict thread mode
   * @return a reference to this, so the API can be used fluently
   */
  @Unstable("Experimental")
  public HttpServerConfig setStrictThreadMode(boolean strictThreadMode) {
    this.strictThreadMode = strictThreadMode;
    return this;
  }

  /**
   * @return the tracing policy
   */
  public TracingPolicy getTracingPolicy() {
    return tracingPolicy;
  }

  /**
   * Set the tracing policy for the server behavior when Vert.x has tracing enabled.
   *
   * @param tracingPolicy the tracing policy
   * @return a reference to this, so the API can be used fluently
   */
  public HttpServerConfig setTracingPolicy(TracingPolicy tracingPolicy) {
    this.tracingPolicy = tracingPolicy;
    return this;
  }
}
