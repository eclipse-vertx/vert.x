var vertx = vertx || {};

vertx.SockJSServer = function(httpServer) {

  var s = new org.vertx.java.core.sockjs.SockJSServer(httpServer);

  var server = new JavaAdapter(s, {
    installApp: function(config, handler) {

      jConfig = new org.vertx.java.core.sockjs.AppConfig();

      this.super$installApp(jConfig, handler);
    }
  });
  //return new org.vertx.java.core.sockjs.SockJSServer(httpServer);
  return server;
}

