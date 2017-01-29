package io.vertx.core.eventbus;

import io.vertx.codegen.annotations.VertxGen;
import io.vertx.core.Handler;

/**
 * An eventbus interceptor that will be called whenever a message is sent from, and delivered by,
 * Vert.x
 */
@VertxGen
public interface Interceptor extends Handler<SendContext> {
  /**
   * A message delivery attempt is about to occur.
   *
   * @param context the message delivery context to handle
   */
  default void handleDelivery(DeliveryContext context) {
    context.next();
  }
}
