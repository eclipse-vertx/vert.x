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
    json.put("sendBufferSize", obj.getSendBufferSize());
    json.put("trafficClass", obj.getTrafficClass());
  }
}