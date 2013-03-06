load("vertx.js");

var eb = vertx.eventBus;

var nodeID = org.vertx.java.platform.impl.RhinoVerticleFactory.container.getNodeID();

eb.send("hatest", {"mod":"failovermod3","node_id":nodeID});