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

package io.vertx.core.datagram;

import io.vertx.core.json.JsonObject;
import io.vertx.core.json.JsonArray;

/**
 * Converter for {@link io.vertx.core.datagram.DatagramSocketOptions}.
 *
 * NOTE: This class has been automatically generated from the {@link io.vertx.core.datagram.DatagramSocketOptions} original class using Vert.x codegen.
 */
public class DatagramSocketOptionsConverter {

  public static void fromJson(JsonObject json, DatagramSocketOptions obj) {
    if (json.getValue("broadcast") instanceof Boolean) {
      obj.setBroadcast((Boolean)json.getValue("broadcast"));
    }
    if (json.getValue("ipV6") instanceof Boolean) {
      obj.setIpV6((Boolean)json.getValue("ipV6"));
    }
    if (json.getValue("loopbackModeDisabled") instanceof Boolean) {
      obj.setLoopbackModeDisabled((Boolean)json.getValue("loopbackModeDisabled"));
    }
    if (json.getValue("multicastNetworkInterface") instanceof String) {
      obj.setMulticastNetworkInterface((String)json.getValue("multicastNetworkInterface"));
    }
    if (json.getValue("multicastTimeToLive") instanceof Number) {
      obj.setMulticastTimeToLive(((Number)json.getValue("multicastTimeToLive")).intValue());
    }
  }

  public static void toJson(DatagramSocketOptions obj, JsonObject json) {
    json.put("broadcast", obj.isBroadcast());
    json.put("ipV6", obj.isIpV6());
    json.put("loopbackModeDisabled", obj.isLoopbackModeDisabled());
    if (obj.getMulticastNetworkInterface() != null) {
      json.put("multicastNetworkInterface", obj.getMulticastNetworkInterface());
    }
    json.put("multicastTimeToLive", obj.getMulticastTimeToLive());
  }
}
