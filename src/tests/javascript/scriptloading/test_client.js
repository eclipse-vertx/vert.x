load("test_utils.js")
load("scriptloading/script1.js");

var tu = new TestUtils();

function testScriptLoading() {
  tu.azzert(func1() === 'foo');
  tu.testComplete();
}

tu.registerTests(this);
tu.appReady();

function vertxStop() {
  tu.unregisterAll();
  tu.appStopped();
}
