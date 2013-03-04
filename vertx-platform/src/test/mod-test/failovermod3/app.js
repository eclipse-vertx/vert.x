load("vertx.js");

var eb = vertx.eventBus;

eb.send("hatest", "failovermod3");