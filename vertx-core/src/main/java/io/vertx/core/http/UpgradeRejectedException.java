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

package io.vertx.core.http;

import io.vertx.core.MultiMap;
import io.vertx.core.VertxException;
import io.vertx.core.buffer.Buffer;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class UpgradeRejectedException extends VertxException {

  private final int status;
  private final MultiMap headers;
  private final Buffer body;

  public UpgradeRejectedException(String message, int status, MultiMap headers, Buffer content) {
    super(message);
    this.status = status;
    this.headers = headers;
    this.body = content;
  }

  /**
   * @return the status code of the response that rejected the upgrade
   */
  public int getStatus() {
    return status;
  }

  /**
   * @return the headers of the response that rejected the upgrade
   */
  public MultiMap getHeaders() {
    return headers;
  }

  /**
   * @return the body of the response that rejected the upgrade
   */
  public Buffer getBody() {
    return body;
  }
}
