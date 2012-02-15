package org.vertx.java.core.net;

import org.vertx.java.core.Context;
import org.vertx.java.core.Handler;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class HandlerHolder<T> {
  public final Context context;
  public final Handler<T> handler;

  HandlerHolder(Context context, Handler<T> handler) {
    this.context = context;
    this.handler = handler;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    HandlerHolder that = (HandlerHolder) o;

    if (context != that.context) return false;
    if (handler != null ? !handler.equals(that.handler) : that.handler != null) return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = context.hashCode();
    result = 31 * result + handler.hashCode();
    return result;
  }
}
