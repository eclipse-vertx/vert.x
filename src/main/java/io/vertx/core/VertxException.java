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

package io.vertx.core;


/*
 * Vert.x hates Java checked exceptions and doesn't want to pollute it's API with them.
 * <p>
 * This is a general purpose exception class that is often thrown from Vert.x APIs if things go wrong.
 *
 * @author <a href="http://tfox.org">Tim Fox</a>
 *
 */
public class VertxException extends RuntimeException {

  /**
   * Create an instance given a message
   *
   * @param message  the message
   */
  public VertxException(String message) {
    super(message);
  }

  /**
   * Create an instance given a message and a cause
   *
   * @param message  the message
   * @param cause  the cause
   */
  public VertxException(String message, Throwable cause) {
    super(message, cause);
  }

  /**
   * Create an instance given a cause
   *
   * @param cause  the cause
   */
  public VertxException(Throwable cause) {
    super(cause);
  }

  /**
   * Create an instance given a message
   *
   * @param message  the message
   * @param noStackTrace  disable stack trace capture
   */
  public VertxException(String message, boolean noStackTrace) {
    super(message, null, !noStackTrace, !noStackTrace);
  }
}
