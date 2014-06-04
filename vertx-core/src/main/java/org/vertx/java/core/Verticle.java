package org.vertx.java.core;

import org.vertx.java.core.json.JsonObject;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public interface Verticle {

  JsonObject getConfig();

  Vertx getVertx();

  void setVertx(Vertx vertx);

  void setConfig(JsonObject config);

  void start(Future<Void> startFuture) throws Exception;

  void stop(Future<Void> stopFuture) throws Exception;
}
