load('test_utils.js')

var tu = new TestUtils();

log.println("in wibble");

var wibble = new org.vertx.java.core.Wibble();

var handler = function() {
  log.println("In handler");
};

handler.equals = function(other) {
  return false;
};

wibble.setHandler(handler);

wibble.removeHandler(handler);

tu.appStopped();

function vertxStop() {
  tu.appStopped();
}