var TestUtils = function() {

  var that = this;
  var jutils = new org.vertx.java.framework.TestUtils(org.vertx.java.deploy.impl.VertxLocator.vertx);

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

  that.registerTests = function(obj) {
    for(var key in obj){
      var val = obj[key];
      if (typeof val === 'function' && key.substring(0, 4) === 'test') {
        jutils.register(key, val);
      }
   }
  }

  that.unregisterAll = function() {
    jutils.unregisterAll();
  }

  that.checkContext = function() {
    jutils.checkContext();
  }

  that.generateRandomBuffer = function(size) {
    return jutils.generateRandomBuffer(size);
  }

  that.randomUnicodeString = function(size) {
    return jutils.randomUnicodeString(size);
  }

  that.buffersEqual = function(buff1, buff2) {
    return jutils.buffersEqual(buff1, buff2);
  }

}
