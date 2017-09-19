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
 * Converter for {@link io.vertx.core.net.NetServerOptions}.
 *
 * NOTE: This class has been automatically generated from the {@link io.vertx.core.net.NetServerOptions} original class using Vert.x codegen.
 */
public class NetServerOptionsConverter {

  public static void fromJson(JsonObject json, NetServerOptions obj) {
    if (json.getValue("acceptBacklog") instanceof Number) {
      obj.setAcceptBacklog(((Number)json.getValue("acceptBacklog")).intValue());
    }
    if (json.getValue("clientAuth") instanceof String) {
      obj.setClientAuth(io.vertx.core.http.ClientAuth.valueOf((String)json.getValue("clientAuth")));
    }
    if (json.getValue("clientAuthRequired") instanceof Boolean) {
      obj.setClientAuthRequired((Boolean)json.getValue("clientAuthRequired"));
    }
    if (json.getValue("host") instanceof String) {
      obj.setHost((String)json.getValue("host"));
    }
    if (json.getValue("port") instanceof Number) {
      obj.setPort(((Number)json.getValue("port")).intValue());
    }
    if (json.getValue("sni") instanceof Boolean) {
      obj.setSni((Boolean)json.getValue("sni"));
    }
  }

  public static void toJson(NetServerOptions obj, JsonObject json) {
    json.put("acceptBacklog", obj.getAcceptBacklog());
    if (obj.getClientAuth() != null) {
      json.put("clientAuth", obj.getClientAuth().name());
    }
    json.put("clientAuthRequired", obj.isClientAuthRequired());
    if (obj.getHost() != null) {
      json.put("host", obj.getHost());
    }
    json.put("port", obj.getPort());
    json.put("sni", obj.isSni());
  }
}
