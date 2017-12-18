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


import java.util.List;
import java.util.stream.Collectors;

/**
 * Exception thrown when the command line is ambiguous meaning it cannot determine exactly which option has to be set.
 *
 * @author Clement Escoffier <clement@apache.org>
 */
public class AmbiguousOptionException extends CLIException {

  private final List<Option> options;
  private final String token;


  /**
   * Creates a new instance of {@link AmbiguousOptionException}.
   *
   * @param token        the ambiguous token
   * @param matchingOpts the list of potential matches
   */
  public AmbiguousOptionException(String token, List<Option> matchingOpts) {
    super("Ambiguous argument in command line: '" + token + "' matches "
        + matchingOpts.stream().map(Option::getName).collect(Collectors.toList()));
    this.token = token;
    this.options = matchingOpts;
  }

  /**
   * @return the list of potential matches.
   */
  public List<Option> getOptions() {
    return options;
  }

  /**
   * @return the token triggering the ambiguity.
   */
  public String getToken() {
    return token;
  }
}
