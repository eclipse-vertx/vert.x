/*
 * Copyright (c) 2011-2017 The original author or authors
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * and Apache License v2.0 which accompanies this distribution.
 *
 *     The Eclipse Public License is available at
 *     https://www.eclipse.org/legal/epl-2.0/
 *
 *     The Apache License v2.0 is available at
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * You may elect to redistribute this code under either of these licenses.
 */
package io.vertx.core.http;

import io.vertx.core.VertxException;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class WebsocketRejectedException extends VertxException {

  private final int status;

  public WebsocketRejectedException(int status) {
    super("Websocket connection attempt returned HTTP status code " + status);
    this.status = status;
  }

  /**
   * @return the status code of the response that rejected the upgrade
   */
  public int getStatus() {
    return status;
  }
}
