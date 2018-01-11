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
