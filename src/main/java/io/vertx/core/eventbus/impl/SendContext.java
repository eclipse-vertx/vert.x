package io.vertx.core.eventbus.impl;

import io.vertx.core.eventbus.DeliveryOptions;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class SendContext<T> {

  public final MessageImpl message;
  public final DeliveryOptions options;
  public final HandlerRegistration<T> handlerRegistration;


  public SendContext(MessageImpl message, DeliveryOptions options, HandlerRegistration<T> handlerRegistration) {
    this.message = message;
    this.options = options;
    this.handlerRegistration = handlerRegistration;
  }
}
