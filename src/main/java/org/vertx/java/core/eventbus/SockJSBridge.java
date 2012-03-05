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

package org.vertx.java.core.eventbus;

import org.vertx.java.core.http.HttpServer;
import org.vertx.java.core.json.JsonObject;
import org.vertx.java.core.sockjs.AppConfig;
import org.vertx.java.core.sockjs.SockJSServer;

import java.util.List;

/**
 * A SockJSBridge bridges between SockJS and the event bus.
 *<p>
 * Bridging allows the event bus to be extended to client side in-browser JavaScript.
 *<p>
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class SockJSBridge {

  public SockJSBridge(HttpServer server, AppConfig sjsConfig, List<JsonObject> permitted) {
    SockJSServer sjsServer = new SockJSServer(server);
    SockJSBridgeHandler handler = new SockJSBridgeHandler();
    for (JsonObject perm: permitted) {
      handler.addPermitted(perm);
    }
    sjsServer.installApp(sjsConfig, handler);
  }
}
