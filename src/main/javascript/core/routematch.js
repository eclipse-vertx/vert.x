var vertx = vertx || {};

if (!vertx.RouteMatcher) {
  vertx.RouteMatcher = function() {
    return new org.vertx.java.core.http.RouteMatcher();
  }
}

