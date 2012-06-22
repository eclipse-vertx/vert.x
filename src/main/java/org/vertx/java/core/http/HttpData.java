package org.vertx.java.core.http;

import org.vertx.java.core.Handler;
import org.vertx.java.core.buffer.Buffer;

public abstract class HttpData {

  private final String name;

  private HttpMultipartFormDataHandler handler;
  private Handler<Void> endHandler;

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

  public void endHandler(Handler<Void> handler) {
    this.endHandler = handler;
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
    if (isLast && endHandler != null) endHandler.handle(null);
  }

  @Override
  public String toString() {
    return getClass().getSimpleName() + " [name=" + name + "]";
  }

}
