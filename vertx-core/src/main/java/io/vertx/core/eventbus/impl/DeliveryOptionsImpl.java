/*
 * Copyright 2014 Red Hat, Inc.
 *
 *   Red Hat licenses this file to you under the Apache License, version 2.0
 *   (the "License"); you may not use this file except in compliance with the
 *   License.  You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 *   WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 *   License for the specific language governing permissions and limitations
 *   under the License.
 */

package io.vertx.core.eventbus.impl;

import io.vertx.core.Headers;
import io.vertx.core.eventbus.DeliveryOptions;
import io.vertx.core.http.CaseInsensitiveHeaders;
import io.vertx.core.json.JsonObject;

import java.util.Map;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class DeliveryOptionsImpl implements DeliveryOptions {

  private static final long DEFAULT_TIMEOUT = 30 * 1000;

  private long timeout = DEFAULT_TIMEOUT;
  private String codecName;
  private Headers headers;

  DeliveryOptionsImpl() {
  }

  DeliveryOptionsImpl(DeliveryOptions other) {
    this.timeout = other.getSendTimeout();
    this.codecName = other.getCodecName();
    this.headers = other.getHeaders();
  }

  DeliveryOptionsImpl(JsonObject json) {
    this.timeout = json.getLong("timeout", DEFAULT_TIMEOUT);
    this.codecName = json.getString("codecName", null);
    JsonObject hdrs = json.getObject("headers", null);
    if (hdrs != null) {
      headers = new CaseInsensitiveHeaders();
      for (Map.Entry<String, Object> entry: hdrs.toMap().entrySet()) {
        if (!(entry.getValue() instanceof String)) {
          throw new IllegalStateException("Invalid type for message header value " + entry.getValue().getClass());
        }
        headers.set(entry.getKey(), (String)entry.getValue());
      }
    }
  }

  @Override
  public long getSendTimeout() {
    return timeout;
  }

  @Override
  public DeliveryOptions setSendTimeout(long timeout) {
    if (timeout < 1) {
      throw new IllegalArgumentException("sendTimeout must be >= 1");
    }
    this.timeout = timeout;
    return this;
  }

  @Override
  public String getCodecName() {
    return codecName;
  }

  @Override
  public DeliveryOptions setCodecName(String codecName) {
    this.codecName = codecName;
    return this;
  }

  @Override
  public DeliveryOptions addHeader(String key, String value) {
    checkHeaders();
    headers.add(key, value);
    return this;
  }

  @Override
  public DeliveryOptions setHeaders(Headers headers) {
    this.headers = headers;
    return this;
  }

  @Override
  public Headers getHeaders() {
    return headers;
  }

  private void checkHeaders() {
    if (headers == null) {
      headers = new CaseInsensitiveHeaders();
    }
  }
}
