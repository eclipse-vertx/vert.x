package io.vertx.core.eventbus;

import io.vertx.codegen.annotations.VertxGen;

/**
 *
 * Encapsulates a message being sent from Vert.x. Used with event bus interceptors
 *
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
@VertxGen
public interface SendContext<T> {

  /**
   * @return  The message being sent
   */
  Message<T> message();

  /**
   * Call the next interceptor
   */
  void next();

  /**
   *
   * @return true if the message is being sent (point to point) or False if the message is being published
   */
  boolean send();
}
