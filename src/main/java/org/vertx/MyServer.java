package org.vertx;

import org.vertx.java.core.Handler;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class MyServer {

    private Handler<Object> handler;

    public void requestHandler(Handler<Object> handler) {
      this.handler = handler;
    }

    public void callHandler() {
      handler.handle(new Object());
    }

    public void callHandlerWithObject(Object obj) {
      handler.handle(obj);
    }


}
