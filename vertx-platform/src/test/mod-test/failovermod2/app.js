load("vertx.js");

var eb = vertx.eventBus;

console.log("failovermod2 deployed");

eb.send("hatest", "failovermod2");