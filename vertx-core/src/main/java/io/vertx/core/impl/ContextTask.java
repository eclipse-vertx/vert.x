package io.vertx.core.impl;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
@FunctionalInterface
public interface ContextTask {

  public void run() throws Exception;
}
