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

package io.vertx.core.impl;

import io.vertx.core.VertxException;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 * @deprecated for removal in Vert.x 6, instead catch {@link io.vertx.core.VertxException}
 */
@Deprecated(forRemoval = true)
public class NoStackTraceException extends VertxException {

  /**
   * @deprecated instead use {@link io.vertx.core.VertxException#noStackTrace(String)}
   */
  public NoStackTraceException(String message) {
    super(message, null, true);
  }

  /**
   * @deprecated instead use {@link io.vertx.core.VertxException#noStackTrace(Throwable)}
   */
  public NoStackTraceException(Throwable cause) {
    super(cause, true);
  }
}
