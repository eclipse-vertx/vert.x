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

import java.util.Collection;
import java.util.stream.Collectors;

/**
 * Exception thrown when an option was expected and was not found on the command line.
 *
 * @author Clement Escoffier <clement@apache.org>
 */
public class MissingOptionException extends CLIException {
  private final Collection<Option> expected;

  /**
   * Creates a new instance of {@link MissingOptionException}.
   *
   * @param expected the list of expected options (missing options)
   */
  public MissingOptionException(Collection<Option> expected) {
    super("The option"
        + (expected.size() > 1 ? "s " : " ")
        + expected.stream().map(Option::getName).collect(Collectors.toList())
        + (expected.size() > 1 ? " are" : " is")
        + " required");
    this.expected = expected;
  }

  /**
   * @return the missing (mandatory) options.
   */
  public Collection<Option> getExpected() {
    return expected;
  }
}
