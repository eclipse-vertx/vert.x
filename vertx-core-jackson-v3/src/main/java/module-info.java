module io.vertx.core.jacksonv3 {

  requires io.vertx.core;
  requires tools.jackson.core;

  // API

  // Internal API

  provides io.vertx.core.spi.JsonFactory with io.vertx.core.jacksonv3.JacksonFactory;

}
