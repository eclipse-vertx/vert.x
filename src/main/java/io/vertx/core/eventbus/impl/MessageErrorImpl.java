/*
 * Copyright (c) 2011-2019 Contributors to the Eclipse Foundation
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
import io.vertx.core.eventbus.MessageError;

/**
 * @author Tom Fingerman 02/07/2019.
 */
public class MessageErrorImpl implements MessageError {
  private final Message message;
  private final Throwable exception;

  public MessageErrorImpl(Message message, Throwable exception) {
    this.message = message;
    this.exception = exception;
  }

  @Override
  public Message message() {
    return message;
  }

  @Override
  public Throwable exception() {
    return exception;
  }
}
