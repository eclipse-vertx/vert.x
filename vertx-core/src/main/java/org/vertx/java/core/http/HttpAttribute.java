package org.vertx.java.core.http;

public class HttpAttribute extends HttpData {

  public HttpAttribute(String name) {
    super(name);
  }

  @Override
  public boolean isFileUpload() {
    return false;
  }

}
