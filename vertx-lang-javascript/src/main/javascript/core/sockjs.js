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

var vertx = vertx || {};

if (!vertx.createSockJSServer) {

  vertx.createSockJSServer = function(httpServer) {

    if (typeof httpServer._to_java_server != 'function') {
      throw "Please construct a vertx.SockJSServer with an instance of vert.HttpServer"
    }

    var vertx = org.vertx.java.deploy.impl.VertxLocator.vertx;

    var jserver = vertx.createSockJSServer(httpServer._to_java_server());
    var server = {
      installApp: function(config, handler) {
        jserver.installApp(new org.vertx.java.core.json.JsonObject(JSON.stringify(config)), handler);
      },
      bridge: function(config, permitted, authTimeout, authAddress) {
        if (typeof authTimeout === 'undefined') {
          authTimeout = 5 * 50 * 1000;
        }
        if (typeof authAddress === 'undefined') {
          authAddress = null;
        }
        var json_arr = new org.vertx.java.core.json.JsonArray();
        for (var i = 0; i < permitted.length; i++) {
          var match = permitted[i];
          var json_str = JSON.stringify(match);
          var jJson = new org.vertx.java.core.json.JsonObject(json_str);
          json_arr.add(jJson);
        }
        jserver.bridge(new org.vertx.java.core.json.JsonObject(JSON.stringify(config)), json_arr, authTimeout, authAddress);
      }
    }
    return server;
  }
}

