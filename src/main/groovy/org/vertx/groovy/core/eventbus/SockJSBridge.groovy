/*
 * Copyright 2011-2012 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.vertx.groovy.core.eventbus

import org.vertx.groovy.core.http.HttpServer
import org.vertx.java.core.sockjs.AppConfig
import org.vertx.java.core.json.JsonObject

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
class SockJSBridge {
  private final org.vertx.java.core.eventbus.SockJSBridge jBridge;

  SockJSBridge(HttpServer server, AppConfig sjsConfig, List<Map<String, Object>> permitted) {
    List<JsonObject> jList = new ArrayList<>();
    for (Map<String, Object> map: permitted) {
      jList.add(new JsonObject(map));
    }
    jBridge = new org.vertx.java.core.eventbus.SockJSBridge(server, sjsConfig, jList);
  }
}
