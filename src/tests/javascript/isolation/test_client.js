load('test_utils.js')
load('vertx.js')

var tu = new TestUtils();

var myglobal;

function testIsolation() {
  tu.azzert(myglobal == undefined);
  myglobal = 123;
  tu.testComplete();
}

tu.registerTests(this);
tu.appReady();

function vertxStop() {
  tu.unregisterAll();
  tu.appStopped();
}