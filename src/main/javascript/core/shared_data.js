var vertx = vertx || {};

if (!vertx.getMap) {

  vertx.getMap = function(name) {
    return org.vertx.java.core.shareddata.SharedData.instance.getMap(name);
  }

  vertx.getSet = function(name) {
    return org.vertx.java.core.shareddata.SharedData.instance.getSet(name);
  }

  vertx.removeMap = function(name) {
    return org.vertx.java.core.shareddata.SharedData.instance.removeMap(name);
  }

  vertx.removeSet = function(name) {
    return org.vertx.java.core.shareddata.SharedData.instance.removeSet(name);
  }
}



