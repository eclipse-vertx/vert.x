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

import java.util.Collection;

/**
 * Exception thrown when an option was expected and was not found on the command line.
 */
public class MissingOptionException extends CommandLineException {
  private final Collection<OptionModel> expected;

  public MissingOptionException(Collection<OptionModel> expected) {
    super("The option"
        + (expected.size() > 1 ? "s" : "")
        + expected
        + (expected.size() > 1 ? "are" : "is")
        + " required");
    this.expected = expected;
  }

  public Collection<OptionModel> getExpected() {
    return expected;
  }
}
