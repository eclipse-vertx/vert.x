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

  public HttpServerConfig() {
    this.maxFormAttributeSize = HttpServerOptions.DEFAULT_MAX_FORM_ATTRIBUTE_SIZE;
    this.maxFormFields = HttpServerOptions.DEFAULT_MAX_FORM_FIELDS;
    this.maxFormBufferedBytes = HttpServerOptions.DEFAULT_MAX_FORM_BUFFERED_SIZE;
    this.handle100ContinueAutomatically = HttpServerOptions.DEFAULT_HANDLE_100_CONTINE_AUTOMATICALLY;
    this.strictThreadMode = HttpServerOptions.DEFAULT_STRICT_THREAD_MODE_STRICT;
    this.tracingPolicy = HttpServerOptions.DEFAULT_TRACING_POLICY;
  }

  public HttpServerConfig(HttpServerConfig other) {
    this.maxFormAttributeSize = other.maxFormAttributeSize;
    this.maxFormFields = other.maxFormFields;
    this.maxFormBufferedBytes = other.maxFormBufferedBytes;
    this.handle100ContinueAutomatically = other.handle100ContinueAutomatically;
    this.strictThreadMode = other.strictThreadMode;
    this.tracingPolicy = other.tracingPolicy;
  }

  /**
   * @return the client SSL options.
   */
  public abstract ServerSSLOptions getSslOptions();

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
