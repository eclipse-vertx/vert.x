// This is just a wrapper around the Java persistor

var j_pers = new org.vertx.java.busmods.persistor.MongoPersistor();

j_pers.start();

function vertxStop() {
  j_pers.stop();
}
