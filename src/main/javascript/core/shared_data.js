var vertx = vertx || {};

vertx.shareddata = vertx.shareddata || {};

vertx.shareddata.getMap = function(name) {
  return org.vertx.java.core.shareddata.SharedData.getMap(name);
}

vertx.shareddata.getSet = function(name) {
  return org.vertx.java.core.shareddata.SharedData.getSet(name);
}

vertx.shareddata.removeMap = function(name) {
  return org.vertx.java.core.shareddata.SharedData.removeMap(name);
}

vertx.shareddata.removeSet = function(name) {
  return org.vertx.java.core.shareddata.SharedData.removeSet(name);
}



