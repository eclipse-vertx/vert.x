load('vertx.js')

console.log("in foomod");

vertx.deployModule('barmod', null, 1, function(depID) {
  vertx.setTimer(1000, function() {
    vertx.undeployModule(depID);
  })
});


