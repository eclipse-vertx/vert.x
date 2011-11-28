package org.vertx.java.core.net;

import org.vertx.java.core.Handler;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class HandlerHolder<T> {
  public final long contextID;
  public final Handler<T> handler;

  HandlerHolder(long contextID, Handler<T> handler) {
    this.contextID = contextID;
    this.handler = handler;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    HandlerHolder that = (HandlerHolder) o;

    if (contextID != that.contextID) return false;
    if (handler != null ? !handler.equals(that.handler) : that.handler != null) return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = (int) (contextID ^ (contextID >>> 32));
    result = 31 * result + (handler != null ? handler.hashCode() : 0);
    return result;
  }
}
