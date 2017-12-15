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

package io.vertx.core.eventbus;

import io.vertx.codegen.annotations.VertxGen;

/**
 * Represents the type of reply failure
 *
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
@VertxGen
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
