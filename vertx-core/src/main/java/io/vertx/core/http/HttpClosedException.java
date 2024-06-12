/*
 * Copyright (c) 2011-2022 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package io.vertx.core.http;

import io.vertx.core.VertxException;
import io.vertx.core.http.impl.HttpUtils;
import io.vertx.core.net.impl.ConnectionBase;

/**
 * Signals a HTTP connection close.
 */
public class HttpClosedException extends VertxException {

  private static String formatErrorMessage(GoAway goAway) {
    if (goAway == null) {
      return "Connection was closed";
    } else {
      return "Stream was closed (GOAWAY error code = " + goAway.getErrorCode() + ")";
    }
  }

  private final GoAway goAway;

  public HttpClosedException(String message) {
    super(message, true);
    this.goAway = null;
  }

  public HttpClosedException(GoAway goAway) {
    super(formatErrorMessage(goAway), true);
    this.goAway = goAway;
  }

  /**
   * @return the data received when the connection received a {@code GOAWAY} frame prior disconnection (HTTP/2 only)
   */
  public GoAway goAway() {
    return goAway != null ? new GoAway(goAway) : null;
  }
}
