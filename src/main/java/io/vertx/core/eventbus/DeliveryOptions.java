/*
 * Copyright (c) 2011-2014 The original author or authors
 * ------------------------------------------------------
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * and Apache License v2.0 which accompanies this distribution.
 *
 *     The Eclipse Public License is available at
 *     http://www.eclipse.org/legal/epl-v10.html
 *
 *     The Apache License v2.0 is available at
 *     http://www.opensource.org/licenses/apache2.0.php
 *
 * You may elect to redistribute this code under either of these licenses.
 */

package io.vertx.core.eventbus;

import io.vertx.codegen.annotations.DataObject;
import io.vertx.core.MultiMap;
import io.vertx.core.http.CaseInsensitiveHeaders;
import io.vertx.core.impl.Arguments;
import io.vertx.core.json.JsonObject;

import java.util.Map;
import java.util.Objects;

/**
 * Delivery options are used to configure message delivery.
 * <p>
 * Delivery options allow to configure delivery timeout and message codec name, and to provide any headers
 * that you wish to send with the message.
 *
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
@DataObject
public class DeliveryOptions {

  /**
   * The default send timeout.
   */
  public static final long DEFAULT_TIMEOUT = 30 * 1000;

  private long timeout = DEFAULT_TIMEOUT;
  private String codecName;
  private MultiMap headers;

  /**
   * Default constructor
   */
  public DeliveryOptions() {
  }

  /**
   * Copy constructor
   *
   * @param other  the options to copy
   */
  public DeliveryOptions(DeliveryOptions other) {
    this.timeout = other.getSendTimeout();
    this.codecName = other.getCodecName();
    this.headers = other.getHeaders();
  }

  /**
   * Create a delivery options from JSON
   *
   * @param json  the JSON
   */
  public DeliveryOptions(JsonObject json) {
    this.timeout = json.getLong("timeout", DEFAULT_TIMEOUT);
    this.codecName = json.getString("codecName", null);
    JsonObject hdrs = json.getJsonObject("headers", null);
    if (hdrs != null) {
      headers = new CaseInsensitiveHeaders();
      for (Map.Entry<String, Object> entry: hdrs) {
        if (!(entry.getValue() instanceof String)) {
          throw new IllegalStateException("Invalid type for message header value " + entry.getValue().getClass());
        }
        headers.set(entry.getKey(), (String)entry.getValue());
      }
    }
  }

  /**
   * Get the send timeout.
   * <p>
   * When sending a message with a response handler a send timeout can be provided. If no response is received
   * within the timeout the handler will be called with a failure.
   *
   * @return  the value of send timeout
   */
  public long getSendTimeout() {
    return timeout;
  }

  /**
   * Set the send timeout.
   *
   * @param timeout  the timeout value, in ms.
   * @return  a reference to this, so the API can be used fluently
   */
  public DeliveryOptions setSendTimeout(long timeout) {
    Arguments.require(timeout >= 1, "sendTimeout must be >= 1");
    this.timeout = timeout;
    return this;
  }

  /**
   * Get the codec name.
   * <p>
   * When sending or publishing a message a codec name can be provided. This must correspond with a previously registered
   * message codec. This allows you to send arbitrary objects on the event bus (e.g. POJOs).
   *
   * @return  the codec name
   */
  public String getCodecName() {
    return codecName;
  }

  /**
   * Set the codec name.
   *
   * @param codecName  the codec name
   * @return  a reference to this, so the API can be used fluently
   */
  public DeliveryOptions setCodecName(String codecName) {
    this.codecName = codecName;
    return this;
  }

  /**
   * Add a message header.
   * <p>
   * Message headers can be sent with any message and will be accessible with {@link io.vertx.core.eventbus.Message#headers}
   * at the recipient.
   *
   * @param key  the header key
   * @param value  the header value
   * @return  a reference to this, so the API can be used fluently
   */
  public DeliveryOptions addHeader(String key, String value) {
    checkHeaders();
    Objects.requireNonNull(key, "no null key accepted");
    Objects.requireNonNull(value, "no null value accepted");
    headers.add(key, value);
    return this;
  }

  /**
   * Set message headers from a multi-map.
   *
   * @param headers  the headers
   * @return  a reference to this, so the API can be used fluently
   */
  public DeliveryOptions setHeaders(MultiMap headers) {
    this.headers = headers;
    return this;
  }

  /**
   * Get the message headers
   *
   * @return  the headers
   */
  public MultiMap getHeaders() {
    return headers;
  }

  private void checkHeaders() {
    if (headers == null) {
      headers = new CaseInsensitiveHeaders();
    }
  }
}
