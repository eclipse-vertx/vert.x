load('vertx.js');

var persistor = new vertx.Persistor("demo.persistor");
persistor.start();

function vertxStop() {
  persistor.stop();
}

