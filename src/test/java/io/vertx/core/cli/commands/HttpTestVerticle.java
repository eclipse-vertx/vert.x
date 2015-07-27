package io.vertx.core.cli.commands;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.json.JsonObject;


public class HttpTestVerticle extends AbstractVerticle {


  @Override
  public void start() throws Exception {
    vertx.createHttpServer().requestHandler(request-> {
      JsonObject json = new JsonObject();
      json
          .put("clustered", vertx.isClustered())
          .put("metrics", vertx.isMetricsEnabled())
          .put("id", System.getProperty("vertx.id", "no id"))
          .put("conf", config());
      request.response().putHeader("content-type", "application/json").end(json.encodePrettily());
    }).listen(8080);
  }
}
