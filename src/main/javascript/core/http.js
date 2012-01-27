var vertx = vertx || {};

vertx.HttpServer = function() {
  return new org.vertx.java.core.http.HttpServer();
}

vertx.HttpClient = function() {
  return new org.vertx.java.core.http.HttpClient();
}

vertx.RouteMatcher = function() {
  return new org.vertx.java.core.http.RouteMatcher();
}
