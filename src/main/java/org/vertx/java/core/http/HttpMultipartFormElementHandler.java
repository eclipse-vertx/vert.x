package org.vertx.java.core.http;

public interface HttpMultipartFormElementHandler {

  void handle(HttpAttribute att);
  void handle(HttpFileUpload upload);

}
