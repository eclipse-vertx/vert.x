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
 * This exception signals a stream reset, it is used only for HTTP/2.
 *
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class StreamResetException extends VertxException {

  private final long code;

  public StreamResetException(long code) {
    super("Stream reset: " + code);
    this.code = code;
  }

  /**
   * @return the reset error code
   */
  public long getCode() {
    return code;
  }
}
