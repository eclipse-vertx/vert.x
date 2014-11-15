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

import io.vertx.codegen.annotations.Options;
import io.vertx.core.MultiMap;
import io.vertx.core.http.CaseInsensitiveHeaders;
import io.vertx.core.impl.Arguments;
import io.vertx.core.json.JsonObject;

import java.util.Map;
import java.util.Objects;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
@Options
public class DeliveryOptions {

  private static final long DEFAULT_TIMEOUT = 30 * 1000;

  private long timeout = DEFAULT_TIMEOUT;
  private String codecName;
  private MultiMap headers;

  public DeliveryOptions() {
  }

  public DeliveryOptions(DeliveryOptions other) {
    this.timeout = other.getSendTimeout();
    this.codecName = other.getCodecName();
    this.headers = other.getHeaders();
  }

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

  public long getSendTimeout() {
    return timeout;
  }

  public DeliveryOptions setSendTimeout(long timeout) {
    Arguments.require(timeout >= 1, "sendTimeout must be >= 1");
    this.timeout = timeout;
    return this;
  }

  public String getCodecName() {
    return codecName;
  }

  public DeliveryOptions setCodecName(String codecName) {
    this.codecName = codecName;
    return this;
  }

  public DeliveryOptions addHeader(String key, String value) {
    checkHeaders();
    Objects.requireNonNull(key, "no null key accepted");
    Objects.requireNonNull(value, "no null value accepted");
    headers.add(key, value);
    return this;
  }

  public DeliveryOptions setHeaders(MultiMap headers) {
    this.headers = headers;
    return this;
  }

  public MultiMap getHeaders() {
    return headers;
  }

  private void checkHeaders() {
    if (headers == null) {
      headers = new CaseInsensitiveHeaders();
    }
  }
}
