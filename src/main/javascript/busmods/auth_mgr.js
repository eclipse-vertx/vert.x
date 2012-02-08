// This is just a wrapper around the Java auth manager

var j_auth = new org.vertx.java.busmods.auth.AuthManager();

j_auth.start();

function vertxStop() {
  j_auth.stop();
}

