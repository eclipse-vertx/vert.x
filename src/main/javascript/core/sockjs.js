var vertx = vertx || {};

if (!vertx.SockJSServer) {

  vertx.SockJSServer = function(httpServer) {
    var jserver = new org.vertx.java.core.sockjs.SockJSServer(httpServer);
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

