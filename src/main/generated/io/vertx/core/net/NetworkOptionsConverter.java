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
 * Converter for {@link io.vertx.core.net.NetworkOptions}.
 *
 * NOTE: This class has been automatically generated from the {@link io.vertx.core.net.NetworkOptions} original class using Vert.x codegen.
 */
public class NetworkOptionsConverter {

  public static void fromJson(JsonObject json, NetworkOptions obj) {
    if (json.getValue("logActivity") instanceof Boolean) {
      obj.setLogActivity((Boolean)json.getValue("logActivity"));
    }
    if (json.getValue("receiveBufferSize") instanceof Number) {
      obj.setReceiveBufferSize(((Number)json.getValue("receiveBufferSize")).intValue());
    }
    if (json.getValue("reuseAddress") instanceof Boolean) {
      obj.setReuseAddress((Boolean)json.getValue("reuseAddress"));
    }
    if (json.getValue("reusePort") instanceof Boolean) {
      obj.setReusePort((Boolean)json.getValue("reusePort"));
    }
    if (json.getValue("sendBufferSize") instanceof Number) {
      obj.setSendBufferSize(((Number)json.getValue("sendBufferSize")).intValue());
    }
    if (json.getValue("trafficClass") instanceof Number) {
      obj.setTrafficClass(((Number)json.getValue("trafficClass")).intValue());
    }
  }

  public static void toJson(NetworkOptions obj, JsonObject json) {
    json.put("logActivity", obj.getLogActivity());
    json.put("receiveBufferSize", obj.getReceiveBufferSize());
    json.put("reuseAddress", obj.isReuseAddress());
    json.put("reusePort", obj.isReusePort());
    json.put("sendBufferSize", obj.getSendBufferSize());
    json.put("trafficClass", obj.getTrafficClass());
  }
}
