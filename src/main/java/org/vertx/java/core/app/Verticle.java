package org.vertx.java.core.app;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public interface Verticle {

  void start() throws Exception;

  void stop() throws Exception;
}
