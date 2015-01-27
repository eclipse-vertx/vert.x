/*
 * Copyright (c) 2011-2014 The original author or authors
 * ------------------------------------------------------
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * and Apache License v2.0 which accompanies this distribution.
 *
 *     The Eclipse Public License is available at
 *     http://www.eclipse.org/legal/epl-v10.html
 *
 *     The Apache License v2.0 is available at
 *     http://www.opensource.org/licenses/apache2.0.php
 *
 * You may elect to redistribute this code under either of these licenses.
 */

package io.vertx.core.eventbus;

/**
 * Represents the type of reply failure
 *
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public enum ReplyFailure {

  /**
   * The message send failed because no reply was received before the timeout time.
   */
  TIMEOUT,

  /**
   * The message send failed because no handlers were available to handle the message.
   */
  NO_HANDLERS,

  /**
   * The message send failed because the recipient actively sent back a failure (rejected the message)
   */
  RECIPIENT_FAILURE;

  public static ReplyFailure fromInt(int i) {
    switch (i) {
      case 0: return TIMEOUT;
      case 1: return NO_HANDLERS;
      case 2: return RECIPIENT_FAILURE;
      default: throw new IllegalStateException("Invalid index " + i);
    }
  }

  public int toInt() {
    switch (this) {
      case TIMEOUT: return 0;
      case NO_HANDLERS: return 1;
      case RECIPIENT_FAILURE: return 2;
      default: throw new IllegalStateException("How did we get here?");
    }
  }
}
