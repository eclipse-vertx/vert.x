package org.vertx.java.core.http;

import org.vertx.java.core.Handler;
import org.vertx.java.core.buffer.Buffer;
import org.vertx.java.core.http.impl.HttpReadStreamBase;

public abstract class HttpMultipartFormDataHandler extends HttpReadStreamBase {

  protected Handler<Buffer> dataHandler;
  protected Handler<Void> endHandler;
  private Handler<Exception> exceptionHandler;

  @Override
  public void dataHandler(Handler<Buffer> handler) {
    this.dataHandler = handler;
  }

  @Override
  public void exceptionHandler(Handler<Exception> handler) {
    this.exceptionHandler = handler;
  }

  @Override
  public void endHandler(Handler<Void> handler) {
    this.endHandler = handler;
  }

  protected void handleException(Exception e) {
    if (exceptionHandler != null) {
      exceptionHandler.handle(e);
    }
  }

  public void handleData(Buffer buffer, boolean isLast) {
    if (dataHandler != null) {
      dataHandler.handle(buffer);
    }
    if (isLast && endHandler != null) {
      endHandler.handle(null);
    }
  }

}
