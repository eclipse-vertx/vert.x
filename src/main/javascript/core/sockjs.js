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

    function convertConfig(config) {
      var jConfig = new org.vertx.java.core.sockjs.AppConfig();
      var prefix = config['prefix'];
      if (typeof prefix != 'undefined') jConfig.setPrefix(prefix);
      var jsessionid = config['insert_JSESSIONID'];
      if (typeof jsessionid != 'undefined') jConfig.setInsertJSESSIONID(jsessionid);
      var session_timeout = config['session_timeout'];
      if (typeof session_timeout != 'undefined') jConfig.setSessionTimeout(session_timeout);
      var heartbeat_period = config['heartbeat_period'];
      if (typeof heartbeat_period != 'undefined') jConfig.setHeartbeatPeriod(heartbeat_period);
      var max_bytes_streaming = config['max_bytes_streaming'];
      if (typeof max_bytes_streaming != 'undefined') jConfig.setMaxBytesStreaming(max_bytes_streaming);
      var library_url = config['library_url'];
      if (typeof library_url != 'undefined') jConfig.setLibraryURL(library_url);
      return jConfig;
    }

    var jserver = vertx.createSockJSServer(httpServer._to_java_server());
    var server = {
      installApp: function(config, handler) {
        jserver.installApp(convertConfig(config), handler);
      },
      bridge: function(config, permitted) {
        var jList = new java.util.ArrayList();
        for (var i = 0; i < permitted.length; i++) {
          var match = permitted[i];
          var json_str = JSON.stringify(match);
          var jJson = new org.vertx.java.core.json.JsonObject(json_str);
          jList.add(jJson);
        }
        jserver.bridge(convertConfig(config), jList);
      }
    }
    return server;
  }
}

