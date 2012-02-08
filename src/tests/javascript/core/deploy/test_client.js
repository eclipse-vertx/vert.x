load('test_utils.js')
load('vertx.js')

var tu = new TestUtils();

var eb = vertx.EventBus;

function testDeploy() {
  eb.registerHandler("test-handler", function(message) {
    tu.azzert("started" === message);
    tu.testComplete();
  });

  vertx.deployVerticle("core/deploy/child.js");
}

function testUndeploy() {

  var id = vertx.deployVerticle("core/deploy/child.js");

  vertx.setTimer(100, function() {
    eb.registerHandler("test-handler", function(message) {
      tu.azzert("stopped" === message);
      tu.testComplete();
    });
    vertx.undeployVerticle(id);
  });

}

tu.registerTests(this);
tu.appReady();

function vertxStop() {
  tu.unregisterAll();
  tu.appStopped();
}