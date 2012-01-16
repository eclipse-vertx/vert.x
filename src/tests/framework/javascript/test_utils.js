var TestUtils = function() {

  var that = this;
  var jutils = new org.vertx.java.newtests.TestUtils();

  that.azzert = function(result, message) {
    if (message) {
      jutils.azzert(result, message);
    } else {
      jutils.azzert(result);
    }
  }

  that.appReady = function() {
    jutils.appReady();
  }

  that.appStopped = function() {
    jutils.appStopped();
  }

  that.testComplete = function() {
    jutils.testComplete();
  }

  that.register = function(testName, test) {
    jutils.register(testName, test);
  }

  that.unregisterAll = function() {
    jutils.unregisterAll();
  }

  that.checkContext = function() {
    jutils.checkContext();
  }

}
