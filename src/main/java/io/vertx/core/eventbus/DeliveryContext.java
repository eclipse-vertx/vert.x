package io.vertx.core.eventbus;

import io.vertx.codegen.annotations.VertxGen;

/**
 * Encapsulates a message being delivered by Vert.x. Used with event bus interceptors.
 */
@VertxGen
public interface DeliveryContext<T> {
  /**
   * @return The message being delivered
   */
  Message<T> message();

  /**
   * Call the next interceptor
   */
  void next();

  /**
   * @return true if the message was sent (point to point) or False if the message was published
   */
  boolean send();
}
