package org.vertx.java.core.http.impl;

import java.util.HashMap;
import java.util.Map;

import org.vertx.java.core.http.HttpAttribute;
import org.vertx.java.core.http.HttpData;
import org.vertx.java.core.http.HttpDataHandler;
import org.vertx.java.core.http.HttpFileUpload;
import org.vertx.java.core.http.HttpMultipartFormDataHandler;
import org.vertx.java.core.http.HttpMultipartFormElementHandler;

public class DefaultHttpMultipartFormElementHandler
implements HttpMultipartFormElementHandler {

  private final Map<String, HandlerPair> handlers = 
      new HashMap<String, HandlerPair>();

  private static class HandlerPair {
    private final HttpDataHandler handler;
    private final HttpMultipartFormDataHandler dataHandler;
    public HandlerPair(HttpDataHandler handler, HttpMultipartFormDataHandler dataHandler) {
      super();
      this.handler = handler;
      this.dataHandler = dataHandler;
    }
    public void handle(HttpData data) {
      data.handler(dataHandler);
      if (handler != null) handler.handle(data, dataHandler);
    }
  }

  public void registerHandler(String name, 
      HttpDataHandler handler, HttpMultipartFormDataHandler dataHandler) {
    this.handlers.put(name, new HandlerPair(handler, dataHandler));
  }

  @Override
  public void handle(HttpAttribute att) {
    HandlerPair pair = handlers.get(att.getName());
    if (pair != null) pair.handle(att);
  }

  @Override
  public void handle(HttpFileUpload upload) {
    HandlerPair pair = handlers.get(upload.getName());
    if (pair != null) pair.handle(upload);
  }

}
