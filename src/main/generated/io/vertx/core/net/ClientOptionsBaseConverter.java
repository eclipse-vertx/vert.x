/*
 * Copyright (c) 2011-2017 The original author or authors
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 and the Apache License, Version 2.0
 * which accompanies this distribution. The Eclipse Public License 2.0 is
 * available at http://www.eclipse.org/legal/epl-2.0.html, and the Apache
 * License, Version 2.0 is available at
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */

package io.vertx.core.net;

import io.vertx.core.json.JsonObject;
import io.vertx.core.json.JsonArray;

/**
 * Converter for {@link io.vertx.core.net.ClientOptionsBase}.
 *
 * NOTE: This class has been automatically generated from the {@link io.vertx.core.net.ClientOptionsBase} original class using Vert.x codegen.
 */
public class ClientOptionsBaseConverter {

  public static void fromJson(JsonObject json, ClientOptionsBase obj) {
    if (json.getValue("connectTimeout") instanceof Number) {
      obj.setConnectTimeout(((Number)json.getValue("connectTimeout")).intValue());
    }
    if (json.getValue("localAddress") instanceof String) {
      obj.setLocalAddress((String)json.getValue("localAddress"));
    }
    if (json.getValue("metricsName") instanceof String) {
      obj.setMetricsName((String)json.getValue("metricsName"));
    }
    if (json.getValue("proxyOptions") instanceof JsonObject) {
      obj.setProxyOptions(new io.vertx.core.net.ProxyOptions((JsonObject)json.getValue("proxyOptions")));
    }
    if (json.getValue("trustAll") instanceof Boolean) {
      obj.setTrustAll((Boolean)json.getValue("trustAll"));
    }
  }

  public static void toJson(ClientOptionsBase obj, JsonObject json) {
    json.put("connectTimeout", obj.getConnectTimeout());
    if (obj.getLocalAddress() != null) {
      json.put("localAddress", obj.getLocalAddress());
    }
    if (obj.getMetricsName() != null) {
      json.put("metricsName", obj.getMetricsName());
    }
    if (obj.getProxyOptions() != null) {
      json.put("proxyOptions", obj.getProxyOptions().toJson());
    }
    json.put("trustAll", obj.isTrustAll());
  }
}
