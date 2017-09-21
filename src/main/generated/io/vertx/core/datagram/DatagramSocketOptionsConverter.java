/*
 * Copyright 2014 Red Hat, Inc.
 *
 * Red Hat licenses this file to you under the Apache License, version 2.0
 * (the "License"); you may not use this file except in compliance with the
 * License.  You may obtain a copy of the License at:
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the specific language governing permissions and limitations
 * under the License.
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