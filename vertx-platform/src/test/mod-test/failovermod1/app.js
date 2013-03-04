load("vertx.js");

var eb = vertx.eventBus;

console.log("failovermod1 deployed");

eb.send("hatest", "failovermod1");