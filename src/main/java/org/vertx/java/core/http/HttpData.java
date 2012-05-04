package org.vertx.java.core.http;

import org.vertx.java.core.buffer.Buffer;

public abstract class HttpData {

  private final String name;

  private HttpMultipartFormDataHandler handler;

  public HttpData(String name) {
    super();
    this.name = name;
  }

  public String getName() {
    return name;
  }

  public abstract boolean isFileUpload();

  public void handler(HttpMultipartFormDataHandler handler) {
    this.handler = handler;
  }

  protected void handleException(Exception e) {
    if (handler != null) {
      handler.handleException(e);
    }
  }

  public void handleData(Buffer buffer, boolean isLast) {
    if (handler != null) {
      handler.handleData(buffer, isLast);
    }
  }

  @Override
  public String toString() {
    return getClass().getSimpleName() + " [name=" + name + "]";
  }

}
