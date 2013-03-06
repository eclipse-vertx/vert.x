load("vertx.js");

var eb = vertx.eventBus;

console.log("failovermod2 deployed");

var nodeID = org.vertx.java.platform.impl.RhinoVerticleFactory.container.getNodeID();

eb.send("hatest", {"mod":"failovermod2","node_id":nodeID});