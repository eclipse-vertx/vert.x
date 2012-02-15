package org.vertx.java.core;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public interface Context {

  void execute(Runnable task);

  void setExtraData(Object data);

  Object getExtraData();
}
