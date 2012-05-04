package org.vertx.java.core.http.impl;

import org.vertx.java.core.http.HttpMultipartFormDataHandler;

public class DefaultHttpMultipartFormDataHandler
extends HttpMultipartFormDataHandler {

  private final ServerConnection conn;

  public DefaultHttpMultipartFormDataHandler(ServerConnection conn) {
    this.conn = conn;
  }

  @Override
  public void pause() {
    conn.pause();
  }

  @Override
  public void resume() {
    conn.resume();
  }

}
