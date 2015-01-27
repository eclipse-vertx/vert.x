/*
 * Copyright (c) 2011-2013 The original author or authors
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
}
