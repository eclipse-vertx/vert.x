/*
 *  Copyright (c) 2011-2013 The original author or authors
 *  ------------------------------------------------------
 *  All rights reserved. This program and the accompanying materials
 *  are made available under the terms of the Eclipse Public License v1.0
 *  and Apache License v2.0 which accompanies this distribution.
 *
 *       The Eclipse Public License is available at
 *       http://www.eclipse.org/legal/epl-v10.html
 *
 *       The Apache License v2.0 is available at
 *       http://www.opensource.org/licenses/apache2.0.php
 *
 *  You may elect to redistribute this code under either of these licenses.
 */
package io.vertx.core.cli;

/**
 * High level exception thrown when an issue in the command line processing occurs.
 *
 * @author Clement Escoffier <clement@apache.org>
 */
public class CLIException extends RuntimeException {

  /**
   * Creates a new instance of {@link CLIException}.
   *
   * @param message the message
   */
  public CLIException(String message) {
    super(message);
  }

  /**
   * Creates a new instance of {@link CLIException}.
   *
   * @param message the message
   * @param cause   the cause
   */
  public CLIException(String message, Exception cause) {
    super(message, cause);
  }
}
