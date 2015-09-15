/*
 *  Copyright (c) 2011-2015 The original author or authors
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
