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

if (!vertx.SockJSServer) {

  vertx.SockJSServer = function(httpServer) {

    if (typeof httpServer._to_java_server != 'function') {
      throw "Please construct a vertx.SockJSServer with an instance of vert.HttpServer"
    }

    var jserver = new org.vertx.java.core.sockjs.SockJSServer(httpServer._to_java_server());
    var server = {
      installApp: function(config, handler) {
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

        jserver.installApp(jConfig, handler);
      }
    }
    return server;
  }
}

