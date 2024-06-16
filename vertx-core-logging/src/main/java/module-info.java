module io.vertx.core.logging {

  requires java.compiler;
  requires java.logging;
  requires java.naming;

  // Optional

  requires static org.apache.logging.log4j;
  requires static org.slf4j;

  // API

  exports io.vertx.core.logging;

  // Internal API

  exports io.vertx.core.internal.logging;

}
