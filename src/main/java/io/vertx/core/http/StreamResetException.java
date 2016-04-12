/*
 * Copyright (c) 2011-2013 The original author or authors
 *  ------------------------------------------------------
 *  All rights reserved. This program and the accompanying materials
 *  are made available under the terms of the Eclipse Public License v1.0
 *  and Apache License v2.0 which accompanies this distribution.
 *
 *      The Eclipse Public License is available at
 *      http://www.eclipse.org/legal/epl-v10.html
 *
 *      The Apache License v2.0 is available at
 *      http://www.opensource.org/licenses/apache2.0.php
 *
 *  You may elect to redistribute this code under either of these licenses.
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
