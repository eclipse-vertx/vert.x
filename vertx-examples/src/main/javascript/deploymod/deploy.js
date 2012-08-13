load("vertx.js");

console.log("Deploying module");

var conf = {"some-var" : "hello"};

vertx.deployModule("org.foo.MyMod-v1.0", conf, 1, function(deploymentID) {
  console.log("This gets called when deployment is complete, deployment id is " + deploymentID);
});