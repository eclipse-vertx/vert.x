var vertx = vertx || {};

vertx.shareddata = vertx.shareddata || {};

vertx.shareddata.getMap = function(name) {
  return org.vertx.java.core.shareddata.SharedData.instance.getMap(name);
}

vertx.shareddata.getSet = function(name) {
  return org.vertx.java.core.shareddata.SharedData.instance.getSet(name);
}

vertx.shareddata.removeMap = function(name) {
  return org.vertx.java.core.shareddata.SharedData.instance.removeMap(name);
}

vertx.shareddata.removeSet = function(name) {
  return org.vertx.java.core.shareddata.SharedData.instance.removeSet(name);
}



