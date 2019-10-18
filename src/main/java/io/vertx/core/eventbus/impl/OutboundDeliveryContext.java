/*
 * Copyright (c) 2011-2017 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package io.vertx.core.eventbus.impl;

import io.vertx.core.Handler;
import io.vertx.core.eventbus.DeliveryContext;
import io.vertx.core.eventbus.DeliveryOptions;
import io.vertx.core.eventbus.Message;
import io.vertx.core.impl.ContextInternal;

import java.util.Iterator;

public class OutboundDeliveryContext<T> implements DeliveryContext<T> {

  public final ContextInternal ctx;
  public final MessageImpl message;
  public final DeliveryOptions options;
  public final EventBusImpl.ReplyHandler<T> replyHandler;
  private final MessageImpl replierMessage;

  Iterator<Handler<DeliveryContext>> iter;
  EventBusImpl bus;

  OutboundDeliveryContext(ContextInternal ctx, MessageImpl message, DeliveryOptions options, EventBusImpl.ReplyHandler<T> replyHandler) {
    this(ctx, message, options, replyHandler, null);
  }

  OutboundDeliveryContext(ContextInternal ctx, MessageImpl message, DeliveryOptions options, EventBusImpl.ReplyHandler<T> replyHandler, MessageImpl replierMessage) {
    this.ctx = ctx;
    this.message = message;
    this.options = options;
    this.replierMessage = replierMessage;
    this.replyHandler = replyHandler;
  }

  @Override
  public Message<T> message() {
    return message;
  }

  @Override
  public void next() {
    if (iter.hasNext()) {
      Handler<DeliveryContext> handler = iter.next();
      try {
        if (handler != null) {
          handler.handle(this);
        } else {
          next();
        }
      } catch (Throwable t) {
        EventBusImpl.log.error("Failure in interceptor", t);
      }
    } else {
      if (replierMessage == null) {
        bus.sendOrPub(this);
      } else {
        bus.sendReply(this, replierMessage);
      }
    }
  }

  @Override
  public boolean send() {
    return message.isSend();
  }

  @Override
  public Object body() {
    return message.sentBody;
  }
}
