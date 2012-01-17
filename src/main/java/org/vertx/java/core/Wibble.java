package org.vertx.java.core;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class Wibble {

  private Runnable handler;

  public void setHandler(Runnable handler) {
    this.handler = handler;
  }

  public void removeHandler(Runnable handler) {
    if (this.handler.equals(handler)) {
      handler = null;
    }
  }
}
