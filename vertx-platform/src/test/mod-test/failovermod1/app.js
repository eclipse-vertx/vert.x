load("vertx.js");

var eb = vertx.eventBus;

console.log("failovermod1 deployed");

var nodeID = org.vertx.java.platform.impl.RhinoVerticleFactory.container.getNodeID();

eb.send("hatest", {"mod":"failovermod1","node_id":nodeID});