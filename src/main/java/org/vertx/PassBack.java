package org.vertx;

import org.vertx.java.core.Handler;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class PassBack {

  private Handler<Object> handler;

  public void passBack(Object obj) {
    System.out.println("In Java, passing object back to JS");
    handler.handle(obj);
  }

  public void start(Handler<Object> handler) {
    System.out.println("Starting");
    this.handler = handler;
    handler.handle(new Object());
  }
}
