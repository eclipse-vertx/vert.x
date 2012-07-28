package org.vertx.java.core.http;

public interface HttpDataHandler {

  void handle(HttpData data, HttpMultipartFormDataHandler dataHandler);
}
