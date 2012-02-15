package org.vertx.java.core;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public abstract class BaseContext implements Context {

  private Object extraData;

  private VertxInternal vertx = VertxInternal.instance;

  public void setExtraData(Object data) {
    this.extraData = data;
  }

  public Object getExtraData() {
    return extraData;
  }

  protected Runnable wrapTask(final Runnable task) {
    return new Runnable() {
      public void run() {
        try {
          vertx.setContext(BaseContext.this);
          task.run();
        } catch (Throwable t) {
          VertxInternal.instance.reportException(t);
        }
      }
    };
  }
}
