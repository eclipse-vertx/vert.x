/*
 * Copyright (c) 2011-2023 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package io.vertx.core.eventbus.impl;

import io.vertx.core.eventbus.Message;
import io.vertx.core.spi.tracing.TagExtractor;

/**
 * A tag extractor for an event-bus message.
 *
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
class MessageTagExtractor implements TagExtractor<Message<?>> {

  static final MessageTagExtractor INSTANCE = new MessageTagExtractor();

  private MessageTagExtractor() {
  }

  @Override
  public int len(Message<?> obj) {
    return 3;
  }

  @Override
  public String name(Message<?> obj, int index) {
    // https://opentelemetry.io/docs/specs/semconv/messaging/messaging-spans/
    switch (index) {
      case 0:
        return "messaging.destination.name";
      case 1:
        return "messaging.system";
      case 2:
        return "messaging.operation.name";
    }
    throw new IndexOutOfBoundsException("Invalid tag index " + index);
  }

  @Override
  public String value(Message<?> obj, int index) {
    switch (index) {
      case 0:
        return obj.address();
      case 1:
        return "vertx-eventbus";
      case 2:
        return obj.isSend() ? "send" : "publish";
    }
    throw new IndexOutOfBoundsException("Invalid tag index " + index);
  }
}
