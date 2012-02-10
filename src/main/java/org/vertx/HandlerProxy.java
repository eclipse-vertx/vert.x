package org.vertx;

import org.vertx.java.core.Handler;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class HandlerProxy {

  private Handler<Object> handler;

  public HandlerProxy(Handler<Object> handler) {
    this.handler = handler;
  }

  public void handle(Object obj) {
    handler.handle(obj);
  }
}
